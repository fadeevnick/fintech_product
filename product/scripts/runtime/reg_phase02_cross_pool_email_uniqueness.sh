#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
password="correct horse battery"
enduser_first_email="phase02.cross.enduser.$(date +%s%N)@example.test"
merchant_first_email="phase02.cross.merchant.$(date +%s%N)@example.test"

curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${enduser_first_email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-cross-enduser-first.json

status="$(curl -sS -o /tmp/minifin-phase02-cross-merchant-duplicate.json -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${enduser_first_email}\",\"password\":\"${password}\",\"companyName\":\"Duplicate Merchant\",\"country\":\"US\",\"businessType\":\"company\"}")"
test "${status}" = "409"
grep -q '"code":"email_already_registered"' /tmp/minifin-phase02-cross-merchant-duplicate.json

curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${merchant_first_email}\",\"password\":\"${password}\",\"companyName\":\"Cross Pool Merchant\",\"country\":\"US\",\"businessType\":\"company\"}" \
  >/tmp/minifin-phase02-cross-merchant-first.json

status="$(curl -sS -o /tmp/minifin-phase02-cross-enduser-duplicate.json -w "%{http_code}" -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${merchant_first_email}\",\"password\":\"${password}\"}")"
test "${status}" = "409"
grep -q '"code":"email_already_registered"' /tmp/minifin-phase02-cross-enduser-duplicate.json

echo "AUTH-04 end-user merchant cross-pool email uniqueness pass"
