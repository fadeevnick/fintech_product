#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"
source "${SCRIPT_DIR}/lib_phase02_backoffice_keycloak.sh"

tag="chb04-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"
evidence_body="/tmp/minifin-${tag}-evidence.json"
won_body="/tmp/minifin-${tag}-won.json"
won_replay_body="/tmp/minifin-${tag}-won-replay.json"
before_evidence_body="/tmp/minifin-${tag}-before-evidence.json"

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
  test "$(pa_public_post_payment_intent "${api_key}" "pi-${local_tag}" '{"amount":"18.25","currency":"EUR","description":"chargeback arbitration won test"}' "${local_intent}")" = "201"
  local_intent_id="$(auth_extract_payment_intent_id "${local_intent}")"
  test "$(auth_public_authorize "${api_key}" "${local_intent_id}" "auth-${local_tag}" "{\"cardToken\":\"${local_card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${local_auth}")" = "200"
  test "$(pa_curl -sS -o "${local_capture}" -w "%{http_code}" -X POST "${auth_base_url}/v1/payment_intents/${local_intent_id}/capture" -H "Authorization: Bearer ${api_key}" -H "Idempotency-Key: cap-${local_tag}" -H "Content-Type: application/json" -d '{}')" = "200"
  pa_curl -fsS -o "${local_settlement}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10"
  test "$(pa_curl -sS -b "${enduser_cookie}" -o "${out_dispute}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${local_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received","narrative":"Runtime CHB-04 dispute"}')" = "201"
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='MERCHANT_NOTIFIED'||!j.data?.provisionalCreditJournalId) process.exit(1); console.log(j.data.id);" "${out_dispute}"
}

submit_evidence() {
  local dispute_id="$1" out_body="$2"
  local evidence_payload_base64="Y2hhcmdlYmFjayBldmlkZW5jZSBydW50aW1lIHBheWxvYWQ="
  local evidence_json="{\"narrative\":\"Representment package for arbitration WON runtime path.\",\"attachments\":[{\"fileName\":\"receipt.pdf\",\"contentType\":\"application/pdf\",\"storageKey\":\"chargeback-evidence/runtime/receipt.pdf\",\"sizeBytes\":35,\"contentBase64\":\"${evidence_payload_base64}\"}]}"
  test "$(pa_curl -sS -b "${merchant_cookie}" -o "${out_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")" = "200"
}

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase09 arbitration won" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
merchant_cookie="${PA_COOKIE_JAR}"
user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
approve_kyc "${user_id}"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token compliance)"

dispute_body="/tmp/minifin-${tag}-dispute.json"
dispute_id="$(create_dispute "${tag}" "${dispute_body}")"
provisional_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); console.log(j.data.provisionalCreditJournalId);" "${dispute_body}")"
submit_evidence "${dispute_id}" "${evidence_body}"

rationale="Network simulation found merchant evidence compelling for runtime arbitration won path."
test "$(pa_curl -sS -o "${won_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/disputes/${dispute_id}/arbitration" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"outcome\":\"WON\",\"rationale\":\"${rationale}\"}")" = "200"
reversal_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='WON'||j.data?.outcome!=='WON'||!j.data?.arbitrationJournalId) process.exit(1); console.log(j.data.arbitrationJournalId);" "${won_body}")"
test "$(p05_platform_psql "select state from chargeback.disputes where id='${dispute_id}'::uuid;")" = "WON"
test "$(p05_platform_psql "select count(*) from chargeback.disputes where id='${dispute_id}'::uuid and arbitration_outcome='WON' and arbitration_journal_id='${reversal_journal_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.journal_entries where id='${reversal_journal_id}'::uuid and journal_type='CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL' and reference_type='CHARGEBACK' and reference_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from ledger.postings p join ledger.accounts a on a.id=p.account_id where p.journal_entry_id='${reversal_journal_id}'::uuid and ((a.code='WALLET_USER:${user_id}' and p.side='DEBIT' and p.amount=18.2500) or (a.code='ACQUIRER_DISPUTE_RESERVE' and p.side='CREDIT' and p.amount=18.2500));")" = "2"
test "$(p05_platform_psql "select coalesce(sum(case when p.side = 'CREDIT' then p.amount else -p.amount end), 0) from ledger.postings p join ledger.accounts a on a.id=p.account_id where a.code='WALLET_USER:${user_id}' and p.journal_entry_id in ('${provisional_journal_id}'::uuid, '${reversal_journal_id}'::uuid);")" = "0.0000"
test "$(p05_platform_psql "select count(*) from audit.audit_log where event_type='chargeback.arbitration_won' and subject_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from merchant.webhook_events where event_type='dispute.won' and aggregate_id='${dispute_id}'::uuid;")" = "1"

test "$(pa_curl -sS -o "${won_replay_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/disputes/${dispute_id}/arbitration" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"outcome\":\"WON\",\"rationale\":\"${rationale}\"}")" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1);" "${won_replay_body}"; echo ok)" = "ok"

before_evidence_dispute_id="$(create_dispute "${tag}-before-evidence" "/tmp/minifin-${tag}-before-evidence-dispute.json")"
test "$(pa_curl -sS -o "${before_evidence_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/disputes/${before_evidence_dispute_id}/arbitration" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"outcome\":\"WON\",\"rationale\":\"${rationale}\"}")" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1);" "${before_evidence_body}"; echo ok)" = "ok"

printf 'CHB-04 arbitration WON pass dispute_id=%s provisional_journal_id=%s reversal_journal_id=%s user_id=%s\n' "${dispute_id}" "${provisional_journal_id}" "${reversal_journal_id}" "${user_id}"
