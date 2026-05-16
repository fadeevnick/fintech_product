#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase02_backoffice_keycloak.sh"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-withdraw-insufficient-cookies.txt"
denied_body="/tmp/minifin-phase03-withdraw-insufficient-denied.json"

kc_seed_backoffice_realm
operator_token="$(kc_backoffice_token operator)"

user_id="$(wd_register_enduser withdraw-insufficient "${cookie_jar}")"
wd_fund_wallet "${cookie_jar}" "5.0000" "${operator_token}" "withdraw-insufficient" >/tmp/minifin-phase03-withdraw-insufficient-funded.txt

status="$(curl -sS -b "${cookie_jar}" -o "${denied_body}" -w "%{http_code}" -X POST \
  "${base_url}/api/v1/withdrawals" \
  -H "Content-Type: application/json" \
  -d '{"amount":"8.0000","currency":"EUR"}')"
test "${status}" = "409"
grep -q '"code":"insufficient_funds"' "${denied_body}"

withdrawal_count="$(wd_psql "select count(*) from wallet.withdraw_requests where user_id = '${user_id}'::uuid;")"
test "${withdrawal_count}" = "0"
wallet_balance="$(wd_psql "select balance from ledger.account_balances where code = 'WALLET_USER:${user_id}';")"
test "${wallet_balance}" = "5.0000"

echo "WLT withdraw insufficient funds pass"
