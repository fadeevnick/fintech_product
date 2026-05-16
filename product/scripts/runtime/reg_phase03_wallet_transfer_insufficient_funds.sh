#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

sender_cookie="/tmp/minifin-phase03-transfer-insufficient-sender-cookies.txt"
receiver_cookie="/tmp/minifin-phase03-transfer-insufficient-receiver-cookies.txt"
transfer_body="/tmp/minifin-phase03-transfer-insufficient-create.json"

sender_id="$(wd_register_enduser transfer-insufficient-sender "${sender_cookie}")"
receiver_id="$(wd_register_enduser transfer-insufficient-receiver "${receiver_cookie}")"

status="$(curl -sS -o "${transfer_body}" -w "%{http_code}" -b "${sender_cookie}" -X POST "${base_url}/api/v1/transfers" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: transfer-insufficient-$(date +%s%N)" \
  -d "{\"receiverUserId\":\"${receiver_id}\",\"amount\":\"10.0000\",\"currency\":\"EUR\"}")"
test "${status}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync('${transfer_body}','utf8')); if(j.errors?.[0]?.code!=='insufficient_funds') process.exit(1);"

transfer_count="$(wd_psql "select count(*) from wallet.internal_transfers where sender_user_id = '${sender_id}'::uuid;")"
test "${transfer_count}" = "0"
transfer_journal_count="$(wd_psql "select count(*) from ledger.journal_entries where journal_type = 'WALLET_INTERNAL_TRANSFER' and reference_id in (select id from wallet.internal_transfers where sender_user_id = '${sender_id}'::uuid);")"
test "${transfer_journal_count}" = "0"

curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >/tmp/minifin-phase03-transfer-insufficient-recon.json
node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase03-transfer-insufficient-recon.json','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT internal transfer insufficient funds pass"
