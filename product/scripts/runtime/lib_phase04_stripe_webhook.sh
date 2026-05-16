#!/usr/bin/env bash

base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"
compose_file="${COMPOSE_FILE:-deploy/docker-compose.yml}"
stripe_webhook_secret="${STRIPE_WEBHOOK_SIGNING_SECRET:-whsec_local_test_secret}"

stripe_psql() {
  docker compose -f "${compose_file}" exec -T platform-db psql -U platform -d platform -Atc "$1"
}

stripe_json_payload() {
  local event_id="$1"
  local stripe_account_id="$2"
  local charges_enabled="$3"
  local payouts_enabled="$4"
  local details_submitted="$5"
  cat <<JSON
{"id":"${event_id}","object":"event","type":"account.updated","data":{"object":{"id":"${stripe_account_id}","object":"account","charges_enabled":${charges_enabled},"payouts_enabled":${payouts_enabled},"details_submitted":${details_submitted}}}}
JSON
}

stripe_signature_header() {
  local payload_file="$1"
  local timestamp="$2"
  local secret="${3:-${stripe_webhook_secret}}"
  node -e '
const crypto = require("crypto");
const fs = require("fs");
const payload = fs.readFileSync(process.argv[1], "utf8");
const timestamp = process.argv[2];
const secret = process.argv[3];
const signature = crypto.createHmac("sha256", secret).update(`${timestamp}.${payload}`).digest("hex");
console.log(`t=${timestamp},v1=${signature}`);
' "${payload_file}" "${timestamp}" "${secret}"
}

stripe_seed_merchant_link() {
  local merchant_id="$1"
  local stripe_account_id="$2"
  stripe_psql "insert into merchant.stripe_account_links (id, merchant_id, stripe_account_id) values (gen_random_uuid(), '${merchant_id}'::uuid, '${stripe_account_id}') on conflict (stripe_account_id) do nothing;"
}

stripe_register_merchant() {
  local tag="$1"
  local email="phase04.${tag}.$(date +%s%N)@example.test"
  local password="correct horse battery"
  local register_body="/tmp/minifin-phase04-${tag}-merchant-register.json"
  curl -fsS -X POST "${base_url}/api/v1/merchant/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${email}\",\"password\":\"${password}\",\"companyName\":\"Phase 04 ${tag}\",\"country\":\"EE\",\"businessType\":\"company\"}" \
    >"${register_body}"
  node -e "const j=JSON.parse(require('fs').readFileSync('${register_body}','utf8')); if(!j.data?.merchantId) process.exit(1); console.log(j.data.merchantId);"
}
