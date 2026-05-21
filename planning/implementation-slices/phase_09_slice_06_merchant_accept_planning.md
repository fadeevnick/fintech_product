# Phase 09 Slice 06 — Merchant Accepts Chargeback — Planning Note

Status: **APPROVED v0.1 — backend/runtime sub-scope executed**.

This document fixes the next narrow chargeback slice after `Phase 09 Slice 05 — Arbitration LOST Merchant Debit`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 06`:

```text
Implement CHB-06 only: a merchant admin can accept a merchant-notified dispute before evidence submission, making the cardholder provisional credit permanent and posting a merchant settlement debit against the dispute reserve.
Do not implement deadline expiry, attachment object upload/download, chargeback metrics or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add merchant dashboard endpoint `POST /api/v1/merchant/disputes/{disputeId}/accept`.
2. Require:
   - active merchant employee session;
   - `merchant_admin` role;
   - dispute belongs to the merchant;
   - dispute state is `MERCHANT_NOTIFIED`;
   - existing provisional-credit journal.
3. Post exactly one final merchant-debit journal:
   - journal type `CHARGEBACK_MERCHANT_DEBIT`;
   - reference type `CHARGEBACK`;
   - debit `MERCHANT_SETTLEMENT:<merchantId>`;
   - credit `ACQUIRER_DISPUTE_RESERVE`;
   - amount = dispute amount.
4. Transition dispute to terminal internal state `MERCHANT_ACCEPTED`.
5. Persist merchant acceptance metadata and journal id.
6. Write `chargeback.merchant_accepted` audit.
7. Persist outbound webhook event `dispute.lost`, exposing the external dispute state as `LOST`.
8. Add retained runtime script `product/scripts/runtime/reg_phase09_merchant_accept.sh`.

## 3. Runtime Verification

Target check:

- `CHB-06` — merchant accept makes cardholder credit permanent and debits merchant correctly.

The retained script should:

1. Create a settled payment and dispute through the existing CHB helper path.
2. Accept the dispute as the owning merchant admin.
3. Assert dispute state `MERCHANT_ACCEPTED`.
4. Assert one `CHARGEBACK_MERCHANT_DEBIT` journal exists with balanced postings.
5. Assert cardholder provisional credit remains unreversed.
6. Assert audit and `dispute.lost` webhook event exist.
7. Assert replay is rejected.
8. Assert evidence submission after merchant accept is rejected.
9. Assert another merchant cannot accept the dispute.

Targeted regressions:

- `CHB-05`;
- `CHB-04`;
- `LDG-05`.

## 4. Explicitly Out Of Scope

Do not implement:

- merchant deadline expiry;
- real S3 attachment upload/download;
- frontend UI;
- chargeback rate metrics;
- broad case-management refactor.
