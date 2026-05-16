#!/usr/bin/env bash
set -euo pipefail

# MRC-03 — API key lifecycle: created once, hashed/fingerprinted at rest, revoked key fails.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "apikey-lifecycle"

create_body="/tmp/minifin-phase04-apikey-create.json"
pa_create_api_key "${PA_COOKIE_JAR}" "primary" "${create_body}"
api_key_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); if(!j.data?.apiKeyId||!j.data?.key||!j.data?.fingerprint||j.data?.status!=='ACTIVE') process.exit(1); console.log(j.data.apiKeyId);")"
raw_key="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.key);")"
fingerprint="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.fingerprint);")"
key_prefix="$(node -e "const j=JSON.parse(require('fs').readFileSync('${create_body}','utf8')); console.log(j.data.keyPrefix);")"

# Raw key must be a long Stripe-style secret never repeated elsewhere.
node -e "if(!/^mfp_live_[A-Za-z0-9_-]{20,}$/.test(process.argv[1])) process.exit(1);" "${raw_key}"
test "${#fingerprint}" = "16"
node -e "if(process.argv[1]!=='mfp_live_'+process.argv[2].slice(9,12)) process.exit(1);" "${key_prefix}" "${raw_key}"

# DB must not contain raw key. Hash should match sha256(raw_key).
expected_hash="$(printf '%s' "${raw_key}" | sha256sum | awk '{print $1}')"
db_hash="$(pa_psql "select key_hash from merchant.api_keys where id = '${api_key_id}'::uuid;")"
test "${db_hash}" = "${expected_hash}"
raw_key_count="$(pa_psql "select count(*) from merchant.api_keys where key_hash like '%${raw_key}%' or key_prefix = '${raw_key}';")"
test "${raw_key_count}" = "0"
db_status="$(pa_psql "select status from merchant.api_keys where id = '${api_key_id}'::uuid;")"
test "${db_status}" = "ACTIVE"
db_fingerprint="$(pa_psql "select fingerprint from merchant.api_keys where id = '${api_key_id}'::uuid;")"
test "${db_fingerprint}" = "${fingerprint}"

# Listing returns prefix/fingerprint, not the raw key.
list_body="/tmp/minifin-phase04-apikey-list.json"
pa_list_api_keys "${PA_COOKIE_JAR}" "${list_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${list_body}','utf8')); const k=j.data?.find(x=>x.apiKeyId===process.argv[1]); if(!k||k.status!=='ACTIVE'||k.keyPrefix!==process.argv[2]||k.fingerprint!==process.argv[3]||'key' in k) process.exit(1);" "${api_key_id}" "${key_prefix}" "${fingerprint}"

# Active key works against public API.
intent_body="/tmp/minifin-phase04-apikey-intent.json"
status="$(pa_public_post_payment_intent "${raw_key}" "lifecycle-pre-revoke-$(date +%s%N)" '{"amount":"5.00","currency":"EUR"}' "${intent_body}")"
test "${status}" = "201"
node -e "const j=JSON.parse(require('fs').readFileSync('${intent_body}','utf8')); if(!j.data?.id||j.data?.state!=='REQUIRES_PAYMENT_METHOD') process.exit(1);"

# Revoke key.
revoke_body="/tmp/minifin-phase04-apikey-revoke.json"
pa_revoke_api_key "${PA_COOKIE_JAR}" "${api_key_id}" "${revoke_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${revoke_body}','utf8')); if(j.data?.status!=='REVOKED'||!j.data?.revokedAt) process.exit(1);"
db_status_after="$(pa_psql "select status from merchant.api_keys where id = '${api_key_id}'::uuid;")"
test "${db_status_after}" = "REVOKED"

# Revoke is idempotent.
revoke_again_body="/tmp/minifin-phase04-apikey-revoke-2.json"
pa_revoke_api_key "${PA_COOKIE_JAR}" "${api_key_id}" "${revoke_again_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${revoke_again_body}','utf8')); if(j.data?.status!=='REVOKED') process.exit(1);"

# Revoked key now fails on public API with 401 invalid_api_key.
denied_body="/tmp/minifin-phase04-apikey-denied.json"
status_after="$(pa_public_post_payment_intent "${raw_key}" "lifecycle-post-revoke-$(date +%s%N)" '{"amount":"5.00","currency":"EUR"}' "${denied_body}")"
test "${status_after}" = "401"
node -e "const j=JSON.parse(require('fs').readFileSync('${denied_body}','utf8')); if(j.errors?.[0]?.code!=='invalid_api_key') process.exit(1);"

# Missing Authorization header returns 401 unauthenticated.
missing_body="/tmp/minifin-phase04-apikey-missing.json"
missing_status="$(curl -sS -o "${missing_body}" -w "%{http_code}" -X POST "${base_url}/v1/payment_intents" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: missing-auth-$(date +%s%N)" \
  -d '{"amount":"5.00","currency":"EUR"}')"
test "${missing_status}" = "401"
node -e "const j=JSON.parse(require('fs').readFileSync('${missing_body}','utf8')); if(j.errors?.[0]?.code!=='unauthenticated') process.exit(1);"

# Malformed bearer returns 401 invalid_api_key.
bad_body="/tmp/minifin-phase04-apikey-bad.json"
bad_status="$(curl -sS -o "${bad_body}" -w "%{http_code}" -X POST "${base_url}/v1/payment_intents" \
  -H "Authorization: Bearer not_an_mfp_key" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: bad-key-$(date +%s%N)" \
  -d '{"amount":"5.00","currency":"EUR"}')"
test "${bad_status}" = "401"
node -e "const j=JSON.parse(require('fs').readFileSync('${bad_body}','utf8')); if(j.errors?.[0]?.code!=='invalid_api_key') process.exit(1);"

audit_count="$(pa_psql "select count(*) from audit.audit_log where event_type in ('merchant.api_key_created','merchant.api_key_revoked') and subject_id = '${api_key_id}'::uuid;")"
test "${audit_count}" = "2"

echo "MRC-03 api key lifecycle pass"
