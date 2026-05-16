#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

kc_seed_backoffice_realm
token="$(kc_backoffice_token operator)"

curl -fsS "${base_url}/api/v1/backoffice/me" \
  -H "Authorization: Bearer ${token}" \
  >/tmp/minifin-phase02-backoffice-me.json

node -e "const parsed=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase02-backoffice-me.json','utf8')); if(!parsed.data?.subject || !parsed.data?.roles?.includes('backoffice_operator')) process.exit(1);"

echo "AUTH-03 backoffice OIDC role mapping pass"
