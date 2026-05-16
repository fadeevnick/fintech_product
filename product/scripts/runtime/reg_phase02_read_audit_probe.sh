#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

kc_seed_backoffice_realm
token="$(kc_backoffice_token operator)"
resource_id="$(node -e "console.log(crypto.randomUUID())")"
body="/tmp/minifin-phase02-read-audit-probe.json"

curl -fsS "${base_url}/api/v1/backoffice/read-audit/probe/${resource_id}" \
  -H "Authorization: Bearer ${token}" \
  >"${body}"

node -e "const parsed=JSON.parse(require('fs').readFileSync('${body}','utf8')); if(parsed.data?.resourceId !== '${resource_id}' || parsed.data?.readAudited !== true) process.exit(1);"

count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.read_audit_log where resource_type = 'READ_AUDIT_PROBE' and resource_id = '${resource_id}'::uuid and decision = 'ALLOW';")"
test "${count}" = "1"

mutation_status="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -v ON_ERROR_STOP=0 -Atc "update audit.read_audit_log set decision = 'DENY' where resource_id = '${resource_id}'::uuid;" >/tmp/minifin-phase02-read-audit-mutation.txt 2>&1; echo "$?")"
test "${mutation_status}" != "0"
grep -q "audit.audit_log is append-only" /tmp/minifin-phase02-read-audit-mutation.txt

echo "AUD-03 read-audit primitive partial"
echo "AUD-99 read-audit append-only foundation partial"
