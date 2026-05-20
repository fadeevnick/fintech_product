#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
run_tag="$(date +%s%N)-$$"
end_user_id="$(cat /proc/sys/kernel/random/uuid)"
wallet_account_id="$(cat /proc/sys/kernel/random/uuid)"
ledger_account_id="$(cat /proc/sys/kernel/random/uuid)"
old_deposit_id="$(cat /proc/sys/kernel/random/uuid)"
recent_deposit_one_id="$(cat /proc/sys/kernel/random/uuid)"
recent_deposit_two_id="$(cat /proc/sys/kernel/random/uuid)"
response_one="/tmp/minifin-phase08-aml-dormancy-one-${run_tag}.json"
response_two="/tmp/minifin-phase08-aml-dormancy-two-${run_tag}.json"

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

aml_psql "insert into identity.end_users (id, email, normalized_email, password_hash, status) values ('${end_user_id}'::uuid, 'aml-dormancy-${run_tag}@example.test', 'aml-dormancy-${run_tag}@example.test', 'runtime-hash', 'ACTIVE');" >/dev/null
aml_psql "insert into ledger.accounts (id, code, currency, account_type, normal_side, owner_type, owner_id) values ('${ledger_account_id}'::uuid, 'WALLET_USER:${end_user_id}', 'EUR', 'WALLET_USER', 'DEBIT', 'END_USER', '${end_user_id}'::uuid);" >/dev/null
aml_psql "insert into wallet.wallet_accounts (id, user_id, ledger_account_id) values ('${wallet_account_id}'::uuid, '${end_user_id}'::uuid, '${ledger_account_id}'::uuid);" >/dev/null

aml_psql "insert into wallet.deposit_requests (id, user_id, wallet_account_id, amount, currency, state, reason, created_at, updated_at, decided_at) values ('${old_deposit_id}'::uuid, '${end_user_id}'::uuid, '${wallet_account_id}'::uuid, 25.0000, 'EUR', 'COMPLETED', 'AML-03 runtime previous activity', now() - interval '45 days', now() - interval '45 days', now() - interval '45 days');" >/dev/null
aml_psql "insert into wallet.deposit_requests (id, user_id, wallet_account_id, amount, currency, state, reason, created_at, updated_at, decided_at) values ('${recent_deposit_one_id}'::uuid, '${end_user_id}'::uuid, '${wallet_account_id}'::uuid, 600.0000, 'EUR', 'COMPLETED', 'AML-03 runtime recent movement one', now(), now(), now());" >/dev/null
aml_psql "insert into wallet.deposit_requests (id, user_id, wallet_account_id, amount, currency, state, reason, created_at, updated_at, decided_at) values ('${recent_deposit_two_id}'::uuid, '${end_user_id}'::uuid, '${wallet_account_id}'::uuid, 700.0000, 'EUR', 'COMPLETED', 'AML-03 runtime recent movement two', now(), now(), now());" >/dev/null

aml_curl -fsS -X POST "${base_url}/internal/aml/evaluate-dormancy-break" \
  -H "Content-Type: application/json" \
  -d "{\"endUserId\":\"${end_user_id}\",\"dormancyDays\":30,\"lookbackHours\":24,\"thresholdAmount\":\"1000.00\"}" >"${response_one}"

alert_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${response_one}','utf8')); if(j.data?.endUserId !== '${end_user_id}' || j.data?.ruleCode !== 'DORMANCY_BREAK' || j.data?.severity !== 'HIGH' || j.data?.status !== 'OPEN' || j.data?.observedCount !== 2 || j.data?.observedAmount !== '1300.00' || j.data?.thresholdAmount !== '1000.00' || j.data?.previousActivityFound !== true || j.data?.dormantGapActivityCount !== 0 || !j.data?.alertId || j.data?.duplicateSuppressed !== false) process.exit(1); console.log(j.data.alertId);")"
test "$(aml_psql "select count(*) from aml.aml_alerts where id = '${alert_id}'::uuid and end_user_id = '${end_user_id}'::uuid and rule_code = 'DORMANCY_BREAK' and severity = 'HIGH' and status = 'OPEN' and observed_count = 2 and metadata->>'observedAmount' = '1300.00' and metadata->>'thresholdAmount' = '1000.00';")" = "1"
test "$(aml_psql "select count(*) from audit.audit_log where event_type = 'aml.alert_created' and subject_id = '${alert_id}'::uuid and outcome = 'SUCCESS';")" = "1"

aml_curl -fsS -X POST "${base_url}/internal/aml/evaluate-dormancy-break" \
  -H "Content-Type: application/json" \
  -d "{\"endUserId\":\"${end_user_id}\",\"dormancyDays\":30,\"lookbackHours\":24,\"thresholdAmount\":\"1000.00\"}" >"${response_two}"

node -e "const j=JSON.parse(require('fs').readFileSync('${response_two}','utf8')); if(j.data?.alertId !== '${alert_id}' || j.data?.duplicateSuppressed !== true || j.data?.observedAmount !== '1300.00') process.exit(1);"
test "$(aml_psql "select count(*) from aml.aml_alerts where end_user_id = '${end_user_id}'::uuid and rule_code = 'DORMANCY_BREAK' and status = 'OPEN';")" = "1"
test "$(aml_psql "select count(*) from aml.aml_rule_evaluations where end_user_id = '${end_user_id}'::uuid and rule_code = 'DORMANCY_BREAK' and tripped = true and alert_id = '${alert_id}'::uuid;")" = "2"

echo "AML-03 dormancy-break alert pass alert_id=${alert_id} end_user_id=${end_user_id}"
