#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_stripe_webhook.sh"

tag="stripe-idem"
merchant_id="$(stripe_register_merchant "${tag}")"
stripe_account_id="acct_idem_$(date +%s%N)"
event_id="evt_idem_$(date +%s%N)"
payload_file="/tmp/minifin-phase04-stripe-idem-payload.json"
first_response="/tmp/minifin-phase04-stripe-idem-first.json"
second_response="/tmp/minifin-phase04-stripe-idem-second.json"

stripe_seed_merchant_link "${merchant_id}" "${stripe_account_id}"
stripe_json_payload "${event_id}" "${stripe_account_id}" "false" "false" "true" >"${payload_file}"
signature="$(stripe_signature_header "${payload_file}" "$(date +%s)")"

curl -fsS -X POST "${base_url}/webhooks/stripe/v1" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: ${signature}" \
  --data-binary @"${payload_file}" \
  >"${first_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${first_response}','utf8')); if(j.data?.outcome!=='PROCESSED') process.exit(1);"

curl -fsS -X POST "${base_url}/webhooks/stripe/v1" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: ${signature}" \
  --data-binary @"${payload_file}" \
  >"${second_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${second_response}','utf8')); if(j.data?.outcome!=='DUPLICATE') process.exit(1);"

kyb_status="$(stripe_psql "select kyb_status from merchant.merchants where id = '${merchant_id}'::uuid;")"
test "${kyb_status}" = "PENDING"
event_rows="$(stripe_psql "select count(*) from merchant.stripe_webhook_events where stripe_event_id = '${event_id}';")"
test "${event_rows}" = "1"
account_updated_audit="$(stripe_psql "select count(*) from audit.audit_log where event_type = 'stripe.account_updated' and subject_id = '${merchant_id}'::uuid;")"
test "${account_updated_audit}" = "1"
duplicate_audit="$(stripe_psql "select count(*) from audit.audit_log where event_type = 'stripe.webhook_duplicate' and metadata ->> 'stripeEventId' = '${event_id}';")"
test "${duplicate_audit}" = "1"

echo "MRC-02 Stripe webhook event idempotency pass"
