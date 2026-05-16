#!/usr/bin/env bash
set -euo pipefail

# PAY-03 — same Idempotency-Key + different body returns HTTP 409 idempotency_conflict.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "idem-conflict"

create_body="/tmp/minifin-phase04-idem-conflict-create.json"
pa_create_api_key "${PA_COOKIE_JAR}" "idem-conflict" "${create_body}"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"

idem_key="conflict-$(date +%s%N)"
first_body_file="/tmp/minifin-phase04-idem-conflict-first.json"
first_status="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" '{"amount":"5.00","currency":"EUR","description":"original"}' "${first_body_file}")"
test "${first_status}" = "201"
first_intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body_file}','utf8')); console.log(j.data.id);")"

conflict_body="/tmp/minifin-phase04-idem-conflict-conflict.json"
conflict_status="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" '{"amount":"5.01","currency":"EUR","description":"original"}' "${conflict_body}")"
test "${conflict_status}" = "409"
node -e "
const j=JSON.parse(require('fs').readFileSync('${conflict_body}','utf8'));
if(!Array.isArray(j.errors)||j.errors[0]?.code!=='idempotency_conflict'||!j.errors[0]?.message) process.exit(1);
if(j.data!==null&&j.data!==undefined) process.exit(1);
"

# Field difference must also conflict.
conflict_body_2="/tmp/minifin-phase04-idem-conflict-conflict-2.json"
conflict_status_2="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" '{"amount":"5.00","currency":"EUR","description":"different"}' "${conflict_body_2}")"
test "${conflict_status_2}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync('${conflict_body_2}','utf8')); if(j.errors?.[0]?.code!=='idempotency_conflict') process.exit(1);"

# No second payment_intent row created.
intent_count="$(pa_psql "select count(*) from merchant.payment_intents where merchant_id = '${PA_MERCHANT_ID}'::uuid;")"
test "${intent_count}" = "1"

# Original body still replays cleanly.
replay_body="/tmp/minifin-phase04-idem-conflict-replay.json"
replay_status="$(pa_public_post_payment_intent "${raw_key}" "${idem_key}" '{"amount":"5.00","currency":"EUR","description":"original"}' "${replay_body}")"
test "${replay_status}" = "201"
replay_intent_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${replay_body}','utf8')); console.log(j.data.id);")"
test "${replay_intent_id}" = "${first_intent_id}"

# Stored idempotency row unchanged.
stored_status="$(pa_psql "select response_status from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and idempotency_key = '${idem_key}';")"
test "${stored_status}" = "201"

echo "PAY-03 public API idempotency conflict pass"
