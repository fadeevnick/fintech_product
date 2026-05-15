#!/usr/bin/env bash
set -euo pipefail

docker compose -f deploy/docker-compose.yml logs --tail=20 platform >/tmp/minifin-platform-logs.txt
curl -fsS "http://localhost:8686/health" >/tmp/minifin-vector-health.json
curl -G -fsS "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={runtime="mini-fintech-platform", container="mini-fintech-platform-platform-1"}' \
  --data-urlencode 'limit=1' \
  >/tmp/minifin-loki-platform-query.json
grep -Fq '"result":[{' /tmp/minifin-loki-platform-query.json
echo "RUN-06 logs pipeline pass"
