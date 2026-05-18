# Phase 07 Slice 02 — Backoffice KYC Review Queue and Manual Decisions — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow backend/runtime slice after `Phase 07 Slice 01 — KYC/Sumsub Foundation`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 07 slice 02`:

```text
Implement KYC-03 backend/runtime scope: backoffice KYC review queue and manual KYC decision API for existing KYC profiles.
Do not implement OpenSanctions, AML, document preview, SeaweedFS document storage, frontend UI or real Sumsub credential-dependent checks.
```

This means:

- use the existing `kyc.kyc_profiles` state created by Phase 07 Slice 01;
- expose backoffice APIs for operators to list KYC cases that need review;
- allow a backoffice operator to approve, reject or request resubmission with rationale;
- persist manual decision/audit data;
- keep document preview/read-audit and sanctions checks for later slices.

## 2. Why This Slice Is Next

This slice is next because:

- Sumsub webhook ingestion now maps ambiguous/review states into `IN_REVIEW`;
- approved journeys require Sumsub `consider` cases to enter a backoffice KYC queue;
- `KYC-FR-07` requires rationale text for manual override;
- backoffice authentication/RBAC already exists;
- this work can proceed without real Sumsub credentials and without touching webhook DLQ replay work.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add backoffice-authenticated KYC queue routes in Platform.
2. List KYC profiles/cases in review-oriented states.
3. Provide detail enough for operators to make a backend decision without exposing document images.
4. Add manual decision route with rationale validation.
5. Persist manual decision metadata and append business audit rows using existing audit conventions.
6. Apply state transitions:
   - `IN_REVIEW -> APPROVED`;
   - `IN_REVIEW -> REJECTED`;
   - `IN_REVIEW -> NEEDS_RESUBMIT`;
   - optionally allow `SUBMITTED -> IN_REVIEW` only if needed for a clear review workflow.
7. Deny invalid transitions with `409 invalid_state`.
8. Add retained runtime script `product/scripts/runtime/reg_phase07_kyc_manual_review.sh`.
9. Run targeted regression for `KYC-02` and wrong-role denial on `POST /api/v1/kyc/start`.

## 4. Planned API

Backoffice routes:

```http
GET  /api/v1/backoffice/kyc-cases
GET  /api/v1/backoffice/kyc-cases/{id}
POST /api/v1/backoffice/kyc-cases/{id}/decision
```

Access:

- `backoffice_operator`, `compliance_officer`, `senior_compliance` may list/detail KYC cases;
- the same roles may make KYC manual decisions for MVP;
- end-user and merchant sessions are forbidden;
- document preview is not part of this slice.

Decision request:

```json
{
  "decision": "APPROVE",
  "rationale": "Verified identity evidence and Sumsub review details are acceptable for MVP manual approval."
}
```

Allowed decision values:

```text
APPROVE
REJECT
REQUEST_RESUBMIT
```

Rationale rule:

- required;
- trimmed length must be at least 20 characters;
- persisted with actor identity and timestamp.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V18__kyc_manual_review.sql
```

Recommended table:

```sql
create table kyc.kyc_manual_decisions (
    id uuid primary key,
    kyc_profile_id uuid not null references kyc.kyc_profiles(id),
    previous_status text not null,
    decision text not null check (decision in ('APPROVE','REJECT','REQUEST_RESUBMIT')),
    resulting_status text not null check (resulting_status in ('APPROVED','REJECTED','NEEDS_RESUBMIT')),
    rationale text not null,
    decided_by_subject text not null,
    decided_by_role text,
    decided_at timestamptz not null default now()
);
```

No SeaweedFS/object-storage table is expected in this slice.

## 6. Runtime Verification

Target check:

- `KYC-03` — backoffice manual KYC review.

The retained script should:

1. Seed or create an end user and a KYC profile in `IN_REVIEW`.
2. Obtain a valid backoffice token/session using the existing Phase 02 backoffice helpers.
3. Assert queue list contains the case.
4. Assert detail returns KYC status/vendor metadata without document payloads.
5. Submit an invalid short rationale and expect validation failure.
6. Submit a valid manual approval and assert:
   - profile status becomes `APPROVED`;
   - one manual decision row is persisted;
   - audit row is written using existing conventions;
   - repeating a decision against the terminal case returns `409 invalid_state`.
7. Assert end-user/merchant sessions cannot access backoffice KYC routes.

Targeted regressions:

- `KYC-02`;
- merchant-session wrong-role denial for `POST /api/v1/kyc/start`;
- backoffice auth/role denial helper if low-friction to run.

## 7. Explicitly Out Of Scope

Do not implement:

- OpenSanctions (`SNX-01`, `SNX-02`);
- AML alerts/freezes/SoF;
- document preview or read-audit for KYC documents (`AUD-03`);
- SeaweedFS document storage;
- case attachment upload;
- wallet/card/payment gating changes based on manual KYC decisions beyond updating `kyc.kyc_profiles.status`;
- end-user or backoffice frontend code;
- real Sumsub sandbox start full pass.

Do not claim:

- `SNX-*`;
- `AML-*`;
- `AUD-03`;
- frontend `UI-*`;
- `KYC-01` full pass unless real Sumsub credentials are configured and used.

## 8. Agent Ownership Guidance

Implementation agent should own:

- Platform KYC/backoffice backend code for KYC queue/manual decisions;
- Platform migration `V18__kyc_manual_review.sql`;
- retained Phase 07 manual review runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit merchant outbound webhook replay code except for incidental build fixes.
