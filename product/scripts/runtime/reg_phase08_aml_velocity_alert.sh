#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
run_tag="$(date +%s%N)-$$"
end_user_id="$(cat /proc/sys/kernel/random/uuid)"
wallet_account_id="$(cat /proc/sys/kernel/random/uuid)"
ledger_account_id="$(cat /proc/sys/kernel/random/uuid)"
response_one="/tmp/minifin-phase08-aml-velocity-one-${run_tag}.json"
response_two="/tmp/minifin-phase08-aml-velocity-two-${run_tag}.json"

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

aml_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${end_user_id}'::uuid, 'aml-${run_tag}@example.test', 'aml-${run_tag}@example.test', 'runtime-hash', 'ACTIVE');" >/dev/null
aml_psql "insert into ledger.accounts (id, code, currency, account_type, normal_side, owner_type, owner_id) values ('${ledger_account_id}'::uuid, 'WALLET_USER:${end_user_id}', 'EUR', 'WALLET_USER', 'DEBIT', 'END_USER', '${end_user_id}'::uuid);" >/dev/null
aml_psql "insert into wallet.wallet_accounts (id, user_id, ledger_account_id) values ('${wallet_account_id}'::uuid, '${end_user_id}'::uuid, '${ledger_account_id}'::uuid);" >/dev/null

for index in 1 2 3 4; do
  deposit_id="$(cat /proc/sys/kernel/random/uuid)"
  aml_psql "insert into wallet.deposit_requests (id, user_id, wallet_account_id, amount, currency, state, reason, created_at, updated_at, decided_at) values ('${deposit_id}'::uuid, '${end_user_id}'::uuid, '${wallet_account_id}'::uuid, 10.0000, 'EUR', 'COMPLETED', 'AML-01 runtime synthetic movement ${index}', now(), now(), now());" >/dev/null
done

aml_curl -fsS -X POST "${base_url}/internal/aml/evaluate-velocity" \
  -H "Content-Type: application/json" \
  -d "{\"endUserId\":\"${end_user_id}\",\"lookbackHours\":24,\"thresholdCount\":3}" >"${response_one}"

alert_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${response_one}','utf8')); if(j.data?.endUserId !== '${end_user_id}' || j.data?.ruleCode !== 'VELOCITY' || j.data?.severity !== 'MEDIUM' || j.data?.status !== 'OPEN' || j.data?.observedCount <= j.data?.thresholdCount || !j.data?.alertId || j.data?.duplicateSuppressed !== false) process.exit(1); console.log(j.data.alertId);")"
test "$(aml_psql "select count(*) from aml.aml_alerts where id = '${alert_id}'::uuid and end_user_id = '${end_user_id}'::uuid and rule_code = 'VELOCITY' and severity = 'MEDIUM' and status = 'OPEN' and observed_count > threshold_count;")" = "1"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'aml.alert_created' and subject_id = '${alert_id}'::uuid and outcome = 'SUCCESS';")" = "1"

aml_curl -fsS -X POST "${base_url}/internal/aml/evaluate-velocity" \
  -H "Content-Type: application/json" \
  -d "{\"endUserId\":\"${end_user_id}\",\"lookbackHours\":24,\"thresholdCount\":3}" >"${response_two}"

node -e "const j=JSON.parse(require('fs').readFileSync('${response_two}','utf8')); if(j.data?.alertId !== '${alert_id}' || j.data?.duplicateSuppressed !== true) process.exit(1);"
test "$(aml_psql "select count(*) from aml.aml_alerts where end_user_id = '${end_user_id}'::uuid and rule_code = 'VELOCITY' and status = 'OPEN';")" = "1"
test "$(aml_psql "select count(*) from aml.aml_rule_evaluations where end_user_id = '${end_user_id}'::uuid and rule_code = 'VELOCITY' and tripped = true and alert_id = '${alert_id}'::uuid;")" = "2"

echo "AML-01 velocity alert pass alert_id=${alert_id} end_user_id=${end_user_id}"
