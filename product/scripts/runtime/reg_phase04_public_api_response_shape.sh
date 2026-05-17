#!/usr/bin/env bash
set -euo pipefail

# PAY-01 — public API responses follow {data, errors} shape on success and on errors.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "shape"

create_body="/tmp/minifin-phase04-shape-create.json"
pa_rm "${create_body}"
pa_create_api_key "${PA_COOKIE_JAR}" "shape" "${create_body}"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"

# Success: 201 with data populated and errors empty.
ok_body="/tmp/minifin-phase04-shape-ok.json"
pa_rm "${ok_body}"
ok_status="$(pa_public_post_payment_intent "${raw_key}" "shape-ok-$(date +%s%N)" '{"amount":"19.99","currency":"EUR","description":"hello"}' "${ok_body}")"
test "${ok_status}" = "201"
node -e "
const j=JSON.parse(require('fs').readFileSync('${ok_body}','utf8'));
if(!Array.isArray(j.errors)||j.errors.length!==0) process.exit(1);
const d=j.data;
if(!d||d.object!=='payment_intent'||!d.id||d.amount!=='19.99'||d.currency!=='EUR'||d.state!=='REQUIRES_PAYMENT_METHOD'||d.description!=='hello'||!d.createdAt) process.exit(1);
"
intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${ok_body}','utf8')); console.log(j.data.id);")"

# GET returns same shape.
get_body="/tmp/minifin-phase04-shape-get.json"
pa_rm "${get_body}"
get_status="$(pa_public_get_payment_intent "${raw_key}" "${intent_id}" "${get_body}")"
test "${get_status}" = "200"
node -e "
const j=JSON.parse(require('fs').readFileSync('${get_body}','utf8'));
if(!Array.isArray(j.errors)||j.errors.length!==0||!j.data||j.data.id!=='${intent_id}') process.exit(1);
"

# Missing idempotency key — error shape with data null and errors[0].code=idempotency_key_required, status 400.
missing_body="/tmp/minifin-phase04-shape-missing-idem.json"
pa_rm "${missing_body}"
missing_status="$(pa_public_post_payment_intent "${raw_key}" "-" '{"amount":"1.00","currency":"EUR"}' "${missing_body}")"
test "${missing_status}" = "400"
node -e "
const j=JSON.parse(require('fs').readFileSync('${missing_body}','utf8'));
if(j.data!==null&&j.data!==undefined) process.exit(1);
if(!Array.isArray(j.errors)||j.errors[0]?.code!=='idempotency_key_required'||!j.errors[0]?.message) process.exit(1);
"

# Invalid amount — error shape with field set.
invalid_body="/tmp/minifin-phase04-shape-invalid-amount.json"
pa_rm "${invalid_body}"
invalid_status="$(pa_public_post_payment_intent "${raw_key}" "shape-invalid-$(date +%s%N)" '{"amount":"-1.00","currency":"EUR"}' "${invalid_body}")"
test "${invalid_status}" = "400"
node -e "
const j=JSON.parse(require('fs').readFileSync('${invalid_body}','utf8'));
if(j.data!==null&&j.data!==undefined) process.exit(1);
if(!Array.isArray(j.errors)||j.errors[0]?.code!=='invalid_amount'||j.errors[0]?.field!=='amount') process.exit(1);
"

# Unauthenticated request also follows {data, errors} shape.
unauth_body="/tmp/minifin-phase04-shape-unauth.json"
pa_rm "${unauth_body}"
unauth_status="$(pa_curl -sS -o "${unauth_body}" -w "%{http_code}" -X POST "${base_url}/v1/payment_intents" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: shape-unauth-$(date +%s%N)" \
  -d '{"amount":"1.00","currency":"EUR"}')"
test "${unauth_status}" = "401"
node -e "
const j=JSON.parse(require('fs').readFileSync('${unauth_body}','utf8'));
if(!Array.isArray(j.errors)||j.errors[0]?.code!=='unauthenticated') process.exit(1);
"

# Cross-merchant payment-intent isolation: GET another merchant's id returns 404 with {data, errors}.
pa_register_merchant "shape-other"
other_create="/tmp/minifin-phase04-shape-other-create.json"
pa_create_api_key "${PA_COOKIE_JAR}" "shape-other" "${other_create}"
other_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${other_create}','utf8')); console.log(j.data.key);")"
forbidden_body="/tmp/minifin-phase04-shape-forbidden.json"
pa_rm "${forbidden_body}"
forbidden_status="$(pa_public_get_payment_intent "${other_key}" "${intent_id}" "${forbidden_body}")"
test "${forbidden_status}" = "404"
node -e "
const j=JSON.parse(require('fs').readFileSync('${forbidden_body}','utf8'));
if(!Array.isArray(j.errors)||j.errors[0]?.code!=='payment_intent_not_found') process.exit(1);
"

echo "PAY-01 public API response shape pass"
