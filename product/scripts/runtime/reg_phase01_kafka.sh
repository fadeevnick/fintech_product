#!/usr/bin/env bash
set -euo pipefail

curl -fsS "http://localhost:8081/internal/runtime/kafka" | grep -q '"status":"CONNECTED"'
docker compose -f deploy/docker-compose.yml exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list >/tmp/minifin-kafka-topics.txt
echo "RUN-03 kafka connectivity pass"
