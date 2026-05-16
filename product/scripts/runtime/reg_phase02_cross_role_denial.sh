#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
password="correct horse battery"
enduser_email="phase02.role.enduser.$(date +%s%N)@example.test"
merchant_email="phase02.role.merchant.$(date +%s%N)@example.test"
enduser_cookies="/tmp/minifin-phase02-role-enduser-cookies.txt"
merchant_cookies="/tmp/minifin-phase02-role-merchant-cookies.txt"

curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${enduser_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-role-enduser-register.json
enduser_token="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-role-enduser-register.json','utf8')); if(!parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.verificationToken);")"
curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${enduser_token}\"}" \
  >/tmp/minifin-phase02-role-enduser-verify.json
curl -fsS -c "${enduser_cookies}" -X POST "${base_url}/api/v1/enduser/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${enduser_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-role-enduser-login.json

status="$(curl -sS -b "${enduser_cookies}" -o /tmp/minifin-phase02-role-enduser-to-merchant.json -w "%{http_code}" "${base_url}/api/v1/merchant/me")"
test "${status}" = "403"
grep -q '"code":"forbidden_actor_type"' /tmp/minifin-phase02-role-enduser-to-merchant.json

curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${merchant_email}\",\"password\":\"${password}\",\"companyName\":\"Role Merchant\",\"country\":\"US\",\"businessType\":\"company\"}" \
  >/tmp/minifin-phase02-role-merchant-register.json
merchant_token="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-role-merchant-register.json','utf8')); if(!parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.verificationToken);")"
curl -fsS -X POST "${base_url}/api/v1/merchant/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${merchant_token}\"}" \
  >/tmp/minifin-phase02-role-merchant-verify.json
curl -fsS -c "${merchant_cookies}" -X POST "${base_url}/api/v1/merchant/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${merchant_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-role-merchant-login.json

status="$(curl -sS -b "${merchant_cookies}" -o /tmp/minifin-phase02-role-merchant-to-enduser.json -w "%{http_code}" "${base_url}/api/v1/enduser/me")"
test "${status}" = "403"
grep -q '"code":"forbidden_actor_type"' /tmp/minifin-phase02-role-merchant-to-enduser.json

echo "AUTH-05 end-user merchant wrong-role denial partial"
