#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
source scripts/runtime/lib_phase04_public_api.sh

pa_register_merchant "mrc05-a"
cookie_a="${PA_COOKIE_JAR}"
employee_a="${PA_EMPLOYEE_ID}"

create_body="/tmp/minifin-mrc05-create.json"
pa_curl -fsS -b "${cookie_a}" -X POST "${base_url}/api/v1/merchant/webhook-endpoints" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://merchant.example.test/webhooks/minifin-a","enabledEvents":["payment_intent.created"],"status":"ACTIVE","description":"primary"}' \
  >"${create_body}"
endpoint_id="$(node -e "const j=require('${create_body}'); if(!j.data.id||j.data.status!=='ACTIVE'||j.errors.length) process.exit(1); console.log(j.data.id)")"

list_body="/tmp/minifin-mrc05-list.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/webhook-endpoints" >"${list_body}"
node -e "const j=require('${list_body}'); if(!Array.isArray(j.data)||!j.data.some(x=>x.id==='${endpoint_id}')||j.errors.length) process.exit(1)"

update_body="/tmp/minifin-mrc05-update.json"
pa_curl -fsS -b "${cookie_a}" -X PUT "${base_url}/api/v1/merchant/webhook-endpoints/${endpoint_id}" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://merchant.example.test/webhooks/minifin-a-updated","enabledEvents":["payment_intent.created","chargeback.created"],"status":"DISABLED","description":"updated"}' \
  >"${update_body}"
node -e "const j=require('${update_body}'); if(j.data.status!=='DISABLED'||j.data.enabledEvents.length!==2||j.errors.length) process.exit(1)"

pa_psql "update identity.merchant_employees set role = 'merchant_member' where id = '${employee_a}';" >/dev/null
member_body="/tmp/minifin-mrc05-member.json"
member_status="$(pa_curl -sS -o "${member_body}" -w "%{http_code}" -b "${cookie_a}" -X POST "${base_url}/api/v1/merchant/webhook-endpoints" -H "Content-Type: application/json" -d '{"url":"https://merchant.example.test/webhooks/member","enabledEvents":["payment_intent.created"],"status":"ACTIVE"}')"
test "${member_status}" = "403"
node -e "const j=require('${member_body}'); if(j.errors?.[0]?.code!=='forbidden_role') process.exit(1)"
member_list="/tmp/minifin-mrc05-member-list.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/webhook-endpoints" >"${member_list}"
node -e "const j=require('${member_list}'); if(!Array.isArray(j.data)||!j.data.some(x=>x.id==='${endpoint_id}')) process.exit(1)"
pa_psql "update identity.merchant_employees set role = 'merchant_admin' where id = '${employee_a}';" >/dev/null

pa_register_merchant "mrc05-b"
cookie_b="${PA_COOKIE_JAR}"
other_list="/tmp/minifin-mrc05-other-list.json"
pa_curl -fsS -b "${cookie_b}" "${base_url}/api/v1/merchant/webhook-endpoints" >"${other_list}"
node -e "const j=require('${other_list}'); if(!Array.isArray(j.data)||j.data.some(x=>x.id==='${endpoint_id}')) process.exit(1)"
other_delete="/tmp/minifin-mrc05-other-delete.json"
other_status="$(pa_curl -sS -o "${other_delete}" -w "%{http_code}" -b "${cookie_b}" -X DELETE "${base_url}/api/v1/merchant/webhook-endpoints/${endpoint_id}")"
test "${other_status}" = "404"

delete_body="/tmp/minifin-mrc05-delete.json"
pa_curl -fsS -b "${cookie_a}" -X DELETE "${base_url}/api/v1/merchant/webhook-endpoints/${endpoint_id}" >"${delete_body}"
node -e "const j=require('${delete_body}'); if(j.data.status!=='DELETED'||j.errors.length) process.exit(1)"
remaining="$(pa_psql "select count(*) from merchant.webhook_endpoints where id = '${endpoint_id}' and status = 'DELETED';")"
test "${remaining}" = "1"

echo "MRC-05 merchant webhook endpoint config pass"
