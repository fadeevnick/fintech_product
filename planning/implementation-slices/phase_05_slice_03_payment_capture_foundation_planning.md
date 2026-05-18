# Phase 05 Slice 03 — Payment Capture Foundation — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow payment lifecycle slice after card authorization.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 05 slice 03`:

```text
Implement PAY-06 only: public API capture for an already AUTHORIZED payment intent, with durable capture state and idempotent response behavior.
Do not implement clearing batch, settlement fee split, refunds, payouts, chargebacks, frontend UI or new merchant webhook event producers beyond what is explicitly needed for capture state.
```

This slice prepares for `SET-01` but does not claim it.

## 2. Why This Slice Is Next

This slice is next because:

- `PAY-04`/`PAY-05` already prove authorization and structured declines;
- payment lifecycle cannot reach clearing/settlement until capture exists as a durable state;
- capture is smaller than settlement and can be implemented without Kafka/outbox topology changes;
- public API idempotency and merchant scoping already exist.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add public route `POST /v1/payment_intents/{id}/capture`.
2. Require public API key auth and `Idempotency-Key`.
3. Allow capture only for the authenticated merchant's `AUTHORIZED` payment intent.
4. Persist capture state and timestamp.
5. Return stable response shape through the existing public API envelope.
6. Make same-key replay deterministic and conflicting same-key requests return the existing idempotency conflict behavior.
7. Deny:
   - cross-merchant capture with `404 not_found`;
   - non-`AUTHORIZED` states with `409 invalid_state`;
   - amount/currency mismatch if request body includes them.
8. Add retained runtime script `product/scripts/runtime/reg_phase05_payment_capture.sh`.
9. Run targeted regressions for `PAY-04`, `PAY-05`, `PAY-01`, `PAY-02`, `PAY-03` and relevant webhook checks if capture touches webhook emission.

## 4. Planned API

```http
POST /v1/payment_intents/{id}/capture
```

Request body may be empty or:

```json
{
  "amount": "12.50",
  "currency": "EUR"
}
```

MVP rule:

- only full capture is supported;
- if `amount` is present it must equal original authorized amount;
- if `currency` is present it must equal original currency.

Recommended response:

```json
{
  "data": {
    "id": "uuid",
    "object": "payment_intent",
    "amount": "12.50",
    "currency": "EUR",
    "state": "CAPTURED",
    "description": "test",
    "createdAt": "iso-8601",
    "capturedAt": "iso-8601"
  },
  "errors": []
}
```

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V19__payment_capture_foundation.sql
```

Recommended minimal changes:

```sql
alter table merchant.payment_intents
    add column captured_at timestamptz,
    add column captured_amount numeric(19,4),
    add column capture_request_id text;
```

The existing state check should allow `CAPTURED` if not already allowed.

No Acquirer, Network, Issuer or Vault migrations are expected for this slice unless the implementation discovers a compile/runtime necessity. Clearing/settlement tables belong to later `SET-*` slices.

## 6. Runtime Verification

Target check:

- `PAY-06` — authorized payment intent can be captured exactly once.

The retained script should:

1. Create merchant, API key, end user, wallet funds and issued card using existing helpers.
2. Create and authorize a payment intent.
3. Capture it with an idempotency key.
4. Assert:
   - response state is `CAPTURED`;
   - DB row has `state = 'CAPTURED'`, `captured_at` and expected amount;
   - same idempotency key replay returns the same response;
   - same key with different body returns conflict;
   - second capture with a different key returns `409 invalid_state`;
   - cross-merchant capture returns `404`.

Targeted regressions:

- `PAY-04`;
- `PAY-05`;
- `PAY-01`;
- `PAY-02`;
- `PAY-03`.

## 7. Explicitly Out Of Scope

Do not implement:

- clearing batch;
- settlement batch;
- fee split ledger postings;
- Acquirer settlement projection;
- refunds;
- payouts;
- chargebacks;
- frontend UI;
- Stripe Connect onboarding (`MRC-01`);
- sanctions/AML/KYC changes.

Do not claim:

- `SET-01`;
- `SET-02`;
- `SET-03`;
- `SET-04`;
- `CHB-*`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- Platform public payment-intent capture API and persistence;
- Platform migration `V19__payment_capture_foundation.sql`;
- retained Phase 05 capture runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit sanctions/compliance code except for incidental build fixes.
