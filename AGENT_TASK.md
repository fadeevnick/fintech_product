# Agent Task — Phase 05 Slice 03 Payment Capture Foundation

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
7. `planning/implementation-slices/phase_05_slice_03_payment_capture_foundation_planning.md`
8. Relevant existing implementation around public payment intents, public API idempotency, merchant API keys, card authorization, payment dashboard reads and Phase 04/05 runtime scripts.

## Target

Implement:

```text
Phase 05 Slice 03 — Payment Capture Foundation
Check: PAY-06
```

Use the planning contract:

```text
planning/implementation-slices/phase_05_slice_03_payment_capture_foundation_planning.md
```

## Required Scope

Implement backend/runtime support for public payment-intent capture:

- `POST /v1/payment_intents/{id}/capture`;
- public API key authentication;
- required `Idempotency-Key`;
- capture only for the authenticated merchant's `AUTHORIZED` payment intent;
- durable capture state/timestamp/amount persistence;
- full capture only;
- if request includes `amount` or `currency`, they must match the original authorized payment intent;
- same-key replay returns deterministic cached response through existing idempotency primitive;
- same-key different-body conflict uses existing idempotency conflict behavior;
- second capture with a different key returns `409 invalid_state`;
- cross-merchant capture returns `404 not_found`.

Add retained runtime script:

```text
product/scripts/runtime/reg_phase05_payment_capture.sh
```

Expected migration:

```text
product/apps/platform/src/main/resources/db/migration/V19__payment_capture_foundation.sql
```

## Verification

Run the strongest practical verification for this slice.

Target:

- `PAY-06` passes.

Targeted regressions:

- `PAY-04`;
- `PAY-05`;
- `PAY-01`;
- `PAY-02`;
- `PAY-03`.

Use a parameterized Compose project/ports if you need runtime verification. Do not reuse another agent's runtime stack.

## Runtime Cleanup

If you start Docker Compose services for verification, stop your compose project before finishing.

Use the same project name you used for runtime checks, for example:

```bash
docker compose -p <your-project-name> -f product/deploy/docker-compose.yml down
```

Do not stop the owner/default stack unless the task explicitly tells you to.

## Update Required Docs

After successful verification, update:

- `planning/implementation_status.md`;
- `planning/runtime_evidence_log.md`;
- `CURRENT.md` if handoff state changes;
- `product/README.md` if local runtime commands/scripts changed.

Record only factual implementation/runtime evidence. Do not claim checks that did not run.

## Out Of Scope

Do not implement:

- clearing batch;
- settlement batch;
- fee split ledger postings;
- Acquirer settlement projection;
- refunds;
- payouts;
- chargebacks;
- frontend UI;
- Stripe Connect onboarding (`MRC-01`);
- sanctions/AML/KYC changes.

Do not claim:

- `SET-01`;
- `SET-02`;
- `SET-03`;
- `SET-04`;
- `CHB-*`;
- frontend `UI-*`.

Do not edit the Phase 07 OpenSanctions slice except for incidental build fixes.

## Temporary File Rule

`AGENT_TASK.md` is temporary branch context. Do not include it in the final merge back to `orchestration`.
