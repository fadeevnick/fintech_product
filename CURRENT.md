# CURRENT

Last updated: 2026-05-22.

## Current State

Backend/runtime and frontend tracks are effectively closed. Remaining open work is vendor-blocked only:
- All 3 SPAs build clean with no TypeScript errors.
- All 6 merchant/end-user screens that were previously blocked on missing backend read endpoints are now implemented and runtime-verified.
- All blocking backend contract gaps (M-01, M-02, M-03) have been resolved by Agent A (backend) and wired by Agent B (frontend).
- 6 UI defects fixed during the polish pass (see `planning/frontend_agent_b_findings.md`).
- Backoffice dispute list/detail and audit-log feed are now runtime-verified on a fresh temporary stack built from the current `platform` `bootJar`.

Passed on a live stack:
- `REC-02`
- `REC-04`
- `REC-05`
- `AUD-99`
- `LDG-99`

Read first:
- `README.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`

## What Was Done This Session (final closure pass)

- **Backoffice runtime closure completed** on a fresh temporary stack (`mfp-bofrt`) with a current `platform` image built from the local `bootJar`:
  - `GET /api/v1/backoffice/disputes?limit=25` returned live dispute rows;
  - `GET /api/v1/backoffice/disputes/{id}` returned live dispute detail plus evidence submission and wrote a `backoffice_chargeback_dispute_detail` read-audit row;
  - `POST /api/v1/backoffice/disputes/{id}/arbitration` succeeded from `EVIDENCE_SUBMITTED -> WON`;
  - `GET /api/v1/backoffice/read-audit/probe/{uuid}` succeeded;
  - `POST /api/v1/backoffice/actor-controls` succeeded for a live end-user actor;
  - `GET /api/v1/backoffice/audit-log` succeeded for `stream=ALL`, `stream=AUDIT_LOG`, and `stream=READ_AUDIT_LOG`, and wrote `backoffice_audit_log_view` read-audit rows.
- **10 UI defects fixed** across both SPAs (D-07 through D-16, see findings doc):
  - Critical: `DepositPage` now shows a proper success panel for non-SoF deposits (was silently re-rendering the form)
  - `fmtAmount` helper applied to all amount columns in both SPAs (was showing raw `100.0000` floats)
  - StatGrid in Settlements and Disputes now hidden during initial load (was showing 0/0.00/0 while fetching)
  - `acceptDispute` error now surfaced to UI instead of silently swallowed
  - WalletPage loading/error double-titles resolved
  - CardsPage inline styles replaced with CSS class spacing
  - Unused `logout` destructure removed from `EnduserLayout` and `MerchantLayout`
- **Both builds clean**: `tsc --noEmit && vite build` — 0 errors, 0 warnings

## What Was Done Previously (frontend polish pass — prior session)

- **Runtime environment** — Kafka heartbeat issue caused deposit/transfer endpoints to hang (6h uptime). Restarted Kafka + platform; full deposit+SoF flow verified afterward.
- **6 defects fixed in spa-enduser and spa-merchant** (see findings doc for details):
  - `depositTone` state names corrected (`COMPLETED`/`PENDING_OPERATOR_REVIEW`/`REQUESTED`)
  - SoF threshold text corrected: EUR 500 → EUR 15,000 (actual backend threshold)
  - `evTone` corrected to uppercase status strings (DELIVERED/FAILED/DLQ/PENDING)
  - `piTone` corrected: SUCCEEDED → SETTLED
  - PaymentsPage "Succeeded" stat corrected to "Settled"
  - Webhook events `Promise.all` decoupled; events URL fixed to require `?status=` param (backend constraint: no-param call returns 500)
- **M-01 wired**: `SettlementsPage` now fetches `GET /api/v1/merchant/settlements` — real data table with stats, runtime-verified (empty list OK)
- **M-02 wired**: `DisputesPage` now fetches `GET /api/v1/merchant/disputes` — real data table + evidence submission + accept action, runtime-verified (empty list OK)
- **M-03 wired**: `CardsPage` now fetches `GET /api/v1/cards` — real card list with "Issue card" button in panel header, runtime-verified (card appeared in list after issue)
- **Full runtime smoke of all flows** against live backend via `VITE_PLATFORM_PROXY_TARGET=http://172.22.0.21:8080`:
  - end-user: register → verify → login → wallet → deposit (small + SoF 15k) → SoF submit → card issue → card list
  - merchant: register → verify → login → onboarding → API key create → webhook endpoints list → webhook events (status filter) → payments list → settlements list → disputes list
  - KYC: `POST /api/v1/kyc/start` returns 503 `sumsub_not_configured` — correctly surfaced as error in UI (vendor-blocked, as expected)

## Remaining Open Checks

Blocked on external credentials:
- `REC-03` — vendor reconciliation depends on real vendor credentials.
- `MRC-01` — Stripe Connect sandbox credentials.
- `KYC-01` — Sumsub sandbox credentials.

Remaining contract limitations (not bugs, no backend fix needed):
- `C-01` — Webhook events endpoint requires `?status=` filter; UI handles with status dropdown.
- `C-03` — Merchant onboarding coarse-status only (no sub-step breakdown).
- `C-04` — API key scopes not in backend contract; UI shows label-only.

## Next Planned Step

No internal implementation work remains on the local stack. The next step is external: obtain vendor credentials and close the blocked integrations.

Open items (all external or separate track):
1. Vendor credentials (`REC-03`, `MRC-01`, `KYC-01`) — cannot be resolved without external accounts.
