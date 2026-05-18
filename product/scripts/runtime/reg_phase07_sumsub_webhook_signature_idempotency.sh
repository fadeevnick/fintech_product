#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase07_kyc_sumsub.sh"

profile_id="$(cat /proc/sys/kernel/random/uuid)"
end_user_id="$(cat /proc/sys/kernel/random/uuid)"
applicant_id="sumsub-applicant-$(date +%s%N)"
event_id="sumsub-event-$(date +%s%N)"
payload_file="/tmp/minifin-phase07-sumsub-webhook-payload.json"
first_response="/tmp/minifin-phase07-sumsub-webhook-first.json"
second_response="/tmp/minifin-phase07-sumsub-webhook-second.json"
invalid_response="/tmp/minifin-phase07-sumsub-webhook-invalid.json"

kyc_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${end_user_id}'::uuid, '${end_user_id}@example.test', '${end_user_id}@example.test', 'runtime-hash', 'ACTIVE');"
kyc_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at) values ('${profile_id}'::uuid, '${end_user_id}'::uuid, 'SUBMITTED', 'SUMSUB', '${applicant_id}', 'basic-kyc-level', 'enduser:${end_user_id}', now());"
sumsub_json_payload "${event_id}" "${applicant_id}" "GREEN" >"${payload_file}"
signature="$(sumsub_signature_header "${payload_file}")"

invalid_status="$(kyc_curl -sS -o "${invalid_response}" -w "%{http_code}" -X POST "${base_url}/webhooks/sumsub/v1" \
  -H "Content-Type: application/json" \
  -H "X-Payload-Digest: bad-signature" \
  --data-binary @"${payload_file}")"
test "${invalid_status}" = "401"
grep -q '"code":"invalid_sumsub_signature"' "${invalid_response}"

kyc_curl -fsS -X POST "${base_url}/webhooks/sumsub/v1" \
  -H "Content-Type: application/json" \
  -H "X-Payload-Digest: ${signature}" \
  --data-binary @"${payload_file}" \
  >"${first_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${first_response}','utf8')); if(j.data?.vendorEventId !== '${event_id}' || j.data?.duplicate !== false || j.data?.status !== 'APPROVED') process.exit(1);"

kyc_curl -fsS -X POST "${base_url}/webhooks/sumsub/v1" \
  -H "Content-Type: application/json" \
  -H "X-Payload-Digest: ${signature}" \
  --data-binary @"${payload_file}" \
  >"${second_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${second_response}','utf8')); if(j.data?.vendorEventId !== '${event_id}' || j.data?.duplicate !== true || j.data?.status !== 'APPROVED') process.exit(1);"

profile_status="$(kyc_psql "select status from kyc.kyc_profiles where id = '${profile_id}'::uuid;")"
event_rows="$(kyc_psql "select count(*) from kyc.sumsub_webhook_events where vendor_event_id = '${event_id}';")"
test "${profile_status}" = "APPROVED"
test "${event_rows}" = "1"

echo "KYC-02 Sumsub webhook signature/idempotency pass profile_id=${profile_id} applicant_id=${applicant_id} event_id=${event_id}"
