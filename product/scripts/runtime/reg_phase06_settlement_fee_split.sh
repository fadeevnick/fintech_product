#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="set-fees-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
capture_body="/tmp/minifin-${tag}-capture.json"
settlement_body="/tmp/minifin-${tag}-settlement.json"
settlement_replay_body="/tmp/minifin-${tag}-settlement-replay.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase06 settlement fees" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
p05_register_enduser "${tag}" "${enduser_cookie}" >/dev/null
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"
wallet_id="$(auth_wallet_for_card_id "${card_id}")"
auth_seed_wallet_balance "${wallet_id}" "75.0000"

status="$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"18.25","currency":"EUR","description":"settlement fee split test"}' "${intent_body}")"
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
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='CAPTURED'||j.errors?.length) process.exit(1);" "${capture_body}"

settlement_status="$(pa_curl -sS -o "${settlement_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10")"
test "${settlement_status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.processedCount < 1 || !j.data?.paymentIntentIds?.includes(process.argv[2])) { console.error(JSON.stringify(j)); process.exit(1); }" "${settlement_body}" "${intent_id}"

fee_row="$(p05_platform_psql "select gross_amount, merchant_net_amount, interchange_amount, network_assessment_amount, acquirer_margin_amount from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
test "${fee_row}" = "18.2500|17.8800|0.2200|0.0300|0.1200"
fee_sum_ok="$(p05_platform_psql "select (merchant_net_amount + interchange_amount + network_assessment_amount + acquirer_margin_amount = gross_amount) from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
test "${fee_sum_ok}" = "t"

posting_destinations="$(p05_platform_psql "select string_agg(a.code || ':' || p.side || ':' || p.amount, ',' order by a.code) from settlement.settlement_items si join ledger.postings p on p.journal_entry_id=si.ledger_journal_id join ledger.accounts a on a.id=p.account_id where si.payment_intent_id='${intent_id}'::uuid;")"
case "${posting_destinations}" in
  *"ACQUIRER_MARGIN_REVENUE:CREDIT:0.1200"* ) ;;
  * ) echo "missing acquirer margin posting: ${posting_destinations}" >&2; exit 1 ;;
esac
case "${posting_destinations}" in
  *"CARD_SETTLEMENT_CLEARING:DEBIT:18.2500"* ) ;;
  * ) echo "missing clearing debit posting: ${posting_destinations}" >&2; exit 1 ;;
esac
case "${posting_destinations}" in
  *"ISSUER_INTERCHANGE_REVENUE:CREDIT:0.2200"* ) ;;
  * ) echo "missing issuer interchange posting: ${posting_destinations}" >&2; exit 1 ;;
esac
case "${posting_destinations}" in
  *"MERCHANT_SETTLEMENT:${PA_MERCHANT_ID}:CREDIT:17.8800"* ) ;;
  * ) echo "missing merchant net posting: ${posting_destinations}" >&2; exit 1 ;;
esac
case "${posting_destinations}" in
  *"NETWORK_ASSESSMENT_REVENUE:CREDIT:0.0300"* ) ;;
  * ) echo "missing network assessment posting: ${posting_destinations}" >&2; exit 1 ;;
esac

imbalanced_count="$(p05_platform_psql "with journal_sums as (select j.id, coalesce(sum(p.amount) filter (where p.side = 'DEBIT'), 0) as debit_total, coalesce(sum(p.amount) filter (where p.side = 'CREDIT'), 0) as credit_total, count(p.id) as posting_count from ledger.journal_entries j join settlement.settlement_items si on si.ledger_journal_id=j.id left join ledger.postings p on p.journal_entry_id=j.id where si.payment_intent_id='${intent_id}'::uuid group by j.id) select count(*) from journal_sums where debit_total <> credit_total or posting_count <> 5;")"
test "${imbalanced_count}" = "0"

journal_id_before="$(p05_platform_psql "select ledger_journal_id from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
replay_status="$(pa_curl -sS -o "${settlement_replay_body}" -w "%{http_code}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10")"
test "${replay_status}" = "200"
item_count_after_replay="$(p05_platform_psql "select count(*) from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
test "${item_count_after_replay}" = "1"
journal_count_after_replay="$(p05_platform_psql "select count(*) from ledger.journal_entries where reference_type='PAYMENT_INTENT' and reference_id='${intent_id}'::uuid and journal_type='CARD_PAYMENT_SETTLEMENT';")"
test "${journal_count_after_replay}" = "1"
journal_id_after="$(p05_platform_psql "select ledger_journal_id from settlement.settlement_items where payment_intent_id='${intent_id}'::uuid;")"
test "${journal_id_after}" = "${journal_id_before}"

printf 'SET-02 settlement fee split pass intent_id=%s\n' "${intent_id}"
