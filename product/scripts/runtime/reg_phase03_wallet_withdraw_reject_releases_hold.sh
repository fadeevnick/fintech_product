#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-withdraw-reject-cookies.txt"
withdraw_body="/tmp/minifin-phase03-withdraw-reject-create.json"
decision_body="/tmp/minifin-phase03-withdraw-reject-decision.json"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

user_id="$(wd_register_enduser withdraw-reject "${cookie_jar}")"
wd_fund_wallet "${cookie_jar}" "40.0000" "${operator_token}" "withdraw-reject" >/tmp/minifin-phase03-withdraw-reject-funded.txt

wd_create_withdrawal "${cookie_jar}" "15.0000" "${withdraw_body}"
withdrawal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_body}','utf8')); if(j.data?.state!=='HELD'||!j.data?.holdJournalEntryId) process.exit(1); console.log(j.data.withdrawalId);")"

held_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${user_id}';")"
test "${held_balance}" = "25.0000"

curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"REJECT","reason":"runtime reject release"}' \
  >"${decision_body}"

node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); if(j.data?.state!=='REJECTED'||!j.data?.releaseJournalEntryId) process.exit(1);"
release_journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); console.log(j.data.releaseJournalEntryId);")"

release_debit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${release_journal_id}'::uuid and p.side = 'DEBIT';")"
test "${release_debit_code}" = "WALLET_WITHDRAW_HOLD:${user_id}"
release_credit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${release_journal_id}'::uuid and p.side = 'CREDIT';")"
test "${release_credit_code}" = "WALLET_USER:${user_id}"

wallet_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${user_id}';")"
test "${wallet_balance}" = "40.0000"
hold_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_WITHDRAW_HOLD:${user_id}';")"
test "${hold_balance}" = "0.0000"

rejected_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.withdrawal_rejected' and subject_id = '${withdrawal_id}'::uuid;")"
test "${rejected_audit}" -ge 1

echo "WLT withdraw reject releases hold pass"
