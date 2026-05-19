#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="pay-capture-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
capture_body="/tmp/minifin-${tag}-capture.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"

# Setup: merchant, API key, end user, card, wallet funds
pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase05 capture" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"
wallet_id="$(auth_wallet_for_card_id "${card_id}")"
auth_seed_wallet_balance "${wallet_id}" "50.0000"

# Create and authorize payment intent
status="$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"12.50","currency":"EUR","description":"capture test"}' "${intent_body}")"
test "${status}" = "201"
intent_id="$(auth_extract_payment_intent_id "${intent_body}")"
status="$(auth_public_authorize "${api_key}" "${intent_id}" "auth-${tag}" "{\"cardToken\":\"${card_token}\",\"amount\":\"12.50\",\"currency\":\"EUR\"}" "${auth_body}")"
test "${status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='AUTHORIZED') process.exit(1);" "${auth_body}"

# Capture with idempotency key
capture_status="$(pa_curl -sS -o "${capture_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Idempotency-Key: cap-${tag}" \
  -H "Content-Type: application/json" \
  -d '{}')"
test "${capture_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='CAPTURED'||!j.data?.capturedAt||j.errors?.length) { console.error(JSON.stringify(j)); process.exit(1); }" "${capture_body}"

# DB assertions
db_state="$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")"
test "${db_state}" = "CAPTURED"
db_captured_at="$(p05_platform_psql "select captured_at is not null from merchant.payment_intents where id='${intent_id}'::uuid;")"
test "${db_captured_at}" = "t"
db_captured_amount="$(p05_platform_psql "select captured_amount from merchant.payment_intents where id='${intent_id}'::uuid;")"
test "${db_captured_amount}" = "12.5000"

# Same idempotency key replay returns same response
replay_body="/tmp/minifin-${tag}-replay.json"
replay_status="$(pa_curl -sS -o "${replay_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Idempotency-Key: cap-${tag}" \
  -H "Content-Type: application/json" \
  -d '{}')"
test "${replay_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='CAPTURED'||j.errors?.length) process.exit(1);" "${replay_body}"

# Same key different body returns 409 conflict
conflict_body="/tmp/minifin-${tag}-conflict.json"
conflict_status="$(pa_curl -sS -o "${conflict_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Idempotency-Key: cap-${tag}" \
  -H "Content-Type: application/json" \
  -d '{"amount":"12.50","currency":"EUR"}')"
test "${conflict_status}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='idempotency_conflict') process.exit(1);" "${conflict_body}"

# Second capture with different key returns 409 invalid_state
second_body="/tmp/minifin-${tag}-second.json"
second_status="$(pa_curl -sS -o "${second_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Idempotency-Key: cap2-${tag}" \
  -H "Content-Type: application/json" \
  -d '{}')"
test "${second_status}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1);" "${second_body}"

# Cross-merchant capture returns 404
pa_register_merchant "${tag}-other"
other_merchant_body="/tmp/minifin-${tag}-other-key.json"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase05 capture other" "${other_merchant_body}"
other_api_key="$(auth_extract_api_key "${other_merchant_body}")"
cross_body="/tmp/minifin-${tag}-cross.json"
cross_status="$(pa_curl -sS -o "${cross_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${other_api_key}" \
  -H "Idempotency-Key: cap-cross-${tag}" \
  -H "Content-Type: application/json" \
  -d '{}')"
test "${cross_status}" = "404"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='payment_intent_not_found') process.exit(1);" "${cross_body}"

printf 'PAY-06 payment capture foundation pass intent_id=%s\n' "${intent_id}"
