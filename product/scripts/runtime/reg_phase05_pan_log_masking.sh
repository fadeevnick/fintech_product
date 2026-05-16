#!/usr/bin/env bash
set -euo pipefail

source "$(dirname "$0")/lib_phase05_vault_card.sh"

tag="pan-logs"
cookie_jar="/tmp/minifin-phase05-${tag}-cookies.txt"
issue_body="/tmp/minifin-phase05-${tag}-issue.json"
detokenize_body="/tmp/minifin-phase05-${tag}-detokenize.json"
logs_body="/tmp/minifin-phase05-${tag}-logs.txt"

p05_register_enduser "${tag}" "${cookie_jar}" >/tmp/minifin-phase05-${tag}-user.txt
p05_issue_card "${cookie_jar}" "${issue_body}"
card_id="$(node -e "const j=JSON.parse(require('fs').readFileSync('${issue_body}','utf8')); console.log(j.data.card.id);")"
card_token="$(p05_issuer_psql "select card_token from issuer.cards where id = '${card_id}'")"
p05_vault_detokenize "${card_token}" issuer "${detokenize_body}"
pan="$(node -e "const j=JSON.parse(require('fs').readFileSync('${detokenize_body}','utf8')); console.log(j.data.pan);")"

docker compose -f "${compose_file}" logs --no-color platform issuer vault >"${logs_body}"
if grep -Fq "${pan}" "${logs_body}"; then
  echo "VLT-03 fail: raw PAN appeared in service logs" >&2
  exit 1
fi

echo "VLT-03 pass: raw PAN for card ${card_id} is absent from platform/issuer/vault general logs."
