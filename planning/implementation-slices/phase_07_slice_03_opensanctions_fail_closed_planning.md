# Phase 07 Slice 03 — OpenSanctions Fail-Closed Foundation — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow compliance slice after KYC manual review.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 07 slice 03`:

```text
Implement SNX-01 only: a Platform OpenSanctions adapter boundary and fail-closed sanctions screening path that opens a sanctions hit and blocks dependent approval when screening is unavailable or returns a high-confidence match.
Do not implement false-positive exception lifecycle, sanctions queue UI, AML, freezes, document preview, frontend UI or real production watchlist ingestion.
```

## 2. Why This Slice Is Next

This slice is next because:

- KYC start, webhook ingestion and manual review now exist;
- approved journeys require sanctions screening after KYC submission/consider cases;
- `SNX-01` is the smallest useful sanctions slice and does not require frontend prototypes;
- fail-closed behavior is a critical compliance invariant before broader wallet/payment gating.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add Platform `sanctions` schema persistence for sanctions hits and optional cached vendor response metadata.
2. Add an OpenSanctions adapter boundary with timeout/error handling.
3. Add a deterministic local runtime mode or test adapter configuration that can simulate:
   - screening unavailable/timeout;
   - high-confidence match;
   - no match.
4. Integrate screening with KYC manual approval path narrowly:
   - before manual approval returns `APPROVED`, run sanctions screening for the KYC profile's end user;
   - on unavailable/timeout, create `sanctions_hit` with reason `SCREENING_UNAVAILABLE`, keep KYC in `IN_REVIEW`, return fail-closed error;
   - on high-confidence match, create `sanctions_hit` with reason `POSSIBLE_MATCH`, keep KYC in `IN_REVIEW`, return blocked error;
   - on no match, allow the manual approval to proceed.
5. Persist audit rows for screening pass/block/unavailable using existing audit conventions.
6. Add retained runtime script `product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh`.
7. Run targeted regression for `KYC-03`.

## 4. Adapter Boundary

Recommended configuration:

```text
OPENSANCTIONS_BASE_URL=https://api.opensanctions.org
OPENSANCTIONS_API_KEY=
OPENSANCTIONS_TIMEOUT_MS=3000
OPENSANCTIONS_LOCAL_MODE=disabled|no_match|match|unavailable
OPENSANCTIONS_MATCH_THRESHOLD=0.85
```

Rules:

- missing real API key must not fake a vendor success unless explicit local mode is enabled for retained runtime checks;
- local mode must be visibly non-production and documented in runtime evidence;
- vendor response snippets must be bounded and not include unnecessary PII.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V20__opensanctions_fail_closed.sql
```

Recommended table:

```sql
create schema if not exists sanctions;

create table sanctions.sanctions_hits (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    kyc_profile_id uuid references kyc.kyc_profiles(id),
    status text not null check (status in ('OPEN','IN_REVIEW','CLEARED_FALSE_POSITIVE','TRUE_MATCH')),
    reason text not null check (reason in ('SCREENING_UNAVAILABLE','POSSIBLE_MATCH')),
    vendor text not null check (vendor in ('OPENSANCTIONS')),
    match_score numeric(5,4),
    matched_entity_id text,
    matched_name text,
    request_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
```

No sanctions decision/exception table is expected in this slice; that belongs to `SNX-02`.

## 6. Runtime Verification

Target check:

- `SNX-01` — OpenSanctions fail-closed.

The retained script should:

1. Seed/create an end user and KYC profile in `IN_REVIEW`.
2. Configure local unavailable mode.
3. Attempt backoffice manual approval.
4. Assert:
   - approval is blocked;
   - KYC profile remains `IN_REVIEW`;
   - one `sanctions.sanctions_hits` row exists with `reason = 'SCREENING_UNAVAILABLE'` and `status = 'OPEN'`;
   - audit row records screening unavailable/fail-closed.
5. Configure local high-confidence match mode and assert `POSSIBLE_MATCH` is created and approval blocked.
6. Configure local no-match mode and assert manual approval succeeds.

Targeted regression:

- `KYC-03`.

## 7. Explicitly Out Of Scope

Do not implement:

- sanctions false-positive exception workflow (`SNX-02`);
- backoffice sanctions hit queue/detail/decision UI;
- permanent account blocking / freeze;
- AML alerts;
- wallet/card/payment sanctions gating beyond the narrow KYC manual approval gate;
- document preview/read-audit (`AUD-03`);
- frontend UI;
- real production watchlist ingestion.

Do not claim:

- `SNX-02`;
- `AML-*`;
- `AUD-03`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- Platform sanctions backend package/schema/adapter;
- narrow KYC manual approval integration;
- Platform migration `V20__opensanctions_fail_closed.sql`;
- retained Phase 07 sanctions runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit payment capture/settlement code except for incidental build fixes.
