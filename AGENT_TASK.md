# Agent Task — Implement Phase 04 Slice 03

Implement the approved Phase 04 Slice 03 backend/runtime scope:
`planning/implementation-slices/phase_04_slice_03_merchant_dashboard_payments_planning.md`.

Task:
- Add merchant dashboard payment-intent list/detail read APIs over existing `merchant.payment_intents`.
- Add persisted merchant webhook endpoint configuration CRUD under `/api/v1/merchant/**`.
- Enforce merchant scoping, cross-merchant `404` behavior, and merchant role rules (`merchant_admin` writes; `merchant_member` read-only if supported by current RBAC conventions).
- Keep API key lifecycle semantics intact; only refine safe non-secret list metadata if needed.
- Add retained runtime scripts for `MRC-04` and `MRC-05`, plus targeted regressions for `MRC-03` / `PAY-01..PAY-03` if touched.
- Update `planning/runtime_checklists.md`, `planning/runtime_evidence_log.md`, `planning/implementation_status.md`, `CURRENT.md`, and `product/README.md` only with factual implementation/runtime state.

Ownership:
- Own Platform merchant dashboard/payment/webhook config code only.
- Own Platform DB migration `V12__merchant_dashboard_read_shell.sql`.
- Do not edit `issuer`, `vault`, `acquirer`, `network`, frontend SPA files, or `prototypes/ui/**`.
- Do not implement Stripe Connect onboarding, payment authorization/capture/refund/settlement, or outbound webhook delivery.

Done when:
- New endpoints work through runtime checks.
- `MRC-04` and `MRC-05` have retained scripts and evidence.
- No `WBH-01..WBH-03` or `MRC-01` claim is made.
- One logical implementation commit is created.
