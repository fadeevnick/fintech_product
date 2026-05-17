#!/usr/bin/env bash
set -euo pipefail

# PAY-02 hardening — concurrent POST /v1/payment_intents under the same
# Idempotency-Key must produce exactly one payment_intent and one cached
# response. Verifies the atomicity fix for the placeholder-then-finalize
# pattern in IdempotencyService.runWriteOnce.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "concurrent-idem"

create_body="/tmp/minifin-phase04-concurrent-create.json"
pa_rm "${create_body}"
pa_create_api_key "${PA_COOKIE_JAR}" "concurrent" "${create_body}"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"

idem_key="concurrent-$(date +%s%N)"
body_json='{"amount":"77.00","currency":"EUR","description":"race"}'
N=8

run_dir="/tmp/minifin-phase04-concurrent-${idem_key}"
mkdir -p "${run_dir}"
pids=()
for i in $(seq 1 ${N}); do
  (
    out="${run_dir}/resp-${i}.json"
    status=$(pa_curl -sS -o "${out}" -w "%{http_code}" \
      -X POST "${base_url}/v1/payment_intents" \
      -H "Authorization: Bearer ${raw_key}" \
      -H "Content-Type: application/json" \
      -H "Idempotency-Key: ${idem_key}" \
      -d "${body_json}")
    echo "${status}" >"${run_dir}/status-${i}"
  ) &
  pids+=($!)
done
for pid in "${pids[@]}"; do wait "${pid}"; done

# All non-error responses must be HTTP 201.
for i in $(seq 1 ${N}); do
  status="$(cat "${run_dir}/status-${i}")"
  test "${status}" = "201" || { echo "concurrent request ${i} returned ${status}, expected 201"; exit 1; }
done

# All responses must reference exactly the same payment intent id.
ids=$(for i in $(seq 1 ${N}); do
  node -e "const j=JSON.parse(require('fs').readFileSync('${run_dir}/resp-${i}.json','utf8')); console.log(j.data?.id||'');"
done | sort -u)
unique_count=$(echo "${ids}" | wc -l)
test "${unique_count}" = "1" || { echo "expected 1 unique payment_intent id, got: ${ids}"; exit 1; }

# DB must contain exactly one payment_intent row for this merchant.
intent_count="$(pa_psql "select count(*) from merchant.payment_intents where merchant_id = '${PA_MERCHANT_ID}'::uuid;")"
test "${intent_count}" = "1" || { echo "expected 1 payment_intent row, got ${intent_count}"; exit 1; }

# Exactly one finalized idempotency row.
idem_count="$(pa_psql "select count(*) from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}' and response_status = 201;")"
test "${idem_count}" = "1" || { echo "expected 1 finalized idempotency row, got ${idem_count}"; exit 1; }

# No leftover placeholder rows (response_status = 0).
placeholder_count="$(pa_psql "select count(*) from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and response_status = 0;")"
test "${placeholder_count}" = "0" || { echo "leftover placeholder rows: ${placeholder_count}"; exit 1; }

rm -rf "${run_dir}"

echo "PAY-02 atomic concurrent idempotency pass"
