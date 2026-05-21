#!/usr/bin/env bash
set -euo pipefail

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
run_tag="$(date +%s%N)-$$"
password="correct horse battery"
email="sof-${run_tag}@example.test"
cookie_jar="/tmp/minifin-phase08-sof-cookies-${run_tag}.txt"
register_body="/tmp/minifin-phase08-sof-register-${run_tag}.json"
deposit_body="/tmp/minifin-phase08-sof-deposit-${run_tag}.json"
sof_body="/tmp/minifin-phase08-sof-submit-${run_tag}.json"

sof_psql() {
  docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "$1"
}

sof_curl() {
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 "$@"
  else
    curl "$@"
  fi
}

sof_curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" >"${register_body}"

user_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.userId||!j.data?.verificationToken) process.exit(1); console.log(j.data.userId);")"
verification_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.verificationToken);")"

sof_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
  -H "Content-Type: application/json" \
  -d "{\"token\":\"${verification_token}\"}" >/tmp/minifin-phase08-sof-verify-${run_tag}.json

sof_curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" >/tmp/minifin-phase08-sof-login-${run_tag}.json

sof_curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/deposits" \
  -H "Content-Type: application/json" \
  -d '{"amount":"15000.0000","currency":"EUR"}' >"${deposit_body}"

deposit_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_body}','utf8')); if(!j.data?.depositId||j.data?.state!=='REQUESTED'||j.data?.sourceOfFundsRequired!==true||j.data?.sourceOfFundsSubmitted!==false) process.exit(1); console.log(j.data.depositId);")"
test "$(sof_psql "select count(*) from wallet.deposit_requests where id = '${deposit_id}'::uuid and user_id = '${user_id}'::uuid and amount = 15000.0000 and state = 'REQUESTED';")" = "1"
test "$(sof_psql "select count(*) from wallet.source_of_funds_declarations where deposit_request_id = '${deposit_id}'::uuid;")" = "0"

sof_curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/deposits/${deposit_id}/source-of-funds" \
  -H "Content-Type: application/json" \
  -d '{"sourceCategory":"SAVINGS","description":"Long-term personal savings accumulated from verified salary income."}' >"${sof_body}"

node -e "const j=JSON.parse(require('fs').readFileSync('${sof_body}','utf8')); if(!j.data?.declarationId||j.data?.depositId!=='${deposit_id}'||j.data?.sourceCategory!=='SAVINGS'||j.data?.depositState!=='PENDING_OPERATOR_REVIEW') process.exit(1);"
test "$(sof_psql "select count(*) from wallet.source_of_funds_declarations where deposit_request_id = '${deposit_id}'::uuid and user_id = '${user_id}'::uuid and source_category = 'SAVINGS';")" = "1"
test "$(sof_psql "select state from wallet.deposit_requests where id = '${deposit_id}'::uuid;")" = "PENDING_OPERATOR_REVIEW"
test "$(sof_psql "select count(*) from audit.audit_log where event_type = 'wallet.source_of_funds_submitted' and subject_id = '${deposit_id}'::uuid and outcome = 'SUCCESS';")" = "1"

status="$(sof_curl -sS -b "${cookie_jar}" -o /tmp/minifin-phase08-sof-duplicate-${run_tag}.json -w "%{http_code}" -X POST \
  "${base_url}/api/v1/deposits/${deposit_id}/source-of-funds" \
  -H "Content-Type: application/json" \
  -d '{"sourceCategory":"SAVINGS","description":"Duplicate declaration should not be accepted after transition."}')"
test "${status}" = "409"
grep -q '"code":"deposit_state_conflict"' /tmp/minifin-phase08-sof-duplicate-${run_tag}.json

echo "WLT-03 source-of-funds deposit threshold pass deposit_id=${deposit_id} user_id=${user_id}"
