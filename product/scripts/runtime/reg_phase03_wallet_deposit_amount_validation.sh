#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase03_wallet_deposit.sh"

cookie_jar="/tmp/minifin-phase03-amount-validation-cookies.txt"
user_id="$(wd_register_enduser amount-validation "${cookie_jar}")"

probe_invalid_amount() {
  local tag="$1"
  local amount="$2"
  local expected_code="$3"
  local body="/tmp/minifin-phase03-amount-validation-${tag}.json"
  local status

  status="$(curl -sS -b "${cookie_jar}" -o "${body}" -w "%{http_code}" -X POST \
    "${base_url}/api/v1/deposits" \
    -H "Content-Type: application/json" \
    -d "{\"amount\":\"${amount}\",\"currency\":\"EUR\"}")"
  test "${status}" = "400"
  grep -q "\"code\":\"${expected_code}\"" "${body}"
}

probe_invalid_amount "scale-overflow" "1.12345" "invalid_amount"
probe_invalid_amount "non-numeric" "abc" "invalid_amount"
probe_invalid_amount "zero" "0" "invalid_amount"
probe_invalid_amount "negative" "-1.0000" "invalid_amount"
probe_invalid_amount "too-large" "1000000.0001" "invalid_amount"

deposit_count="$(wd_psql "select count(*) from wallet.deposit_requests where user_id = '${user_id}'::uuid;")"
test "${deposit_count}" = "0"

echo "WLT deposit amount validation pass"
