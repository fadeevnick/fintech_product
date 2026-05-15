#!/usr/bin/env bash
set -euo pipefail

declare -A users=(
  [platform]=platform
  [acquirer]=acquirer
  [network]=network
  [issuer]=issuer
  [vault]=vault
)

for service in "${!users[@]}"; do
  container="${service}-db"
  count="$(docker compose -f deploy/docker-compose.yml exec -T "$container" psql -U "${users[$service]}" -d "$service" -Atc "select count(*) from flyway_schema_history where success = true;")"
  marker="$(docker compose -f deploy/docker-compose.yml exec -T "$container" psql -U "${users[$service]}" -d "$service" -Atc "select service_name from service_runtime_marker where id = 1;")"
  test "$count" -ge 1
  test "$marker" = "$service"
  echo "RUN-02 $service migration pass"
done
