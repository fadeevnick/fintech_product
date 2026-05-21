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
  kyc_psql "insert into kyc.kyc_profiles (id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id, submitted_at, in_review_at, review_answer, review_reject_type, review_moderation_comment) values ('${profile_id}'::uuid, '${end_user_id}'::uuid, 'IN_REVIEW', 'SUMSUB', 'sumsub-applicant-${tag}-${profile_id}', 'basic-kyc-level', 'enduser:${end_user_id}', now(), now(), 'GREEN', null, 'runtime compliance read-audit fixture');" >/dev/null
  echo "${profile_id}:${end_user_id}"
}

read_audit_count() {
  local hit_id="$1"
  kyc_psql "select count(*) from audit.read_audit_log where subject_type = 'SANCTIONS_HIT' and subject_id = '${hit_id}'::uuid and resource_type = 'SANCTIONS_HIT' and resource_id = '${hit_id}'::uuid and purpose = 'compliance_sanctions_hit_detail' and decision = 'ALLOW';"
}

wait_keycloak
kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"
compliance_token="$(kc_backoffice_token compliance)"
rationale="Runtime fixture creates a sanctions hit for compliance read audit verification."
run_tag="$(date +%s%N)-$$"

set_mode match
ids="$(seed_case "read-audit-${run_tag}")"
profile_id="${ids%%:*}"
end_user_id="${ids##*:}"

match_response="/tmp/minifin-phase07-aud03-match-${run_tag}.json"
status="$(kyc_curl -sS -o "${match_response}" -w "%{http_code}" -X POST "${base_url}/api/v1/backoffice/kyc-cases/${profile_id}/decision" -H "Authorization: Bearer ${operator_token}" -H "Content-Type: application/json" -d "{\"decision\":\"APPROVE\",\"rationale\":\"${rationale}\"}")"
test "${status}" = "409"
grep -q '"code":"sanctions_possible_match"' "${match_response}"

hit_id="$(kyc_psql "select id from sanctions.sanctions_hits where kyc_profile_id = '${profile_id}'::uuid and end_user_id = '${end_user_id}'::uuid and reason = 'POSSIBLE_MATCH' and status = 'OPEN' and matched_entity_id = 'local-sanctions-entity-1' order by created_at desc limit 1;")"
test -n "${hit_id}"

list_response="/tmp/minifin-phase07-aud03-list-${run_tag}.json"
detail_response="/tmp/minifin-phase07-aud03-detail-${run_tag}.json"
operator_detail_response="/tmp/minifin-phase07-aud03-operator-detail-${run_tag}.json"

kyc_curl -fsS "${base_url}/api/v1/backoffice/sanctions-hits" -H "Authorization: Bearer ${compliance_token}" >"${list_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${list_response}','utf8')); if(!j.data?.some(h => h.id === '${hit_id}' && h.status === 'OPEN')) process.exit(1);"
test "$(read_audit_count "${hit_id}")" = "0"

kyc_curl -fsS "${base_url}/api/v1/backoffice/sanctions-hits/${hit_id}" -H "Authorization: Bearer ${compliance_token}" >"${detail_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${detail_response}','utf8')); if(j.data?.id !== '${hit_id}' || j.data?.matchedEntityId !== 'local-sanctions-entity-1') process.exit(1);"
test "$(read_audit_count "${hit_id}")" = "1"

test "$(kyc_psql "select count(*) from audit.read_audit_log where subject_type = 'SANCTIONS_HIT' and subject_id = '${hit_id}'::uuid and actor_type = 'BACKOFFICE' and actor_id is not null and actor_reference is not null and metadata->'roles' ? 'compliance_officer';")" = "1"

status="$(kyc_curl -sS -o "${operator_detail_response}" -w "%{http_code}" "${base_url}/api/v1/backoffice/sanctions-hits/${hit_id}" -H "Authorization: Bearer ${operator_token}")"
test "${status}" = "403"
grep -q '"code":"forbidden_role"' "${operator_detail_response}"
test "$(read_audit_count "${hit_id}")" = "1"

echo "AUD-03 compliance sanctions detail read audit pass hit_id=${hit_id} end_user_id=${end_user_id}"
