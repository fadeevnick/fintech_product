#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
suffix="$(date +%s%N)"
debit_body="/tmp/minifin-phase03-ledger-balanced-debit.json"
credit_body="/tmp/minifin-phase03-ledger-balanced-credit.json"
journal_body="/tmp/minifin-phase03-ledger-balanced-journal.json"
debit_balance_body="/tmp/minifin-phase03-ledger-balanced-debit-balance.json"
credit_balance_body="/tmp/minifin-phase03-ledger-balanced-credit-balance.json"
reference_id="$(node -e "console.log(crypto.randomUUID())")"

curl -fsS -X POST "${base_url}/internal/ledger/runtime/accounts" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"runtime:balanced:debit:${suffix}\",\"currency\":\"EUR\",\"accountType\":\"RUNTIME_TEST\",\"normalSide\":\"DEBIT\"}" \
  >"${debit_body}"
curl -fsS -X POST "${base_url}/internal/ledger/runtime/accounts" \
  -H "Content-Type: application/json" \
  -d "{\"code\":\"runtime:balanced:credit:${suffix}\",\"currency\":\"EUR\",\"accountType\":\"RUNTIME_TEST\",\"normalSide\":\"CREDIT\"}" \
  >"${credit_body}"

debit_account="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${debit_body}','utf8')); if(!parsed.data?.accountId) process.exit(1); console.log(parsed.data.accountId);")"
credit_account="$(node -e "const parsed=JSON.parse(require('fs').readFileSync('${credit_body}','utf8')); if(!parsed.data?.accountId) process.exit(1); console.log(parsed.data.accountId);")"

curl -fsS -X POST "${base_url}/internal/ledger/runtime/journals" \
  -H "Content-Type: application/json" \
  -d "{\"journalType\":\"RUNTIME_BALANCED\",\"referenceType\":\"RUNTIME_PROBE\",\"referenceId\":\"${reference_id}\",\"currency\":\"EUR\",\"description\":\"Balanced runtime probe\",\"postings\":[{\"accountId\":\"${debit_account}\",\"side\":\"DEBIT\",\"amount\":\"10.0000\"},{\"accountId\":\"${credit_account}\",\"side\":\"CREDIT\",\"amount\":\"10.0000\"}]}" \
  >"${journal_body}"

node -e "const parsed=JSON.parse(require('fs').readFileSync('${journal_body}','utf8')); if(parsed.data?.balanced !== true || !parsed.data?.journalId) process.exit(1);"

curl -fsS "${base_url}/internal/ledger/runtime/accounts/${debit_account}/balance" >"${debit_balance_body}"
curl -fsS "${base_url}/internal/ledger/runtime/accounts/${credit_account}/balance" >"${credit_balance_body}"

node -e "const parsed=JSON.parse(require('fs').readFileSync('${debit_balance_body}','utf8')); if(parsed.data?.balance !== '10.0000') process.exit(1);"
node -e "const parsed=JSON.parse(require('fs').readFileSync('${credit_balance_body}','utf8')); if(parsed.data?.balance !== '10.0000') process.exit(1);"

posting_count="$(docker compose -f deploy/docker-compose.yml exec -T platform-db psql -U platform -d platform -Atc "select count(*) from ledger.postings p join ledger.journal_entries j on j.id = p.journal_entry_id where j.reference_id = '${reference_id}'::uuid;")"
test "${posting_count}" = "2"

echo "LDG foundation balanced posting and derived balances pass"
