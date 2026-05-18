#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
source scripts/runtime/lib_phase04_public_api.sh
source scripts/runtime/lib_phase06_webhooks.sh

received_file="/tmp/minifin-phase06-webhook-replay-received.jsonl"
receiver_log="/tmp/minifin-phase06-webhook-replay-receiver.log"

pa_register_merchant "wbh03-a"
cookie_a="${PA_COOKIE_JAR}"
employee_a="${PA_EMPLOYEE_ID}"

api_key_body="/tmp/minifin-wbh03-api-key.json"
pa_create_api_key "${cookie_a}" "wbh03" "${api_key_body}"
api_key="$(node -e "const j=require('${api_key_body}'); if(!j.data?.key) process.exit(1); console.log(j.data.key)")"

endpoint_seed="/tmp/minifin-wbh03-endpoint-seed.json"
pa_curl -fsS -b "${cookie_a}" -X POST "${base_url}/api/v1/merchant/webhook-endpoints" \
  -H "Content-Type: application/json" \
  -d '{"url":"http://host.docker.internal:39091/webhooks/minifin","enabledEvents":["payment_intent.created"],"status":"ACTIVE","description":"wbh03 replay receiver"}' \
  >"${endpoint_seed}"
signing_secret="$(node -e "const j=require('${endpoint_seed}'); if(!j.data?.signingSecret||j.errors.length) process.exit(1); console.log(j.data.signingSecret)")"
endpoint_id="$(node -e "const j=require('${endpoint_seed}'); console.log(j.data.id)")"

MINIFIN_WEBHOOK_RESPONSE_STATUS=500 phase06_start_receiver "${signing_secret}" "${received_file}" "${receiver_log}"
trap phase06_stop_receiver EXIT
pa_psql "update merchant.webhook_endpoints set url = '${PHASE06_RECEIVER_URL}' where id = '${endpoint_id}';" >/dev/null

create_body="/tmp/minifin-wbh03-payment-intent.json"
status="$(pa_public_post_payment_intent "${api_key}" "wbh03-$(date +%s%N)" '{"amount":"15.50","currency":"EUR","description":"wbh03"}' "${create_body}")"
test "${status}" = "201"
intent_id="$(node -e "const j=require('${create_body}'); if(j.data?.state!=='REQUIRES_PAYMENT_METHOD') process.exit(1); console.log(j.data.id)")"
event_id="$(pa_psql "select id from merchant.webhook_events where aggregate_id = '${intent_id}' and event_type = 'payment_intent.created';")"
test -n "${event_id}"

for _ in $(seq 1 8); do
  current_status="$(pa_psql "select status from merchant.webhook_events where id = '${event_id}';")"
  if test "${current_status}" = "DLQ"; then
    break
  fi
  sleep 1
  pa_curl -fsS -X POST "${base_url}/internal/merchant/webhooks/dispatch-due?limit=10" >/tmp/minifin-wbh03-dispatch.json
done

test "$(pa_psql "select status from merchant.webhook_events where id = '${event_id}';")" = "DLQ"

list_body="/tmp/minifin-wbh03-list.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/webhook-events?status=DLQ" >"${list_body}"
node -e "const j=require('${list_body}'); if(!j.data?.items?.some(e=>e.id==='${event_id}'&&e.status==='DLQ')||j.errors.length) process.exit(1)"

detail_body="/tmp/minifin-wbh03-detail.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/webhook-events/${event_id}" >"${detail_body}"
node -e "const j=require('${detail_body}'); if(j.data?.id!=='${event_id}'||j.data?.status!=='DLQ'||j.errors.length) process.exit(1)"

pa_psql "update identity.merchant_employees set role = 'merchant_member' where id = '${employee_a}';" >/dev/null
member_detail="/tmp/minifin-wbh03-member-detail.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/webhook-events/${event_id}" >"${member_detail}"
node -e "const j=require('${member_detail}'); if(j.data?.id!=='${event_id}'||j.errors.length) process.exit(1)"
member_replay="/tmp/minifin-wbh03-member-replay.json"
member_status="$(pa_curl -sS -o "${member_replay}" -w "%{http_code}" -b "${cookie_a}" -X POST "${base_url}/api/v1/merchant/webhook-events/${event_id}/replay")"
test "${member_status}" = "403"
node -e "const j=require('${member_replay}'); if(j.errors?.[0]?.code!=='forbidden_role') process.exit(1)"
pa_psql "update identity.merchant_employees set role = 'merchant_admin' where id = '${employee_a}';" >/dev/null

pa_register_merchant "wbh03-b"
cookie_b="${PA_COOKIE_JAR}"
other_detail="/tmp/minifin-wbh03-other-detail.json"
other_status="$(pa_curl -sS -o "${other_detail}" -w "%{http_code}" -b "${cookie_b}" "${base_url}/api/v1/merchant/webhook-events/${event_id}")"
test "${other_status}" = "404"
other_replay="/tmp/minifin-wbh03-other-replay.json"
other_replay_status="$(pa_curl -sS -o "${other_replay}" -w "%{http_code}" -b "${cookie_b}" -X POST "${base_url}/api/v1/merchant/webhook-events/${event_id}/replay")"
test "${other_replay_status}" = "404"

phase06_stop_receiver
MINIFIN_WEBHOOK_RESPONSE_STATUS=200 phase06_start_receiver "${signing_secret}" "${received_file}" "${receiver_log}"

replay_body="/tmp/minifin-wbh03-replay.json"
pa_curl -fsS -b "${cookie_a}" -X POST "${base_url}/api/v1/merchant/webhook-events/${event_id}/replay" >"${replay_body}"
node -e "const j=require('${replay_body}'); if(j.data?.eventId!=='${event_id}'||j.data?.status!=='DELIVERED'||j.data?.replayAttemptNumber!==4||j.data?.httpStatus!==200||j.errors.length) process.exit(1)"

event_state="$(pa_psql "select status from merchant.webhook_events where id = '${event_id}';")"
test "${event_state}" = "DELIVERED"
manual_attempt="$(pa_psql "select count(*) from merchant.webhook_delivery_attempts where event_id = '${event_id}' and endpoint_id = '${endpoint_id}' and attempt_number = 4 and status = 'SUCCEEDED' and http_status = 200 and trigger_type = 'MANUAL_REPLAY';")"
test "${manual_attempt}" = "1"

non_dlq_body="/tmp/minifin-wbh03-non-dlq-replay.json"
non_dlq_status="$(pa_curl -sS -o "${non_dlq_body}" -w "%{http_code}" -b "${cookie_a}" -X POST "${base_url}/api/v1/merchant/webhook-events/${event_id}/replay")"
test "${non_dlq_status}" = "409"
node -e "const j=require('${non_dlq_body}'); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1)"

node -e "const fs=require('fs'); const rows=fs.readFileSync('${received_file}','utf8').trim().split(/\n/).map(JSON.parse); if(rows.length!==1) process.exit(1); const r=rows[0]; if(r.id!=='evt_${event_id}'||r.event!=='payment_intent.created'||r.attempt!=='4'||r.body.data.object.id!=='${intent_id}') process.exit(1);"

echo "WBH-03 webhook dlq replay pass event_id=${event_id} endpoint_id=${endpoint_id} replay_attempt=4 final_status=${event_state}"
