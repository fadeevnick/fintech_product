# Phase 06 Slice 06 — Acquirer Settlement Projection — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow settlement slice after `Phase 06 Slice 05 — Settlement Fee Split`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 06 slice 06`:

```text
Implement SET-03 only: Acquirer maintains a local merchant settlement projection that can be reconciled against Platform settlement/ledger state.
Do not implement refunds, payouts, chargebacks, merchant settlement UI, bank file export or scheduled daily reconciliation jobs.
```

This slice makes the already-settled Platform state visible in the Acquirer service as a local projection and adds a retained reconciliation script.

## 2. Why This Slice Is Next

This slice is next because:

- `SET-01` created durable settlement batches/items;
- `SET-02` added deterministic fee split and merchant net settlement amount;
- approved architecture explicitly calls for Acquirer local merchant settlement projection reconciled against Platform Ledger;
- refund, payout and chargeback paths need a clear merchant settlement projection before they adjust balances.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add Acquirer DB schema/table(s) for merchant settlement projection.
2. Add a narrow internal projection ingestion path from Platform settlement state into Acquirer.
3. Project settled payment intent data at least by:
   - platform settlement item id;
   - merchant id;
   - payment intent id;
   - gross amount;
   - merchant net amount;
   - fee components;
   - currency;
   - settlement timestamp/batch id.
4. Make projection ingestion idempotent by Platform settlement item id.
5. Add a retained reconciliation script comparing Platform settlement state to Acquirer projection.
6. Keep projection local and deterministic; Kafka/outbox can be deferred if the existing product code does not yet have a stable settlement event bus.

## 4. Recommended API / Runtime Surface

Recommended internal route in Acquirer:

```http
POST /internal/settlement/projections
```

Recommended internal route or script path in Platform:

```http
POST /internal/settlement/publish-projections
```

The exact shape may follow existing service-to-service helper patterns. It must be internal-only and runtime-verifiable.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/acquirer/src/main/resources/db/migration/V2__merchant_settlement_projection.sql
```

Recommended minimal table:

```sql
create schema if not exists merchant_settlement;

create table merchant_settlement.balance_projection (
    id uuid primary key,
    platform_settlement_item_id uuid not null unique,
    platform_batch_id uuid not null,
    merchant_id uuid not null,
    payment_intent_id uuid not null,
    gross_amount numeric(19,4) not null,
    merchant_net_amount numeric(19,4) not null,
    interchange_amount numeric(19,4) not null,
    network_assessment_amount numeric(19,4) not null,
    acquirer_margin_amount numeric(19,4) not null,
    currency text not null,
    projected_at timestamptz not null default now()
);
```

If implementation uses a different name, keep it narrow and document it in product README/status.

## 6. Runtime Verification

Target check:

- `SET-03` — Acquirer settlement projection matches Platform settlement state.

The retained script should:

1. Create merchant, API key, funded end user, issued card and payment intent.
2. Authorize, capture, settle and fee-split the payment using existing retained paths.
3. Publish or ingest settlement projection into Acquirer.
4. Assert Acquirer projection has exactly one row for the Platform settlement item.
5. Assert Platform vs Acquirer values match:
   - merchant id;
   - payment intent id;
   - gross amount;
   - merchant net amount;
   - fee components;
   - currency.
6. Rerun ingestion and assert no duplicate projection row.

Retained script:

```text
product/scripts/runtime/reg_phase06_acquirer_settlement_projection.sh
```

Targeted regressions:

- `SET-02`;
- `SET-01`;
- `PAY-06`;
- `LDG-05`.

## 7. Explicitly Out Of Scope

Do not implement:

- refunds (`SET-04`);
- payouts/Stripe Connect;
- chargebacks;
- merchant settlement frontend;
- bank file export;
- scheduled reconciliation jobs;
- AML/KYC/sanctions changes.

Do not claim:

- `SET-04`;
- `CHB-*`;
- frontend `UI-*`;
- real bank/payout integration.

## 8. Agent Ownership Guidance

Implementation agent should own:

- Acquirer settlement projection persistence and ingestion endpoint;
- Platform projection publication trigger if needed;
- retained Phase 06 acquirer settlement projection runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit AML/KYC/sanctions code except for incidental build fixes.
