#!/usr/bin/env bash
set -euo pipefail

success_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type = 'identity.backoffice_authenticated';")"
test "${success_count}" -ge 1

failure_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type = 'identity.backoffice_auth_failed';")"
test "${failure_count}" -ge 1

echo "AUD-01 backoffice auth audit events pass"
