# Phase 06 Slice 04 — Capture to Settlement Foundation — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow settlement slice after `Phase 05 Slice 03 — Payment Capture Foundation`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 06 slice 04`:

```text
Implement SET-01 only: a captured payment can be advanced through a durable local clearing/settlement foundation and become settlement-recorded.
Do not implement fee split correctness, acquirer projection reconciliation, refunds, payouts, chargebacks, merchant settlement UI or full scheduled batch orchestration.
```

This slice should create the first concrete settlement path after capture. It may use an internal dispatcher/runtime command instead of a real daily scheduler, but state must be durable and runtime-verifiable.

## 2. Why This Slice Is Next

This slice is next because:

- `PAY-06` makes `CAPTURED` a durable payment-intent state;
- `SET-01` is the next checklist item required for the merchant payment lifecycle;
- fee split (`SET-02`) and acquirer projection reconciliation (`SET-03`) need a basic settlement record/state first;
- frontend settlement views remain gated by accepted prototypes and are out of scope.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add durable settlement/clearing persistence for captured payment intents.
2. Add a narrow internal runtime-dispatch path that processes eligible `CAPTURED` payment intents.
3. Transition processed payment intents to a settlement-recorded state such as `SETTLED` or persist linked settlement state while keeping the payment state queryable.
4. Post minimal balanced ledger movement required for `SET-01`.
5. Keep fee split simple for this slice:
   - either gross merchant settlement only;
   - or zero-fee local config;
   - do not claim `SET-02`.
6. Make processing idempotent: re-running the dispatcher must not create duplicate settlement records or duplicate ledger postings.
7. Add retained runtime script `product/scripts/runtime/reg_phase06_capture_to_settlement.sh`.
8. Run targeted regressions for `PAY-06`, `PAY-04`, `PAY-05`, and ledger balance checks if touched.

## 4. Recommended API / Runtime Surface

Recommended internal route or retained-script entry point:

```http
POST /internal/settlement/process-captured
```

Requirements:

- service/internal access only, not public merchant API;
- processes due captured payment intents deterministically for local checks;
- returns counts and settlement ids/batch ids;
- safe to call repeatedly.

No merchant-facing settlement API or UI is required in this slice.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V21__capture_to_settlement_foundation.sql
```

Recommended minimal tables under Platform for the current topology:

```sql
create schema if not exists settlement;

create table settlement.settlement_batches (
    id uuid primary key,
    status text not null check (status in ('OPEN','SETTLED')),
    created_at timestamptz not null default now(),
    settled_at timestamptz
);

create table settlement.settlement_items (
    id uuid primary key,
    batch_id uuid not null references settlement.settlement_batches(id),
    payment_intent_id uuid not null references merchant.payment_intents(id),
    merchant_id uuid not null references merchant.merchants(id),
    gross_amount numeric(19,4) not null,
    currency text not null,
    status text not null check (status in ('SETTLED')),
    ledger_journal_id uuid,
    created_at timestamptz not null default now(),
    unique (payment_intent_id)
);
```

If implementation uses existing ledger accounts, it should follow established account-type patterns. If a new merchant settlement account type is needed, keep it narrow and document it in evidence.

## 6. Runtime Verification

Target check:

- `SET-01` — captured payment reaches settlement foundation.

The retained script should:

1. Create merchant, API key, funded end user, issued card, payment intent.
2. Authorize and capture the payment intent using existing retained flows.
3. Run the settlement processor.
4. Assert:
   - one settlement batch/item exists for the payment intent;
   - payment intent is settlement-recorded;
   - linked ledger journal exists and is balanced if ledger postings are added;
   - rerunning the processor does not create duplicates;
   - public payment-intent read/dashboard read still returns coherent state.

Targeted regressions:

- `PAY-06`;
- `PAY-04`;
- `PAY-05`;
- `PAY-01` if public response shape changes.

## 7. Explicitly Out Of Scope

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

## 8. Agent Ownership Guidance

Implementation agent should own:

- settlement/capture processing backend code;
- Platform migration `V21__capture_to_settlement_foundation.sql`;
- retained Phase 06 settlement runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit sanctions/compliance code except for incidental build fixes.
