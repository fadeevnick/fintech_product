#!/usr/bin/env bash
set -euo pipefail

compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"

if test "${MINIFIN_RESET_CONFIRM:-}" != "reset-dev"; then
  echo "Refusing destructive reset: set MINIFIN_RESET_CONFIRM=reset-dev" >&2
  exit 2
fi

compose_args=(-f "${compose_file}")
if test -n "${COMPOSE_OVERRIDE_FILE:-}"; then
  compose_args+=(-f "${COMPOSE_OVERRIDE_FILE}")
fi

docker compose "${compose_args[@]}" down -v --remove-orphans
docker compose "${compose_args[@]}" up -d --no-build platform acquirer network issuer vault

echo "REC-01 reset dev stack started project=${COMPOSE_PROJECT_NAME:-mini-fintech-platform}"
