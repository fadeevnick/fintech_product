#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="chb03-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
intent_body="/tmp/minifin-${tag}-intent.json"
card_body="/tmp/minifin-${tag}-card.json"
auth_body="/tmp/minifin-${tag}-auth.json"
capture_body="/tmp/minifin-${tag}-capture.json"
settlement_body="/tmp/minifin-${tag}-settlement.json"
dispute_body="/tmp/minifin-${tag}-dispute.json"
evidence_body="/tmp/minifin-${tag}-evidence.json"
evidence_replay_body="/tmp/minifin-${tag}-evidence-replay.json"
other_merchant_body="/tmp/minifin-${tag}-other-merchant-key.json"
other_merchant_evidence_body="/tmp/minifin-${tag}-other-merchant-evidence.json"
expired_evidence_body="/tmp/minifin-${tag}-expired-evidence.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"

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
  test "$(pa_public_post_payment_intent "${api_key}" "pi-${local_tag}" '{"amount":"18.25","currency":"EUR","description":"chargeback evidence test"}' "${local_intent}")" = "201"
  local_intent_id="$(auth_extract_payment_intent_id "${local_intent}")"
  test "$(auth_public_authorize "${api_key}" "${local_intent_id}" "auth-${local_tag}" "{\"cardToken\":\"${local_card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${local_auth}")" = "200"
  test "$(pa_curl -sS -o "${local_capture}" -w "%{http_code}" -X POST "${auth_base_url}/v1/payment_intents/${local_intent_id}/capture" -H "Authorization: Bearer ${api_key}" -H "Idempotency-Key: cap-${local_tag}" -H "Content-Type: application/json" -d '{}')" = "200"
  pa_curl -fsS -o "${local_settlement}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10"
  test "$(pa_curl -sS -b "${enduser_cookie}" -o "${out_dispute}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${local_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received","narrative":"Runtime CHB-03 dispute"}')" = "201"
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='MERCHANT_NOTIFIED'||!j.data?.provisionalCreditJournalId) process.exit(1); console.log(j.data.id);" "${out_dispute}"
}

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase09 merchant evidence" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
merchant_cookie="${PA_COOKIE_JAR}"
merchant_id="${PA_MERCHANT_ID}"

user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
approve_kyc "${user_id}"

dispute_id="$(create_dispute "${tag}" "${dispute_body}")"
evidence_json='{"narrative":"Delivery proof, customer correspondence and signed receipt attached.","attachments":[{"fileName":"delivery-proof.pdf","contentType":"application/pdf","storageKey":"chargeback-evidence/runtime/delivery-proof.pdf","sizeBytes":12345}]}'
test "$(pa_curl -sS -b "${merchant_cookie}" -o "${evidence_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")" = "200"
evidence_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='EVIDENCE_SUBMITTED'||j.data?.attachments?.length!==1) process.exit(1); console.log(j.data.id);" "${evidence_body}")"
test "$(p05_platform_psql "select state from chargeback.disputes where id='${dispute_id}'::uuid;")" = "EVIDENCE_SUBMITTED"
test "$(p05_platform_psql "select count(*) from chargeback.evidence_submissions where id='${evidence_id}'::uuid and dispute_id='${dispute_id}'::uuid and merchant_id='${merchant_id}'::uuid and narrative like 'Delivery proof%';")" = "1"
test "$(p05_platform_psql "select count(*) from chargeback.evidence_attachments where evidence_submission_id='${evidence_id}'::uuid and file_name='delivery-proof.pdf' and content_type='application/pdf' and storage_key='chargeback-evidence/runtime/delivery-proof.pdf' and size_bytes=12345;")" = "1"
test "$(p05_platform_psql "select count(*) from audit.audit_log where event_type='chargeback.evidence_submitted' and subject_id='${dispute_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from merchant.webhook_events where event_type='dispute.evidence_received' and aggregate_id='${dispute_id}'::uuid;")" = "1"

test "$(pa_curl -sS -b "${merchant_cookie}" -o "${evidence_replay_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_state') process.exit(1);" "${evidence_replay_body}"; echo ok)" = "ok"

pa_register_merchant "${tag}-other"
pa_create_api_key "${PA_COOKIE_JAR}" "Other merchant evidence" "${other_merchant_body}"
other_merchant_cookie="${PA_COOKIE_JAR}"
other_status="$(pa_curl -sS -b "${other_merchant_cookie}" -o "${other_merchant_evidence_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")"
test "${other_status}" = "404"

expired_dispute_id="$(create_dispute "${tag}-expired" "/tmp/minifin-${tag}-expired-dispute.json")"
p05_platform_psql "update chargeback.disputes set merchant_response_deadline = now() - interval '1 minute' where id='${expired_dispute_id}'::uuid;" >/dev/null
test "$(pa_curl -sS -b "${merchant_cookie}" -o "${expired_evidence_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${expired_dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")" = "409"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='merchant_deadline_expired') process.exit(1);" "${expired_evidence_body}"; echo ok)" = "ok"
test "$(p05_platform_psql "select count(*) from chargeback.evidence_submissions where dispute_id='${expired_dispute_id}'::uuid;")" = "0"

printf 'CHB-03 merchant evidence submission pass dispute_id=%s evidence_id=%s user_id=%s\n' "${dispute_id}" "${evidence_id}" "${user_id}"
