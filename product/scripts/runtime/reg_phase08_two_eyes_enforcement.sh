#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

run_tag="$(date +%s%N)-$$"
cookie_jar="/tmp/minifin-phase08-twoeyes-cookies-${run_tag}.txt"

tw_curl() {
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 "$@"
  else
    curl "$@"
  fi
}

tw_post_decision() {
  local token="$1"
  local path="$2"
  local decision="$3"
  local reason="$4"
  local out="$5"
  tw_curl -fsS -X POST "${base_url}${path}" \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    -d "{\"decision\":\"${decision}\",\"reason\":\"${reason}\"}" \
    >"${out}"
}

tw_register_enduser() {
  local tag="$1"
  local cookie_jar="$2"
  local email="phase08.${tag}.${run_tag}@example.test"
  local password="correct horse battery"
  local register_body="/tmp/minifin-phase08-twoeyes-register-${run_tag}.json"
  tw_curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" >"${register_body}"
  local user_id
  user_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.userId||!j.data?.verificationToken) process.exit(1); console.log(j.data.userId);")"
  local verification_token
  verification_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.verificationToken);")"
  tw_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"${verification_token}\"}" >/tmp/minifin-phase08-twoeyes-verify-${run_tag}.json
  tw_curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" >/tmp/minifin-phase08-twoeyes-login-${run_tag}.json
  echo "${user_id}"
}

tw_create_deposit() {
  local amount="$1"
  local out="$2"
  tw_curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/deposits" \
    -H "Content-Type: application/json" \
    -d "{\"amount\":\"${amount}\",\"currency\":\"EUR\"}" >"${out}"
}

tw_create_withdrawal() {
  local amount="$1"
  local out="$2"
  tw_curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/withdrawals" \
    -H "Content-Type: application/json" \
    -d "{\"amount\":\"${amount}\",\"currency\":\"EUR\"}" >"${out}"
}

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"
compliance_token="$(kc_backoffice_token compliance)"

user_id="$(tw_register_enduser twoeyes "${cookie_jar}")"

high_deposit_body="/tmp/minifin-phase08-twoeyes-deposit-${run_tag}.json"
sof_body="/tmp/minifin-phase08-twoeyes-sof-${run_tag}.json"
deposit_first_body="/tmp/minifin-phase08-twoeyes-deposit-first-${run_tag}.json"
deposit_same_actor_body="/tmp/minifin-phase08-twoeyes-deposit-same-${run_tag}.json"
deposit_second_body="/tmp/minifin-phase08-twoeyes-deposit-second-${run_tag}.json"

tw_create_deposit "15000.0000" "${high_deposit_body}"
deposit_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${high_deposit_body}','utf8')); if(!j.data?.depositId||j.data?.state!=='REQUESTED'||j.data?.sourceOfFundsRequired!==true) process.exit(1); console.log(j.data.depositId);")"

tw_curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/deposits/${deposit_id}/source-of-funds" \
  -H "Content-Type: application/json" \
  -d '{"sourceCategory":"SAVINGS","description":"Long-term personal savings accumulated from verified salary income."}' >"${sof_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${sof_body}','utf8')); if(j.data?.depositState!=='PENDING_OPERATOR_REVIEW') process.exit(1);"

tw_post_decision "${operator_token}" "/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" "APPROVE" "first high value deposit review" "${deposit_first_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_first_body}','utf8')); if(j.data?.state!=='READY_FOR_SECOND_REVIEW'||j.data?.journalEntryId) process.exit(1);"
test "$(wd_psql "select count(*) from ledger.journal_entries where reference_id = '${deposit_id}'::uuid and journal_type = 'WALLET_DEPOSIT';")" = "0"

same_status="$(tw_curl -sS -o "${deposit_same_actor_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","reason":"same actor must be denied"}')"
test "${same_status}" = "403"
grep -q '"code":"two_eyes_same_actor_denied"' "${deposit_same_actor_body}"

tw_post_decision "${compliance_token}" "/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" "APPROVE" "second high value deposit review" "${deposit_second_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_second_body}','utf8')); if(j.data?.state!=='COMPLETED'||!j.data?.journalEntryId) process.exit(1);"
test "$(wd_psql "select count(*) from ledger.journal_entries where reference_id = '${deposit_id}'::uuid and journal_type = 'WALLET_DEPOSIT';")" = "1"
test "$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.deposit_marked_for_second_review' and subject_id = '${deposit_id}'::uuid;")" = "1"
test "$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.deposit_approved' and subject_id = '${deposit_id}'::uuid;")" = "1"

high_withdraw_body="/tmp/minifin-phase08-twoeyes-withdraw-${run_tag}.json"
withdraw_first_body="/tmp/minifin-phase08-twoeyes-withdraw-first-${run_tag}.json"
withdraw_same_actor_body="/tmp/minifin-phase08-twoeyes-withdraw-same-${run_tag}.json"
withdraw_second_body="/tmp/minifin-phase08-twoeyes-withdraw-second-${run_tag}.json"

tw_create_withdrawal "12000.0000" "${high_withdraw_body}"
withdrawal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${high_withdraw_body}','utf8')); if(!j.data?.withdrawalId||j.data?.state!=='HELD'||!j.data?.holdJournalEntryId) process.exit(1); console.log(j.data.withdrawalId);")"
test "$(wd_psql "select count(*) from ledger.journal_entries where reference_id = '${withdrawal_id}'::uuid and journal_type = 'WALLET_WITHDRAW_HOLD';")" = "1"

tw_post_decision "${operator_token}" "/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" "COMPLETE" "first high value withdrawal review" "${withdraw_first_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_first_body}','utf8')); if(j.data?.state!=='READY_FOR_SECOND_REVIEW'||j.data?.completionJournalEntryId) process.exit(1);"
test "$(wd_psql "select count(*) from ledger.journal_entries where reference_id = '${withdrawal_id}'::uuid and journal_type = 'WALLET_WITHDRAW_COMPLETE';")" = "0"

same_withdraw_status="$(tw_curl -sS -o "${withdraw_same_actor_body}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"COMPLETE","reason":"same actor must be denied"}')"
test "${same_withdraw_status}" = "403"
grep -q '"code":"two_eyes_same_actor_denied"' "${withdraw_same_actor_body}"

tw_post_decision "${compliance_token}" "/api/v1/backoffice/manual-ops/withdrawals/${withdrawal_id}/decision" "COMPLETE" "second high value withdrawal review" "${withdraw_second_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${withdraw_second_body}','utf8')); if(j.data?.state!=='COMPLETED'||!j.data?.completionJournalEntryId) process.exit(1);"
test "$(wd_psql "select count(*) from ledger.journal_entries where reference_id = '${withdrawal_id}'::uuid and journal_type = 'WALLET_WITHDRAW_COMPLETE';")" = "1"
test "$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.withdrawal_marked_for_second_review' and subject_id = '${withdrawal_id}'::uuid;")" = "1"
test "$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.withdrawal_completed' and subject_id = '${withdrawal_id}'::uuid;")" = "1"

recon_body="/tmp/minifin-phase08-twoeyes-recon-${run_tag}.json"
tw_curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >"${recon_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${recon_body}','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT-04 two-eyes enforcement pass deposit_id=${deposit_id} withdrawal_id=${withdrawal_id} user_id=${user_id}"
