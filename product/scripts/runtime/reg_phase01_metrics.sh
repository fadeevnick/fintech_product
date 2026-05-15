#!/usr/bin/env bash
set -euo pipefail

curl -fsS "http://localhost:9090/-/ready" | grep -q "Prometheus Server is Ready"
curl -fsS "http://localhost:8081/actuator/prometheus" | grep -q "jvm_info"
echo "RUN-07 metrics pipeline pass"
