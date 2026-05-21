# Phase 09 Slice 07 — Merchant Deadline Expiry — Planning Note

Status: **APPROVED v0.1 — backend/runtime sub-scope executed**.

This document fixes the next narrow chargeback slice after `Phase 09 Slice 06 — Merchant Accepts Chargeback`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 07`:

```text
Implement CHB-07 only: an internal runtime processor can expire merchant-notified disputes after the merchant response deadline, making the cardholder provisional credit permanent and posting a merchant settlement debit against the dispute reserve.
Do not implement scheduled jobs, object upload/download, chargeback metrics or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add internal endpoint `POST /internal/chargebacks/process-deadlines?limit=...`.
2. Select due disputes where:
   - state is `MERCHANT_NOTIFIED`;
   - `merchant_response_deadline <= now()`;
   - provisional-credit journal exists;
   - deadline-expiry journal is not already set.
3. For each due dispute, post exactly one final merchant-debit journal:
   - journal type `CHARGEBACK_MERCHANT_DEBIT`;
   - reference type `CHARGEBACK`;
   - debit `MERCHANT_SETTLEMENT:<merchantId>`;
   - credit `ACQUIRER_DISPUTE_RESERVE`;
   - amount = dispute amount.
4. Transition dispute to terminal internal state `MERCHANT_DEADLINE_EXPIRED`.
5. Persist deadline-expiry metadata and journal id.
6. Write `chargeback.deadline_expired` audit.
7. Persist outbound webhook event `dispute.lost`, exposing the external dispute state as `LOST`.
8. Add retained runtime script `product/scripts/runtime/reg_phase09_merchant_deadline_expiry.sh`.

## 3. Runtime Verification

Target check:

- `CHB-07` — merchant deadline expiry makes cardholder credit permanent and debits merchant correctly.

The retained script should:

1. Create a settled payment and dispute through the existing CHB helper path.
2. Move the dispute deadline into the past.
3. Run the internal deadline processor.
4. Assert dispute state `MERCHANT_DEADLINE_EXPIRED`.
5. Assert one `CHARGEBACK_MERCHANT_DEBIT` journal exists with balanced postings.
6. Assert cardholder provisional credit remains unreversed.
7. Assert audit and `dispute.lost` webhook event exist.
8. Assert rerunning the processor does not create a duplicate journal/event.
9. Assert evidence submission and merchant accept after deadline expiry are rejected.
10. Assert a not-yet-due dispute is not processed.

Targeted regressions:

- `CHB-06`;
- `CHB-05`;
- `LDG-05`.

## 4. Explicitly Out Of Scope

Do not implement:

- scheduler/cron wiring;
- real S3 attachment upload/download;
- frontend UI;
- chargeback rate metrics;
- broad case-management refactor.
