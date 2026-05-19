#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
body="/tmp/minifin-phase03-ledger-reconciliation.json"

if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
  docker run --rm -v /tmp:/tmp alpine:3.20 rm -f "${body}"
  docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 -fsS "${base_url}/internal/ledger/runtime/reconciliation" >"${body}"
else
  rm -f "${body}"
  curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >"${body}"
fi

node -e "const parsed=JSON.parse(require('fs').readFileSync('${body}','utf8')); if(parsed.data?.balancedJournals !== true) process.exit(1); if(Number(parsed.data?.journalCount ?? 0) < 1) process.exit(1);"

imbalanced_count="$(docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "with journal_sums as (select j.id, coalesce(sum(p.amount) filter (where p.side = 'DEBIT'), 0) as debit_total, coalesce(sum(p.amount) filter (where p.side = 'CREDIT'), 0) as credit_total, count(p.id) as posting_count from ledger.journal_entries j left join ledger.postings p on p.journal_entry_id = j.id group by j.id) select count(*) from journal_sums where debit_total <> credit_total or posting_count < 2;")"
test "${imbalanced_count}" = "0"

echo "LDG-05 ledger reconciliation pass"
