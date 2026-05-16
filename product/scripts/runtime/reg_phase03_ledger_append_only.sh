#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
suffix="$(date +%s%N)"
debit_body="/tmp/minifin-phase03-ledger-append-debit.json"
credit_body="/tmp/minifin-phase03-ledger-append-credit.json"
journal_body="/tmp/minifin-phase03-ledger-append-journal.json"
reference_id="$(node -e "console.log(crypto.randomUUID())")"

curl -fsS -X POST "${base_url}/internal/ledger/runtime/accounts" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"runtime:append:debit:${suffix}\",\"currency\":\"EUR\",\"accountType\":\"RUNTIME_TEST\",\"normalSide\":\"DEBIT\"}" \
  >"${debit_body}"
curl -fsS -X POST "${base_url}/internal/ledger/runtime/accounts" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"runtime:append:credit:${suffix}\",\"currency\":\"EUR\",\"accountType\":\"RUNTIME_TEST\",\"normalSide\":\"CREDIT\"}" \
  >"${credit_body}"

debit_account="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${debit_body}','utf8')); console.log(parsed.data.accountId);")"
credit_account="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${credit_body}','utf8')); console.log(parsed.data.accountId);")"

curl -fsS -X POST "${base_url}/internal/ledger/runtime/journals" \
  -H "Content-Type: application/json" \
  -d "{\"journalType\":\"RUNTIME_APPEND_ONLY\",\"referenceType\":\"RUNTIME_PROBE\",\"referenceId\":\"${reference_id}\",\"currency\":\"EUR\",\"description\":\"Append-only runtime probe\",\"postings\":[{\"accountId\":\"${debit_account}\",\"side\":\"DEBIT\",\"amount\":\"7.0000\"},{\"accountId\":\"${credit_account}\",\"side\":\"CREDIT\",\"amount\":\"7.0000\"}]}" \
  >"${journal_body}"
journal_id="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${journal_body}','utf8')); if(!parsed.data?.journalId) process.exit(1); console.log(parsed.data.journalId);")"
posting_id="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select id from ledger.postings where journal_entry_id = '${journal_id}'::uuid limit 1;")"

journal_update_status="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -v ON_ERROR_STOP=0 -Atc "update ledger.journal_entries set description = 'mutated' where id = '${journal_id}'::uuid;" >/tmp/minifin-phase03-ledger-journal-update.txt 2>&1; echo "$?")"
test "${journal_update_status}" != "0"
grep -q "ledger tables are append-only" /tmp/minifin-phase03-ledger-journal-update.txt

posting_delete_status="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -v ON_ERROR_STOP=0 -Atc "delete from ledger.postings where id = '${posting_id}'::uuid;" >/tmp/minifin-phase03-ledger-posting-delete.txt 2>&1; echo "$?")"
test "${posting_delete_status}" != "0"
grep -q "ledger tables are append-only" /tmp/minifin-phase03-ledger-posting-delete.txt

echo "LDG-04 ledger append-only protection pass"
