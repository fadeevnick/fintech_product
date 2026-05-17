# Agent Task — Phase 06 Slice 02 Planning

You are working in `mini-fintech-platform` as an implementation-planning agent.

## Context To Read First

Read these files before changing anything:

- `project-method/`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/06_implementation_guide.md`
- `planning/runtime_checklists.md`
- `planning/design-details/test_matrix.md`
- `planning/design-details/schema_drafts.md`
- `planning/design-details/api_contracts.md`
- `planning/implementation-slices/phase_06_slice_01_outbound_webhook_delivery_planning.md`

## Task

Create a planning-only slice for:

```text
Phase 06 Slice 02 — Outbound Webhook Retry and DLQ
```

Target file:

```text
planning/implementation-slices/phase_06_slice_02_outbound_webhook_retry_dlq_planning.md
```

The planning note must define the next narrow backend/runtime slice after `WBH-01`.

## Required Scope

The slice should cover `WBH-02` only:

- failed outbound webhook delivery retries;
- persisted retry schedule / attempt state;
- transition to terminal `DLQ` after configured retry exhaustion;
- retained runtime proof that a failing receiver is retried and eventually moved to DLQ.

Keep `WBH-03` replay explicitly out of scope for this slice unless the existing code makes replay trivial without broadening the slice. Recommended default: defer `WBH-03` to Phase 06 Slice 03.

## Important Boundaries

- This is planning-only. Do not edit product code, SQL migrations, runtime scripts or frontend code.
- Do not claim runtime evidence.
- Do not mark `WBH-02` complete.
- Do not touch `AGENT_TASK.md` except if your own final instructions require no change; this file is temporary orchestration context and will not be merged into final `orchestration`.
- Do not implement Stripe Connect onboarding, settlement, refunds, chargebacks, frontend UI, hosted payment form, or DLQ replay UI.
- Do not fake future payment lifecycle events.

## Planning Note Must Include

- Status line: planning draft, not approved, no product code implemented.
- Decision: exact recommended slice boundary.
- Why this slice is next.
- Exact backend/runtime scope.
- Explicit in-scope and out-of-scope lists.
- Service/module boundary for the current implementation, respecting that outbound webhook delivery currently lives in `platform`.
- Expected DB/model changes, including migration version recommendation after existing Platform migrations `V13` and `V14`.
- Retry policy recommendation suitable for local MVP runtime.
- DLQ state semantics.
- Runtime verification plan for `WBH-02`.
- Targeted regression checks that should remain green, especially `WBH-01`, `MRC-05`, and public API idempotency checks.
- Documentation/evidence files that the later implementation agent must update.
- Next planned step after the planning artifact.

## Commit

Make one logical commit on your branch with a clear message, for example:

```text
docs: plan outbound webhook retry dlq slice
```
