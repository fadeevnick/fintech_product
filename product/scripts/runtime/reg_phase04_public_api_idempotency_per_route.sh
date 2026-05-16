#!/usr/bin/env bash
set -euo pipefail

# PAY-02 / PAY-03 scope — idempotency is scoped per (merchant, route, key) per
# planning/03_functional_requirements.md. Reusing the same key against a
# different endpoint must NOT collide with a previous use on /v1/payment_intents
# and must NOT raise idempotency_conflict.
#
# We exercise this with two routes that share the same merchant and the same
# Idempotency-Key string:
#   POST /v1/payment_intents
#   POST /api/v1/merchant/api-keys (when the dashboard route opts in via the
#                                   Idempotency-Key header).
# Both succeed independently; neither blocks the other.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "scope"

create_body="/tmp/minifin-phase04-scope-create.json"
pa_create_api_key "${PA_COOKIE_JAR}" "scope-bootstrap" "${create_body}"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"

shared_key="scope-shared-$(date +%s%N)"

# 1) Use shared key on the public payment intent route.
public_body="/tmp/minifin-phase04-scope-public.json"
public_status="$(pa_public_post_payment_intent "${raw_key}" "${shared_key}" '{"amount":"3.50","currency":"EUR"}' "${public_body}")"
test "${public_status}" = "201"

# 2) Use the SAME idempotency key string on the dashboard API key create route.
dashboard_body="/tmp/minifin-phase04-scope-dashboard.json"
dashboard_status=$(curl -sS -o "${dashboard_body}" -w "%{http_code}" \
  -b "${PA_COOKIE_JAR}" \
  -X POST "${base_url}/api/v1/merchant/api-keys" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${shared_key}" \
  -d '{"label":"scope"}')
test "${dashboard_status}" = "201" || { echo "dashboard create returned ${dashboard_status}, expected 201"; exit 1; }
node -e "const j=JSON.parse(require('fs').readFileSync('${dashboard_body}','utf8')); if(j.data?.status!=='ACTIVE'||!j.data?.key) process.exit(1);"

# Two idempotency rows in different (route) buckets must coexist.
public_rows="$(pa_psql "select count(*) from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${shared_key}' and route = '/v1/payment_intents';")"
dashboard_rows="$(pa_psql "select count(*) from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${shared_key}' and route = '/api/v1/merchant/api-keys';")"
test "${public_rows}" = "1"
test "${dashboard_rows}" = "1"

# Replaying each route with the same key + same body returns the cached row.
public_replay="/tmp/minifin-phase04-scope-public-replay.json"
public_replay_status="$(pa_public_post_payment_intent "${raw_key}" "${shared_key}" '{"amount":"3.50","currency":"EUR"}' "${public_replay}")"
test "${public_replay_status}" = "201"
diff -q "${public_body}" "${public_replay}" >/dev/null

dashboard_replay="/tmp/minifin-phase04-scope-dashboard-replay.json"
dashboard_replay_status=$(curl -sS -o "${dashboard_replay}" -w "%{http_code}" \
  -b "${PA_COOKIE_JAR}" \
  -X POST "${base_url}/api/v1/merchant/api-keys" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${shared_key}" \
  -d '{"label":"scope"}')
test "${dashboard_replay_status}" = "201"
# Replayed dashboard body has a redacted key, original body has the real key.
node -e "
const first=JSON.parse(require('fs').readFileSync('${dashboard_body}','utf8'));
const replay=JSON.parse(require('fs').readFileSync('${dashboard_replay}','utf8'));
if(first.data.apiKeyId !== replay.data.apiKeyId) process.exit(1);
if(!first.data.key||!first.data.key.startsWith('mfp_live_')) process.exit(1);
if(replay.data.key !== 'REDACTED_ON_REPLAY') process.exit(1);
"

# Replay with a DIFFERENT body on either route returns 409.
public_conflict="/tmp/minifin-phase04-scope-public-conflict.json"
status_pc="$(pa_public_post_payment_intent "${raw_key}" "${shared_key}" '{"amount":"3.51","currency":"EUR"}' "${public_conflict}")"
test "${status_pc}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync('${public_conflict}','utf8')); if(j.errors?.[0]?.code!=='idempotency_conflict') process.exit(1);"

dashboard_conflict="/tmp/minifin-phase04-scope-dashboard-conflict.json"
status_dc=$(curl -sS -o "${dashboard_conflict}" -w "%{http_code}" \
  -b "${PA_COOKIE_JAR}" \
  -X POST "${base_url}/api/v1/merchant/api-keys" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${shared_key}" \
  -d '{"label":"different"}')
test "${status_dc}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync('${dashboard_conflict}','utf8')); if(j.errors?.[0]?.code!=='idempotency_conflict') process.exit(1);"

# DB still has exactly one payment_intent and exactly two API keys total
# (bootstrap + the one created via Idempotency-Key) — no extras.
intent_count="$(pa_psql "select count(*) from merchant.payment_intents where merchant_id = '${PA_MERCHANT_ID}'::uuid;")"
test "${intent_count}" = "1"
api_key_count="$(pa_psql "select count(*) from merchant.api_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid;")"
test "${api_key_count}" = "2"

echo "PAY-02 PAY-03 idempotency per-merchant per-route scope pass"
