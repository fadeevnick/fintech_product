#!/usr/bin/env bash
# LDG-99 — Ledger cross-phase invariant sweep.
# Runs against a live stack after all features have been exercised.
# Requires: COMPOSE_FILE and COMPOSE_PROJECT_NAME pointing at a running stack,
# or PLATFORM_CURL_CONTAINER_NETWORK if host HTTP is unreliable.
set -euo pipefail

compose_file="${COMPOSE_FILE:-product/deploy/docker-compose.yml}"
base_url="${PLATFORM_BASE_URL:-http://localhost:8081}"

platform_psql() {
  docker compose -f "${compose_file}" exec -T platform-db \
    psql -U platform -d platform -Atc "$1"
}

echo "=== LDG-99 ledger invariant sweep ==="

# ── 1. Reconciliation API ────────────────────────────────────────────────────

echo "-- 1. Reconciliation API (LDG-05 re-run)"
recon_body="/tmp/minifin-ldg99-recon-$$.json"

if test -n "${PLATFORM_CURL_CONTAINER_NETWORK:-}"; then
  docker run --rm --network "${PLATFORM_CURL_CONTAINER_NETWORK}" -v /tmp:/tmp \
    curlimages/curl:8.10.1 -fsS "${base_url}/internal/ledger/runtime/reconciliation" >"${recon_body}"
else
  curl -fsS "${base_url}/internal/ledger/runtime/reconciliation" >"${recon_body}"
fi

node -e "
  const r = JSON.parse(require('fs').readFileSync('${recon_body}','utf8'));
  if (r.data?.balancedJournals !== true) { console.error('balancedJournals not true'); process.exit(1); }
  if (Number(r.data?.journalCount ?? 0) < 1) { console.error('journalCount < 1'); process.exit(1); }
  console.log('  [ok] balancedJournals=true journalCount=' + r.data.journalCount);
"

# ── 2. Every journal has >= 2 postings and debit_total == credit_total ───────

echo "-- 2. All journals individually balanced (DB cross-check)"
imbalanced="$(platform_psql "
  with sums as (
    select j.id, j.journal_type,
      coalesce(sum(p.amount) filter (where p.side = 'DEBIT'), 0) as dr,
      coalesce(sum(p.amount) filter (where p.side = 'CREDIT'), 0) as cr,
      count(p.id) as posting_count
    from ledger.journal_entries j
    left join ledger.postings p on p.journal_entry_id = j.id
    group by j.id, j.journal_type
  )
  select count(*) from sums where dr <> cr or posting_count < 2;
")"
test "${imbalanced}" = "0" || { echo "LDG-99 FAIL: ${imbalanced} imbalanced journal(s) found"; exit 1; }
echo "  [ok] all journals balanced, all have >= 2 postings"

# ── 3. Global debit/credit parity (sum of all postings nets to zero) ─────────

echo "-- 3. Global net balance across all postings"
global_net="$(platform_psql "
  select coalesce(
    sum(case when side = 'DEBIT' then amount else -amount end),
    0
  )
  from ledger.postings;
")"
node -e "
  const net = parseFloat('${global_net}');
  if (Math.abs(net) > 0.001) { console.error('global net != 0: ' + net); process.exit(1); }
  console.log('  [ok] global net balance = ' + net);
"

# ── 4. No account has a balance on the wrong side of zero ───────────────────

echo "-- 4. Wallet account balances are non-negative (credit-normal accounts)"
negative_wallets="$(platform_psql "
  select count(*) from ledger.account_balances
  where code like 'WALLET_USER:%' and balance < 0;
")"
test "${negative_wallets}" = "0" || { echo "LDG-99 FAIL: ${negative_wallets} negative wallet balance(s)"; exit 1; }
echo "  [ok] no negative WALLET_USER balances"

negative_holds="$(platform_psql "
  select count(*) from ledger.account_balances
  where code like 'WALLET_WITHDRAW_HOLD:%' and balance < 0;
")"
test "${negative_holds}" = "0" || { echo "LDG-99 FAIL: ${negative_holds} negative hold balance(s)"; exit 1; }
echo "  [ok] no negative WALLET_WITHDRAW_HOLD balances"

# ── 5. Journal type distribution ─────────────────────────────────────────────

echo "-- 5. Journal type distribution"
platform_psql "
  select journal_type, count(*) as journals, sum(
    (select sum(amount) from ledger.postings p where p.journal_entry_id = j.id and p.side = 'DEBIT')
  ) as total_debit_eur
  from ledger.journal_entries j
  group by journal_type
  order by journals desc;
" | while IFS='|' read -r jtype cnt total; do
  echo "  journal_type=${jtype}  count=${cnt}  total_debit=${total}"
done

# ── 6. Total journal and posting counts ──────────────────────────────────────

echo "-- 6. Totals"
journal_count="$(platform_psql "select count(*) from ledger.journal_entries;")"
posting_count="$(platform_psql "select count(*) from ledger.postings;")"
echo "  journals=${journal_count}  postings=${posting_count}"

test "${journal_count}" -ge 1 || { echo "LDG-99 FAIL: no journals found"; exit 1; }
test "${posting_count}" -ge 2 || { echo "LDG-99 FAIL: fewer than 2 postings found"; exit 1; }

echo ""
echo "LDG-99 ledger invariant sweep pass journals=${journal_count} postings=${posting_count}"
