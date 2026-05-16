#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-happy-cookies.txt"
deposit_body="/tmp/minifin-phase03-happy-deposit.json"
decision_body="/tmp/minifin-phase03-happy-decision.json"
wallet_body="/tmp/minifin-phase03-happy-wallet.json"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

user_id="$(wd_register_enduser happy "${cookie_jar}")"

wd_create_deposit "${cookie_jar}" "12.0000" "${deposit_body}"
deposit_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${deposit_body}','utf8')); if(!j.data?.depositId||j.data?.state!=='PENDING_OPERATOR_REVIEW'||j.data?.amount!=='12.0000') process.exit(1); console.log(j.data.depositId);")"

# operator reads queue while deposit is still PENDING_OPERATOR_REVIEW — this is the
# sensitive backoffice read that must produce read-audit rows for the listed deposits.
queue_body="/tmp/minifin-phase03-happy-queue.json"
curl -fsS -H "Authorization: Bearer ${operator_token}" "${base_url}/api/v1/backoffice/manual-ops/deposits" >"${queue_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${queue_body}','utf8')); if(!Array.isArray(j.data) || !j.data.some(d => d.depositId === '${deposit_id}')) process.exit(1);"

# operator approves
curl -fsS -X POST "${base_url}/api/v1/backoffice/manual-ops/deposits/${deposit_id}/decision" \
  -H "Authorization: Bearer ${operator_token}" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVE","reason":"runtime happy path"}' \
  >"${decision_body}"

node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); if(j.data?.state!=='COMPLETED'||!j.data?.journalEntryId) process.exit(1);"

journal_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${decision_body}','utf8')); console.log(j.data.journalEntryId);")"

posting_count="$(wd_psql "select count(*) from ledger.postings where journal_entry_id = '${journal_id}'::uuid;")"
test "${posting_count}" = "2"

debit_total="$(wd_psql "select coalesce(sum(amount), 0) from ledger.postings where journal_entry_id = '${journal_id}'::uuid and side = 'DEBIT';")"
credit_total="$(wd_psql "select coalesce(sum(amount), 0) from ledger.postings where journal_entry_id = '${journal_id}'::uuid and side = 'CREDIT';")"
test "${debit_total}" = "12.0000"
test "${credit_total}" = "12.0000"

# verify ledger account types and codes
debit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${journal_id}'::uuid and p.side = 'DEBIT';")"
test "${debit_code}" = "EXTERNAL_DEPOSIT_CLEARING"
credit_code="$(wd_psql "select a.code from ledger.postings p join ledger.accounts a on a.id = p.account_id where p.journal_entry_id = '${journal_id}'::uuid and p.side = 'CREDIT';")"
test "${credit_code}" = "WALLET_USER:${user_id}"

# wallet summary returns balance 12.0000
curl -fsS -b "${cookie_jar}" "${base_url}/api/v1/wallet" >"${wallet_body}"
node -e "const j=JSON.parse(require('fs').readFileSync('${wallet_body}','utf8')); if(j.data?.balance!=='12.0000'||!Array.isArray(j.data?.deposits)||j.data.deposits[0]?.state!=='COMPLETED') process.exit(1);"

# audit rows exist
created_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.deposit_created' and subject_id = '${deposit_id}'::uuid;")"
approved_audit="$(wd_psql "select count(*) from audit.audit_log where event_type = 'wallet.deposit_approved' and subject_id = '${deposit_id}'::uuid;")"
test "${created_audit}" -ge 1
test "${approved_audit}" -ge 1

# read-audit row exists for the deposit we listed during the pending queue read above
read_audit="$(wd_psql "select count(*) from audit.read_audit_log where resource_type = 'WALLET_DEPOSIT_REQUEST' and resource_id = '${deposit_id}'::uuid;")"
test "${read_audit}" -ge 1

# reconciliation still passes
curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >/tmp/minifin-phase03-happy-recon.json
node -e "const j=JSON.parse(require('fs').readFileSync('/tmp/minifin-phase03-happy-recon.json','utf8')); if(j.data?.balancedJournals!==true||j.data?.imbalancedJournalCount!==0) process.exit(1);"

echo "WLT deposit happy path pass"
