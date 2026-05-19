# Agent Task — Phase 07 Slice 04 Sanctions False-Positive Exception

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
7. `planning/implementation-slices/phase_07_slice_04_sanctions_false_positive_planning.md`
8. Relevant existing implementation around sanctions fail-closed screening, KYC manual review, backoffice auth/RBAC, audit conventions and Phase 07 runtime scripts.

## Target

Implement:

```text
Phase 07 Slice 04 — Sanctions False-Positive Exception
Check: SNX-02
```

Use the planning contract:

```text
planning/implementation-slices/phase_07_slice_04_sanctions_false_positive_planning.md
```

## Required Scope

Implement backend/runtime support for sanctions false-positive exception:

- sanctions exception persistence for cleared false positives;
- backoffice sanctions hit list/detail/decision backend APIs;
- compliance role access:
  - `compliance_officer` and `senior_compliance` can review/decide sanctions hits;
  - `backoffice_operator` cannot decide sanctions hits;
- decision `CLEAR_FALSE_POSITIVE` with required rationale length >= 20 characters;
- transition hit `OPEN -> CLEARED_FALSE_POSITIVE`;
- persist decision row and exception row;
- exception suppresses same future OpenSanctions match for same user/vendor entity;
- same future match no longer blocks KYC manual approval;
- audit rows using existing audit conventions.

Add retained runtime script:

```text
product/scripts/runtime/reg_phase07_sanctions_false_positive.sh
```

Expected migration:

```text
product/apps/platform/src/main/resources/db/migration/V22__sanctions_false_positive_exception.sql
```

## Verification

Run the strongest practical verification for this slice.

Target:

- `SNX-02` passes.

Targeted regression:

- `SNX-01` unavailable/fail-closed path still blocks and creates a hit.

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

- true-match permanent account block;
- freeze/unfreeze;
- AML;
- document preview/read-audit (`AUD-03`);
- sanctions frontend UI;
- wallet/card/payment sanctions gating beyond KYC approval gate;
- broad case-management abstraction;
- settlement/payment processing (`SET-01`).

Do not claim:

- `AML-*`;
- `AUD-03`;
- frontend `UI-*`.

Do not edit the Phase 06 settlement slice except for incidental build fixes.

## Temporary File Rule

`AGENT_TASK.md` is temporary branch context. Do not include it in the final merge back to `orchestration`.
