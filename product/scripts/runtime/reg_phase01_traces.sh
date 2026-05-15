#!/usr/bin/env bash
set -euo pipefail

curl -fsS "http://localhost:13133/" >/tmp/minifin-otel-health.txt
curl -fsS "http://localhost:3200/ready" >/tmp/minifin-tempo-ready.txt
echo "RUN-08 traces pipeline pass-indirect"
