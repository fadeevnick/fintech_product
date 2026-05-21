#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib_phase05_card_authorization.sh"

tag="chb08-$(date +%s%N)"
merchant_body="/tmp/minifin-${tag}-merchant-key.json"
enduser_cookie="/tmp/minifin-${tag}-enduser-cookies.txt"
dispute_body="/tmp/minifin-${tag}-dispute.json"
evidence_body="/tmp/minifin-${tag}-evidence.json"
bad_base64_body="/tmp/minifin-${tag}-bad-base64.json"
size_mismatch_body="/tmp/minifin-${tag}-size-mismatch.json"
object_body="/tmp/minifin-${tag}-evidence-object.txt"

object_storage_base_url="${OBJECT_STORAGE_BASE_URL:-http://localhost:8333}"
if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
  object_storage_base_url="${OBJECT_STORAGE_BASE_URL:-http://seaweedfs:8333}"
fi

object_curl() {
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 "$@"
  else
    curl "$@"
  fi
}

approve_kyc() {
  local user_id="$1"
  p05_platform_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, approved_at) values (gen_random_uuid(), '${user_id}'::uuid, 'APPROVED', 'SUMSUB', 'runtime-${tag}-${user_id}', 'basic-kyc-level', 'enduser:${user_id}', now(), now(), now()) on conflict (end_user_id) do update set status='APPROVED', approved_at=now(), updated_at=now();" >/dev/null
}

create_dispute() {
  local local_intent="/tmp/minifin-${tag}-intent.json"
  local local_card="/tmp/minifin-${tag}-card.json"
  local local_auth="/tmp/minifin-${tag}-auth.json"
  local local_capture="/tmp/minifin-${tag}-capture.json"
  local local_settlement="/tmp/minifin-${tag}-settlement.json"
  p05_issue_card "${enduser_cookie}" "${local_card}"
  local local_card_id local_card_token local_wallet_id local_intent_id
  local_card_id="$(auth_extract_card_id "${local_card}")"
  local_card_token="$(auth_card_token_for_card_id "${local_card_id}")"
  local_wallet_id="$(auth_wallet_for_card_id "${local_card_id}")"
  auth_seed_wallet_balance "${local_wallet_id}" "75.0000"
  test "$(pa_public_post_payment_intent "${api_key}" "pi-${tag}" '{"amount":"18.25","currency":"EUR","description":"chargeback evidence object storage test"}' "${local_intent}")" = "201"
  local_intent_id="$(auth_extract_payment_intent_id "${local_intent}")"
  test "$(auth_public_authorize "${api_key}" "${local_intent_id}" "auth-${tag}" "{\"cardToken\":\"${local_card_token}\",\"amount\":\"18.25\",\"currency\":\"EUR\"}" "${local_auth}")" = "200"
  test "$(pa_curl -sS -o "${local_capture}" -w "%{http_code}" -X POST "${auth_base_url}/v1/payment_intents/${local_intent_id}/capture" -H "Authorization: Bearer ${api_key}" -H "Idempotency-Key: cap-${tag}" -H "Content-Type: application/json" -d '{}')" = "200"
  pa_curl -fsS -o "${local_settlement}" -X POST "${auth_base_url}/internal/settlement/process-captured?limit=10"
  test "$(pa_curl -sS -b "${enduser_cookie}" -o "${dispute_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/card-payments/${local_intent_id}/disputes" -H "Content-Type: application/json" -d '{"reasonCode":"goods_not_received","narrative":"Runtime CHB-08 dispute"}')" = "201"
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='MERCHANT_NOTIFIED') process.exit(1); console.log(j.data.id);" "${dispute_body}"
}

pa_register_merchant "${tag}"
pa_create_api_key "${PA_COOKIE_JAR}" "Phase09 evidence object storage" "${merchant_body}"
api_key="$(auth_extract_api_key "${merchant_body}")"
merchant_cookie="${PA_COOKIE_JAR}"
merchant_id="${PA_MERCHANT_ID}"

user_id="$(p05_register_enduser "${tag}" "${enduser_cookie}")"
approve_kyc "${user_id}"

dispute_id="$(create_dispute)"
storage_key="chargeback-evidence/runtime/${tag}-delivery-proof.txt"
evidence_payload="chargeback evidence runtime payload"
evidence_payload_base64="Y2hhcmdlYmFjayBldmlkZW5jZSBydW50aW1lIHBheWxvYWQ="
bad_base64_json="{\"narrative\":\"Bad base64 must be rejected before metadata.\",\"attachments\":[{\"fileName\":\"bad.txt\",\"contentType\":\"text/plain\",\"storageKey\":\"chargeback-evidence/runtime/${tag}-bad.txt\",\"sizeBytes\":35,\"contentBase64\":\"not-valid-base64\"}]}"
size_mismatch_json="{\"narrative\":\"Size mismatch must be rejected before metadata.\",\"attachments\":[{\"fileName\":\"mismatch.txt\",\"contentType\":\"text/plain\",\"storageKey\":\"chargeback-evidence/runtime/${tag}-mismatch.txt\",\"sizeBytes\":34,\"contentBase64\":\"${evidence_payload_base64}\"}]}"
evidence_json="{\"narrative\":\"Delivery proof backed by local object storage.\",\"attachments\":[{\"fileName\":\"delivery-proof.txt\",\"contentType\":\"text/plain\",\"storageKey\":\"${storage_key}\",\"sizeBytes\":35,\"contentBase64\":\"${evidence_payload_base64}\"}]}"

test "$(pa_curl -sS -b "${merchant_cookie}" -o "${bad_base64_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${bad_base64_json}")" = "400"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_attachment') process.exit(1);" "${bad_base64_body}"; echo ok)" = "ok"
test "$(p05_platform_psql "select count(*) from chargeback.evidence_submissions where dispute_id='${dispute_id}'::uuid;")" = "0"

test "$(pa_curl -sS -b "${merchant_cookie}" -o "${size_mismatch_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${size_mismatch_json}")" = "400"
test "$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.errors?.[0]?.code!=='invalid_attachment') process.exit(1);" "${size_mismatch_body}"; echo ok)" = "ok"
test "$(p05_platform_psql "select count(*) from chargeback.evidence_submissions where dispute_id='${dispute_id}'::uuid;")" = "0"

test "$(pa_curl -sS -b "${merchant_cookie}" -o "${evidence_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/disputes/${dispute_id}/evidence" -H "Content-Type: application/json" -d "${evidence_json}")" = "200"
evidence_id="$(node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(j.data?.state!=='EVIDENCE_SUBMITTED'||j.data?.attachments?.[0]?.storageKey!==process.argv[2]) process.exit(1); console.log(j.data.id);" "${evidence_body}" "${storage_key}")"

object_curl -fsS -o "${object_body}" "${object_storage_base_url}/chargeback-evidence/${storage_key}"
test "$(cat "${object_body}")" = "${evidence_payload}"

test "$(p05_platform_psql "select count(*) from chargeback.evidence_submissions where id='${evidence_id}'::uuid and dispute_id='${dispute_id}'::uuid and merchant_id='${merchant_id}'::uuid;")" = "1"
test "$(p05_platform_psql "select count(*) from chargeback.evidence_attachments where evidence_submission_id='${evidence_id}'::uuid and storage_key='${storage_key}' and size_bytes=35;")" = "1"

printf 'CHB-08 evidence object storage pass dispute_id=%s evidence_id=%s storage_key=%s user_id=%s\n' "${dispute_id}" "${evidence_id}" "${storage_key}" "${user_id}"
