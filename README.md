# Mini Fintech Platform

Учебно-практическая production-grade fintech-система: digital wallet + merchant payment processor + double-entry ledger в одной кодовой базе.

Проект построен по canonical kit: `/home/nickf/Documents/sre_projects/project-kit-short/project-method`.

## Navigation

- `planning/` — вся методология: design chain (01..05), implementation guide (06), implementation-near pack, runtime checks, status, evidence.
- `prototypes/ui/` — standalone HTML UI prototypes from the UI/UX prototype agent.
- `product/` — реальный код, миграции, verification scripts, deploy конфиги. Появляется, когда начинается первый coding slice.
- `CURRENT.md` — handoff state, создаётся только при незавершённой работе и удаляется по её завершении.

## Read Order

1. `CURRENT.md` (если есть).
2. `planning/01_business_requirements.md` … `planning/05_tech_stack.md` — approved/draft baseline inputs.
3. `planning/06_implementation_guide.md` (когда появится) — phased implementation plan.
4. `planning/design-details/` (когда появится) — implementation-near planning pack.
5. `planning/implementation_status.md`, `planning/runtime_evidence_log.md` (когда появятся) — factual state.

## Status

Stage: Phase 06 Slice 06 acquirer settlement projection is implemented and runtime-verified (`SET-03`); Phase 08 Slice 02 AML structuring alert remains approved planning-only and ready for implementation. Phase 06 Slice 05 settlement fee split and Phase 08 Slice 01 AML velocity alert foundation are implemented and runtime-verified (`SET-02`, `AML-01`). `SET-01`, `PAY-06`, `SNX-01`, `SNX-02`, `WBH-01`, `WBH-02`, `WBH-03`, `KYC-02` and `KYC-03` have runtime evidence; `KYC-01` is partial until real Sumsub sandbox credentials are provided. Phase 04 merchant API keys/public API idempotency, Stripe webhook, merchant dashboard payment reads/webhook config, and Phase 05 Vault/card issuance plus card authorization backend/runtime sub-scopes are implemented and runtime-verified (`MRC-02`, `MRC-03`, `MRC-04`, `MRC-05`, `PAY-01`, `PAY-02`, `PAY-03`, `PAY-04`, `PAY-05`, `VLT-01`, `VLT-02`, `VLT-03`, `AUD-01`, `LDG-05` regression pass where applicable). Stripe Connect onboarding start (`MRC-01`) remains blocked on real Stripe sandbox credentials and is not claimed. Frontend checkpoints remain gated by accepted standalone HTML prototypes; merchant UI prototype baseline is complete through `MDB-UI-07`. `planning/implementation_status.md` and `planning/runtime_evidence_log.md` contain factual implementation/evidence state.
