#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib_phase04_public_api.sh
source "${SCRIPT_DIR}/lib_phase04_public_api.sh"
# shellcheck source=lib_phase05_vault_card.sh
source "${SCRIPT_DIR}/lib_phase05_vault_card.sh"

auth_base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"

auth_public_authorize() {
  local api_key="$1" intent_id="$2" idem_key="$3" body_json="$4" out_body="$5"
  pa_rm "${out_body}"
  pa_curl -sS -o "${out_body}" -w "%{http_code}" \
    -X POST "${auth_base_url}/v1/payment_intents/${intent_id}/authorize" \
    -H "Authorization: Bearer ${api_key}" \
    -H "Idempotency-Key: ${idem_key}" \
    -H "Content-Type: application/json" \
    -d "${body_json}"
}

auth_extract_api_key() {
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const key=j.data?.apiKey ?? j.data?.key; if(!key) process.exit(1); console.log(key);" "$1"
}

auth_extract_payment_intent_id() {
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(!j.data?.id) process.exit(1); console.log(j.data.id);" "$1"
}

auth_extract_card_id() {
  node -e "const j=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); if(!j.data?.card?.id) process.exit(1); console.log(j.data.card.id);" "$1"
}

auth_card_token_for_card_id() {
  local card_id="$1"
  p05_issuer_psql "select card_token from issuer.cards where id = '${card_id}'::uuid;"
}

auth_wallet_for_card_id() {
  local card_id="$1"
  p05_issuer_psql "select wallet_account_id from issuer.cards where id = '${card_id}'::uuid;"
}

auth_seed_wallet_balance() {
  local wallet_id="$1" amount="$2"
  p05_platform_psql "with w as (select wa.user_id, wa.ledger_account_id from wallet.wallet_accounts wa where wa.id='${wallet_id}'::uuid), ext as (select id from ledger.accounts where code='EXTERNAL_DEPOSIT_CLEARING') select ledger.post_journal(gen_random_uuid(), 'CARD_AUTH_TEST_DEPOSIT', 'CARD_AUTH_TEST_DEPOSIT', gen_random_uuid(), 'EUR', 'Card authorization test funding', jsonb_build_array(jsonb_build_object('accountId',(select id from ext)::text,'side','DEBIT','amount','${amount}'), jsonb_build_object('accountId',(select ledger_account_id from w)::text,'side','CREDIT','amount','${amount}')));" >/dev/null
}

auth_count_card_holds() {
  local token="$1"
  p05_issuer_psql "select count(*) from issuer.holds where card_token='${token}';"
}

auth_count_platform_card_hold_journals() {
  p05_platform_psql "select count(*) from ledger.journal_entries where journal_type='CARD_AUTHORIZATION_HOLD';"
}
