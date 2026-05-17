#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
source scripts/runtime/lib_phase04_public_api.sh
source scripts/runtime/lib_phase06_webhooks.sh

received_file="/tmp/minifin-phase06-webhook-received.jsonl"
receiver_log="/tmp/minifin-phase06-webhook-receiver.log"

pa_register_merchant "wbh01"
cookie="${PA_COOKIE_JAR}"

api_key_body="/tmp/minifin-wbh01-api-key.json"
pa_create_api_key "${cookie}" "wbh01" "${api_key_body}"
api_key="$(node -e "const j=require('${api_key_body}'); if(!j.data?.key) process.exit(1); console.log(j.data.key)")"

endpoint_seed="/tmp/minifin-wbh01-endpoint-seed.json"
pa_curl -fsS -b "${cookie}" -X POST "${base_url}/api/v1/merchant/webhook-endpoints" \
  -H "Content-Type: application/json" \
  -d '{"url":"http://host.docker.internal:39091/webhooks/minifin","enabledEvents":["payment_intent.created"],"status":"ACTIVE","description":"wbh01 local receiver"}' \
  >"${endpoint_seed}"
signing_secret="$(node -e "const j=require('${endpoint_seed}'); if(!j.data?.signingSecret||j.errors.length) process.exit(1); console.log(j.data.signingSecret)")"
endpoint_id="$(node -e "const j=require('${endpoint_seed}'); console.log(j.data.id)")"
list_body="/tmp/minifin-wbh01-endpoint-list.json"
pa_curl -fsS -b "${cookie}" "${base_url}/api/v1/merchant/webhook-endpoints" >"${list_body}"
node -e "const j=require('${list_body}'); const e=j.data.find(x=>x.id==='${endpoint_id}'); if(!e||e.signingSecret) process.exit(1)"
rotate_body="/tmp/minifin-wbh01-endpoint-rotate.json"
pa_curl -fsS -b "${cookie}" -X POST "${base_url}/api/v1/merchant/webhook-endpoints/${endpoint_id}/rotate-secret" >"${rotate_body}"
signing_secret="$(node -e "const j=require('${rotate_body}'); if(!j.data?.signingSecret||j.data.signingSecret==='${signing_secret}') process.exit(1); console.log(j.data.signingSecret)")"

phase06_start_receiver "${signing_secret}" "${received_file}" "${receiver_log}"
trap phase06_stop_receiver EXIT
pa_psql "update merchant.webhook_endpoints set url = '${PHASE06_RECEIVER_URL}' where id = '${endpoint_id}';" >/dev/null

create_body="/tmp/minifin-wbh01-payment-intent.json"
status="$(pa_public_post_payment_intent "${api_key}" "wbh01-$(date +%s%N)" '{"amount":"12.50","currency":"EUR","description":"wbh01"}' "${create_body}")"
test "${status}" = "201"
intent_id="$(node -e "const j=require('${create_body}'); if(j.data?.state!=='REQUIRES_PAYMENT_METHOD') process.exit(1); console.log(j.data.id)")"

for _ in $(seq 1 50); do
  if test -s "${received_file}"; then
    break
  fi
  sleep 0.1
done
test -s "${received_file}"

node -e "const fs=require('fs'); const rows=fs.readFileSync('${received_file}','utf8').trim().split(/\n/).map(JSON.parse); if(rows.length!==1) process.exit(1); const r=rows[0]; if(r.event!=='payment_intent.created'||r.attempt!=='1'||r.body.data.object.id!=='${intent_id}'||r.body.data.object.amount!=='12.50') process.exit(1);"

event_count="$(pa_psql "select count(*) from merchant.webhook_events where aggregate_id = '${intent_id}' and event_type = 'payment_intent.created' and status = 'DELIVERED';")"
test "${event_count}" = "1"
attempt_count="$(pa_psql "select count(*) from merchant.webhook_delivery_attempts a join merchant.webhook_events e on e.id = a.event_id where e.aggregate_id = '${intent_id}' and a.status = 'SUCCEEDED' and a.http_status between 200 and 299;")"
test "${attempt_count}" = "1"

replay_body="/tmp/minifin-wbh01-payment-intent-replay.json"
replay_status="$(pa_public_post_payment_intent "${api_key}" "wbh01-replay-fixed" '{"amount":"13.50","currency":"EUR","description":"wbh01 replay"}' "${replay_body}")"
test "${replay_status}" = "201"
replay_id="$(node -e "const j=require('${replay_body}'); console.log(j.data.id)")"
pa_public_post_payment_intent "${api_key}" "wbh01-replay-fixed" '{"amount":"13.50","currency":"EUR","description":"wbh01 replay"}' /tmp/minifin-wbh01-payment-intent-replay2.json >/dev/null
dup_events="$(pa_psql "select count(*) from merchant.webhook_events where aggregate_id = '${replay_id}' and event_type = 'payment_intent.created';")"
test "${dup_events}" = "1"

echo "WBH-01 outbound webhook signing/delivery pass"
