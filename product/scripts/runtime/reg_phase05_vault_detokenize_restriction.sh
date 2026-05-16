#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/lib_phase05_vault_card.sh"

tag="detokenize"
cookie_jar="/tmp/minifin-phase05-${tag}-cookies.txt"
issue_body="/tmp/minifin-phase05-${tag}-issue.json"
issuer_body="/tmp/minifin-phase05-${tag}-issuer-detokenize.json"
platform_body="/tmp/minifin-phase05-${tag}-platform-detokenize.json"

p05_register_enduser "${tag}" "${cookie_jar}" >/tmp/minifin-phase05-${tag}-user.txt
p05_issue_card "${cookie_jar}" "${issue_body}"
card_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${issue_body}','utf8')); console.log(j.data.card.id);")"
card_token="$(p05_issuer_psql "select card_token from issuer.cards where id = '${card_id}'")"

p05_vault_detokenize "${card_token}" issuer "${issuer_body}"
pan="$(node -e "const j=JSON.parse(require('fs').readFileSync('${issuer_body}','utf8')); if(!/^\\d{16}$/.test(j.data?.pan||'')) process.exit(1); console.log(j.data.pan);")"
http_code="$(p05_vault_detokenize "${card_token}" platform "${platform_body}")"
test "${http_code}" = "403"
node -e "const j=JSON.parse(require('fs').readFileSync('${platform_body}','utf8')); if(j.data||j.errors?.[0]?.code!=='service_auth_denied') process.exit(1);"

audit_allowed="$(p05_vault_psql "select count(*) from vault.detokenize_audit_log where card_token = '${card_token}' and caller_service = 'issuer' and outcome = 'ALLOWED'")"
audit_denied="$(p05_vault_psql "select count(*) from vault.detokenize_audit_log where card_token = '${card_token}' and caller_service = 'platform' and outcome = 'DENIED'")"
test "${audit_allowed}" -ge 1
test "${audit_denied}" -ge 1

echo "VLT-02 pass: issuer detokenized ${card_token} (${pan:0:6}******${pan: -4}) with audit; non-issuer denied with audit."
