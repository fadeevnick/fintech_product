#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="set-proj-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
capture_body="/tmp/minifin-${tag}-capture.json"
settlement_body="/tmp/minifin-${tag}-settlement.json"
projection_body="/tmp/minifin-${tag}-projection.json"
projection_replay_body="/tmp/minifin-${tag}-projection-replay.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase06 acquirer settlement projection" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
p05_register_enduser "${tag}" "${enduser_cookie}" >/dev/null
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"
wallet_id="$(auth_wallet_for_card_id "${card_id}")"
auth_seed_wallet_balance "${wallet_id}" "80.0000"

status="$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"18.25","currency":"EUR","description":"acquirer settlement projection test"}' "${intent_body}")"
test "${status}" = "201"
intent_id="$(auth_extract_payment_intent_id "${intent_body}")"
status="$(auth_public_authorize "${api_key}" "${intent_id}" "auth-${tag}" "{\"cardToken\":\"${card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${auth_body}")"
test "${status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='AUTHORIZED') process.exit(1);" "${auth_body}"

capture_status="$(pa_curl -sS -o "${capture_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Idempotency-Key: cap-${tag}" \
  -H "Content-Type: application/json" \
  -d '{}')"
test "${capture_status}" = "200"

settlement_status="$(pa_curl -sS -o "${settlement_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10")"
test "${settlement_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.processedCount < 1 || !j.data?.paymentIntentIds?.includes(process.argv[2])) { console.error(JSON.stringify(j)); process.exit(1); }" "${settlement_body}" "${intent_id}"

settlement_item_id="$(p05_platform_psql "select id from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
test -n "${settlement_item_id}"

projection_status="$(pa_curl -sS -o "${projection_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/publish-projections?limit=200")"
test "${projection_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.publishedCount < 1 || j.data?.acquirerReceivedCount < 1 || j.errors?.length) { console.error(JSON.stringify(j)); process.exit(1); }" "${projection_body}"

projection_count="$(p05_acquirer_psql "select count(*) from merchant_settlement.balance_projection where platform_settlement_item_id='${settlement_item_id}'::uuid;")"
test "${projection_count}" = "1"

platform_values="$(p05_platform_psql "select merchant_id, payment_intent_id, gross_amount, merchant_net_amount, interchange_amount, network_assessment_amount, acquirer_margin_amount, currency from settlement.settlement_items where id='${settlement_item_id}'::uuid;")"
acquirer_values="$(p05_acquirer_psql "select merchant_id, payment_intent_id, gross_amount, merchant_net_amount, interchange_amount, network_assessment_amount, acquirer_margin_amount, currency from merchant_settlement.balance_projection where platform_settlement_item_id='${settlement_item_id}'::uuid;")"
test "${acquirer_values}" = "${platform_values}"

projection_replay_status="$(pa_curl -sS -o "${projection_replay_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/publish-projections?limit=200")"
test "${projection_replay_status}" = "200"
projection_count_after_replay="$(p05_acquirer_psql "select count(*) from merchant_settlement.balance_projection where platform_settlement_item_id='${settlement_item_id}'::uuid;")"
test "${projection_count_after_replay}" = "1"

printf 'SET-03 acquirer settlement projection pass intent_id=%s settlement_item_id=%s\n' "${intent_id}" "${settlement_item_id}"
