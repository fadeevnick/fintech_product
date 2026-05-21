#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
run_tag="$(date +%s%N)-$$"

aml_psql() {
  docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "$1"
}

aml_curl() {
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 "$@"
  else
    curl "$@"
  fi
}

insert_end_user() {
  local uid="$1"
  aml_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${uid}'::uuid, 'aml-review-${uid}@example.test', 'aml-review-${uid}@example.test', 'runtime-hash', 'ACTIVE');" >/dev/null
}

insert_open_alert() {
  local alert_id="$1"
  local user_id="$2"
  local rule_code="${3:-VELOCITY}"
  local severity="${4:-MEDIUM}"
  aml_psql "insert into aml.aml_alerts (id, end_user_id, rule_code, severity, status, window_started_at, window_ended_at, observed_count, threshold_count, metadata) values ('${alert_id}'::uuid, '${user_id}'::uuid, '${rule_code}', '${severity}', 'OPEN', now() - interval '2 hours', now() - interval '1 hour', 5, 3, '{\"source\":\"runtime_phase08_review\"}'::jsonb) on conflict do nothing;" >/dev/null
}

kc_seed_backoffice_realm

operator_token="$(kc_backoffice_token operator)"
compliance_token="$(kc_backoffice_token compliance)"

# --- AML-05 Part 1: list and detail as operator ---
user_a="$(cat /proc/sys/kernel/random/uuid)"
alert_a="$(cat /proc/sys/kernel/random/uuid)"
insert_end_user "${user_a}"
insert_open_alert "${alert_a}" "${user_a}" "VELOCITY" "MEDIUM"

list_resp="/tmp/minifin-phase08-aml-review-list-${run_tag}.json"
aml_curl -fsS "${base_url}/api/v1/backoffice/aml-alerts" \
  -H "Authorization: Bearer ${operator_token}" \
  >"${list_resp}"
node -e "const j=JSON.parse(require('fs').readFileSync('${list_resp}','utf8')); if(!Array.isArray(j.data) || !j.data.some(a=>a.id==='${alert_a}'&&a.status==='OPEN')) process.exit(1);"

detail_resp="/tmp/minifin-phase08-aml-review-detail-${run_tag}.json"
aml_curl -fsS "${base_url}/api/v1/backoffice/aml-alerts/${alert_a}" \
  -H "Authorization: Bearer ${operator_token}" \
  >"${detail_resp}"
node -e "const j=JSON.parse(require('fs').readFileSync('${detail_resp}','utf8')); if(j.data?.id!=='${alert_a}'||j.data?.endUserId!=='${user_a}'||j.data?.status!=='OPEN') process.exit(1);"

# --- AML-05 Part 2: short rationale rejected ---
short_resp="/tmp/minifin-phase08-aml-review-short-${run_tag}.json"
status="$(aml_curl -sS -o "${short_resp}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/aml-alerts/${alert_a}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"CLOSED_FALSE_POSITIVE","rationale":"too short"}')"
test "${status}" = "400"
grep -q '"code":"invalid_rationale"' "${short_resp}"

# --- AML-05 Part 3: CLOSED_FALSE_POSITIVE by operator ---
fp_resp="/tmp/minifin-phase08-aml-review-fp-${run_tag}.json"
aml_curl -fsS -X POST "${base_url}/api/v1/backoffice/aml-alerts/${alert_a}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"CLOSED_FALSE_POSITIVE","rationale":"Reviewed transaction history; velocity pattern is legitimate regular payroll distribution."}' \
  >"${fp_resp}"
node -e "const j=JSON.parse(require('fs').readFileSync('${fp_resp}','utf8')); if(j.data?.alertId!=='${alert_a}'||j.data?.status!=='CLOSED_FALSE_POSITIVE'||j.data?.previousStatus!=='OPEN'||j.data?.decision!=='CLOSED_FALSE_POSITIVE'||j.data?.unfrozeActor!==false) process.exit(1);"
test "$(aml_psql "select status from aml.aml_alerts where id = '${alert_a}'::uuid;")" = "CLOSED_FALSE_POSITIVE"
test "$(aml_psql "select count(*) from aml.aml_alert_decisions where alert_id = '${alert_a}'::uuid and decision = 'CLOSED_FALSE_POSITIVE';")" = "1"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'aml.alert_reviewed' and subject_id = '${alert_a}'::uuid and outcome = 'SUCCESS';")" = "1"

# --- AML-05 Part 4: repeat decision returns 409 ---
repeat_resp="/tmp/minifin-phase08-aml-review-repeat-${run_tag}.json"
status="$(aml_curl -sS -o "${repeat_resp}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/aml-alerts/${alert_a}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"ESCALATED","rationale":"Second decision attempt should be rejected by state guard."}')"
test "${status}" = "409"
grep -q '"code":"invalid_state"' "${repeat_resp}"

# --- AML-05 Part 5: CLOSED_FALSE_POSITIVE on auto-frozen alert unfreeze ---
user_b="$(cat /proc/sys/kernel/random/uuid)"
alert_b="$(cat /proc/sys/kernel/random/uuid)"
insert_end_user "${user_b}"
aml_psql "insert into aml.aml_alerts (id, end_user_id, rule_code, severity, status, window_started_at, window_ended_at, observed_count, threshold_count, metadata) values ('${alert_b}'::uuid, '${user_b}'::uuid, 'RUNTIME_CRITICAL', 'CRITICAL', 'ACCOUNT_FROZEN_PERMANENT', now() - interval '2 hours', now() - interval '1 hour', 1, 0, '{\"source\":\"runtime_phase08_review\",\"autoFreezeProcessed\":true}'::jsonb) on conflict do nothing;" >/dev/null
aml_psql "insert into identity.actor_controls (actor_type, actor_id, state, reason_code, updated_by_actor_type, updated_by_actor_id, updated_by_reference) values ('END_USER', '${user_b}'::uuid, 'FROZEN', 'aml_critical_alert', 'SYSTEM', null, 'aml:${alert_b}') on conflict (actor_type, actor_id) do update set state='FROZEN', reason_code='aml_critical_alert', updated_at=now();" >/dev/null

unfreeze_resp="/tmp/minifin-phase08-aml-review-unfreeze-${run_tag}.json"
aml_curl -fsS -X POST "${base_url}/api/v1/backoffice/aml-alerts/${alert_b}/decision" \
  -H "Authorization: Bearer ${compliance_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"CLOSED_FALSE_POSITIVE","rationale":"Critical auto-freeze reviewed and determined to be false positive; account activity is consistent with known business pattern."}' \
  >"${unfreeze_resp}"
node -e "const j=JSON.parse(require('fs').readFileSync('${unfreeze_resp}','utf8')); if(j.data?.alertId!=='${alert_b}'||j.data?.status!=='CLOSED_FALSE_POSITIVE'||j.data?.previousStatus!=='ACCOUNT_FROZEN_PERMANENT'||j.data?.unfrozeActor!==true) process.exit(1);"
test "$(aml_psql "select state from identity.actor_controls where actor_type = 'END_USER' and actor_id = '${user_b}'::uuid;")" = "ACTIVE"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'identity.actor_control_changed' and subject_type = 'END_USER' and subject_id = '${user_b}'::uuid and outcome = 'SUCCESS';")" -ge 1

# --- AML-05 Part 6: ESCALATED by operator ---
user_c="$(cat /proc/sys/kernel/random/uuid)"
alert_c="$(cat /proc/sys/kernel/random/uuid)"
insert_end_user "${user_c}"
insert_open_alert "${alert_c}" "${user_c}" "STRUCTURING" "HIGH"

escalate_resp="/tmp/minifin-phase08-aml-review-escalate-${run_tag}.json"
aml_curl -fsS -X POST "${base_url}/api/v1/backoffice/aml-alerts/${alert_c}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"ESCALATED","rationale":"Structuring pattern warrants escalation to compliance officer for further investigation."}' \
  >"${escalate_resp}"
node -e "const j=JSON.parse(require('fs').readFileSync('${escalate_resp}','utf8')); if(j.data?.status!=='ESCALATED'||j.data?.previousStatus!=='OPEN') process.exit(1);"
test "$(aml_psql "select status from aml.aml_alerts where id = '${alert_c}'::uuid;")" = "ESCALATED"

# --- AML-05 Part 7: MARKED_FOR_SAR denied for operator, allowed for compliance ---
user_d="$(cat /proc/sys/kernel/random/uuid)"
alert_d="$(cat /proc/sys/kernel/random/uuid)"
insert_end_user "${user_d}"
insert_open_alert "${alert_d}" "${user_d}" "DORMANCY_BREAK" "HIGH"

sar_denied_resp="/tmp/minifin-phase08-aml-review-sar-denied-${run_tag}.json"
status="$(aml_curl -sS -o "${sar_denied_resp}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/aml-alerts/${alert_d}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"MARKED_FOR_SAR","rationale":"Operator attempts SAR marking but should be denied due to insufficient role."}')"
test "${status}" = "403"
grep -q '"code":"forbidden_role"' "${sar_denied_resp}"
test "$(aml_psql "select status from aml.aml_alerts where id = '${alert_d}'::uuid;")" = "OPEN"

sar_resp="/tmp/minifin-phase08-aml-review-sar-${run_tag}.json"
aml_curl -fsS -X POST "${base_url}/api/v1/backoffice/aml-alerts/${alert_d}/decision" \
  -H "Authorization: Bearer ${compliance_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"MARKED_FOR_SAR","rationale":"Dormancy-break pattern combined with structuring history meets internal SAR-filing threshold; marking for compliance filing workflow."}' \
  >"${sar_resp}"
node -e "const j=JSON.parse(require('fs').readFileSync('${sar_resp}','utf8')); if(j.data?.status!=='MARKED_FOR_SAR'||j.data?.previousStatus!=='OPEN') process.exit(1);"
test "$(aml_psql "select status from aml.aml_alerts where id = '${alert_d}'::uuid;")" = "MARKED_FOR_SAR"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'aml.alert_reviewed' and subject_id = '${alert_d}'::uuid;")" = "1"

echo "AML-05 aml alert review decisions pass run_tag=${run_tag}"
