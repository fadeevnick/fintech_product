# Phase 07 Slice 04 — Sanctions False-Positive Exception — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow sanctions slice after `Phase 07 Slice 03 — OpenSanctions Fail-Closed Foundation`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 07 slice 04`:

```text
Implement SNX-02 only: compliance officer can clear a sanctions hit as false positive with rationale, creating an exception that suppresses the same future match.
Do not implement sanctions UI, true-match permanent block, AML, freezes, document preview/read-audit or broader payment/wallet sanctions gating.
```

## 2. Why This Slice Is Next

This slice is next because:

- `SNX-01` creates persisted `OPEN` sanctions hits for possible matches;
- approved journeys require compliance review and false-positive clearing;
- `SNX-02` closes the loop for repeated false positives without implementing the full sanctions queue UI;
- it is independent from settlement work if migrations and code ownership stay separate.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add sanctions exception persistence for cleared false positives.
2. Add backoffice sanctions hit list/detail/decision backend APIs for compliance roles.
3. Enforce RBAC:
   - `compliance_officer` and `senior_compliance` can review sanctions hits;
   - `backoffice_operator` cannot access sanctions hit decisions.
4. Add decision `CLEAR_FALSE_POSITIVE` with required rationale length >= 20 characters.
5. Transition hit `OPEN -> CLEARED_FALSE_POSITIVE`.
6. Persist exception keyed narrowly enough to suppress the same future match for the same user/vendor entity.
7. Update OpenSanctions approval gate so the same future local/vendor match is suppressed when an active exception exists.
8. Add retained runtime script `product/scripts/runtime/reg_phase07_sanctions_false_positive.sh`.
9. Run targeted regression for `SNX-01`.

## 4. Planned API

Backoffice routes:

```http
GET  /api/v1/backoffice/sanctions-hits
GET  /api/v1/backoffice/sanctions-hits/{id}
POST /api/v1/backoffice/sanctions-hits/{id}/decision
```

Decision request:

```json
{
  "decision": "CLEAR_FALSE_POSITIVE",
  "rationale": "Reviewed matched entity details and confirmed this is not the same person."
}
```

Response should include:

- hit id;
- previous status;
- new status;
- exception id;
- decided by subject/role;
- decided timestamp.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V22__sanctions_false_positive_exception.sql
```

Recommended tables:

```sql
create table sanctions.sanctions_hit_decisions (
    id uuid primary key,
    hit_id uuid not null references sanctions.sanctions_hits(id),
    previous_status text not null,
    decision text not null check (decision in ('CLEAR_FALSE_POSITIVE')),
    resulting_status text not null check (resulting_status in ('CLEARED_FALSE_POSITIVE')),
    rationale text not null,
    decided_by_subject text not null,
    decided_by_role text,
    decided_at timestamptz not null default now()
);

create table sanctions.sanctions_false_positive_exceptions (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    vendor text not null check (vendor in ('OPENSANCTIONS')),
    matched_entity_id text not null,
    rationale text not null,
    created_from_hit_id uuid not null references sanctions.sanctions_hits(id),
    created_by_subject text not null,
    created_at timestamptz not null default now(),
    revoked_at timestamptz,
    unique (end_user_id, vendor, matched_entity_id)
);
```

No permanent block/freeze table is expected in this slice.

## 6. Runtime Verification

Target check:

- `SNX-02` — sanctions false-positive exception suppresses same future match.

The retained script should:

1. Configure deterministic OpenSanctions local `match` mode.
2. Seed/create KYC profile in `IN_REVIEW`.
3. Attempt manual approval and assert `SNX-01` possible-match block plus `OPEN` hit.
4. Use compliance officer token to clear the hit as false positive with valid rationale.
5. Assert:
   - hit status becomes `CLEARED_FALSE_POSITIVE`;
   - decision row exists;
   - exception row exists.
6. Create/attempt the same future match for the same end user/entity.
7. Assert manual approval now succeeds and no new blocking hit is created for the suppressed match.
8. Assert `backoffice_operator` cannot decide sanctions hits.

Targeted regression:

- `SNX-01` fail-closed unavailable path still blocks and creates hit.

## 7. Explicitly Out Of Scope

Do not implement:

- true-match permanent account block;
- freeze/unfreeze;
- AML;
- document preview/read-audit (`AUD-03`);
- sanctions frontend UI;
- wallet/card/payment sanctions gating beyond KYC approval gate;
- broad case-management abstraction.

Do not claim:

- `AML-*`;
- `AUD-03`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- sanctions review backend code;
- Platform migration `V22__sanctions_false_positive_exception.sql`;
- retained Phase 07 sanctions false-positive runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit settlement/payment processing code except for incidental build fixes.
