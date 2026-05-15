# Mini Fintech Platform

Учебно-практическая production-grade fintech-система: digital wallet + merchant payment processor + double-entry ledger в одной кодовой базе.

Проект построен по методу `project-kit-short` (`../project-kit-short/`).

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

Stage: UI prototype baseline in progress before Phase 02 Slice 02 product code. `product/` skeleton and end-user identity foundation are runtime-verified; local compose stack is currently up. `planning/01_business_requirements.md` — APPROVED v0.3. `planning/02_user_journeys.md` — APPROVED v0.2. `planning/03_functional_requirements.md` — APPROVED v0.2. `planning/04_architecture.md` — APPROVED v0.2. `planning/05_tech_stack.md` — APPROVED v0.4. `planning/06_implementation_guide.md` — APPROVED v0.2. `planning/design-details/` — APPROVED v0.2. `planning/runtime_checklists.md` — APPROVED v0.1. `planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md` — APPROVED v0.1. `planning/implementation-slices/phase_02_slice_01_identity_foundation_planning.md` — APPROVED v0.1. `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — DRAFT v0.1. UI prototype artifacts: `prototypes/ui/01_app_shell_cross_surface.html`, `prototypes/ui/02_merchant_auth.html`. **См. `CURRENT.md` для handoff state.**
