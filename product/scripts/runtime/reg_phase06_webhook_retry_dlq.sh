#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
source scripts/runtime/lib_phase04_public_api.sh
source scripts/runtime/lib_phase06_webhooks.sh

received_file="/tmp/minifin-phase06-webhook-dlq-received.jsonl"
receiver_log="/tmp/minifin-phase06-webhook-dlq-receiver.log"

pa_register_merchant "wbh02"
cookie="${PA_COOKIE_JAR}"

api_key_body="/tmp/minifin-wbh02-api-key.json"
pa_create_api_key "${cookie}" "wbh02" "${api_key_body}"
api_key="$(node -e "const j=require('${api_key_body}'); if(!j.data?.key) process.exit(1); console.log(j.data.key)")"

endpoint_seed="/tmp/minifin-wbh02-endpoint-seed.json"
pa_curl -fsS -b "${cookie}" -X POST "${base_url}/api/v1/merchant/webhook-endpoints" \
  -H "Content-Type: application/json" \
  -d '{"url":"http://host.docker.internal:39091/webhooks/minifin","enabledEvents":["payment_intent.created"],"status":"ACTIVE","description":"wbh02 failing receiver"}' \
  >"${endpoint_seed}"
signing_secret="$(node -e "const j=require('${endpoint_seed}'); if(!j.data?.signingSecret||j.errors.length) process.exit(1); console.log(j.data.signingSecret)")"
endpoint_id="$(node -e "const j=require('${endpoint_seed}'); console.log(j.data.id)")"

MINIFIN_WEBHOOK_RESPONSE_STATUS=500 phase06_start_receiver "${signing_secret}" "${received_file}" "${receiver_log}"
trap phase06_stop_receiver EXIT
pa_psql "update merchant.webhook_endpoints set url = '${PHASE06_RECEIVER_URL}' where id = '${endpoint_id}';" >/dev/null

create_body="/tmp/minifin-wbh02-payment-intent.json"
status="$(pa_public_post_payment_intent "${api_key}" "wbh02-$(date +%s%N)" '{"amount":"14.50","currency":"EUR","description":"wbh02"}' "${create_body}")"
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
  pa_curl -fsS -X POST "${base_url}/internal/merchant/webhooks/dispatch-due?limit=10" >/tmp/minifin-wbh02-dispatch.json
 done

final_status="$(pa_psql "select status from merchant.webhook_events where id = '${event_id}';")"
test "${final_status}" = "DLQ"
attempt_count="$(pa_psql "select count(*) from merchant.webhook_delivery_attempts where event_id = '${event_id}' and endpoint_id = '${endpoint_id}' and status = 'FAILED';")"
test "${attempt_count}" = "3"
attempt_numbers="$(pa_psql "select string_agg(attempt_number::text, ',' order by attempt_number) from merchant.webhook_delivery_attempts where event_id = '${event_id}' and endpoint_id = '${endpoint_id}';")"
test "${attempt_numbers}" = "1,2,3"
dlq_state="$(pa_psql "select count(*) from merchant.webhook_events where id = '${event_id}' and status = 'DLQ' and dlq_at is not null and next_retry_at is null and last_http_status = 500;")"
test "${dlq_state}" = "1"

node -e "const fs=require('fs'); const rows=fs.readFileSync('${received_file}','utf8').trim().split(/\n/).map(JSON.parse); if(rows.length!==3) process.exit(1); const ids=new Set(rows.map(r=>r.id)); if(ids.size!==1) process.exit(1); const attempts=rows.map(r=>r.attempt).join(','); if(attempts!=='1,2,3') process.exit(1); for (const r of rows) { if(r.event!=='payment_intent.created'||r.body.data.object.id!=='${intent_id}') process.exit(1); }"

echo "WBH-02 webhook retry dlq pass event_id=${event_id} endpoint_id=${endpoint_id} attempts=${attempt_count} final_status=${final_status}"
