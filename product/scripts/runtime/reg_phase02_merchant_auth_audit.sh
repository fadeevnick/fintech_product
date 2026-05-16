#!/usr/bin/env bash
set -euo pipefail

count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type in ('identity.merchant_registered','identity.merchant_email_verified','identity.merchant_login_succeeded');")"
test "${count}" -ge 3

failure_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type = 'identity.merchant_login_failed';")"
test "${failure_count}" -ge 1

echo "AUD-01 merchant auth audit events pass"
