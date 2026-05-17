#!/usr/bin/env bash
set -euo pipefail

# PAY-02 — same Idempotency-Key + same body returns the cached response.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "idem-replay"

create_body="/tmp/minifin-phase04-idem-replay-create.json"
pa_rm "${create_body}"
pa_create_api_key "${PA_COOKIE_JAR}" "idem-replay" "${create_body}"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"

idem_key="replay-$(date +%s%N)"
body_json='{"amount":"12.50","currency":"EUR","description":"replay"}'

first_body="/tmp/minifin-phase04-idem-replay-first.json"
pa_rm "${first_body}"
first_status="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" "${body_json}" "${first_body}")"
test "${first_status}" = "201"
first_intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); console.log(j.data.id);")"
first_created="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); console.log(j.data.createdAt);")"

second_body="/tmp/minifin-phase04-idem-replay-second.json"
pa_rm "${second_body}"
second_status="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" "${body_json}" "${second_body}")"
test "${second_status}" = "201"
second_intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${second_body}','utf8')); console.log(j.data.id);")"
second_created="$(node -e "const j=JSON.parse(require('fs').readFileSync('${second_body}','utf8')); console.log(j.data.createdAt);")"

test "${first_intent_id}" = "${second_intent_id}"
test "${first_created}" = "${second_created}"

# Cached body must be byte-identical to first response.
diff -q "${first_body}" "${second_body}" >/dev/null

# DB cross-check: exactly one payment intent + one idempotency row + one stored response status 201.
intent_count="$(pa_psql "select count(*) from merchant.payment_intents where merchant_id = '${PA_MERCHANT_ID}'::uuid;")"
test "${intent_count}" = "1"
idem_count="$(pa_psql "select count(*) from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';")"
test "${idem_count}" = "1"
stored_status="$(pa_psql "select response_status from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';")"
test "${stored_status}" = "201"

# Trying a fresh idempotency key with the same body must create a NEW payment intent (independent op).
fresh_key="replay-fresh-$(date +%s%N)"
fresh_body_file="/tmp/minifin-phase04-idem-replay-fresh.json"
fresh_status="$(pa_public_post_payment_intent "${raw_key}" "${fresh_key}" "${body_json}" "${fresh_body_file}")"
test "${fresh_status}" = "201"
fresh_intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${fresh_body_file}','utf8')); console.log(j.data.id);")"
test "${fresh_intent_id}" != "${first_intent_id}"

# Cross-merchant: same idempotency_key string used by another merchant is an independent scope.
pa_register_merchant "idem-replay-other"
other_create="/tmp/minifin-phase04-idem-replay-other-create.json"
pa_create_api_key "${PA_COOKIE_JAR}" "idem-replay-other" "${other_create}"
other_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${other_create}','utf8')); console.log(j.data.key);")"
other_merchant_id="${PA_MERCHANT_ID}"
other_intent_body="/tmp/minifin-phase04-idem-replay-other-intent.json"
pa_rm "${other_intent_body}"
other_status="$(pa_public_post_payment_intent "${other_key}" "${idem_key}" "${body_json}" "${other_intent_body}")"
test "${other_status}" = "201"
other_intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${other_intent_body}','utf8')); console.log(j.data.id);")"
test "${other_intent_id}" != "${first_intent_id}"
other_row="$(pa_psql "select count(*) from idempotency.idempotency_keys where merchant_id = '${other_merchant_id}'::uuid and idempotency_key = '${idem_key}';")"
test "${other_row}" = "1"

echo "PAY-02 public API idempotency replay pass"
