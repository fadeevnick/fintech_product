# Agent Task — Implement Phase 05 Slice 01

Implement the approved Phase 05 Slice 01 backend/runtime scope:
`planning/implementation-slices/phase_05_slice_01_vault_card_issuance_planning.md`.

Task:
- Add Vault tokenization storage and APIs so full PAN is persisted only in `vault`.
- Add restricted Vault detokenize API for `service:issuer` only, with append-only audit rows.
- Add Issuer card records and minimal card issuance path storing only token/last4/expiration/BIN metadata.
- Add Platform end-user `POST /api/v1/cards` entrypoint that delegates to Issuer and returns safe metadata only.
- Wire narrow service-authenticated Platform -> Issuer and Issuer -> Vault calls using existing project patterns.
- Add PAN masking verification so raw PAN does not appear in general service logs.
- Add retained runtime scripts for `VLT-01`, `VLT-02`, and `VLT-03`, plus targeted foundational regressions listed in the planning note.
- Update `planning/runtime_evidence_log.md`, `planning/implementation_status.md`, `CURRENT.md`, and `product/README.md` only with factual implementation/runtime state.

Ownership:
- Own `vault` and `issuer` service implementation for this slice.
- Own Platform card issuance code only; avoid merchant dashboard/webhook config areas.
- If a Platform DB migration is needed, reserve `V13__end_user_card_issuance.sql`; Phase 04 Slice 03 owns `V12`.
- Do not edit frontend SPA files, `prototypes/ui/**`, merchant dashboard payment/webhook config code, or outbound webhook delivery.
- Do not implement authorization, capture, settlement, refunds, chargebacks, `PAY-04`, or `PAY-05`.

Done when:
- End-user card issuance works through retained runtime checks.
- `VLT-01`, `VLT-02`, and `VLT-03` have retained scripts and evidence.
- No raw PAN is stored outside Vault or logged unmasked.
- One logical implementation commit is created.
