#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
password="correct horse battery"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

kc_seed_backoffice_realm

status="$(curl -sS -o /tmp/minifin-phase02-backoffice-missing-token.json -w "%{http_code}" "${base_url}/api/v1/backoffice/me")"
test "${status}" = "401"
grep -q '"code":"unauthenticated"' /tmp/minifin-phase02-backoffice-missing-token.json

viewer_token="$(kc_backoffice_token viewer)"
status="$(curl -sS -o /tmp/minifin-phase02-backoffice-forbidden-role.json -w "%{http_code}" "${base_url}/api/v1/backoffice/me" \
  -H "Authorization: Bearer ${viewer_token}")"
test "${status}" = "403"
grep -q '"code":"forbidden_role"' /tmp/minifin-phase02-backoffice-forbidden-role.json

enduser_email="phase02.backoffice.denial.enduser.$(date +%s%N)@example.test"
enduser_cookies="/tmp/minifin-phase02-backoffice-denial-enduser-cookies.txt"
curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${enduser_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-backoffice-denial-enduser-register.json
enduser_token="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-backoffice-denial-enduser-register.json','utf8')); if(!parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.verificationToken);")"
curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${enduser_token}\"}" \
  >/tmp/minifin-phase02-backoffice-denial-enduser-verify.json
curl -fsS -c "${enduser_cookies}" -X POST "${base_url}/api/v1/enduser/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${enduser_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-backoffice-denial-enduser-login.json
status="$(curl -sS -b "${enduser_cookies}" -o /tmp/minifin-phase02-backoffice-enduser-cookie.json -w "%{http_code}" "${base_url}/api/v1/backoffice/me")"
test "${status}" = "401"
grep -q '"code":"unauthenticated"' /tmp/minifin-phase02-backoffice-enduser-cookie.json

merchant_email="phase02.backoffice.denial.merchant.$(date +%s%N)@example.test"
merchant_cookies="/tmp/minifin-phase02-backoffice-denial-merchant-cookies.txt"
curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${merchant_email}\",\"password\":\"${password}\",\"companyName\":\"Backoffice Denial Merchant\",\"country\":\"US\",\"businessType\":\"company\"}" \
  >/tmp/minifin-phase02-backoffice-denial-merchant-register.json
merchant_token="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-backoffice-denial-merchant-register.json','utf8')); if(!parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.verificationToken);")"
curl -fsS -X POST "${base_url}/api/v1/merchant/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${merchant_token}\"}" \
  >/tmp/minifin-phase02-backoffice-denial-merchant-verify.json
curl -fsS -c "${merchant_cookies}" -X POST "${base_url}/api/v1/merchant/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${merchant_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-backoffice-denial-merchant-login.json
status="$(curl -sS -b "${merchant_cookies}" -o /tmp/minifin-phase02-backoffice-merchant-cookie.json -w "%{http_code}" "${base_url}/api/v1/backoffice/me")"
test "${status}" = "401"
grep -q '"code":"unauthenticated"' /tmp/minifin-phase02-backoffice-merchant-cookie.json

echo "AUTH-05 backoffice unauthenticated and wrong-role denial pass"
