#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-withdraw-happy-cookies.txt"
withdraw_body="/tmp/minifin-phase03-withdraw-happy-create.json"
decision_body="/tmp/minifin-phase03-withdraw-happy-decision.json"
wallet_body="/tmp/minifin-phase03-withdraw-happy-wallet.json"
queue_body="/tmp/minifin-phase03-withdraw-happy-queue.json"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

user_id="$(wd_register_enduser withdraw-happy "${cookie_jar}")"
wd_fund_wallet "${cookie_jar}" "50.0000" "${operator_token}" "withdraw-happy" >/tmp/minifin-phase03-withdraw-happy-funded.txt

wd_create_withdrawal "${cookie_jar}" "12.0000" "${withdraw_body}"
withdrawal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_body}','utf8')); if(!j.data?.withdrawalId||j.data?.state!=='HELD'||j.data?.amount!=='12.0000'||!j.data?.holdJournalEntryId) process.exit(1); console.log(j.data.withdrawalId);")"
hold_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_body}','utf8')); console.log(j.data.holdJournalEntryId);")"

curl -fsS -b "${cookie_jar}" "${base_url}/api/v1/wallet" >"${wallet_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${wallet_body}','utf8')); if(j.data?.balance!=='38.0000'||!Array.isArray(j.data?.withdrawals)||j.data.withdrawals[0]?.state!=='HELD') process.exit(1);"

hold_posting_count="$(wd_psql "select count(*) from ledger.postings where journal_entry_id = '${hold_journal_id}'::uuid;")"
test "${hold_posting_count}" = "2"
hold_debit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${hold_journal_id}'::uuid and p.side = 'DEBIT';")"
test "${hold_debit_code}" = "WALLET_USER:${user_id}"
hold_credit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${hold_journal_id}'::uuid and p.side = 'CREDIT';")"
test "${hold_credit_code}" = "WALLET_WITHDRAW_HOLD:${user_id}"

curl -fsS -H "Authorization: Bearer ${operator_token}" "${base_url}/api/v1/backoffice/manual-ops/withdrawals" >"${queue_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${queue_body}','utf8')); if(!Array.isArray(j.data) || !j.data.some(w => w.withdrawalId === '${withdrawal_id}')) process.exit(1);"

curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"COMPLETE","reason":"runtime hold complete"}' \
  >"${decision_body}"

node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); if(j.data?.state!=='COMPLETED'||!j.data?.completionJournalEntryId) process.exit(1);"
completion_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); console.log(j.data.completionJournalEntryId);")"

completion_debit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${completion_journal_id}'::uuid and p.side = 'DEBIT';")"
test "${completion_debit_code}" = "WALLET_WITHDRAW_HOLD:${user_id}"
completion_credit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${completion_journal_id}'::uuid and p.side = 'CREDIT';")"
test "${completion_credit_code}" = "EXTERNAL_WITHDRAWAL_CLEARING"

wallet_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${user_id}';")"
test "${wallet_balance}" = "38.0000"
hold_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_WITHDRAW_HOLD:${user_id}';")"
test "${hold_balance}" = "0.0000"

created_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.withdrawal_hold_created' and subject_id = '${withdrawal_id}'::uuid;")"
completed_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.withdrawal_completed' and subject_id = '${withdrawal_id}'::uuid;")"
read_audit="$(wd_psql "select count(*) from audit.read_audit_log where resource_type = 'WALLET_WITHDRAW_REQUEST' and resource_id = '${withdrawal_id}'::uuid;")"
test "${created_audit}" -ge 1
test "${completed_audit}" -ge 1
test "${read_audit}" -ge 1

curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >/tmp/minifin-phase03-withdraw-happy-recon.json
node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase03-withdraw-happy-recon.json','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT withdraw hold complete pass"
