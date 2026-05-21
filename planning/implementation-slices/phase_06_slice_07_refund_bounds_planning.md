# Phase 06 Slice 07 — Refund Bounds — Planning Note

Status: **APPROVED v0.1 — backend/runtime sub-scope executed**.

This document fixes the next narrow backend/runtime slice after `Phase 09 Slice 08 — Evidence Object Storage`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 06 slice 07`:

```text
Implement SET-04 only: merchant public API refunds for already-settled payment intents, with aggregate refund bounds so total successful refunds cannot exceed the captured payment amount.
Do not implement dashboard refund UI, pre-settlement refund netting, issuer/network refund rails, payout adjustments, bank files, scheduled jobs or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add durable refund persistence for public API refunds.
2. Add public endpoint `POST /v1/payment_intents/{id}/refund`.
3. Keep the existing public API-key auth and idempotency primitive.
4. Accept request body:
   - `amount` — required positive EUR amount;
   - `currency` — optional, defaults to `EUR`;
   - `reason` — optional bounded text.
5. Allow refunds only for the owning merchant and payment intents in `SETTLED`, `PARTIALLY_REFUNDED` or `REFUNDED` states.
6. Reject new refunds once the payment is fully refunded.
7. Enforce aggregate successful refunds `<= captured_amount`.
8. For each accepted refund:
   - create a refund record in terminal `SUCCEEDED` state for this local runtime slice;
   - post one balanced ledger journal `CARD_PAYMENT_REFUND`;
   - debit `MERCHANT_SETTLEMENT:<merchantId>`;
   - credit the cardholder wallet ledger account linked through the payment intent card token;
   - persist the refund ledger journal id;
   - update payment intent state to `PARTIALLY_REFUNDED` or `REFUNDED`;
   - write audit row `payment.refund_succeeded`;
   - persist outbound webhook event `payment_intent.refunded`.
9. Add retained runtime script `product/scripts/runtime/reg_phase06_refund_bounds.sh`.

## 3. Runtime Verification

Target check:

- `SET-04` — partial refunds cannot exceed original captured amount.

The retained script should:

1. Create, authorize, capture and settle one payment.
2. Submit a first partial refund and assert:
   - HTTP 200;
   - refund record persisted;
   - payment state `PARTIALLY_REFUNDED`;
   - balanced `CARD_PAYMENT_REFUND` ledger journal exists;
   - merchant settlement debited and cardholder wallet credited;
   - audit and webhook outbox rows exist.
3. Submit a second partial refund that reaches the exact captured amount and assert payment state `REFUNDED`.
4. Submit an over-refund attempt and assert:
   - HTTP 422 or 409;
   - no extra refund record;
   - no extra refund ledger journal.
5. Assert idempotent replay returns the same refund response.

Targeted regressions:

- `SET-01`;
- `PAY-06`;
- `LDG-05`.

## 4. Explicitly Out Of Scope

Do not implement:

- merchant dashboard refund route or UI;
- refund-before-settlement netting;
- issuer/network refund rails;
- Acquirer projection refund adjustments;
- payout adjustments;
- bank files;
- scheduled jobs;
- frontend UI.
