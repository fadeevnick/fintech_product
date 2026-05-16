#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
email="phase02.control.merchant.$(date +%s%N)@example.test"
password="correct horse battery"
cookie_jar="/tmp/minifin-phase02-control-merchant-cookies.txt"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

kc_seed_backoffice_realm
backoffice_token="$(kc_backoffice_token compliance)"

curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\",\"companyName\":\"Control Merchant\",\"country\":\"US\",\"businessType\":\"company\"}" \
  >/tmp/minifin-phase02-control-merchant-register.json
merchant_id="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-control-merchant-register.json','utf8')); if(!parsed.data?.merchantId || !parsed.data?.verificationToken) process.exit(1); console.log(parsed.data.merchantId);")"
verification_token="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-control-merchant-register.json','utf8')); console.log(parsed.data.verificationToken);")"

curl -fsS -X POST "${base_url}/api/v1/merchant/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${verification_token}\"}" \
  >/tmp/minifin-phase02-control-merchant-verify.json
curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/merchant/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
  >/tmp/minifin-phase02-control-merchant-login.json

curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/merchant/write-guard/probe" \
  >/tmp/minifin-phase02-control-merchant-active.json
node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-control-merchant-active.json','utf8')); if(parsed.data?.allowed !== true || parsed.data?.actorId !== '${merchant_id}') process.exit(1);"

curl -fsS -X POST "${base_url}/api/v1/backoffice/actor-controls" \
  -H "Authorization: Bearer ${backoffice_token}" \
  -H "Content-Type: application/json" \
  -d "{\"actorType\":\"MERCHANT\",\"actorId\":\"${merchant_id}\",\"state\":\"BLOCKED\",\"reasonCode\":\"runtime_probe\"}" \
  >/tmp/minifin-phase02-control-merchant-block.json

status="$(curl -sS -b "${cookie_jar}" -o /tmp/minifin-phase02-control-merchant-denied.json -w "%{http_code}" -X POST "${base_url}/api/v1/merchant/write-guard/probe")"
test "${status}" = "403"
grep -q '"code":"actor_control_blocked"' /tmp/minifin-phase02-control-merchant-denied.json

curl -fsS -X POST "${base_url}/api/v1/backoffice/actor-controls" \
  -H "Authorization: Bearer ${backoffice_token}" \
  -H "Content-Type: application/json" \
  -d "{\"actorType\":\"MERCHANT\",\"actorId\":\"${merchant_id}\",\"state\":\"ACTIVE\",\"reasonCode\":\"runtime_probe_clear\"}" \
  >/tmp/minifin-phase02-control-merchant-active-control.json

curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/merchant/write-guard/probe" \
  >/tmp/minifin-phase02-control-merchant-active-again.json

changed_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type = 'identity.actor_control_changed' and subject_type = 'MERCHANT' and subject_id = '${merchant_id}'::uuid;")"
test "${changed_count}" -ge 2
denied_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type = 'identity.actor_control_write_denied' and subject_type = 'MERCHANT' and subject_id = '${merchant_id}'::uuid;")"
test "${denied_count}" -ge 1

echo "ACT-CTRL merchant blocked write-guard probe pass"
