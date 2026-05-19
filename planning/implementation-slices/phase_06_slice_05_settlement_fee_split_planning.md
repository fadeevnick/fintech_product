# Phase 06 Slice 05 — Settlement Fee Split — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow settlement slice after `Phase 06 Slice 04 — Capture to Settlement Foundation`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 06 slice 05`:

```text
Implement SET-02 only: settled card payments produce an explicit, balanced fee split with merchant net settlement, issuer interchange, network assessment and acquirer margin postings.
Do not implement acquirer settlement-file projection, refunds, payouts, chargebacks, merchant settlement UI or scheduled daily batch orchestration.
```

The existing `SET-01` processor may remain the runtime trigger. This slice makes the settlement ledger movement more realistic and verifiable without expanding into payout/reconciliation scope.

## 2. Why This Slice Is Next

This slice is next because:

- `SET-01` already creates durable settlement batches/items and a balanced gross settlement journal;
- approved journeys require next-day settlement with fee splits;
- `SET-03` acquirer projection reconciliation needs deterministic fee components first;
- refunds and chargebacks depend on correct settled/net merchant amount semantics.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add deterministic local fee configuration for card settlement.
2. Compute fee split for each settled payment intent:
   - gross amount;
   - merchant net amount;
   - issuer interchange;
   - network assessment;
   - acquirer margin.
3. Persist fee components on settlement item or a narrow child table.
4. Update settlement ledger postings so the journal remains exactly balanced and exposes the four split destinations.
5. Keep reruns idempotent: no duplicate settlement items, fee rows or ledger journals.
6. Keep public/dashboard payment read state coherent after settlement.
7. Add retained runtime script `product/scripts/runtime/reg_phase06_settlement_fee_split.sh`.
8. Run targeted regressions for `SET-01`, `PAY-06`, `PAY-01` and ledger reconciliation.

## 4. Fee Model

Use a deterministic simple local model:

```text
interchange = 1.20% of gross
network assessment = 0.15% of gross
acquirer margin = 0.65% of gross
merchant net = gross - interchange - network assessment - acquirer margin
```

Amounts must round consistently to EUR minor units. Persist numeric values with the existing money precision, but runtime assertions should compare to two-decimal expected values for normal EUR card payments.

No tenant-specific pricing engine is required in this slice.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V23__settlement_fee_split.sql
```

Recommended minimal additions:

```sql
alter table settlement.settlement_items
    add column merchant_net_amount numeric(19,4),
    add column interchange_amount numeric(19,4),
    add column network_assessment_amount numeric(19,4),
    add column acquirer_margin_amount numeric(19,4);
```

If a child table is cleaner in the existing code, it is acceptable, but the runtime script must prove each component exists and sums to gross.

## 6. Runtime Verification

Target check:

- `SET-02` — settlement fee postings balance exactly.

The retained script should:

1. Create merchant, API key, funded end user, issued card and payment intent.
2. Authorize, capture and process settlement.
3. Assert one settlement item exists for the payment intent.
4. Assert fee components exist and:
   - `merchant_net + interchange + network_assessment + acquirer_margin = gross`;
   - component values match the deterministic local rates for a normal EUR amount.
5. Assert linked ledger journal is balanced.
6. Assert ledger postings include merchant settlement, issuer interchange, network assessment and acquirer margin destinations.
7. Rerun settlement processor and assert no duplicate fee rows/items/journals.

Targeted regressions:

- `SET-01`;
- `PAY-06`;
- `PAY-01`;
- `LDG-05`.

## 7. Explicitly Out Of Scope

Do not implement:

- acquirer settlement projection/reconciliation (`SET-03`);
- refunds (`SET-04`);
- payouts/Stripe Connect;
- chargebacks;
- merchant settlement frontend;
- configurable pricing UI or tenant pricing engine;
- scheduled daily batch infrastructure beyond the existing callable local processor;
- sanctions/AML/KYC changes.

Do not claim:

- `SET-03`;
- `SET-04`;
- `CHB-*`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- settlement fee calculation and persistence;
- Platform migration `V23__settlement_fee_split.sql`;
- retained Phase 06 fee split runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit sanctions/AML/KYC code except for incidental build fixes.
