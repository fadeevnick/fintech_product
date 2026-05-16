#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"
compliance_token="$(kc_backoffice_token compliance)"

prove_create_refused() {
  local state="$1"
  local tag="withdraw-control-create-${state,,}"
  local cookie_jar="/tmp/minifin-phase03-${tag}-cookies.txt"
  local user_id
  user_id="$(wd_register_enduser "${tag}" "${cookie_jar}")"

  wd_set_actor_control "${compliance_token}" "${user_id}" "${state}" "runtime_withdraw_${state,,}_create"

  local denied_body="/tmp/minifin-phase03-${tag}-denied.json"
  local status
  status="$(curl -sS -b "${cookie_jar}" -o "${denied_body}" -w "%{http_code}" -X POST \
    "${base_url}/api/v1/withdrawals" \
    -H "Content-Type: application/json" \
    -d '{"amount":"5.0000","currency":"EUR"}')"
  test "${status}" = "403"
  grep -q '"code":"actor_control_blocked"' "${denied_body}"

  local withdrawal_count
  withdrawal_count="$(wd_psql "select count(*) from wallet.withdraw_requests where user_id = '${user_id}'::uuid;")"
  test "${withdrawal_count}" = "0"
}

prove_complete_refused_and_reject_allowed() {
  local state="$1"
  local tag="withdraw-control-complete-${state,,}"
  local cookie_jar="/tmp/minifin-phase03-${tag}-cookies.txt"
  local withdraw_body="/tmp/minifin-phase03-${tag}-withdraw.json"
  local denied_body="/tmp/minifin-phase03-${tag}-denied.json"
  local reject_body="/tmp/minifin-phase03-${tag}-reject.json"
  local user_id
  user_id="$(wd_register_enduser "${tag}" "${cookie_jar}")"

  wd_fund_wallet "${cookie_jar}" "25.0000" "${operator_token}" "${tag}" >/tmp/minifin-phase03-${tag}-funded.txt
  wd_create_withdrawal "${cookie_jar}" "10.0000" "${withdraw_body}"
  local withdrawal_id
  withdrawal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_body}','utf8')); console.log(j.data.withdrawalId);")"

  wd_set_actor_control "${compliance_token}" "${user_id}" "${state}" "runtime_withdraw_${state,,}_complete"

  local status
  status="$(curl -sS -o "${denied_body}" -w "%{http_code}" -X POST \
    "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
    -H "Authorization: Bearer ${operator_token}" \
    -H "Content-Type: application/json" \
    -d "{\"decision\":\"COMPLETE\",\"reason\":\"control complete probe ${state}\"}")"
  test "${status}" = "403"
  grep -q '"code":"actor_control_blocked"' "${denied_body}"

  local current_state
  current_state="$(wd_psql "select state from wallet.withdraw_requests where id = '${withdrawal_id}'::uuid;")"
  test "${current_state}" = "HELD"
  local completion_count
  completion_count="$(wd_psql "select count(*) from ledger.journal_entries where reference_type = 'WALLET_WITHDRAW_REQUEST' and reference_id = '${withdrawal_id}'::uuid and journal_type = 'WALLET_WITHDRAW_COMPLETE';")"
  test "${completion_count}" = "0"

  curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
    -H "Authorization: Bearer ${operator_token}" \
    -H "Content-Type: application/json" \
    -d '{"decision":"REJECT","reason":"release held funds after control block"}' \
    >"${reject_body}"
  node -e "const j=JSON.parse(require('fs').readFileSync('${reject_body}','utf8')); if(j.data?.state!=='REJECTED'||!j.data?.releaseJournalEntryId) process.exit(1);"
}

prove_create_refused FROZEN
prove_create_refused BLOCKED
prove_complete_refused_and_reject_allowed FROZEN
prove_complete_refused_and_reject_allowed BLOCKED

echo "WLT-02 withdrawal actor-control block (FROZEN+BLOCKED, create+complete) pass"
