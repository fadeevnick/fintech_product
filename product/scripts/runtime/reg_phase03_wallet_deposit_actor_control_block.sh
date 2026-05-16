#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"
compliance_token="$(kc_backoffice_token compliance)"

# Helper: prove deposit create refused for a given control state.
prove_create_refused() {
  local state="$1"
  local tag="control-create-${state,,}"
  local cookie_jar="/tmp/minifin-phase03-${tag}-cookies.txt"
  local user_id
  user_id="$(wd_register_enduser "${tag}" "${cookie_jar}")"

  wd_set_actor_control "${compliance_token}" "${user_id}" "${state}" "runtime_${state,,}_probe"

  local status
  status="$(curl -sS -b "${cookie_jar}" -o "/tmp/minifin-phase03-${tag}-denied.json" -w "%{http_code}" -X POST \
    "${base_url}/api/v1/deposits" \
    -H "Content-Type: application/json" \
    -d '{"amount":"5.0000","currency":"EUR"}')"
  test "${status}" = "403"
  grep -q '"code":"actor_control_blocked"' "/tmp/minifin-phase03-${tag}-denied.json"

  local denied_count
  denied_count="$(wd_psql "select count(*) from audit.audit_log where event_type = 'identity.actor_control_write_denied' and subject_type = 'END_USER' and subject_id = '${user_id}'::uuid;")"
  test "${denied_count}" -ge 1

  local deposit_count
  deposit_count="$(wd_psql "select count(*) from wallet.deposit_requests where user_id = '${user_id}'::uuid;")"
  test "${deposit_count}" = "0"
}

# Helper: prove approve refused for a deposit whose owner becomes controlled.
prove_approve_refused() {
  local state="$1"
  local tag="control-approve-${state,,}"
  local cookie_jar="/tmp/minifin-phase03-${tag}-cookies.txt"
  local deposit_body="/tmp/minifin-phase03-${tag}-deposit.json"
  local decision_body="/tmp/minifin-phase03-${tag}-decision.json"
  local user_id
  user_id="$(wd_register_enduser "${tag}" "${cookie_jar}")"

  wd_create_deposit "${cookie_jar}" "9.0000" "${deposit_body}"
  local deposit_id
  deposit_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_body}','utf8')); console.log(j.data.depositId);")"

  wd_set_actor_control "${compliance_token}" "${user_id}" "${state}" "runtime_${state,,}_probe"

  local status
  status="$(curl -sS -o "${decision_body}" -w "%{http_code}" -X POST \
    "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
    -H "Authorization: Bearer ${operator_token}" \
    -H "Content-Type: application/json" \
    -d "{\"decision\":\"APPROVE\",\"reason\":\"control approve probe ${state}\"}")"
  test "${status}" = "403"
  grep -q '"code":"actor_control_blocked"' "${decision_body}"

  # deposit remains in pending state, no ledger movement
  local current_state
  current_state="$(wd_psql "select state from wallet.deposit_requests where id = '${deposit_id}'::uuid;")"
  test "${current_state}" = "PENDING_OPERATOR_REVIEW"
  local posting_count
  posting_count="$(wd_psql "select count(*) from ledger.postings p join ledger.journal_entries j on j.id = p.journal_entry_id where j.reference_id = '${deposit_id}'::uuid;")"
  test "${posting_count}" = "0"
}

prove_create_refused FROZEN
prove_create_refused BLOCKED
prove_approve_refused FROZEN
prove_approve_refused BLOCKED

echo "WLT-02 actor-control block (FROZEN+BLOCKED, create+approve) pass"
