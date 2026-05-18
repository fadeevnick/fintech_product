# Agent Task — Phase 07 Slice 02 KYC Review Queue and Manual Decisions

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
7. `planning/implementation-slices/phase_07_slice_02_kyc_review_queue_manual_decisions_planning.md`
8. Relevant existing implementation around backoffice auth/RBAC, KYC/Sumsub foundation, identity/session checks, audit conventions and Phase 07 runtime scripts.

## Target

Implement:

```text
Phase 07 Slice 02 — Backoffice KYC Review Queue and Manual Decisions
Check: KYC-03
```

Use the planning contract:

```text
planning/implementation-slices/phase_07_slice_02_kyc_review_queue_manual_decisions_planning.md
```

## Required Scope

Implement backend/runtime support for backoffice KYC manual review:

- backoffice-authenticated KYC queue route;
- KYC case detail route;
- manual decision route for approve/reject/request-resubmit;
- rationale validation: required, trimmed length at least 20 characters;
- persist manual decision metadata;
- write business audit rows using existing audit conventions;
- valid transitions from `IN_REVIEW` to `APPROVED`, `REJECTED` or `NEEDS_RESUBMIT`;
- invalid/terminal transitions return `409 invalid_state`;
- end-user and merchant sessions cannot access backoffice KYC routes;
- no document payloads or document preview in this slice.

Add the retained runtime script:

```text
product/scripts/runtime/reg_phase07_kyc_manual_review.sh
```

Expected migration:

```text
product/apps/platform/src/main/resources/db/migration/V18__kyc_manual_review.sql
```

## Verification

Run the strongest practical verification for this slice.

Target:

- `KYC-03` passes.

Targeted regressions:

- `KYC-02`;
- merchant-session wrong-role denial for `POST /api/v1/kyc/start`;
- backoffice auth/role denial helper if low-friction to run.

Use a parameterized Compose project/ports if you need runtime verification. Do not reuse another agent's runtime stack.

## Update Required Docs

After successful verification, update:

- `planning/implementation_status.md`;
- `planning/runtime_evidence_log.md`;
- `CURRENT.md` if handoff state changes.

Record only factual implementation/runtime evidence. Do not claim checks that did not run.

## Out Of Scope

Do not implement:

- OpenSanctions (`SNX-01`, `SNX-02`);
- AML alerts/freezes/SoF;
- document preview or KYC document read-audit (`AUD-03`);
- SeaweedFS document storage;
- case attachment upload;
- wallet/card/payment gating changes beyond updating `kyc.kyc_profiles.status`;
- frontend UI;
- real Sumsub sandbox start full pass;
- merchant webhook DLQ replay (`WBH-03`).

Do not edit the Phase 06 webhook DLQ replay slice except for incidental build fixes.

## Temporary File Rule

`AGENT_TASK.md` is temporary branch context. Do not include it in the final merge back to `orchestration`.
