#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
source scripts/runtime/lib_phase04_public_api.sh

pa_register_merchant "mrc04-a"
cookie_a="${PA_COOKIE_JAR}"
merchant_a="${PA_MERCHANT_ID}"
key_body_a="/tmp/minifin-mrc04-key-a.json"
pa_create_api_key "${cookie_a}" "mrc04-a" "${key_body_a}"
api_key_a="$(node -e "const j=require('${key_body_a}'); console.log(j.data.key)")"

pi_body="/tmp/minifin-mrc04-pi.json"
status="$(pa_public_post_payment_intent "${api_key_a}" "mrc04-$(date +%s%N)" '{"amount":"19.99","currency":"EUR","description":"dashboard read"}' "${pi_body}")"
test "${status}" = "201"
intent_id="$(node -e "const j=require('${pi_body}'); console.log(j.data.id)")"

list_body="/tmp/minifin-mrc04-list.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/payment-intents" >"${list_body}"
node -e "const j=require('${list_body}'); if(!Array.isArray(j.data.items)||!j.data.items.some(x=>x.id==='${intent_id}')||j.errors.length) process.exit(1)"

detail_body="/tmp/minifin-mrc04-detail.json"
pa_curl -fsS -b "${cookie_a}" "${base_url}/api/v1/merchant/payment-intents/${intent_id}" >"${detail_body}"
node -e "const j=require('${detail_body}'); if(j.data.id!=='${intent_id}'||j.data.state!=='REQUIRES_PAYMENT_METHOD'||j.errors.length) process.exit(1)"

pa_register_merchant "mrc04-b"
cookie_b="${PA_COOKIE_JAR}"
other_list="/tmp/minifin-mrc04-other-list.json"
pa_curl -fsS -b "${cookie_b}" "${base_url}/api/v1/merchant/payment-intents" >"${other_list}"
node -e "const j=require('${other_list}'); if(!Array.isArray(j.data.items)||j.data.items.some(x=>x.id==='${intent_id}')) process.exit(1)"

other_detail="/tmp/minifin-mrc04-other-detail.json"
other_status="$(pa_curl -sS -o "${other_detail}" -w "%{http_code}" -b "${cookie_b}" "${base_url}/api/v1/merchant/payment-intents/${intent_id}")"
test "${other_status}" = "404"
node -e "const j=require('${other_detail}'); if(j.errors?.[0]?.code!=='payment_intent_not_found') process.exit(1)"

db_count="$(pa_psql "select count(*) from merchant.payment_intents where merchant_id = '${merchant_a}' and id = '${intent_id}';")"
test "${db_count}" = "1"

echo "MRC-04 merchant dashboard payment reads pass"
