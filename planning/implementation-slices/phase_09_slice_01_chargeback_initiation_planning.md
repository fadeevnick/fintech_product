# Phase 09 Slice 01 — Chargeback Initiation — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow Phase 09 slice after the Phase 08 wallet/compliance controls.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 01`:

```text
Implement CHB-01 only: an eligible settled card payment can be disputed by the cardholder within the configured window.
Do not implement provisional cardholder credit, merchant evidence, arbitration, reserve movements or frontend UI.
```

This creates the durable chargeback lifecycle anchor and eligibility gates while deferring ledger value movement to `CHB-02`.

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add a `chargeback` schema with a durable disputes table linked to the original payment intent:
   - dispute id;
   - original `merchant.payment_intents.id`;
   - merchant id;
   - cardholder/end-user id resolved from the authorized card;
   - amount/currency copied from the payment intent;
   - reason code;
   - cardholder narrative;
   - state;
   - merchant response deadline;
   - created/updated/version metadata.
2. Extend `merchant.payment_intents` state constraints to allow `DISPUTED`.
3. Add a configurable dispute window:
   - default: 60 days from captured/settled payment date;
   - use a backend property with a sane default if no environment override is supplied.
4. Add an authenticated end-user endpoint:
   - `POST /api/v1/card-payments/{paymentIntentId}/disputes`;
   - request body: `reasonCode`, optional `narrative`;
   - response body: dispute id, payment intent id, amount, currency, reason, state, created timestamp and merchant deadline.
5. Enforce initiation eligibility:
   - caller must be the cardholder/end-user behind the authorized payment card;
   - user must have `kyc_status = APPROVED`;
   - payment must be in `SETTLED` state;
   - payment must have a captured/settled timestamp inside the configured dispute window;
   - payment must not already have an open or terminal chargeback record;
   - payment currency must be EUR for this MVP.
6. Support MVP reason codes only:
   - `fraud_no_authorization`;
   - `goods_not_received`;
   - `goods_not_as_described`;
   - `duplicate_charge`.
7. On successful initiation:
   - insert a chargeback row in state `MERCHANT_NOTIFIED`;
   - set the original payment intent state to `DISPUTED`;
   - compute merchant response deadline from the reason-code default, using 14 days for this slice unless a more specific property map already exists;
   - write audit row `chargeback.initiated`;
   - publish/persist merchant webhook event `dispute.created` if the existing outbox abstraction can be reused without widening scope.
8. Preserve idempotent behavior for repeated user retries:
   - either by accepting an `Idempotency-Key` header on the new endpoint, or by returning the existing dispute for the same payment/cardholder after the first successful initiation;
   - do not create duplicate disputes for one payment intent.
9. Add retained runtime script `product/scripts/runtime/reg_phase09_chargeback_initiation.sh` proving `CHB-01`.

## 3. Runtime Verification Contract

`CHB-01` should pass only if the retained script proves:

1. Happy path:
   - create/authorize/capture/settle a card payment with an approved KYC cardholder;
   - cardholder initiates a dispute with an MVP reason code;
   - response returns a dispute in `MERCHANT_NOTIFIED`;
   - `chargeback` persistence links the dispute to the original payment intent, merchant and cardholder;
   - original payment intent state becomes `DISPUTED`;
   - audit contains `chargeback.initiated`;
   - repeated initiation for the same payment does not create a duplicate dispute.
2. Eligibility denials:
   - non-cardholder user cannot dispute the payment;
   - unapproved-KYC cardholder cannot initiate a dispute;
   - non-settled payment cannot be disputed;
   - unsupported reason code is rejected;
   - payment outside the dispute window is rejected or is covered by a deterministic fixture/update in the script.
3. Regressions:
   - existing payment authorization/capture/settlement retained scripts still pass for the paths touched by this slice;
   - ledger reconciliation (`LDG-05`) still passes, with no chargeback ledger postings claimed in this slice.

## 4. Explicitly Out Of Scope

Do not implement:

- provisional cardholder credit (`CHB-02`);
- merchant evidence submission (`CHB-03`);
- arbitration `WON`/`LOST` (`CHB-04`, `CHB-05`);
- merchant reserve or settlement balance debit/hold;
- attachment storage;
- backoffice chargeback arbitration UI;
- merchant disputes frontend UI;
- end-user transaction-detail frontend dispute button;
- chargeback rate dashboard/metric;
- real card-network message simulation beyond local persisted state/events.

Do not claim:

- `CHB-02` through `CHB-05`;
- settlement reserve correctness;
- merchant evidence or arbitration runtime evidence;
- frontend `UI-*`.

## 5. Resolution Notes

1. **Initial stored state** — insert directly as `MERCHANT_NOTIFIED` and use audit/event metadata to record initiation.
2. **Webhook event in scope** — persist `dispute.created` through the existing outbound webhook outbox publisher.
3. **Idempotency shape** — rely on a unique `payment_intent_id` dispute constraint and return the existing dispute for repeated same-cardholder initiation.
4. **Dispute window test fixture** — retained runtime verification mutates `captured_at`/`settled_at` directly to prove expiry.
