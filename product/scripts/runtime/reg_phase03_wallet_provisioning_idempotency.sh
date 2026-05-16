#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-provisioning-cookies.txt"
user_id="$(wd_register_enduser provisioning "${cookie_jar}")"
parallel_count="${WALLET_PROVISION_PARALLEL:-8}"
status_dir="/tmp/minifin-phase03-provisioning-statuses"

pre_wallet_count="$(wd_psql "select count(*) from wallet.wallet_accounts where user_id = '${user_id}'::uuid;")"
test "${pre_wallet_count}" = "0"
pre_ledger_count="$(wd_psql "select count(*) from ledger.accounts where code = 'WALLET_USER:${user_id}';")"
test "${pre_ledger_count}" = "0"

rm -rf "${status_dir}"
mkdir -p "${status_dir}"

for i in $(seq 1 "${parallel_count}"); do
  (
    body_file="${status_dir}/body-${i}.json"
    status_file="${status_dir}/status-${i}.txt"
    status="$(curl -sS -b "${cookie_jar}" -o "${body_file}" -w "%{http_code}" \
      "${base_url}/api/v1/wallet")"
    echo "${status}" >"${status_file}"
  ) &
done
wait

for i in $(seq 1 "${parallel_count}"); do
  status="$(cat "${status_dir}/status-${i}.txt")"
  if [ "${status}" != "200" ]; then
    echo "GET /api/v1/wallet attempt ${i} returned ${status}" >&2
    cat "${status_dir}/body-${i}.json" >&2 || true
    exit 1
  fi
done

wallet_count="$(wd_psql "select count(*) from wallet.wallet_accounts where user_id = '${user_id}'::uuid;")"
test "${wallet_count}" = "1"
ledger_count="$(wd_psql "select count(*) from ledger.accounts where code = 'WALLET_USER:${user_id}';")"
test "${ledger_count}" = "1"

ledger_id="$(wd_psql "select id from ledger.accounts where code = 'WALLET_USER:${user_id}';")"
wallet_ledger_id="$(wd_psql "select ledger_account_id from wallet.wallet_accounts where user_id = '${user_id}'::uuid;")"
test "${wallet_ledger_id}" = "${ledger_id}"

for i in $(seq 1 "${parallel_count}"); do
  node -e "
const body = JSON.parse(require('fs').readFileSync('${status_dir}/body-${i}.json', 'utf8'));
if (body.data?.currency !== 'EUR') process.exit(1);
if (body.data?.ledgerAccountId !== '${ledger_id}') process.exit(1);
"
done

echo "WLT wallet provisioning idempotency pass"
