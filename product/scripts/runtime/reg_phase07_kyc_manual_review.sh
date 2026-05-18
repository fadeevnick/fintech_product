#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase07_kyc_sumsub.sh"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

password="correct horse battery"
tag="manual-review-$(date +%s%N)"
profile_id="$(cat /proc/sys/kernel/random/uuid)"
end_user_id="$(cat /proc/sys/kernel/random/uuid)"
applicant_id="sumsub-applicant-${tag}"
queue_response="/tmp/minifin-phase07-kyc-manual-queue.json"
detail_response="/tmp/minifin-phase07-kyc-manual-detail.json"
short_response="/tmp/minifin-phase07-kyc-manual-short.json"
decision_response="/tmp/minifin-phase07-kyc-manual-decision.json"
repeat_response="/tmp/minifin-phase07-kyc-manual-repeat.json"
enduser_cookies="/tmp/minifin-phase07-kyc-manual-enduser-cookies.txt"
merchant_cookies="/tmp/minifin-phase07-kyc-manual-merchant-cookies.txt"

kyc_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${end_user_id}'::uuid, '${end_user_id}@example.test', '${end_user_id}@example.test', 'runtime-hash', 'ACTIVE');"
kyc_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, review_answer, review_reject_type, review_moderation_comment) values ('${profile_id}'::uuid, '${end_user_id}'::uuid, 'IN_REVIEW', 'SUMSUB', '${applicant_id}', 'basic-kyc-level', 'enduser:${end_user_id}', now(), now(), 'RED', 'RETRY', 'runtime fixture review');"

kc_seed_backoffice_realm
token="$(kc_backoffice_token operator)"

kyc_curl -fsS "${base_url}/api/v1/backoffice/kyc-cases" \
  -H "Authorization: Bearer ${token}" \
  >"${queue_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${queue_response}','utf8')); if(!j.data?.some(c => c.id === '${profile_id}' && c.status === 'IN_REVIEW')) process.exit(1);"

kyc_curl -fsS "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}" \
  -H "Authorization: Bearer ${token}" \
  >"${detail_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${detail_response}','utf8')); const d=j.data; if(d?.id !== '${profile_id}' || d.status !== 'IN_REVIEW' || d.vendorApplicantId !== '${applicant_id}' || JSON.stringify(d).includes('document')) process.exit(1);"

status="$(kyc_curl -sS -o "${short_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","rationale":"too short"}')"
test "${status}" = "400"
grep -q '"code":"invalid_rationale"' "${short_response}"

kyc_curl -fsS -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","rationale":"Verified identity evidence and Sumsub review details are acceptable for MVP manual approval."}' \
  >"${decision_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${decision_response}','utf8')); if(j.data?.caseId !== '${profile_id}' || j.data?.status !== 'APPROVED' || j.data?.decision !== 'APPROVE') process.exit(1);"

status="$(kyc_curl -sS -o "${repeat_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"REJECT","rationale":"Second decision should be rejected because case is already terminal."}')"
test "${status}" = "409"
grep -q '"code":"invalid_state"' "${repeat_response}"

profile_status="$(kyc_psql "select status from kyc.kyc_profiles where id = '${profile_id}'::uuid;")"
decision_rows="$(kyc_psql "select count(*) from kyc.kyc_manual_decisions where kyc_profile_id = '${profile_id}'::uuid and decision = 'APPROVE' and resulting_status = 'APPROVED';")"
audit_rows="$(kyc_psql "select count(*) from audit.audit_log where event_type = 'kyc.manual_decision_recorded' and subject_id = '${profile_id}'::uuid;")"
test "${profile_status}" = "APPROVED"
test "${decision_rows}" = "1"
test "${audit_rows}" = "1"

enduser_email="phase07.kyc.manual.enduser.${tag}@example.test"
kyc_curl -fsS -X POST "${base_url}/api/v1/enduser/register" -H "Content-Type: application/json" -d "{\"email\":\"${enduser_email}\",\"password\":\"${password}\"}" >/tmp/minifin-phase07-kyc-manual-enduser-register.json
enduser_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase07-kyc-manual-enduser-register.json','utf8')); console.log(j.data.verificationToken);")"
kyc_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" -H "Content-Type: application/json" -d "{\"token\":\"${enduser_token}\"}" >/tmp/minifin-phase07-kyc-manual-enduser-verify.json
kyc_curl -fsS -c "${enduser_cookies}" -X POST "${base_url}/api/v1/enduser/login" -H "Content-Type: application/json" -d "{\"email\":\"${enduser_email}\",\"password\":\"${password}\"}" >/tmp/minifin-phase07-kyc-manual-enduser-login.json
status="$(kyc_curl -sS -b "${enduser_cookies}" -o /tmp/minifin-phase07-kyc-manual-enduser-denial.json -w "%{http_code}" "${base_url}/api/v1/backoffice/kyc-cases")"
test "${status}" = "401"
grep -q '"code":"unauthenticated"' /tmp/minifin-phase07-kyc-manual-enduser-denial.json

merchant_email="phase07.kyc.manual.merchant.${tag}@example.test"
kyc_curl -fsS -X POST "${base_url}/api/v1/merchant/register" -H "Content-Type: application/json" -d "{\"email\":\"${merchant_email}\",\"password\":\"${password}\",\"companyName\":\"KYC Manual Denial Merchant\",\"country\":\"US\",\"businessType\":\"company\"}" >/tmp/minifin-phase07-kyc-manual-merchant-register.json
merchant_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase07-kyc-manual-merchant-register.json','utf8')); console.log(j.data.verificationToken);")"
kyc_curl -fsS -X POST "${base_url}/api/v1/merchant/email/verify" -H "Content-Type: application/json" -d "{\"token\":\"${merchant_token}\"}" >/tmp/minifin-phase07-kyc-manual-merchant-verify.json
kyc_curl -fsS -c "${merchant_cookies}" -X POST "${base_url}/api/v1/merchant/login" -H "Content-Type: application/json" -d "{\"email\":\"${merchant_email}\",\"password\":\"${password}\"}" >/tmp/minifin-phase07-kyc-manual-merchant-login.json
status="$(kyc_curl -sS -b "${merchant_cookies}" -o /tmp/minifin-phase07-kyc-manual-merchant-denial.json -w "%{http_code}" "${base_url}/api/v1/backoffice/kyc-cases")"
test "${status}" = "401"
grep -q '"code":"unauthenticated"' /tmp/minifin-phase07-kyc-manual-merchant-denial.json

echo "KYC-03 backoffice manual KYC review pass profile_id=${profile_id} applicant_id=${applicant_id}"
