#!/usr/bin/env bash
set -euo pipefail

# Append-only protection for idempotency.idempotency_keys:
# - finalized rows cannot be UPDATEd (response_status > 0 → trigger raises);
# - rows cannot be DELETEd (no-delete trigger raises).
#
# This complements PAY-02 / PAY-03 by enforcing the at-rest invariants the
# planning note relies on.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "append-only"

create_body="/tmp/minifin-phase04-append-create.json"
pa_create_api_key "${PA_COOKIE_JAR}" "append-only" "${create_body}"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"

idem_key="append-$(date +%s%N)"
pi_body="/tmp/minifin-phase04-append-pi.json"
pi_status="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" '{"amount":"1.00","currency":"EUR"}' "${pi_body}")"
test "${pi_status}" = "201"

# UPDATE attempt against a finalized row must raise.
update_log="/tmp/minifin-phase04-append-update.log"
set +e
docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -c \
  "update idempotency.idempotency_keys set response_body = 'tampered' where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';" \
  >"${update_log}" 2>&1
update_rc=$?
set -e
test "${update_rc}" -ne 0 || { echo "expected UPDATE to fail; psql output:"; cat "${update_log}"; exit 1; }
grep -q "immutable once finalized" "${update_log}"

# DELETE attempt must raise.
delete_log="/tmp/minifin-phase04-append-delete.log"
set +e
docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -c \
  "delete from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';" \
  >"${delete_log}" 2>&1
delete_rc=$?
set -e
test "${delete_rc}" -ne 0 || { echo "expected DELETE to fail; psql output:"; cat "${delete_log}"; exit 1; }
grep -q "append-only" "${delete_log}"

# Row still present and unchanged.
remaining_status="$(pa_psql "select response_status from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';")"
test "${remaining_status}" = "201"
remaining_body="$(pa_psql "select response_body from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';")"
test "${remaining_body}" != "tampered"

echo "Idempotency append-only (no-update on finalized + no-delete) pass"
