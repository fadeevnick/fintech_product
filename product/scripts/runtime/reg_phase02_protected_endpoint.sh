#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
body="/tmp/minifin-phase02-protected-endpoint.json"

status="$(curl -sS -o "${body}" -w "%{http_code}" "${base_url}/api/v1/enduser/me")"
test "${status}" = "401"
grep -q '"code":"unauthenticated"' "${body}"

echo "AUTH-05 protected endpoint denial partial"
