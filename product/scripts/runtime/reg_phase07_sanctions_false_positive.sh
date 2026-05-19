#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase07_kyc_sumsub.sh"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

wait_platform() {
  for _ in $(seq 1 60); do
    if kyc_curl --max-time 5 -fsS "${base_url}/actuator/health" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

wait_keycloak() {
  for _ in $(seq 1 90); do
    if kc_curl --max-time 5 -fsS "${keycloak_url}/realms/master" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

set_mode() {
  export OPENSANCTIONS_LOCAL_MODE="$1"
  local compose_args=(-f "${compose_file}")
  if test -n "${COMPOSE_OVERRIDE_FILE:-}"; then
    compose_args+=(-f "${COMPOSE_OVERRIDE_FILE}")
  fi
  docker compose "${compose_args[@]}" up -d --no-build --force-recreate platform >/dev/null
  docker compose "${compose_args[@]}" restart platform-db platform >/dev/null
  wait_platform
}

seed_case() {
  local tag="$1"
  local profile_id
  local end_user_id
  profile_id="$(cat /proc/sys/kernel/random/uuid)"
  end_user_id="$(cat /proc/sys/kernel/random/uuid)"
  kyc_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${end_user_id}'::uuid, '${end_user_id}@example.test', '${end_user_id}@example.test', 'runtime-hash', 'ACTIVE');" >/dev/null
  kyc_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, review_answer, review_reject_type, review_moderation_comment) values ('${profile_id}'::uuid, '${end_user_id}'::uuid, 'IN_REVIEW', 'SUMSUB', 'sumsub-applicant-${tag}-${profile_id}', 'basic-kyc-level', 'enduser:${end_user_id}', now(), now(), 'GREEN', null, 'runtime sanctions false-positive fixture');" >/dev/null
  echo "${profile_id}:${end_user_id}"
}

wait_keycloak
kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"
compliance_token="$(kc_backoffice_token compliance)"
rationale="Reviewed matched entity details and confirmed this is not the same person."
run_tag="$(date +%s%N)-$$"

set_mode match
ids="$(seed_case first-match)"
profile_id="${ids%%:*}"
end_user_id="${ids##*:}"
match_response="/tmp/minifin-phase07-snx-fp-match-${run_tag}.json"
status="$(kyc_curl -sS -o "${match_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"decision\":\"APPROVE\",\"rationale\":\"${rationale}\"}")"
test "${status}" = "409"
grep -q '"code":"sanctions_possible_match"' "${match_response}"
hit_id="$(kyc_psql "select id from sanctions.sanctions_hits where kyc_profile_id = '${profile_id}'::uuid and end_user_id = '${end_user_id}'::uuid and reason = 'POSSIBLE_MATCH' and status = 'OPEN' and matched_entity_id = 'local-sanctions-entity-1' order by created_at desc limit 1;")"
test -n "${hit_id}"

list_response="/tmp/minifin-phase07-snx-fp-list-${run_tag}.json"
detail_response="/tmp/minifin-phase07-snx-fp-detail-${run_tag}.json"
operator_list_response="/tmp/minifin-phase07-snx-fp-operator-list-${run_tag}.json"
operator_detail_response="/tmp/minifin-phase07-snx-fp-operator-detail-${run_tag}.json"
operator_decide_response="/tmp/minifin-phase07-snx-fp-operator-decide-${run_tag}.json"
decision_response="/tmp/minifin-phase07-snx-fp-decision-${run_tag}.json"
status="$(kyc_curl -sS -o "${operator_list_response}" -w "%{http_code}" "${base_url}/api/v1/backoffice/sanctions-hits" -H "Authorization: Bearer ${operator_token}")"
test "${status}" = "403"
grep -q '"code":"forbidden_role"' "${operator_list_response}"
status="$(kyc_curl -sS -o "${operator_detail_response}" -w "%{http_code}" "${base_url}/api/v1/backoffice/sanctions-hits/${hit_id}" -H "Authorization: Bearer ${operator_token}")"
test "${status}" = "403"
grep -q '"code":"forbidden_role"' "${operator_detail_response}"
kyc_curl -fsS "${base_url}/api/v1/backoffice/sanctions-hits" -H "Authorization: Bearer ${compliance_token}" >"${list_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${list_response}','utf8')); if(!j.data?.some(h => h.id === '${hit_id}' && h.status === 'OPEN')) process.exit(1);"
kyc_curl -fsS "${base_url}/api/v1/backoffice/sanctions-hits/${hit_id}" -H "Authorization: Bearer ${compliance_token}" >"${detail_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${detail_response}','utf8')); if(j.data?.id !== '${hit_id}' || j.data?.matchedEntityId !== 'local-sanctions-entity-1') process.exit(1);"

status="$(kyc_curl -sS -o "${operator_decide_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/sanctions-hits/${hit_id}/decision" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"decision\":\"CLEAR_FALSE_POSITIVE\",\"rationale\":\"${rationale}\"}")"
test "${status}" = "403"
grep -q '"code":"forbidden_role"' "${operator_decide_response}"

kyc_curl -fsS -X POST "${base_url}/api/v1/backoffice/sanctions-hits/${hit_id}/decision" -H "Authorization: Bearer ${compliance_token}" -H "Content-Type: application/json" -d "{\"decision\":\"CLEAR_FALSE_POSITIVE\",\"rationale\":\"${rationale}\"}" >"${decision_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${decision_response}','utf8')); if(j.data?.hitId !== '${hit_id}' || j.data?.status !== 'CLEARED_FALSE_POSITIVE' || j.data?.decision !== 'CLEAR_FALSE_POSITIVE' || !j.data?.exceptionId) process.exit(1);"
test "$(kyc_psql "select status from sanctions.sanctions_hits where id = '${hit_id}'::uuid;")" = "CLEARED_FALSE_POSITIVE"
test "$(kyc_psql "select count(*) from sanctions.sanctions_hit_decisions where hit_id = '${hit_id}'::uuid and decision = 'CLEAR_FALSE_POSITIVE' and resulting_status = 'CLEARED_FALSE_POSITIVE';")" = "1"
test "$(kyc_psql "select count(*) from sanctions.sanctions_false_positive_exceptions where end_user_id = '${end_user_id}'::uuid and vendor = 'OPENSANCTIONS' and matched_entity_id = 'local-sanctions-entity-1' and revoked_at is null;")" = "1"
test "$(kyc_psql "select count(*) from audit.audit_log where event_type = 'sanctions.hit_false_positive_cleared' and subject_id = '${hit_id}'::uuid and outcome = 'SUCCESS';")" = "1"

suppressed_response="/tmp/minifin-phase07-snx-fp-suppressed-${run_tag}.json"
kyc_curl -fsS -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"decision\":\"APPROVE\",\"rationale\":\"${rationale}\"}" >"${suppressed_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${suppressed_response}','utf8')); if(j.data?.caseId !== '${profile_id}' || j.data?.status !== 'APPROVED') process.exit(1);"
test "$(kyc_psql "select count(*) from sanctions.sanctions_hits where kyc_profile_id = '${profile_id}'::uuid;")" = "1"
test "$(kyc_psql "select count(*) from audit.audit_log where event_type = 'sanctions.opensanctions_screening_suppressed' and subject_id = '${profile_id}'::uuid and outcome = 'SUCCESS';")" = "1"

echo "SNX-02 sanctions false-positive exception pass hit_id=${hit_id} end_user_id=${end_user_id}"
