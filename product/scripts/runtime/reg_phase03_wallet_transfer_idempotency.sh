#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

sender_cookie="/tmp/minifin-phase03-transfer-idem-sender-cookies.txt"
receiver_cookie="/tmp/minifin-phase03-transfer-idem-receiver-cookies.txt"
first_body="/tmp/minifin-phase03-transfer-idem-first.json"
second_body="/tmp/minifin-phase03-transfer-idem-second.json"
conflict_body="/tmp/minifin-phase03-transfer-idem-conflict.json"
idempotency_key="transfer-idem-$(date +%s%N)"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

sender_id="$(wd_register_enduser transfer-idem-sender "${sender_cookie}")"
receiver_id="$(wd_register_enduser transfer-idem-receiver "${receiver_cookie}")"
wd_fund_wallet "${sender_cookie}" "30.0000" "${operator_token}" "transfer-idem" >/tmp/minifin-phase03-transfer-idem-funded.txt

wd_create_transfer "${sender_cookie}" "${receiver_id}" "7.0000" "${first_body}" "${idempotency_key}"
first_transfer_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); if(!j.data?.transferId||j.data?.state!=='COMPLETED'||!j.data?.journalEntryId) process.exit(1); console.log(j.data.transferId);")"
first_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); console.log(j.data.journalEntryId);")"

wd_create_transfer "${sender_cookie}" "${receiver_id}" "7.0000" "${second_body}" "${idempotency_key}"
second_transfer_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${second_body}','utf8')); if(!j.data?.transferId||j.data?.state!=='COMPLETED'||!j.data?.journalEntryId) process.exit(1); console.log(j.data.transferId);")"
second_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${second_body}','utf8')); console.log(j.data.journalEntryId);")"
test "${first_transfer_id}" = "${second_transfer_id}"
test "${first_journal_id}" = "${second_journal_id}"

row_count="$(wd_psql "select count(*) from wallet.internal_transfers where sender_user_id = '${sender_id}'::uuid and idempotency_key = '${idempotency_key}';")"
test "${row_count}" = "1"
journal_count="$(wd_psql "select count(*) from ledger.journal_entries where journal_type = 'WALLET_INTERNAL_TRANSFER' and reference_id = '${first_transfer_id}'::uuid;")"
test "${journal_count}" = "1"

status="$(curl -sS -o "${conflict_body}" -w "%{http_code}" -b "${sender_cookie}" -X POST "${base_url}/api/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${idempotency_key}" \
  -d "{\"receiverUserId\":\"${receiver_id}\",\"amount\":\"8.0000\",\"currency\":\"EUR\"}")"
test "${status}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync('${conflict_body}','utf8')); if(j.errors?.[0]?.code!=='transfer_idempotency_conflict') process.exit(1);"

sender_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${sender_id}';")"
test "${sender_balance}" = "23.0000"
receiver_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${receiver_id}';")"
test "${receiver_balance}" = "7.0000"

curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >/tmp/minifin-phase03-transfer-idem-recon.json
node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase03-transfer-idem-recon.json','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT internal transfer idempotency pass"
