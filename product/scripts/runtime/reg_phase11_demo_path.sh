#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

run_tag="$(date +%s%N)-$$"

# Seeded demo data (from reg_phase11_seed_dev.sh)
DEMO_LEDGER_ACCOUNT_ID="30000000-0000-0000-0000-000000000001"
DEMO_CARD_TOKEN="tok_demo_approved_card"

merchant_body="/tmp/minifin-rec02-merchant-${run_tag}.json"
intent_body="/tmp/minifin-rec02-intent-${run_tag}.json"
auth_body="/tmp/minifin-rec02-auth-${run_tag}.json"
capture_body="/tmp/minifin-rec02-capture-${run_tag}.json"
settlement_body="/tmp/minifin-rec02-settlement-${run_tag}.json"
projection_body="/tmp/minifin-rec02-projection-${run_tag}.json"
refund_body="/tmp/minifin-rec02-refund-${run_tag}.json"
recon_body="/tmp/minifin-rec02-recon-${run_tag}.json"

# --- REC-02 Part 1: verify seed state ---
initial_balance="$(p05_platform_psql "select coalesce(sum(case when p.side='CREDIT' then p.amount else -p.amount end),0) from ledger.postings p where p.account_id='${DEMO_LEDGER_ACCOUNT_ID}'::uuid;")"
node -e "if(parseFloat('${initial_balance}')<30){console.error('seed wallet balance insufficient for demo payment: ${initial_balance}');process.exit(1);}"
test "$(p05_platform_psql "select state from cards.issued_cards where card_token='${DEMO_CARD_TOKEN}';")" = "ACTIVE"

# --- REC-02 Part 2: fresh merchant + real API key ---
# Seeded merchant key hash is a stub; register a live merchant to get an authenticatable key.
pa_register_merchant "rec02-${run_tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "REC-02 demo key" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"

# --- REC-02 Part 3: create payment intent ---
status="$(pa_public_post_payment_intent "${api_key}" "pi-rec02-${run_tag}" '{"amount":"30.00","currency":"EUR","description":"REC-02 full demo payment"}' "${intent_body}")"
test "${status}" = "201"
intent_id="$(auth_extract_payment_intent_id "${intent_body}")"

# --- REC-02 Part 4: authorize with seeded card ---
status="$(auth_public_authorize "${api_key}" "${intent_id}" "auth-rec02-${run_tag}" "{\"cardToken\":\"${DEMO_CARD_TOKEN}\",\"amount\":\"30.00\",\"currency\":\"EUR\"}" "${auth_body}")"
test "${status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync('${auth_body}','utf8')); if(j.data?.state!=='AUTHORIZED'){console.error(JSON.stringify(j));process.exit(1);}"
test "$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")" = "AUTHORIZED"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries where journal_type='CARD_AUTHORIZATION_HOLD';")" -ge "1"

# --- REC-02 Part 5: capture ---
status="$(pa_curl -sS -o "${capture_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Idempotency-Key: cap-rec02-${run_tag}")"
test "${status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync('${capture_body}','utf8')); if(j.data?.state!=='CAPTURED'){console.error(JSON.stringify(j));process.exit(1);}"

# --- REC-02 Part 6: settlement ---
status="$(pa_curl -sS -o "${settlement_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10")"
test "${status}" = "200"
test "$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")" = "SETTLED"
settlement_item_id="$(p05_platform_psql "select si.id from settlement.settlement_items si join settlement.settlement_batches sb on sb.id=si.batch_id where si.payment_intent_id='${intent_id}'::uuid limit 1;")"
test -n "${settlement_item_id}"

# --- REC-02 Part 7: acquirer settlement projection ---
status="$(pa_curl -sS -o "${projection_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/internal/settlement/publish-projections?limit=200")"
test "${status}" = "200"
test "$(p05_acquirer_psql "select count(*) from merchant_settlement.balance_projection where platform_settlement_item_id='${settlement_item_id}'::uuid;")" = "1"

# --- REC-02 Part 8: partial refund (EUR 10.00) ---
status="$(pa_curl -sS -o "${refund_body}" -w "%{http_code}" \
  -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/refund" \
  -H "Authorization: Bearer ${api_key}" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: refund-rec02-${run_tag}" \
  -d '{"amount":"10.00","reason":"requested_by_customer"}')"
test "${status}" = "200"
node -e "const j=JSON.parse(require('fs').readFileSync('${refund_body}','utf8')); if(j.data?.state!=='SUCCEEDED'||!j.data?.ledgerJournalId){console.error(JSON.stringify(j));process.exit(1);}"
test "$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")" = "PARTIALLY_REFUNDED"

# --- REC-02 Part 9: wallet balance delta ---
# CARD_AUTHORIZATION_HOLD debits 30.00 from wallet; CARD_PAYMENT_REFUND credits 10.00 back.
# Net delta from initial: -20.00
final_balance="$(p05_platform_psql "select coalesce(sum(case when p.side='CREDIT' then p.amount else -p.amount end),0) from ledger.postings p where p.account_id='${DEMO_LEDGER_ACCOUNT_ID}'::uuid;")"
node -e "const diff=parseFloat('${final_balance}')-parseFloat('${initial_balance}'); if(Math.abs(diff-(-20))>0.001){console.error('unexpected wallet delta '+diff+' (expected -20)');process.exit(1);}"

# --- REC-02 Part 10: ledger reconciliation ---
if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
  docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 \
    -fsS "${auth_base_url}/internal/ledger/runtime/reconciliation" >"${recon_body}"
else
  curl -fsS "${auth_base_url}/internal/ledger/runtime/reconciliation" >"${recon_body}"
fi
node -e "const j=JSON.parse(require('fs').readFileSync('${recon_body}','utf8')); if(j.data?.balancedJournals!==true||Number(j.data?.journalCount??0)<1)process.exit(1);"
imbalanced="$(p05_platform_psql "with s as (select j.id, sum(p.amount) filter(where p.side='DEBIT') d, sum(p.amount) filter(where p.side='CREDIT') c from ledger.journal_entries j join ledger.postings p on p.journal_entry_id=j.id group by j.id) select count(*) from s where d<>c;")"
test "${imbalanced}" = "0"

echo "REC-02 full demo path pass run_tag=${run_tag} intent_id=${intent_id} initial_balance=${initial_balance} final_balance=${final_balance}"
