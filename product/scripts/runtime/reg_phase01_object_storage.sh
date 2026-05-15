#!/usr/bin/env bash
set -euo pipefail

bucket="minifin-smoke"
object="phase01.txt"
payload="phase01-smoke"

bucket_status="$(curl -sS -o /tmp/minifin-seaweedfs-create-bucket.txt -w "%{http_code}" -X PUT "http://localhost:8333/${bucket}")"
test "${bucket_status}" = "200" -o "${bucket_status}" = "409"
curl -fsS -X PUT --data "${payload}" "http://localhost:8333/${bucket}/${object}" >/tmp/minifin-seaweedfs-put-object.txt
actual="$(curl -fsS "http://localhost:8333/${bucket}/${object}")"
test "${actual}" = "${payload}"
echo "RUN-05 seaweedfs connectivity pass"
