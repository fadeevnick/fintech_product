#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_stripe_webhook.sh"

tag="stripe-valid"
merchant_id="$(stripe_register_merchant "${tag}")"
stripe_account_id="acct_valid_$(date +%s%N)"
event_id="evt_valid_$(date +%s%N)"
payload_file="/tmp/minifin-phase04-stripe-valid-payload.json"
response_file="/tmp/minifin-phase04-stripe-valid-response.json"

stripe_seed_merchant_link "${merchant_id}" "${stripe_account_id}"
stripe_json_payload "${event_id}" "${stripe_account_id}" "true" "true" "true" >"${payload_file}"
signature="$(stripe_signature_header "${payload_file}" "$(date +%s)")"

curl -fsS -X POST "${base_url}/webhooks/stripe/v1" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: ${signature}" \
  --data-binary @"${payload_file}" \
  >"${response_file}"

node -e "const j=JSON.parse(require('fs').readFileSync('${response_file}','utf8')); if(j.data?.outcome!=='PROCESSED'||j.data?.eventId!=='${event_id}') process.exit(1);"

kyb_status="$(stripe_psql "select kyb_status from merchant.merchants where id = '${merchant_id}'::uuid;")"
test "${kyb_status}" = "VERIFIED"
event_rows="$(stripe_psql "select count(*) from merchant.stripe_webhook_events where stripe_event_id = '${event_id}' and outcome = 'PROCESSED';")"
test "${event_rows}" = "1"
audit_rows="$(stripe_psql "select count(*) from audit.audit_log where event_type = 'stripe.account_updated' and subject_id = '${merchant_id}'::uuid;")"
test "${audit_rows}" -ge 1

echo "MRC-02 Stripe webhook valid signature pass"
