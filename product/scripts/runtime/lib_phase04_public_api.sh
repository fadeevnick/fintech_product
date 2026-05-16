#!/usr/bin/env bash

# Shared helpers for Phase 04 merchant API keys / public API runtime scripts.

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"

pa_psql() {
  docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "$1"
}

# Register a merchant employee (auto-admin), verify, login, return merchantId + employeeId
# via globals: PA_MERCHANT_ID, PA_EMPLOYEE_ID, PA_COOKIE_JAR
pa_register_merchant() {
  local tag="$1"
  PA_COOKIE_JAR="/tmp/minifin-phase04-${tag}-cookies.txt"
  local email="phase04.${tag}.$(date +%s%N)@example.test"
  local password="correct horse battery"
  local register_body="/tmp/minifin-phase04-${tag}-register.json"
  local verify_body="/tmp/minifin-phase04-${tag}-verify.json"

  curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\",\"companyName\":\"Phase04 Co ${tag}\",\"country\":\"FR\",\"businessType\":\"saas\"}" \
    >"${register_body}"

  PA_MERCHANT_ID="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.merchantId||!j.data?.verificationToken) process.exit(1); console.log(j.data.merchantId);")"
  PA_EMPLOYEE_ID="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.employeeId);")"
  local verification_token
  verification_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.verificationToken);")"

  curl -fsS -X POST "${base_url}/api/v1/merchant/email/verify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"${verification_token}\"}" \
    >"${verify_body}"

  curl -fsS -c "${PA_COOKIE_JAR}" -X POST "${base_url}/api/v1/merchant/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >/tmp/minifin-phase04-${tag}-login.json
}

pa_create_api_key() {
  local cookie_jar="$1"
  local label="$2"
  local out_body="$3"
  curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/merchant/api-keys" \
    -H "Content-Type: application/json" \
    -d "{\"label\":\"${label}\"}" \
    >"${out_body}"
}

pa_list_api_keys() {
  local cookie_jar="$1"
  local out_body="$2"
  curl -fsS -b "${cookie_jar}" "${base_url}/api/v1/merchant/api-keys" >"${out_body}"
}

pa_revoke_api_key() {
  local cookie_jar="$1"
  local key_id="$2"
  local out_body="$3"
  curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/merchant/api-keys/${key_id}/revoke" >"${out_body}"
}

# usage: pa_public_post_payment_intent <api_key> <idempotency_key|-> <json_body> <out_body>
# echoes the HTTP status to stdout
pa_public_post_payment_intent() {
  local api_key="$1"
  local idem_key="$2"
  local body_json="$3"
  local out_body="$4"
  local -a header_args=()
  if test "${idem_key}" != "-"; then
    header_args+=(-H "Idempotency-Key: ${idem_key}")
  fi
  curl -sS -o "${out_body}" -w "%{http_code}" \
    -X POST "${base_url}/v1/payment_intents" \
    -H "Authorization: Bearer ${api_key}" \
    -H "Content-Type: application/json" \
    "${header_args[@]}" \
    -d "${body_json}"
}

pa_public_get_payment_intent() {
  local api_key="$1"
  local intent_id="$2"
  local out_body="$3"
  curl -sS -o "${out_body}" -w "%{http_code}" \
    -X GET "${base_url}/v1/payment_intents/${intent_id}" \
    -H "Authorization: Bearer ${api_key}"
}
