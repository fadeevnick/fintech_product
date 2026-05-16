#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
email="phase02.merchant.$(date +%s%N)@example.test"
password="correct horse battery"
cookie_jar="/tmp/minifin-phase02-merchant-auth-cookies.txt"
register_body="/tmp/minifin-phase02-merchant-register.json"
verify_body="/tmp/minifin-phase02-merchant-verify.json"
login_body="/tmp/minifin-phase02-merchant-login.json"
me_body="/tmp/minifin-phase02-merchant-me.json"

curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\",\"companyName\":\"Phase 02 Merchant\",\"country\":\"US\",\"businessType\":\"company\"}" \
  >"${register_body}"

verification_token="$(node -e "const body=require('fs').readFileSync('${register_body}','utf8'); const parsed=JSON.parse(body); if(parsed.data?.employeeStatus !== 'EMAIL_UNVERIFIED' || !parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.verificationToken);")"

status="$(curl -sS -o /tmp/minifin-phase02-merchant-login-unverified.json -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}")"
test "${status}" = "403"
grep -q '"code":"email_not_verified"' /tmp/minifin-phase02-merchant-login-unverified.json

curl -fsS -X POST "${base_url}/api/v1/merchant/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${verification_token}\"}" \
  >"${verify_body}"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${verify_body}','utf8')); if(parsed.data?.employeeStatus !== 'ACTIVE' || parsed.data?.role !== 'merchant_admin') process.exit(1);"

curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/merchant/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
  >"${login_body}"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${login_body}','utf8')); if(parsed.data?.employeeStatus !== 'ACTIVE' || parsed.data?.merchantStatus !== 'NOT_STARTED') process.exit(1);"
grep -q "MFP_SESSION" "${cookie_jar}"

curl -fsS -b "${cookie_jar}" "${base_url}/api/v1/merchant/me" >"${me_body}"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${me_body}','utf8')); if(parsed.data?.email !== '${email}' || parsed.data?.role !== 'merchant_admin') process.exit(1);"

echo "AUTH-02 merchant register verify login me pass"
