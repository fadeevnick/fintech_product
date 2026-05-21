#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="chb06-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"
accept_body="/tmp/minifin-${tag}-accept.json"
accept_replay_body="/tmp/minifin-${tag}-accept-replay.json"
other_accept_body="/tmp/minifin-${tag}-other-accept.json"
evidence_after_accept_body="/tmp/minifin-${tag}-evidence-after-accept.json"

approve_kyc() {
  local user_id="$1"
  p05_platform_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, approved_at) values (gen_random_uuid(), '${user_id}'::uuid, 'APPROVED', 'SUMSUB', 'runtime-${tag}-${user_id}', 'basic-kyc-level', 'enduser:${user_id}', now(), now(), now()) on conflict (end_user_id) do update set status='APPROVED', approved_at=now(), updated_at=now();" >/dev/null
}

create_dispute() {
  local local_tag="$1" out_dispute="$2"
  local local_intent="/tmp/minifin-${local_tag}-intent.json"
  local local_card="/tmp/minifin-${local_tag}-card.json"
  local local_auth="/tmp/minifin-${local_tag}-auth.json"
  local local_capture="/tmp/minifin-${local_tag}-capture.json"
  local local_settlement="/tmp/minifin-${local_tag}-settlement.json"
  p05_issue_card "${enduser_cookie}" "${local_card}"
  local local_card_id local_card_token local_wallet_id local_intent_id
  local_card_id="$(auth_extract_card_id "${local_card}")"
  local_card_token="$(auth_card_token_for_card_id "${local_card_id}")"
  local_wallet_id="$(auth_wallet_for_card_id "${local_card_id}")"
  auth_seed_wallet_balance "${local_wallet_id}" "75.0000"
  test "$(pa_public_post_payment_intent "${api_key}" "pi-${local_tag}" '{"amount":"18.25","currency":"EUR","description":"chargeback merchant accept test"}' "${local_intent}")" = "201"
  local_intent_id="$(auth_extract_payment_intent_id "${local_intent}")"
  test "$(auth_public_authorize "${api_key}" "${local_intent_id}" "auth-${local_tag}" "{\"cardToken\":\"${local_card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${local_auth}")" = "200"
  test "$(pa_curl -sS -o "${local_capture}" -w "%{http_code}" -X POST "${auth_base_url}/v1/payment_intents/${local_intent_id}/capture" -H "Authorization: Bearer ${api_key}" -H "Idempotency-Key: cap-${local_tag}" -H "Content-Type: application/json" -d '{}')" = "200"
  pa_curl -fsS -o "${local_settlement}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10"
  test "$(pa_curl -sS -b "${enduser_cookie}" -o "${out_dispute}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${local_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received","narrative":"Runtime CHB-06 merchant accept dispute"}')" = "201"
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='MERCHANT_NOTIFIED'||!j.data?.provisionalCreditJournalId) process.exit(1); console.log(j.data.id);" "${out_dispute}"
}

pa_register_merchant "${tag}-owner"
owner_cookie="${PA_COOKIE_JAR}"
owner_merchant_id="${PA_MERCHANT_ID}"
pa_create_api_key "${owner_cookie}" "Phase09 merchant accept" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
approve_kyc "${user_id}"

dispute_body="/tmp/minifin-${tag}-dispute.json"
dispute_id="$(create_dispute "${tag}" "${dispute_body}")"
provisional_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); console.log(j.data.provisionalCreditJournalId);" "${dispute_body}")"

pa_register_merchant "${tag}-other"
other_cookie="${PA_COOKIE_JAR}"
test "$(pa_curl -sS -b "${other_cookie}" -o "${other_accept_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/accept")" = "404"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='dispute_not_found') process.exit(1);" "${other_accept_body}"; echo ok)" = "ok"

test "$(pa_curl -sS -b "${owner_cookie}" -o "${accept_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/accept")" = "200"
merchant_debit_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='MERCHANT_ACCEPTED'||j.data?.externalState!=='LOST'||!j.data?.merchantDebitJournalId) process.exit(1); console.log(j.data.merchantDebitJournalId);" "${accept_body}")"
test "$(p05_platform_psql "select state from chargeback.disputes where id='${dispute_id}'::uuid;")" = "MERCHANT_ACCEPTED"
test "$(p05_platform_psql "select count(*) from chargeback.disputes where id='${dispute_id}'::uuid and merchant_acceptance_journal_id='${merchant_debit_journal_id}'::uuid and merchant_accepted_by_employee_id is not null and merchant_accepted_at is not null;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries where id='${merchant_debit_journal_id}'::uuid and journal_type='CHARGEBACK_MERCHANT_DEBIT' and reference_type='CHARGEBACK' and reference_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.postings p join ledger.accounts a on a.id=p.account_id where p.journal_entry_id='${merchant_debit_journal_id}'::uuid and ((a.code='MERCHANT_SETTLEMENT:${owner_merchant_id}' and p.side='DEBIT' and p.amount=18.2500) or (a.code='ACQUIRER_DISPUTE_RESERVE' and p.side='CREDIT' and p.amount=18.2500));")" = "2"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries where journal_type='CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL' and reference_id='${dispute_id}'::uuid;")" = "0"
test "$(p05_platform_psql "select coalesce(sum(case when p.side = 'CREDIT' then p.amount else -p.amount end), 0) from ledger.postings p join ledger.accounts a on a.id=p.account_id where a.code='WALLET_USER:${user_id}' and p.journal_entry_id='${provisional_journal_id}'::uuid;")" = "18.2500"
test "$(p05_platform_psql "select count(*) from audit.audit_log where event_type='chargeback.merchant_accepted' and subject_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from merchant.webhook_events where event_type='dispute.lost' and aggregate_id='${dispute_id}'::uuid;")" = "1"

test "$(pa_curl -sS -b "${owner_cookie}" -o "${accept_replay_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/accept")" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1);" "${accept_replay_body}"; echo ok)" = "ok"

evidence_json='{"narrative":"Evidence should be rejected after merchant acceptance.","attachments":[]}'
test "$(pa_curl -sS -b "${owner_cookie}" -o "${evidence_after_accept_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1);" "${evidence_after_accept_body}"; echo ok)" = "ok"

printf 'CHB-06 merchant accept pass dispute_id=%s provisional_journal_id=%s merchant_debit_journal_id=%s user_id=%s\n' "${dispute_id}" "${provisional_journal_id}" "${merchant_debit_journal_id}" "${user_id}"
