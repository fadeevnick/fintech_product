#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="auth-declines-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"
card_body="/tmp/minifin-${tag}-card.json"

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase05 auth declines" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"

run_decline() {
  local suffix="$1" token="$2" expected="$3" amount="${4:-12.50}"
  local intent_body="/tmp/minifin-${tag}-${suffix}-intent.json"
  local auth_body="/tmp/minifin-${tag}-${suffix}-auth.json"
  local before after status intent_id
  before="$(auth_count_card_holds "${token}")"
  status="$(pa_public_post_payment_intent "${api_key}" "pi-${tag}-${suffix}" "{\"amount\":\"${amount}\",\"currency\":\"EUR\"}" "${intent_body}")"
  test "${status}" = "201"
  intent_id="$(auth_extract_payment_intent_id "${intent_body}")"
  status="$(auth_public_authorize "${api_key}" "${intent_id}" "auth-${tag}-${suffix}" "{\"cardToken\":\"${token}\",\"amount\":\"${amount}\",\"currency\":\"EUR\"}" "${auth_body}")"
  test "${status}" = "200"
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='FAILED'||j.data?.authorization?.status!=='AUTH_DECLINED'||j.data?.authorization?.declineCode!==process.argv[2]) { console.error(JSON.stringify(j)); process.exit(1); }" "${auth_body}" "${expected}"
  after="$(auth_count_card_holds "${token}")"
  test "${after}" = "${before}"
}

run_decline insufficient "${card_token}" insufficient_funds 999.00
p05_platform_psql "insert into identity.actor_controls (actor_type, actor_id, state, reason_code, updated_by_actor_type, updated_by_reference) values ('END_USER','${user_id}'::uuid,'BLOCKED','runtime-test','SYSTEM','phase05-runtime') on conflict (actor_type, actor_id) do update set state='BLOCKED', reason_code='runtime-test', updated_by_actor_type='SYSTEM', updated_by_reference='phase05-runtime';" >/dev/null
run_decline blocked "${card_token}" actor_blocked 1.00
p05_platform_psql "update identity.actor_controls set state='ACTIVE' where actor_type='END_USER' and actor_id='${user_id}'::uuid;" >/dev/null
p05_issuer_psql "update issuer.cards set state='BLOCKED' where id='${card_id}'::uuid;" >/dev/null
run_decline inactive "${card_token}" card_inactive 1.00
run_decline unknown "tok_unknown_${tag}" card_not_found 1.00
printf 'PAY-05 structured authorization declines pass\n'
