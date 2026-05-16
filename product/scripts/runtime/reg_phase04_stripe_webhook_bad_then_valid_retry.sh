#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase04_stripe_webhook.sh"

tag="stripe-retry"
merchant_id="$(stripe_register_merchant "${tag}")"
stripe_account_id="acct_retry_$(date +%s%N)"
event_id="evt_retry_$(date +%s%N)"
payload_file="/tmp/minifin-phase04-stripe-retry-payload.json"
bad_response="/tmp/minifin-phase04-stripe-retry-bad.json"
good_response="/tmp/minifin-phase04-stripe-retry-good.json"
timestamp="$(date +%s)"

stripe_seed_merchant_link "${merchant_id}" "${stripe_account_id}"
stripe_json_payload "${event_id}" "${stripe_account_id}" "true" "true" "true" >"${payload_file}"
bad_signature="$(stripe_signature_header "${payload_file}" "${timestamp}" "whsec_wrong_secret")"
good_signature="$(stripe_signature_header "${payload_file}" "${timestamp}")"

status="$(curl -sS -o "${bad_response}" -w "%{http_code}" -X POST "${base_url}/webhooks/stripe/v1" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: ${bad_signature}" \
  --data-binary @"${payload_file}")"
test "${status}" = "400"
node -e "const j=JSON.parse(require('fs').readFileSync('${bad_response}','utf8')); if(j.errors?.[0]?.code!=='stripe_webhook_signature_invalid') process.exit(1);"

event_rows_after_bad="$(stripe_psql "select count(*) from merchant.stripe_webhook_events where stripe_event_id = '${event_id}';")"
test "${event_rows_after_bad}" = "0"

curl -fsS -X POST "${base_url}/webhooks/stripe/v1" \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: ${good_signature}" \
  --data-binary @"${payload_file}" \
  >"${good_response}"
node -e "const j=JSON.parse(require('fs').readFileSync('${good_response}','utf8')); if(j.data?.outcome!=='PROCESSED'||j.data?.eventId!=='${event_id}') process.exit(1);"

kyb_status="$(stripe_psql "select kyb_status from merchant.merchants where id = '${merchant_id}'::uuid;")"
test "${kyb_status}" = "VERIFIED"
event_rows_after_good="$(stripe_psql "select count(*) from merchant.stripe_webhook_events where stripe_event_id = '${event_id}' and outcome = 'PROCESSED';")"
test "${event_rows_after_good}" = "1"
invalid_audit="$(stripe_psql "select count(*) from audit.audit_log where event_type = 'stripe.webhook_signature_invalid' and outcome = 'FAILURE';")"
test "${invalid_audit}" -ge 1
account_updated_audit="$(stripe_psql "select count(*) from audit.audit_log where event_type = 'stripe.account_updated' and subject_id = '${merchant_id}'::uuid;")"
test "${account_updated_audit}" = "1"

echo "MRC-02 Stripe webhook bad-then-valid retry pass"
