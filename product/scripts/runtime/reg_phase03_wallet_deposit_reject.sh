#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-reject-cookies.txt"
deposit_body="/tmp/minifin-phase03-reject-deposit.json"
decision_body="/tmp/minifin-phase03-reject-decision.json"
wallet_body="/tmp/minifin-phase03-reject-wallet.json"

kc_seed_backoffice_realm
compliance_token="$(kc_backoffice_token compliance)"

user_id="$(wd_register_enduser reject "${cookie_jar}")"
wd_create_deposit "${cookie_jar}" "7.5000" "${deposit_body}"
deposit_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_body}','utf8')); if(!j.data?.depositId) process.exit(1); console.log(j.data.depositId);")"

curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
  -H "Authorization: Bearer ${compliance_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"REJECT","reason":"runtime reject probe"}' \
  >"${decision_body}"

node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); if(j.data?.state!=='REJECTED'||j.data?.journalEntryId!==null) process.exit(1);"

# no ledger postings for this deposit reference
posting_count="$(wd_psql "select count(*) from ledger.postings p join ledger.journal_entries j on j.id = p.journal_entry_id where j.reference_id = '${deposit_id}'::uuid;")"
test "${posting_count}" = "0"

# wallet balance is 0
curl -fsS -b "${cookie_jar}" "${base_url}/api/v1/wallet" >"${wallet_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${wallet_body}','utf8')); if(j.data?.balance!=='0.0000'||j.data?.deposits?.[0]?.state!=='REJECTED') process.exit(1);"

# audit row exists
rejected_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.deposit_rejected' and subject_id = '${deposit_id}'::uuid;")"
test "${rejected_audit}" -ge 1

echo "WLT deposit reject pass"
