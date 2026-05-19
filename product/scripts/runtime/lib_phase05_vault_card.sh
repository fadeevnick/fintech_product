#!/usr/bin/env bash

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
issuer_base_url="${ISSUER_BASE_URL:-http://localhost:8084}"
vault_base_url="${VAULT_BASE_URL:-http://localhost:8085}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
service_secret="${SERVICE_AUTH_SECRET:-local-service-secret}"

p05_register_enduser() {
  local tag="$1"
  local cookie_jar="$2"
  local email="phase05.${tag}.$(date +%s%N)@example.test"
  local password="correct horse battery"
  local register_body="/tmp/minifin-phase05-${tag}-register.json"

  pa_curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >"${register_body}"

  local user_id verification_token
  user_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.userId||!j.data?.verificationToken) process.exit(1); console.log(j.data.userId);")"
  verification_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.verificationToken);")"

  pa_curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"${verification_token}\"}" \
    >/tmp/minifin-phase05-${tag}-verify.json

  pa_curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >/tmp/minifin-phase05-${tag}-login.json

  echo "${user_id}"
  if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
    local cookie_host
    cookie_host="$(node -e "console.log(new URL(process.argv[1]).hostname)" "${base_url}")"
    docker run --rm -v /tmp:/tmp alpine:3.20 sh -c "sed -i -e 's/^#HttpOnly_[^[:space:]]*/#HttpOnly_${cookie_host}/' -e 's/^127\\.0\\.0\\.1[[:space:]]/${cookie_host}\t/' -e 's/^localhost[[:space:]]/${cookie_host}\t/' '${cookie_jar}' && chmod 600 '${cookie_jar}'"
  fi
}

p05_issue_card() {
  local cookie_jar="$1"
  local out_body="$2"
  pa_curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/cards" \
    -H "Content-Type: application/json" \
    -d '{}' \
    >"${out_body}"
}

p05_vault_detokenize() {
  local token="$1"
  local caller="$2"
  local out_body="$3"
  local extra_args=()
  if test "${caller}" != "issuer"; then
    extra_args=(-w "%{http_code}" -o "${out_body}")
  else
    extra_args=(-fsS -o "${out_body}")
  fi
  curl "${extra_args[@]}" -X POST "${vault_base_url}/internal/vault/detokenize" \
    -H "X-Service-Name: ${caller}" \
    -H "X-Service-Secret: ${service_secret}" \
    -H "Content-Type: application/json" \
    -d "{\"cardToken\":\"${token}\"}"
}

p05_psql() {
  local service="$1"
  local db="$2"
  local user="$3"
  local sql="$4"
  docker compose -f "${compose_file}" exec -T "${service}" psql -U "${user}" -d "${db}" -Atc "${sql}"
}

p05_platform_psql() { p05_psql platform-db platform platform "$1"; }
p05_issuer_psql() { p05_psql issuer-db issuer issuer "$1"; }
p05_vault_psql() { p05_psql vault-db vault vault "$1"; }
