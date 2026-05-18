#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase07_kyc_sumsub.sh"

cookie_jar="$(kyc_register_verified_enduser "kyc-start")"
response_body="/tmp/minifin-phase07-kyc-start-response.json"
status="$(kyc_curl -sS -b "${cookie_jar}" -o "${response_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/kyc/start")"

if test -n "${SUMSUB_APP_TOKEN:-}" && test -n "${SUMSUB_SECRET_KEY:-}"; then
  test "${status}" = "201"
  node -e "const j=JSON.parse(require('fs').readFileSync('${response_body}','utf8')); if(!j.data?.profileId || !j.data?.sessionId || !j.data?.vendorApplicantId || !j.data?.accessToken) process.exit(1);"
  profile_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${response_body}','utf8')); console.log(j.data.profileId);")"
  profile_status="$(kyc_psql "select status from kyc.kyc_profiles where id = '${profile_id}'::uuid;")"
  test "${profile_status}" = "SUBMITTED"
  echo "KYC-01 Sumsub KYC start pass (real Sumsub credentials configured) profile_id=${profile_id}"
else
  test "${status}" = "503"
  grep -q '"code":"sumsub_not_configured"' "${response_body}"
  profile_count="$(kyc_psql "select count(*) from kyc.kyc_profiles where external_user_id like 'enduser:%';")"
  failed_count="$(kyc_psql "select count(*) from kyc.kyc_sessions where status = 'FAILED' and failure_code = 'sumsub_not_configured';")"
  test "${profile_count}" -ge 1
  test "${failed_count}" -ge 1
  echo "KYC-01 Sumsub KYC start partial (credentials absent; local auth/config/persistence gate proven)"
fi
