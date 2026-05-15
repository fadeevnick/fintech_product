#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
email="phase02.$(date +%s%N)@example.test"
password="correct horse battery"
cookie_jar="/tmp/minifin-phase02-auth-cookies.txt"
register_body="/tmp/minifin-phase02-register.json"
verify_body="/tmp/minifin-phase02-verify.json"
login_body="/tmp/minifin-phase02-login.json"
me_body="/tmp/minifin-phase02-me.json"

curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
  >"${register_body}"

verification_token="$(node -e "const body=require('fs').readFileSync('${register_body}','utf8'); const parsed=JSON.parse(body); if(!parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.verificationToken);")"

curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${verification_token}\"}" \
  >"${verify_body}"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${verify_body}','utf8')); if(parsed.data?.status !== 'ACTIVE') process.exit(1);"

curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
  >"${login_body}"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${login_body}','utf8')); if(parsed.data?.status !== 'ACTIVE') process.exit(1);"
grep -q "MFP_SESSION" "${cookie_jar}"

curl -fsS -b "${cookie_jar}" "${base_url}/api/v1/enduser/me" >"${me_body}"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${me_body}','utf8')); if(parsed.data?.email !== '${email}') process.exit(1);"

echo "AUTH-01 end-user register verify login me pass"
