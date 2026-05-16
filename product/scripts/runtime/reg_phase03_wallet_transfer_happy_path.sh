#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

sender_cookie="/tmp/minifin-phase03-transfer-happy-sender-cookies.txt"
receiver_cookie="/tmp/minifin-phase03-transfer-happy-receiver-cookies.txt"
transfer_body="/tmp/minifin-phase03-transfer-happy-create.json"
sender_wallet_body="/tmp/minifin-phase03-transfer-happy-sender-wallet.json"
receiver_wallet_body="/tmp/minifin-phase03-transfer-happy-receiver-wallet.json"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

sender_id="$(wd_register_enduser transfer-happy-sender "${sender_cookie}")"
receiver_id="$(wd_register_enduser transfer-happy-receiver "${receiver_cookie}")"
wd_fund_wallet "${sender_cookie}" "40.0000" "${operator_token}" "transfer-happy" >/tmp/minifin-phase03-transfer-happy-funded.txt

wd_create_transfer "${sender_cookie}" "${receiver_id}" "15.0000" "${transfer_body}" "transfer-happy-$(date +%s%N)"
transfer_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${transfer_body}','utf8')); if(!j.data?.transferId||j.data?.state!=='COMPLETED'||j.data?.amount!=='15.0000'||j.data?.senderUserId!=='${sender_id}'||j.data?.receiverUserId!=='${receiver_id}'||!j.data?.journalEntryId) process.exit(1); console.log(j.data.transferId);")"
journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${transfer_body}','utf8')); console.log(j.data.journalEntryId);")"

posting_count="$(wd_psql "select count(*) from ledger.postings where journal_entry_id = '${journal_id}'::uuid;")"
test "${posting_count}" = "2"
debit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${journal_id}'::uuid and p.side = 'DEBIT';")"
test "${debit_code}" = "WALLET_USER:${sender_id}"
credit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${journal_id}'::uuid and p.side = 'CREDIT';")"
test "${credit_code}" = "WALLET_USER:${receiver_id}"

sender_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${sender_id}';")"
test "${sender_balance}" = "25.0000"
receiver_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${receiver_id}';")"
test "${receiver_balance}" = "15.0000"

curl -fsS -b "${sender_cookie}" "${base_url}/api/v1/wallet" >"${sender_wallet_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${sender_wallet_body}','utf8')); if(j.data?.balance!=='25.0000'||!Array.isArray(j.data?.transfers)||!j.data.transfers.some(t => t.transferId === '${transfer_id}' && t.senderUserId === '${sender_id}')) process.exit(1);"

curl -fsS -b "${receiver_cookie}" "${base_url}/api/v1/wallet" >"${receiver_wallet_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${receiver_wallet_body}','utf8')); if(j.data?.balance!=='15.0000'||!Array.isArray(j.data?.transfers)||!j.data.transfers.some(t => t.transferId === '${transfer_id}' && t.receiverUserId === '${receiver_id}')) process.exit(1);"

created_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.internal_transfer_completed' and subject_id = '${transfer_id}'::uuid;")"
test "${created_audit}" -ge 1

curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >/tmp/minifin-phase03-transfer-happy-recon.json
node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase03-transfer-happy-recon.json','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT internal transfer happy path pass"
