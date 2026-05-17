# Agent Task — Phase 07 Slice 01 Planning

You are working in `mini-fintech-platform` as an implementation-planning agent.

## Context To Read First

Read these files before changing anything:

- `project-method/`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/06_implementation_guide.md`
- `planning/runtime_checklists.md`
- `planning/design-details/access_matrix.md`
- `planning/design-details/api_contracts.md`
- `planning/design-details/schema_drafts.md`
- `planning/design-details/state_machines.md`
- `planning/design-details/test_matrix.md`
- `planning/design-details/ui_prototypes.md`

## Task

Create a planning-only slice for:

```text
Phase 07 Slice 01 — KYC/Sumsub Foundation
```

Target file:

```text
planning/implementation-slices/phase_07_slice_01_kyc_sumsub_foundation_planning.md
```

The planning note must define the first narrow backend/runtime slice for Phase 07 compliance work.

## Required Scope

The slice should cover `KYC-01` and `KYC-02` only:

- end-user starts KYC through a real Sumsub sandbox-style integration boundary;
- persisted KYC applicant/session/request state;
- Sumsub webhook signature verification;
- Sumsub webhook vendor event id idempotency;
- KYC state machine transition for a minimal happy path and review/consider path;
- retained runtime proof for start flow and webhook verification/idempotency.

If real Sumsub sandbox credentials are required, the planning note must explicitly define:

- what is blocked without credentials;
- what local deterministic verification can still prove without faking the vendor;
- which checks must not be claimed until real credentials exist.

## Important Boundaries

- This is planning-only. Do not edit product code, SQL migrations, runtime scripts or frontend code.
- Do not claim runtime evidence.
- Do not mark `KYC-01` or `KYC-02` complete.
- Do not touch `AGENT_TASK.md` except if your own final instructions require no change; this file is temporary orchestration context and will not be merged into final `orchestration`.
- Do not implement OpenSanctions, AML, account freezes, Source of Funds, chargebacks, frontend UI, document preview UI, or case attachment upload unless explicitly scoped as a minimal DB placeholder for KYC.
- Do not fake successful real-vendor KYC checks.

## Planning Note Must Include

- Status line: planning draft, not approved, no product code implemented.
- Decision: exact recommended slice boundary.
- Why this slice is next and how it can proceed while UI prototypes continue in parallel.
- Exact backend/runtime scope.
- Explicit in-scope and out-of-scope lists.
- Service/module boundary for current implementation.
- Expected DB/model changes and migration version recommendation.
- End-user API contract for starting KYC.
- Sumsub webhook route contract, signature verification, timestamp tolerance if applicable, and idempotency behavior.
- KYC state machine mapping for the first slice.
- Runtime verification plan for `KYC-01` and `KYC-02`.
- Targeted regression checks that should remain green, especially auth/session, read-audit where applicable, and existing wallet/card/payment smoke checks if they are touched.
- Documentation/evidence files that the later implementation agent must update.
- Next planned step after the planning artifact.

## Commit

Make one logical commit on your branch with a clear message, for example:

```text
docs: plan kyc sumsub foundation slice
```
