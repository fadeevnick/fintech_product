# Agent Task — Phase 07 Slice 03 OpenSanctions Fail-Closed Foundation

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
7. `planning/implementation-slices/phase_07_slice_03_opensanctions_fail_closed_planning.md`
8. Relevant existing implementation around KYC manual review, backoffice auth/RBAC, identity end users, audit conventions and Phase 07 runtime scripts.

## Target

Implement:

```text
Phase 07 Slice 03 — OpenSanctions Fail-Closed Foundation
Check: SNX-01
```

Use the planning contract:

```text
planning/implementation-slices/phase_07_slice_03_opensanctions_fail_closed_planning.md
```

## Required Scope

Implement backend/runtime support for OpenSanctions fail-closed screening:

- Platform `sanctions` schema persistence for sanctions hits;
- OpenSanctions adapter boundary with timeout/error handling;
- explicit local runtime mode for deterministic checks:
  - unavailable/timeout;
  - high-confidence match;
  - no match;
- narrow integration with KYC manual approval:
  - before manual approval returns `APPROVED`, run sanctions screening for the KYC profile's end user;
  - unavailable/timeout creates `sanctions_hit` reason `SCREENING_UNAVAILABLE`, keeps KYC `IN_REVIEW`, and returns fail-closed error;
  - high-confidence match creates `sanctions_hit` reason `POSSIBLE_MATCH`, keeps KYC `IN_REVIEW`, and returns blocked error;
  - no match allows manual approval to proceed;
- audit rows for screening pass/block/unavailable using existing audit conventions.

Add retained runtime script:

```text
product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh
```

Expected migration:

```text
product/apps/platform/src/main/resources/db/migration/V20__opensanctions_fail_closed.sql
```

## Verification

Run the strongest practical verification for this slice.

Target:

- `SNX-01` passes.

Targeted regression:

- `KYC-03`.

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

- sanctions false-positive exception workflow (`SNX-02`);
- backoffice sanctions hit queue/detail/decision UI;
- permanent account blocking / freeze;
- AML alerts;
- wallet/card/payment sanctions gating beyond the narrow KYC manual approval gate;
- document preview/read-audit (`AUD-03`);
- frontend UI;
- real production watchlist ingestion;
- payment capture (`PAY-06`).

Do not claim:

- `SNX-02`;
- `AML-*`;
- `AUD-03`;
- frontend `UI-*`.

Do not edit the Phase 05 payment capture slice except for incidental build fixes.

## Temporary File Rule

`AGENT_TASK.md` is temporary branch context. Do not include it in the final merge back to `orchestration`.
