#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="auth-approved-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase05 auth approved" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"
wallet_id="$(auth_wallet_for_card_id "${card_id}")"
auth_seed_wallet_balance "${wallet_id}" "50.0000"

status="$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"12.50","currency":"EUR"}' "${intent_body}")"
test "${status}" = "201"
intent_id="$(auth_extract_payment_intent_id "${intent_body}")"
before_holds="$(auth_count_card_holds "${card_token}")"
status="$(auth_public_authorize "${api_key}" "${intent_id}" "auth-${tag}" "{\"cardToken\":\"${card_token}\",\"amount\":\"12.50\",\"currency\":\"EUR\"}" "${auth_body}")"
test "${status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='AUTHORIZED'||j.data?.authorization?.status!=='AUTH_APPROVED'||j.errors?.length) process.exit(1);" "${auth_body}"
after_holds="$(auth_count_card_holds "${card_token}")"
test "$((after_holds-before_holds))" = "1"
test "$(auth_count_platform_card_hold_journals)" -ge 1
"${SCRIPT_DIR}/reg_phase03_ledger_reconciliation.sh" >/tmp/minifin-${tag}-ldg.txt
printf 'PAY-04 approved authorization hold pass\n'
