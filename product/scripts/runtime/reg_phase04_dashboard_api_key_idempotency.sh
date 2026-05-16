#!/usr/bin/env bash
set -euo pipefail

# MRC-03 idempotency — POST /api/v1/merchant/api-keys with the same
# Idempotency-Key + same body returns the same apiKeyId on retry without
# generating a second active key. The cached body redacts the one-time-visible
# raw key so it cannot be exfiltrated from the idempotency cache.

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_public_api.sh"

pa_register_merchant "dash-idem"

idem_key="dashkey-$(date +%s%N)"
first_body="/tmp/minifin-phase04-dash-first.json"
first_status=$(curl -sS -o "${first_body}" -w "%{http_code}" \
  -b "${PA_COOKIE_JAR}" \
  -X POST "${base_url}/api/v1/merchant/api-keys" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${idem_key}" \
  -d '{"label":"primary"}')
test "${first_status}" = "201"
first_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); if(!j.data?.apiKeyId||!j.data?.key||!j.data?.key.startsWith('mfp_live_')) process.exit(1); console.log(j.data.apiKeyId);")"
first_raw="$(node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); console.log(j.data.key);")"

# Replay returns the same apiKeyId, no new row, raw key is REDACTED.
second_body="/tmp/minifin-phase04-dash-second.json"
second_status=$(curl -sS -o "${second_body}" -w "%{http_code}" \
  -b "${PA_COOKIE_JAR}" \
  -X POST "${base_url}/api/v1/merchant/api-keys" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${idem_key}" \
  -d '{"label":"primary"}')
test "${second_status}" = "201"
second_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${second_body}','utf8')); console.log(j.data.apiKeyId);")"
test "${first_id}" = "${second_id}"
node -e "const j=JSON.parse(require('fs').readFileSync('${second_body}','utf8')); if(j.data.key!=='REDACTED_ON_REPLAY') process.exit(1);"

# DB has exactly one ACTIVE api_key for this merchant — no duplicate from retry.
active_count="$(pa_psql "select count(*) from merchant.api_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and status = 'ACTIVE';")"
test "${active_count}" = "1"

# Cached idempotency row exists, response_body must NOT contain the raw key.
cached_body="$(pa_psql "select response_body from idempotency.idempotency_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and route = '/api/v1/merchant/api-keys' and idempotency_key = '${idem_key}';")"
case "${cached_body}" in
  *"${first_raw}"*) echo "raw key leaked into idempotency cache"; exit 1 ;;
esac
case "${cached_body}" in
  *REDACTED_ON_REPLAY*) ;;
  *) echo "expected REDACTED_ON_REPLAY marker in cached body"; exit 1 ;;
esac

# Same key + different body returns 409.
conflict_body="/tmp/minifin-phase04-dash-conflict.json"
conflict_status=$(curl -sS -o "${conflict_body}" -w "%{http_code}" \
  -b "${PA_COOKIE_JAR}" \
  -X POST "${base_url}/api/v1/merchant/api-keys" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: ${idem_key}" \
  -d '{"label":"different"}')
test "${conflict_status}" = "409"
node -e "const j=JSON.parse(require('fs').readFileSync('${conflict_body}','utf8')); if(j.errors?.[0]?.code!=='idempotency_conflict') process.exit(1);"

# DB still exactly one ACTIVE api_key.
active_count_after="$(pa_psql "select count(*) from merchant.api_keys where merchant_id = '${PA_MERCHANT_ID}'::uuid and status = 'ACTIVE';")"
test "${active_count_after}" = "1"

# Without Idempotency-Key, each request creates a fresh key (back-compat).
no_idem_a="/tmp/minifin-phase04-dash-noidem-a.json"
no_idem_b="/tmp/minifin-phase04-dash-noidem-b.json"
pa_create_api_key "${PA_COOKIE_JAR}" "no-idem-a" "${no_idem_a}"
pa_create_api_key "${PA_COOKIE_JAR}" "no-idem-b" "${no_idem_b}"
id_a="$(node -e "const j=JSON.parse(require('fs').readFileSync('${no_idem_a}','utf8')); console.log(j.data.apiKeyId);")"
id_b="$(node -e "const j=JSON.parse(require('fs').readFileSync('${no_idem_b}','utf8')); console.log(j.data.apiKeyId);")"
test "${id_a}" != "${id_b}"

echo "MRC-03 dashboard api key idempotency pass"
