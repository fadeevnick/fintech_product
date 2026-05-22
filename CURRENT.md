# CURRENT

Last updated: 2026-05-22.

## Current State

`REC-04`, `AUD-99` (code + script), and `LDG-99` (script) are implemented and committed.

Read first:
- `README.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`

## What Was Done This Session

- **REC-05** — cut register final audit pass (committed dc85254 → 4bfd167).
- **REC-04** — Grafana dashboards provisioned (`service_health.json`, `business_metrics.json`).
- **AUD-99** — read-audit added to KYC case detail and AML alert detail; sweep script written.
- **LDG-99** — ledger invariant sweep script written.

## Remaining Open Checks

Needs a live stack for runtime verification:
- `LDG-99` — run `reg_phase11_ledger_invariant_sweep.sh` against a seeded stack.
- `AUD-99` — run `reg_phase11_audit_coverage_sweep.sh` against a seeded stack; also needs new Platform image (AmlService + KycBackofficeService changed).
- `REC-04` — start the stack, open Grafana at port 3003, confirm both dashboards load.

Blocked on external credentials:
- `MRC-01` — Stripe Connect sandbox credentials.
- `KYC-01` — Sumsub sandbox credentials.

Explicitly deferred (separate track):
- Phase 10 frontend: `UI-02`..`UI-05`, `UI-99`.

## Next Planned Step

Runtime verification of LDG-99 and AUD-99 on a live stack, or move to Phase 10 frontend.
Frontend/UI work is handled separately and should not be touched unless explicitly requested.
