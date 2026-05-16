#!/usr/bin/env bash

# Shared helpers for Phase 03 wallet/manual-deposit runtime scripts.

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"

wd_register_enduser() {
  local tag="$1"
  local cookie_jar="$2"
  local email="phase03.${tag}.$(date +%s%N)@example.test"
  local password="correct horse battery"
  local register_body="/tmp/minifin-phase03-${tag}-register.json"

  curl -fsS -X POST "${base_url}/api/v1/enduser/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >"${register_body}"
  local user_id
  user_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.userId||!j.data?.verificationToken) process.exit(1); console.log(j.data.userId);")"
  local verification_token
  verification_token="$(node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); console.log(j.data.verificationToken);")"

  curl -fsS -X POST "${base_url}/api/v1/enduser/email/verify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"${verification_token}\"}" \
    >/tmp/minifin-phase03-${tag}-verify.json

  curl -fsS -c "${cookie_jar}" -X POST "${base_url}/api/v1/enduser/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
    >/tmp/minifin-phase03-${tag}-login.json

  echo "${user_id}"
}

wd_set_actor_control() {
  local backoffice_token="$1"
  local user_id="$2"
  local state="$3"
  local reason="$4"
  curl -fsS -X POST "${base_url}/api/v1/backoffice/actor-controls" \
    -H "Authorization: Bearer ${backoffice_token}" \
    -H "Content-Type: application/json" \
    -d "{\"actorType\":\"END_USER\",\"actorId\":\"${user_id}\",\"state\":\"${state}\",\"reasonCode\":\"${reason}\"}" \
    >/tmp/minifin-phase03-actor-${state,,}.json
}

wd_create_deposit() {
  local cookie_jar="$1"
  local amount="$2"
  local out_body="$3"
  curl -fsS -b "${cookie_jar}" -X POST "${base_url}/api/v1/deposits" \
    -H "Content-Type: application/json" \
    -d "{\"amount\":\"${amount}\",\"currency\":\"EUR\"}" \
    >"${out_body}"
}

wd_psql() {
  docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "$1"
}
