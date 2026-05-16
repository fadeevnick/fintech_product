#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-withdraw-double-cookies.txt"
withdraw_body="/tmp/minifin-phase03-withdraw-double-create.json"
first_body="/tmp/minifin-phase03-withdraw-double-first.json"
second_body="/tmp/minifin-phase03-withdraw-double-second.json"
late_reject_body="/tmp/minifin-phase03-withdraw-double-late-reject.json"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

user_id="$(wd_register_enduser withdraw-double "${cookie_jar}")"
wd_fund_wallet "${cookie_jar}" "30.0000" "${operator_token}" "withdraw-double" >/tmp/minifin-phase03-withdraw-double-funded.txt

wd_create_withdrawal "${cookie_jar}" "11.0000" "${withdraw_body}"
withdrawal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_body}','utf8')); if(j.data?.state!=='HELD') process.exit(1); console.log(j.data.withdrawalId);")"

curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"COMPLETE","reason":"first complete"}' \
  >"${first_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); if(j.data?.state!=='COMPLETED'||!j.data?.completionJournalEntryId) process.exit(1);"

status="$(curl -sS -o "${second_body}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"COMPLETE","reason":"second complete"}')"
test "${status}" = "409"
grep -q '"code":"withdrawal_already_decided"' "${second_body}"

status="$(curl -sS -o "${late_reject_body}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"REJECT","reason":"late reject"}')"
test "${status}" = "409"
grep -q '"code":"withdrawal_already_decided"' "${late_reject_body}"

completion_journals="$(wd_psql "select count(*) from ledger.journal_entries where reference_type = 'WALLET_WITHDRAW_REQUEST' and reference_id = '${withdrawal_id}'::uuid and journal_type = 'WALLET_WITHDRAW_COMPLETE';")"
release_journals="$(wd_psql "select count(*) from ledger.journal_entries where reference_type = 'WALLET_WITHDRAW_REQUEST' and reference_id = '${withdrawal_id}'::uuid and journal_type = 'WALLET_WITHDRAW_RELEASE';")"
completed_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.withdrawal_completed' and subject_id = '${withdrawal_id}'::uuid;")"
test "${completion_journals}" = "1"
test "${release_journals}" = "0"
test "${completed_audit}" = "1"

wallet_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${user_id}';")"
test "${wallet_balance}" = "19.0000"

echo "WLT withdraw double decision pass"
