#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

cookie_jar="/tmp/minifin-phase03-double-cookies.txt"
deposit_body="/tmp/minifin-phase03-double-deposit.json"
first_body="/tmp/minifin-phase03-double-first.json"
second_body="/tmp/minifin-phase03-double-second.json"

user_id="$(wd_register_enduser double "${cookie_jar}")"
wd_create_deposit "${cookie_jar}" "3.0000" "${deposit_body}"
deposit_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_body}','utf8')); console.log(j.data.depositId);")"

# First approve succeeds
curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","reason":"first decision"}' \
  >"${first_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${first_body}','utf8')); if(j.data?.state!=='COMPLETED') process.exit(1);"

# Second decision returns 409
status="$(curl -sS -o "${second_body}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","reason":"second decision"}')"
test "${status}" = "409"
grep -q '"code":"deposit_already_decided"' "${second_body}"

# only one approved audit row
approved="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.deposit_approved' and subject_id = '${deposit_id}'::uuid;")"
test "${approved}" = "1"
# only one ledger journal for this deposit
journals="$(wd_psql "select count(*) from ledger.journal_entries where reference_id = '${deposit_id}'::uuid;")"
test "${journals}" = "1"

# reject after completed also refused
status="$(curl -sS -o /tmp/minifin-phase03-double-third.json -w "%{http_code}" -X POST \
  "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"REJECT","reason":"late reject"}')"
test "${status}" = "409"

echo "WLT deposit double decision pass"
