#!/usr/bin/env bash
set -euo pipefail

curl -fsS "http://localhost:18080/realms/master/.well-known/openid-configuration" | grep -q '"issuer"'
echo "RUN-04 keycloak connectivity pass"
