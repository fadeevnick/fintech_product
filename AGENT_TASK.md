# Agent Task — Phase 06 Slice 04 Capture to Settlement Foundation

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
7. `planning/implementation-slices/phase_06_slice_04_capture_to_settlement_foundation_planning.md`
8. Relevant existing implementation around public payment intents, capture, ledger posting, wallet/card authorization, merchant dashboard payment reads and Phase 05/06 runtime scripts.

## Target

Implement:

```text
Phase 06 Slice 04 — Capture to Settlement Foundation
Check: SET-01
```

Use the planning contract:

```text
planning/implementation-slices/phase_06_slice_04_capture_to_settlement_foundation_planning.md
```

## Required Scope

Implement backend/runtime support for captured payment settlement foundation:

- durable settlement/clearing persistence for captured payment intents;
- narrow internal runtime processor for eligible `CAPTURED` payment intents;
- settlement-recorded payment state or linked settlement state that is queryable;
- minimal balanced ledger movement required for `SET-01`;
- idempotent processing: re-running the processor does not duplicate settlement records or ledger postings;
- no fee split correctness claim;
- no acquirer projection reconciliation claim.

Add retained runtime script:

```text
product/scripts/runtime/reg_phase06_capture_to_settlement.sh
```

Expected migration:

```text
product/apps/platform/src/main/resources/db/migration/V21__capture_to_settlement_foundation.sql
```

## Verification

Run the strongest practical verification for this slice.

Target:

- `SET-01` passes.

Targeted regressions:

- `PAY-06`;
- `PAY-04`;
- `PAY-05`;
- `PAY-01` if public response shape changes;
- ledger balance checks if touched.

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

- fee split correctness (`SET-02`);
- acquirer settlement projection/reconciliation (`SET-03`);
- refunds (`SET-04`);
- payouts/Stripe Connect;
- chargebacks;
- merchant settlement frontend;
- scheduled daily batch infrastructure beyond a callable local processor;
- sanctions/AML/KYC changes.

Do not claim:

- `SET-02`;
- `SET-03`;
- `SET-04`;
- `CHB-*`;
- frontend `UI-*`.

Do not edit the Phase 07 sanctions false-positive slice except for incidental build fixes.

## Temporary File Rule

`AGENT_TASK.md` is temporary branch context. Do not include it in the final merge back to `orchestration`.
