#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
run_tag="$(date +%s%N)-$$"
password="correct horse battery"
email="aml-freeze-${run_tag}@example.test"
cookie_jar="/tmp/minifin-phase08-aml-freeze-cookies-${run_tag}.txt"
register_body="/tmp/minifin-phase08-aml-freeze-register-${run_tag}.json"
freeze_body_one="/tmp/minifin-phase08-aml-freeze-one-${run_tag}.json"
freeze_body_two="/tmp/minifin-phase08-aml-freeze-two-${run_tag}.json"
denied_body="/tmp/minifin-phase08-aml-freeze-denied-${run_tag}.json"
alert_id="$(cat /proc/sys/kernel/random/uuid)"

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

aml_curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" >"${register_body}"

end_user_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.userId||!j.data?.verificationToken) process.exit(1); console.log(j.data.userId);")"
verification_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.verificationToken);")"

aml_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${verification_token}\"}" >/tmp/minifin-phase08-aml-freeze-verify-${run_tag}.json

aml_curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" >/tmp/minifin-phase08-aml-freeze-login-${run_tag}.json

aml_psql "insert into aml.aml_alerts (id, end_user_id, rule_code, severity, status, window_started_at, window_ended_at, observed_count, threshold_count, metadata) values ('${alert_id}'::uuid, '${end_user_id}'::uuid, 'RUNTIME_CRITICAL', 'CRITICAL', 'OPEN', now() - interval '1 hour', now(), 1, 0, '{\"source\":\"runtime_aml_04\"}'::jsonb);" >/dev/null

aml_curl -fsS -X POST "${base_url}/internal/aml/process-critical-auto-freezes" >"${freeze_body_one}"
node -e "const j=JSON.parse(require('fs').readFileSync('${freeze_body_one}','utf8')); if(j.data?.processedAlertCount !== 1 || j.data?.frozenActorCount !== 1) process.exit(1);"

test "$(aml_psql "select count(*) from identity.actor_controls where actor_type = 'END_USER' and actor_id = '${end_user_id}'::uuid and state = 'FROZEN' and reason_code = 'aml_critical_alert';")" = "1"
test "$(aml_psql "select count(*) from aml.aml_alerts where id = '${alert_id}'::uuid and status = 'ACCOUNT_FROZEN_PERMANENT' and metadata->>'autoFreezeProcessed' = 'true';")" = "1"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'identity.actor_control_changed' and subject_type = 'END_USER' and subject_id = '${end_user_id}'::uuid and outcome = 'SUCCESS';")" -ge 1
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'aml.critical_alert_auto_frozen' and subject_id = '${alert_id}'::uuid and outcome = 'SUCCESS';")" = "1"

status="$(aml_curl -sS -b "${cookie_jar}" -o "${denied_body}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/deposits" \
  -H "Content-Type: application/json" \
  -d '{"amount":"5.0000","currency":"EUR"}')"
test "${status}" = "403"
grep -q '"code":"actor_control_blocked"' "${denied_body}"
test "$(aml_psql "select count(*) from wallet.deposit_requests where user_id = '${end_user_id}'::uuid;")" = "0"

aml_curl -fsS -X POST "${base_url}/internal/aml/process-critical-auto-freezes" >"${freeze_body_two}"
node -e "const j=JSON.parse(require('fs').readFileSync('${freeze_body_two}','utf8')); if(j.data?.processedAlertCount !== 0 || j.data?.frozenActorCount !== 0) process.exit(1);"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'aml.critical_alert_auto_frozen' and subject_id = '${alert_id}'::uuid and outcome = 'SUCCESS';")" = "1"

echo "AML-04 critical auto-freeze pass alert_id=${alert_id} end_user_id=${end_user_id}"
