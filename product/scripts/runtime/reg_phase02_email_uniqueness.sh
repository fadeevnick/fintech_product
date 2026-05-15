#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
email="phase02.unique.$(date +%s%N)@example.test"
password="correct horse battery"
first_body="/tmp/minifin-phase02-unique-first.json"
second_body="/tmp/minifin-phase02-unique-second.json"

curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
  >"${first_body}"

status="$(curl -sS -o "${second_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}")"
test "${status}" = "409"
grep -q '"code":"email_already_registered"' "${second_body}"

echo "AUTH-04 global email uniqueness foundation partial"
