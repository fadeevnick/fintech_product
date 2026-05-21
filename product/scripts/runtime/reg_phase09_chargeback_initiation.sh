#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="chb01-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
capture_body="/tmp/minifin-${tag}-capture.json"
settlement_body="/tmp/minifin-${tag}-settlement.json"
dispute_body="/tmp/minifin-${tag}-dispute.json"
dispute_replay_body="/tmp/minifin-${tag}-dispute-replay.json"
bad_reason_body="/tmp/minifin-${tag}-bad-reason.json"
not_cardholder_body="/tmp/minifin-${tag}-not-cardholder.json"
not_kyc_body="/tmp/minifin-${tag}-not-kyc.json"
unsettled_body="/tmp/minifin-${tag}-unsettled.json"
expired_body="/tmp/minifin-${tag}-expired.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"
other_cookie="/tmp/minifin-${tag}-other-cookies.txt"
unapproved_cookie="/tmp/minifin-${tag}-unapproved-cookies.txt"

approve_kyc() {
  local user_id="$1"
  p05_platform_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, approved_at) values (gen_random_uuid(), '${user_id}'::uuid, 'APPROVED', 'SUMSUB', 'runtime-${tag}-${user_id}', 'basic-kyc-level', 'enduser:${user_id}', now(), now(), now()) on conflict (end_user_id) do update set status='APPROVED', approved_at=now(), updated_at=now();" >/dev/null
}

create_settled_payment() {
  local local_tag="$1" cookie_jar="$2" out_intent="$3"
  local local_card="/tmp/minifin-${local_tag}-card.json"
  local local_auth="/tmp/minifin-${local_tag}-auth.json"
  local local_capture="/tmp/minifin-${local_tag}-capture.json"
  local local_settlement="/tmp/minifin-${local_tag}-settlement.json"
  p05_issue_card "${cookie_jar}" "${local_card}"
  local local_card_id local_card_token local_wallet_id local_intent_id
  local_card_id="$(auth_extract_card_id "${local_card}")"
  local_card_token="$(auth_card_token_for_card_id "${local_card_id}")"
  local_wallet_id="$(auth_wallet_for_card_id "${local_card_id}")"
  auth_seed_wallet_balance "${local_wallet_id}" "75.0000"
  test "$(pa_public_post_payment_intent "${api_key}" "pi-${local_tag}" '{"amount":"18.25","currency":"EUR","description":"chargeback test"}' "${out_intent}")" = "201"
  local_intent_id="$(auth_extract_payment_intent_id "${out_intent}")"
  test "$(auth_public_authorize "${api_key}" "${local_intent_id}" "auth-${local_tag}" "{\"cardToken\":\"${local_card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${local_auth}")" = "200"
  test "$(pa_curl -sS -o "${local_capture}" -w "%{http_code}" -X POST "${auth_base_url}/v1/payment_intents/${local_intent_id}/capture" -H "Authorization: Bearer ${api_key}" -H "Idempotency-Key: cap-${local_tag}" -H "Content-Type: application/json" -d '{}')" = "200"
  pa_curl -fsS -o "${local_settlement}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10"
  echo "${local_intent_id}"
}

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase09 chargebacks" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"

user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
approve_kyc "${user_id}"
p05_issue_card "${enduser_cookie}" "${card_body}"
card_id="$(auth_extract_card_id "${card_body}")"
card_token="$(auth_card_token_for_card_id "${card_id}")"
wallet_id="$(auth_wallet_for_card_id "${card_id}")"
auth_seed_wallet_balance "${wallet_id}" "75.0000"

test "$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"18.25","currency":"EUR","description":"chargeback test"}' "${intent_body}")" = "201"
intent_id="$(auth_extract_payment_intent_id "${intent_body}")"
test "$(auth_public_authorize "${api_key}" "${intent_id}" "auth-${tag}" "{\"cardToken\":\"${card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${auth_body}")" = "200"
test "$(pa_curl -sS -o "${capture_body}" -w "%{http_code}" -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/capture" -H "Authorization: Bearer ${api_key}" -H "Idempotency-Key: cap-${tag}" -H "Content-Type: application/json" -d '{}')" = "200"
pa_curl -fsS -o "${settlement_body}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10"
test "$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")" = "SETTLED"

test "$(pa_curl -sS -b "${enduser_cookie}" -o "${dispute_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received","narrative":"Runtime CHB-01 dispute"}')" = "201"
dispute_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='MERCHANT_NOTIFIED'||j.data?.paymentIntentId!==process.argv[2]||j.data?.reasonCode!=='goods_not_received'||!j.data?.provisionalCreditJournalId) process.exit(1); console.log(j.data.id);" "${dispute_body}" "${intent_id}")"
provisional_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); console.log(j.data.provisionalCreditJournalId);" "${dispute_body}")"
test "$(p05_platform_psql "select state from merchant.payment_intents where id='${intent_id}'::uuid;")" = "DISPUTED"
test "$(p05_platform_psql "select count(*) from chargeback.disputes where id='${dispute_id}'::uuid and payment_intent_id='${intent_id}'::uuid and merchant_id='${PA_MERCHANT_ID}'::uuid and cardholder_user_id='${user_id}'::uuid and amount=18.2500 and state='MERCHANT_NOTIFIED' and provisional_credit_journal_id='${provisional_journal_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from audit.audit_log where event_type='chargeback.initiated' and subject_id='${dispute_id}'::uuid and actor_id='${user_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from merchant.webhook_events where event_type='dispute.created' and aggregate_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries where id='${provisional_journal_id}'::uuid and journal_type='CARDHOLDER_PROVISIONAL_CREDIT' and reference_type='CHARGEBACK' and reference_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.postings p join ledger.accounts a on a.id=p.account_id where p.journal_entry_id='${provisional_journal_id}'::uuid and ((a.code='ACQUIRER_DISPUTE_RESERVE' and p.side='DEBIT' and p.amount=18.2500) or (a.code='WALLET_USER:${user_id}' and p.side='CREDIT' and p.amount=18.2500));")" = "2"

test "$(pa_curl -sS -b "${enduser_cookie}" -o "${dispute_replay_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received"}')" = "201"
test "$(node -e "const a=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const b=JSON.parse(require('fs').readFileSync(process.argv[2],'utf8')); console.log(a.data.id===b.data.id ? 'true' : 'false');" "${dispute_body}" "${dispute_replay_body}")" = "true"
test "$(p05_platform_psql "select count(*) from chargeback.disputes where payment_intent_id='${intent_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries where journal_type='CARDHOLDER_PROVISIONAL_CREDIT' and reference_type='CHARGEBACK' and reference_id='${dispute_id}'::uuid;")" = "1"

other_user_id="$(p05_register_enduser "${tag}-other" "${other_cookie}")"
approve_kyc "${other_user_id}"
test "$(pa_curl -sS -b "${other_cookie}" -o "${not_cardholder_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"duplicate_charge"}')" = "404"

unapproved_user_id="$(p05_register_enduser "${tag}-unapproved" "${unapproved_cookie}")"
unapproved_intent_id="$(create_settled_payment "${tag}-unapproved" "${unapproved_cookie}" "/tmp/minifin-${tag}-unapproved-intent.json")"
test "$(pa_curl -sS -b "${unapproved_cookie}" -o "${not_kyc_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${unapproved_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received"}')" = "403"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='kyc_not_approved') process.exit(1);" "${not_kyc_body}"; echo ok)" = "ok"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries j join chargeback.disputes d on d.id=j.reference_id where d.payment_intent_id='${unapproved_intent_id}'::uuid and j.journal_type='CARDHOLDER_PROVISIONAL_CREDIT';")" = "0"

test "$(pa_public_post_payment_intent "${api_key}" "pi-${tag}-unsettled" '{"amount":"12.00","currency":"EUR"}' "/tmp/minifin-${tag}-unsettled-intent.json")" = "201"
unsettled_intent_id="$(auth_extract_payment_intent_id "/tmp/minifin-${tag}-unsettled-intent.json")"
test "$(auth_public_authorize "${api_key}" "${unsettled_intent_id}" "auth-${tag}-unsettled" "{\"cardToken\":\"${card_token}\",\"amount\":\"12.00\",\"currency\":\"EUR\"}" "/tmp/minifin-${tag}-unsettled-auth.json")" = "200"
test "$(pa_curl -sS -b "${enduser_cookie}" -o "${unsettled_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${unsettled_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received"}')" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_payment_state') process.exit(1);" "${unsettled_body}"; echo ok)" = "ok"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries j join chargeback.disputes d on d.id=j.reference_id where d.payment_intent_id='${unsettled_intent_id}'::uuid and j.journal_type='CARDHOLDER_PROVISIONAL_CREDIT';")" = "0"

expired_intent_id="$(create_settled_payment "${tag}-expired" "${enduser_cookie}" "/tmp/minifin-${tag}-expired-intent.json")"
p05_platform_psql "update settlement.settlement_items set created_at = now() - interval '61 days' where payment_intent_id='${expired_intent_id}'::uuid; update merchant.payment_intents set captured_at = now() - interval '61 days' where id='${expired_intent_id}'::uuid;" >/dev/null
test "$(pa_curl -sS -b "${enduser_cookie}" -o "${expired_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${expired_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received"}')" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='dispute_window_expired') process.exit(1);" "${expired_body}"; echo ok)" = "ok"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries j join chargeback.disputes d on d.id=j.reference_id where d.payment_intent_id='${expired_intent_id}'::uuid and j.journal_type='CARDHOLDER_PROVISIONAL_CREDIT';")" = "0"

test "$(pa_curl -sS -b "${enduser_cookie}" -o "${bad_reason_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${expired_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"not_real"}')" = "400"

printf 'CHB-01/CHB-02 chargeback initiation provisional credit pass dispute_id=%s payment_intent_id=%s user_id=%s provisional_journal_id=%s\n' "${dispute_id}" "${intent_id}" "${user_id}" "${provisional_journal_id}"
