#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_stripe_webhook.sh"

tag="stripe-invalid"
merchant_id="$(stripe_register_merchant "${tag}")"
stripe_account_id="acct_invalid_$(date +%s%N)"
event_id="evt_invalid_$(date +%s%N)"
payload_file="/tmp/minifin-phase04-stripe-invalid-payload.json"
response_file="/tmp/minifin-phase04-stripe-invalid-response.json"

stripe_seed_merchant_link "${merchant_id}" "${stripe_account_id}"
stripe_json_payload "${event_id}" "${stripe_account_id}" "true" "true" "true" >"${payload_file}"
signature="$(stripe_signature_header "${payload_file}" "$(date +%s)" "whsec_wrong_secret")"

status="$(curl -sS -o "${response_file}" -w "%{http_code}" -X POST "${base_url}/webhooks/stripe/v1" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: ${signature}" \
  --data-binary @"${payload_file}")"
test "${status}" = "400"
node -e "const j=JSON.parse(require('fs').readFileSync('${response_file}','utf8')); if(j.errors?.[0]?.code!=='stripe_webhook_signature_invalid') process.exit(1);"

kyb_status="$(stripe_psql "select kyb_status from merchant.merchants where id = '${merchant_id}'::uuid;")"
test "${kyb_status}" = "NOT_STARTED"
processed_rows="$(stripe_psql "select count(*) from merchant.stripe_webhook_events where stripe_event_id = '${event_id}' and outcome = 'PROCESSED';")"
test "${processed_rows}" = "0"
rejected_rows="$(stripe_psql "select count(*) from merchant.stripe_webhook_events where stripe_event_id = '${event_id}' and outcome = 'REJECTED_SIGNATURE';")"
test "${rejected_rows}" = "1"
audit_rows="$(stripe_psql "select count(*) from audit.audit_log where event_type = 'stripe.webhook_signature_invalid' and outcome = 'FAILURE';")"
test "${audit_rows}" -ge 1

echo "MRC-02 Stripe webhook invalid signature rejection pass"
