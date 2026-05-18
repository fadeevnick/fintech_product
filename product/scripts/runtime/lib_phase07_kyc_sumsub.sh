#!/usr/bin/env bash

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"

kyc_curl() {
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp curlimages/curl:8.10.1 "$@"
  else
    curl "$@"
  fi
}
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
sumsub_webhook_secret="${SUMSUB_WEBHOOK_SECRET:-local-sumsub-webhook-secret}"

kyc_psql() {
  docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "$1"
}

kyc_register_verified_enduser() {
  local tag="$1"
  local password="${2:-correct horse battery}"
  local email="phase07.${tag}.$(date +%s%N)@example.test"
  local register_body="/tmp/minifin-phase07-${tag}-register.json"
  local verify_body="/tmp/minifin-phase07-${tag}-verify.json"
  local cookie_jar="/tmp/minifin-phase07-${tag}-cookies.txt"
  kyc_curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >"${register_body}"
  local token
  token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.verificationToken) process.exit(1); console.log(j.data.verificationToken);")"
  kyc_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"${token}\"}" \
    >"${verify_body}"
  kyc_curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >/tmp/minifin-phase07-${tag}-login.json
  echo "${cookie_jar}"
}

sumsub_signature_header() {
  local payload_file="$1"
  local secret="${2:-${sumsub_webhook_secret}}"
  node -e '
const crypto = require("crypto");
const fs = require("fs");
const payload = fs.readFileSync(process.argv[1], "utf8");
const secret = process.argv[2];
console.log(crypto.createHmac("sha256", secret).update(payload).digest("hex"));
' "${payload_file}" "${secret}"
}

sumsub_json_payload() {
  local event_id="$1"
  local applicant_id="$2"
  local review_answer="$3"
  local reject_type="${4:-}"
  if test -n "${reject_type}"; then
    cat <<JSON
{"id":"${event_id}","type":"applicantReviewed","applicantId":"${applicant_id}","reviewStatus":"completed","reviewResult":{"reviewAnswer":"${review_answer}","rejectType":"${reject_type}","moderationComment":"runtime fixture"}}
JSON
  else
    cat <<JSON
{"id":"${event_id}","type":"applicantReviewed","applicantId":"${applicant_id}","reviewStatus":"completed","reviewResult":{"reviewAnswer":"${review_answer}"}}
JSON
  fi
}
