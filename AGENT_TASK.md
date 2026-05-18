# Agent Task — Phase 06 Slice 03 Webhook DLQ Replay

You are an implementation agent working in this repository.

## Role

Implement one approved backend/runtime slice. Do not do planning work beyond small implementation-local decisions needed to complete the slice.

## Read First

Read these files before changing code:

1. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/README.md`
2. `CURRENT.md`
3. `README.md`
4. `planning/implementation_status.md`
5. `planning/runtime_evidence_log.md`
6. `planning/runtime_checklists.md`
7. `planning/implementation-slices/phase_06_slice_03_outbound_webhook_dlq_replay_planning.md`
8. Relevant existing implementation around merchant webhook config, outbound webhook delivery, retry/DLQ, public API payment intents and Phase 06 runtime scripts.

## Target

Implement:

```text
Phase 06 Slice 03 — Outbound Webhook DLQ Replay
Check: WBH-03
```

Use the planning contract:

```text
planning/implementation-slices/phase_06_slice_03_outbound_webhook_dlq_replay_planning.md
```

## Required Scope

Implement backend/runtime support for merchant webhook DLQ replay:

- merchant-authenticated dashboard routes under `/api/v1/merchant/webhook-events`;
- DLQ list/detail scoped to the authenticated merchant;
- single-event replay for `merchant_admin`;
- replay must reuse the existing signed outbound delivery path and current endpoint signing secret behavior;
- successful replay moves the event from `DLQ` to `DELIVERED`;
- failed replay keeps the event in `DLQ` and updates latest failure metadata;
- cross-merchant access must not leak event existence;
- `merchant_member` may read list/detail but must not replay.

Add the retained runtime script:

```text
product/scripts/runtime/reg_phase06_webhook_dlq_replay.sh
```

Expected migration if needed:

```text
product/apps/platform/src/main/resources/db/migration/V17__merchant_webhook_dlq_replay.sql
```

## Verification

Run the strongest practical verification for this slice.

Target:

- `WBH-03` passes.

Targeted regressions:

- `WBH-01`;
- `WBH-02`;
- `MRC-05`;
- `PAY-01`;
- `PAY-02`;
- `PAY-03`.

Use a parameterized Compose project/ports if you need runtime verification. Do not reuse another agent's runtime stack.

## Update Required Docs

After successful verification, update:

- `planning/implementation_status.md`;
- `planning/runtime_evidence_log.md`;
- `CURRENT.md` if handoff state changes.

Record only factual implementation/runtime evidence. Do not claim checks that did not run.

## Out Of Scope

Do not implement:

- frontend UI;
- bulk replay;
- automatic replay of DLQ events;
- replay of non-DLQ delivered events;
- new webhook event producers beyond existing `payment_intent.created`;
- capture, settlement, refund, payout or chargeback lifecycle;
- Stripe Connect onboarding (`MRC-01`);
- KYC/backoffice compliance work;
- OpenSanctions, AML or document preview.

Do not edit the Phase 07 KYC manual review slice except for incidental build fixes.

## Temporary File Rule

`AGENT_TASK.md` is temporary branch context. Do not include it in the final merge back to `orchestration`.
