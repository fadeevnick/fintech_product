#!/usr/bin/env bash
set -euo pipefail

declare -A services=(
  [platform]="http://localhost:8081/internal/health"
  [acquirer]="http://localhost:8082/internal/health"
  [network]="http://localhost:8083/internal/health"
  [issuer]="http://localhost:8084/internal/health"
  [vault]="http://localhost:8085/internal/health"
)

for service in "${!services[@]}"; do
  body="$(curl -fsS "${services[$service]}")"
  echo "$body" | grep -q "\"service\":\"$service\""
  echo "RUN-01 $service health pass"
done
