#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="set-capture-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
capture_body="/tmp/minifin-${tag}-capture.json"
settlement_body="/tmp/minifin-${tag}-settlement.json"
settlement_replay_body="/tmp/minifin-${tag}-settlement-replay.json"
payment_get_body="/tmp/minifin-${tag}-payment-get.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase06 settlement" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"
wallet_id="$(auth_wallet_for_card_id "${card_id}")"
auth_seed_wallet_balance "${wallet_id}" "75.0000"

status="$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"18.25","currency":"EUR","description":"settlement test"}' "${intent_body}")"
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
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='CAPTURED'||!j.data?.capturedAt||j.errors?.length) process.exit(1);" "${capture_body}"

settlement_status="$(pa_curl -sS -o "${settlement_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10")"
test "${settlement_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.processedCount < 1 || !j.data?.batchId || !j.data?.paymentIntentIds?.includes(process.argv[2])) { console.error(JSON.stringify(j)); process.exit(1); }" "${settlement_body}" "${intent_id}"

batch_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); console.log(j.data.batchId);" "${settlement_body}")"
item_count="$(p05_platform_psql "select count(*) from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid and batch_id='${batch_id}'::uuid and status='SETTLED' and gross_amount=18.2500 and currency='EUR';")"
test "${item_count}" = "1"
db_state="$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")"
test "${db_state}" = "SETTLED"
journal_count="$(p05_platform_psql "select count(*) from ledger.journal_entries j join settlement.settlement_items si on si.ledger_journal_id=j.id where si.payment_intent_id='${intent_id}'::uuid and j.journal_type='CARD_PAYMENT_SETTLEMENT';")"
test "${journal_count}" = "1"
imbalanced_count="$(p05_platform_psql "with journal_sums as (select j.id, coalesce(sum(p.amount) filter (where p.side = 'DEBIT'), 0) as debit_total, coalesce(sum(p.amount) filter (where p.side = 'CREDIT'), 0) as credit_total, count(p.id) as posting_count from ledger.journal_entries j join settlement.settlement_items si on si.ledger_journal_id=j.id left join ledger.postings p on p.journal_entry_id=j.id where si.payment_intent_id='${intent_id}'::uuid group by j.id) select count(*) from journal_sums where debit_total <> credit_total or posting_count < 2;")"
test "${imbalanced_count}" = "0"

get_status="$(pa_curl -sS -o "${payment_get_body}" -w "%{http_code}" \
  -H "Authorization: Bearer ${api_key}" \
  "${auth_base_url}/v1/payment_intents/${intent_id}")"
test "${get_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='SETTLED'||!j.data?.capturedAt||j.errors?.length) process.exit(1);" "${payment_get_body}"

replay_status="$(pa_curl -sS -o "${settlement_replay_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10")"
test "${replay_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.paymentIntentIds?.includes(process.argv[2])) process.exit(1);" "${settlement_replay_body}" "${intent_id}"
item_count_after_replay="$(p05_platform_psql "select count(*) from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
test "${item_count_after_replay}" = "1"

printf 'SET-01 capture to settlement foundation pass intent_id=%s batch_id=%s\n' "${intent_id}" "${batch_id}"
