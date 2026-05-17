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

Stage: Phase 04 Slice 01 merchant API keys/public API idempotency, Phase 04 Slice 02 Stripe webhook and Phase 04 Slice 03 merchant dashboard payment reads/webhook config backend/runtime sub-scopes are implemented and runtime-verified (`MRC-02`, `MRC-03`, `MRC-04`, `MRC-05`, `PAY-01`, `PAY-02`, `PAY-03`, `AUD-01`, `LDG-05` regression pass where applicable). Stripe Connect onboarding start (`MRC-01`) remains blocked on real Stripe sandbox credentials and is not claimed. Frontend checkpoints remain gated by accepted standalone HTML prototypes. UI prototype baseline continues in parallel. Local compose stack is currently up. `planning/01_business_requirements.md` — APPROVED v0.3. `planning/02_user_journeys.md` — APPROVED v0.2. `planning/03_functional_requirements.md` — APPROVED v0.2. `planning/04_architecture.md` — APPROVED v0.2. `planning/05_tech_stack.md` — APPROVED v0.4. `planning/06_implementation_guide.md` — APPROVED v0.2. `planning/design-details/` — APPROVED v0.2. `planning/runtime_checklists.md` — APPROVED v0.1. `planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md` — APPROVED v0.1. `planning/implementation-slices/phase_02_slice_01_identity_foundation_planning.md` — APPROVED v0.1. `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_02_slice_03_backoffice_oidc_rbac_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_02_slice_04_read_audit_account_controls_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_03_slice_01_ledger_foundation_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_03_slice_02_wallet_manual_deposit_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_03_slice_04_wallet_internal_transfer_planning.md` — backend/runtime sub-scope executed v0.2. `planning/implementation-slices/phase_04_slice_01_api_keys_idempotency_planning.md` — backend/runtime sub-scope executed v0.3. `planning/implementation-slices/phase_04_slice_02_stripe_connect_webhooks_planning.md` — webhook-only backend/runtime sub-scope executed v0.1; `MRC-01` blocked on Stripe sandbox credentials. `planning/implementation-slices/phase_04_slice_03_merchant_dashboard_payments_planning.md` — backend/runtime sub-scope executed v0.1. UI prototype artifacts: `prototypes/ui/01_app_shell_cross_surface.html`, `prototypes/ui/02_merchant_auth.html`, `prototypes/ui/03_enduser_auth.html`, `prototypes/ui/04_backoffice_oidc_login.html`, `prototypes/ui/05_backoffice_work_queue_home.html`, `prototypes/ui/06_backoffice_manual_deposits.html`, `prototypes/ui/07_backoffice_manual_withdrawals.html`, `prototypes/ui/08_backoffice_kyc_queue.html`, `prototypes/ui/09_backoffice_aml_alerts.html`, `prototypes/ui/10_backoffice_sanctions_hits.html`, `prototypes/ui/11_backoffice_chargeback_arbitration.html`, `prototypes/ui/12_backoffice_audit_log.html`, `prototypes/ui/13_enduser_kyc_status.html`, `prototypes/ui/14_enduser_wallet_home.html`, `prototypes/ui/15_enduser_deposit_request.html`, `prototypes/ui/16_enduser_transfer.html`, `prototypes/ui/17_enduser_cards.html`. **См. `CURRENT.md` для handoff state.**
