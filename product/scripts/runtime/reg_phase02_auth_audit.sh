#!/usr/bin/env bash
set -euo pipefail

count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from audit.audit_log where event_type in ('identity.end_user_registered','identity.email_verified','identity.login_succeeded');")"
test "${count}" -ge 3

mutation_status="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -v ON_ERROR_STOP=0 -Atc "update audit.audit_log set outcome = 'MUTATED' where id = (select min(id) from audit.audit_log);" >/tmp/minifin-phase02-audit-mutation.txt 2>&1; echo "$?")"
test "${mutation_status}" != "0"
grep -q "audit.audit_log is append-only" /tmp/minifin-phase02-audit-mutation.txt

echo "AUD-01 auth audit events pass"
echo "AUD-02 audit append-only protection pass"
