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

wait_keycloak
seed_case() {
  local mode="$1"
  local tag="snx-${mode}-$(date +%s%N)"
  local profile_id
  local end_user_id
  profile_id="$(cat /proc/sys/kernel/random/uuid)"
  end_user_id="$(cat /proc/sys/kernel/random/uuid)"
  kyc_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${end_user_id}'::uuid, '${end_user_id}@example.test', '${end_user_id}@example.test', 'runtime-hash', 'ACTIVE');" >/dev/null
  kyc_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, review_answer, review_reject_type, review_moderation_comment) values ('${profile_id}'::uuid, '${end_user_id}'::uuid, 'IN_REVIEW', 'SUMSUB', 'sumsub-applicant-${tag}', 'basic-kyc-level', 'enduser:${end_user_id}', now(), now(), 'GREEN', null, 'runtime sanctions fixture');" >/dev/null
  echo "${profile_id}:${end_user_id}"
}

kc_seed_backoffice_realm
token="$(kc_backoffice_token operator)"
rationale="Verified identity evidence is acceptable; sanctions screening determines approval outcome for this runtime check."

set_mode unavailable
ids="$(seed_case unavailable)"
profile_id="${ids%%:*}"
end_user_id="${ids##*:}"
unavailable_response="/tmp/minifin-phase07-snx-unavailable.json"
status="$(kyc_curl -sS -o "${unavailable_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d "{\"decision\":\"APPROVE\",\"rationale\":\"${rationale}\"}")"
test "${status}" = "503"
grep -q '"code":"sanctions_screening_unavailable"' "${unavailable_response}"
test "$(kyc_psql "select status from kyc.kyc_profiles where id = '${profile_id}'::uuid;")" = "IN_REVIEW"
test "$(kyc_psql "select count(*) from sanctions.sanctions_hits where kyc_profile_id = '${profile_id}'::uuid and end_user_id = '${end_user_id}'::uuid and reason = 'SCREENING_UNAVAILABLE' and status = 'OPEN';")" = "1"
test "$(kyc_psql "select count(*) from audit.audit_log where event_type = 'sanctions.opensanctions_screening_unavailable' and subject_id = '${profile_id}'::uuid and outcome = 'FAIL_CLOSED';")" = "1"

set_mode match
ids="$(seed_case match)"
profile_id="${ids%%:*}"
end_user_id="${ids##*:}"
match_response="/tmp/minifin-phase07-snx-match.json"
status="$(kyc_curl -sS -o "${match_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d "{\"decision\":\"APPROVE\",\"rationale\":\"${rationale}\"}")"
test "${status}" = "409"
grep -q '"code":"sanctions_possible_match"' "${match_response}"
test "$(kyc_psql "select status from kyc.kyc_profiles where id = '${profile_id}'::uuid;")" = "IN_REVIEW"
test "$(kyc_psql "select count(*) from sanctions.sanctions_hits where kyc_profile_id = '${profile_id}'::uuid and end_user_id = '${end_user_id}'::uuid and reason = 'POSSIBLE_MATCH' and status = 'OPEN' and match_score >= 0.85;")" = "1"
test "$(kyc_psql "select count(*) from audit.audit_log where event_type = 'sanctions.opensanctions_screening_blocked' and subject_id = '${profile_id}'::uuid and outcome = 'BLOCKED';")" = "1"

set_mode no_match
ids="$(seed_case no-match)"
profile_id="${ids%%:*}"
no_match_response="/tmp/minifin-phase07-snx-no-match.json"
kyc_curl -fsS -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" -H "Authorization: Bearer ${token}" -H "Content-Type: application/json" -d "{\"decision\":\"APPROVE\",\"rationale\":\"${rationale}\"}" >"${no_match_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${no_match_response}','utf8')); if(j.data?.caseId !== '${profile_id}' || j.data?.status !== 'APPROVED') process.exit(1);"
test "$(kyc_psql "select status from kyc.kyc_profiles where id = '${profile_id}'::uuid;")" = "APPROVED"
test "$(kyc_psql "select count(*) from sanctions.sanctions_hits where kyc_profile_id = '${profile_id}'::uuid;")" = "0"
test "$(kyc_psql "select count(*) from audit.audit_log where event_type = 'sanctions.opensanctions_screening_passed' and subject_id = '${profile_id}'::uuid and outcome = 'SUCCESS';")" = "1"

echo "SNX-01 OpenSanctions fail-closed pass"
