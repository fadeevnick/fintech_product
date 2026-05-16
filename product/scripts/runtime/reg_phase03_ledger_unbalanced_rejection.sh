#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
suffix="$(date +%s%N)"
debit_body="/tmp/minifin-phase03-ledger-unbalanced-debit.json"
credit_body="/tmp/minifin-phase03-ledger-unbalanced-credit.json"
error_body="/tmp/minifin-phase03-ledger-unbalanced-error.json"
reference_id="$(node -e "console.log(crypto.randomUUID())")"

curl -fsS -X POST "${base_url}/internal/ledger/runtime/accounts" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"runtime:unbalanced:debit:${suffix}\",\"currency\":\"EUR\",\"accountType\":\"RUNTIME_TEST\",\"normalSide\":\"DEBIT\"}" \
  >"${debit_body}"
curl -fsS -X POST "${base_url}/internal/ledger/runtime/accounts" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"runtime:unbalanced:credit:${suffix}\",\"currency\":\"EUR\",\"accountType\":\"RUNTIME_TEST\",\"normalSide\":\"CREDIT\"}" \
  >"${credit_body}"

debit_account="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${debit_body}','utf8')); if(!parsed.data?.accountId) process.exit(1); console.log(parsed.data.accountId);")"
credit_account="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${credit_body}','utf8')); if(!parsed.data?.accountId) process.exit(1); console.log(parsed.data.accountId);")"

status="$(curl -sS -o "${error_body}" -w "%{http_code}" -X POST "${base_url}/internal/ledger/runtime/journals" \
  -H "Content-Type: application/json" \
  -d "{\"journalType\":\"RUNTIME_UNBALANCED\",\"referenceType\":\"RUNTIME_PROBE\",\"referenceId\":\"${reference_id}\",\"currency\":\"EUR\",\"description\":\"Unbalanced runtime probe\",\"postings\":[{\"accountId\":\"${debit_account}\",\"side\":\"DEBIT\",\"amount\":\"10.0000\"},{\"accountId\":\"${credit_account}\",\"side\":\"CREDIT\",\"amount\":\"9.0000\"}]}")"

test "${status}" = "400"
grep -q '"code":"ledger_journal_unbalanced"' "${error_body}"

persisted_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from ledger.journal_entries where reference_id = '${reference_id}'::uuid;")"
test "${persisted_count}" = "0"

echo "LDG-01 unbalanced journal rejection pass"
