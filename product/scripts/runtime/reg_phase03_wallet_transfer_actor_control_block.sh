#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

attempt_blocked_transfer() {
  local tag="$1"
  local blocked_side="$2"
  local state="$3"
  local sender_cookie="/tmp/minifin-phase03-transfer-${tag}-sender-cookies.txt"
  local receiver_cookie="/tmp/minifin-phase03-transfer-${tag}-receiver-cookies.txt"
  local body="/tmp/minifin-phase03-transfer-${tag}-blocked.json"

  local sender_id
  local receiver_id
  sender_id="$(wd_register_enduser "transfer-${tag}-sender" "${sender_cookie}")"
  receiver_id="$(wd_register_enduser "transfer-${tag}-receiver" "${receiver_cookie}")"
  wd_fund_wallet "${sender_cookie}" "20.0000" "${operator_token}" "transfer-${tag}" >/tmp/minifin-phase03-transfer-${tag}-funded.txt

  local blocked_user_id
  if test "${blocked_side}" = "sender"; then
    blocked_user_id="${sender_id}"
  else
    blocked_user_id="${receiver_id}"
  fi

  wd_set_actor_control "${operator_token}" "${blocked_user_id}" "${state}" "runtime_transfer_${blocked_side}_${state}"

  status="$(curl -sS -o "${body}" -w "%{http_code}" -b "${sender_cookie}" -X POST "${base_url}/api/v1/transfers" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: transfer-block-${tag}-$(date +%s%N)" \
    -d "{\"receiverUserId\":\"${receiver_id}\",\"amount\":\"5.0000\",\"currency\":\"EUR\"}")"
  test "${status}" = "403"
  node -e "const j=JSON.parse(require('fs').readFileSync('${body}','utf8')); if(j.errors?.[0]?.code!=='actor_control_blocked') process.exit(1);"

  transfer_count="$(wd_psql "select count(*) from wallet.internal_transfers where sender_user_id = '${sender_id}'::uuid;")"
  test "${transfer_count}" = "0"
  denied_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'identity.actor_control_write_denied' and subject_id = '${blocked_user_id}'::uuid;")"
  test "${denied_audit}" -ge 1
}

attempt_blocked_transfer "sender-frozen" "sender" "FROZEN"
attempt_blocked_transfer "sender-blocked" "sender" "BLOCKED"
attempt_blocked_transfer "receiver-frozen" "receiver" "FROZEN"
attempt_blocked_transfer "receiver-blocked" "receiver" "BLOCKED"

curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >/tmp/minifin-phase03-transfer-block-recon.json
node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase03-transfer-block-recon.json','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT internal transfer actor-control block pass"
