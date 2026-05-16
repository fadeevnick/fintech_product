#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/lib_phase05_vault_card.sh"

tag="pan-isolation"
cookie_jar="/tmp/minifin-phase05-${tag}-cookies.txt"
issue_body="/tmp/minifin-phase05-${tag}-issue.json"

user_id="$(p05_register_enduser "${tag}" "${cookie_jar}")"
p05_issue_card "${cookie_jar}" "${issue_body}"

card_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${issue_body}','utf8')); if(!j.data?.card?.id||j.data.card.cardToken||!j.data.card.last4) process.exit(1); console.log(j.data.card.id);")"
last4="$(node -e "const j=JSON.parse(require('fs').readFileSync('${issue_body}','utf8')); console.log(j.data.card.last4);")"

issuer_row="$(p05_issuer_psql "select card_token || '|' || last4 || '|' || state from issuer.cards where id = '${card_id}'")"
card_token="${issuer_row%%|*}"
issuer_last4="$(echo "${issuer_row}" | cut -d'|' -f2)"
issuer_state="$(echo "${issuer_row}" | cut -d'|' -f3)"

test "${issuer_last4}" = "${last4}"
test "${issuer_state}" = "ACTIVE"

vault_count="$(p05_vault_psql "select count(*) from vault.card_tokens where card_token = '${card_token}' and pan_last4 = '${last4}' and length(encode(pan_ciphertext, 'hex')) > 0")"
test "${vault_count}" = "1"

platform_pan_count="$(p05_platform_psql "select count(*) from information_schema.columns where table_schema not in ('pg_catalog','information_schema') and lower(column_name) in ('pan','pan_ciphertext','cvv','cvv_hash')")"
issuer_pan_count="$(p05_issuer_psql "select count(*) from information_schema.columns where table_schema not in ('pg_catalog','information_schema') and lower(column_name) in ('pan','pan_ciphertext','cvv','cvv_hash')")"
test "${platform_pan_count}" = "0"
test "${issuer_pan_count}" = "0"

echo "VLT-01 pass: card ${card_id} for user ${user_id} stores PAN only in Vault; issuer keeps token/last4 metadata only."
