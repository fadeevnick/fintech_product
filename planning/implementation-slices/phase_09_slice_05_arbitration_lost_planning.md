# Phase 09 Slice 05 — Arbitration LOST Merchant Debit — Planning Note

Status: **APPROVED v0.1 — backend/runtime sub-scope executed**.

This document fixes the next narrow chargeback slice after `Phase 09 Slice 04 — Arbitration WON Reversal`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 05`:

```text
Implement CHB-05 only: an evidence-submitted dispute can be decided LOST, leaving the cardholder provisional credit in place and posting a merchant settlement debit against the dispute reserve.
Do not implement merchant accept, deadline expiry, attachment storage, chargeback metrics or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Extend the existing backoffice arbitration endpoint to support `outcome = LOST`.
2. Require the same eligibility as `WON`: valid backoffice principal, rationale, `EVIDENCE_SUBMITTED` dispute and existing provisional-credit journal.
3. Post exactly one final merchant-debit journal:
   - journal type `CHARGEBACK_MERCHANT_DEBIT`;
   - reference type `CHARGEBACK`;
   - debit `MERCHANT_SETTLEMENT:<merchantId>`;
   - credit `ACQUIRER_DISPUTE_RESERVE`;
   - amount = dispute amount.
4. Transition dispute to terminal `LOST`.
5. Persist arbitration metadata and journal id.
6. Write `chargeback.arbitration_lost` audit.
7. Persist outbound webhook event `dispute.lost`.
8. Add retained runtime script `product/scripts/runtime/reg_phase09_arbitration_lost.sh`.

## 3. Runtime Verification

Target check:

- `CHB-05` — arbitration LOST makes cardholder credit permanent and debits merchant correctly.

The retained script should:

1. Create a settled payment and dispute through the existing CHB helper path.
2. Submit merchant evidence.
3. Decide arbitration as `LOST`.
4. Assert dispute state/outcome/journal id.
5. Assert one `CHARGEBACK_MERCHANT_DEBIT` journal exists with balanced postings.
6. Assert cardholder provisional credit remains unreversed.
7. Assert audit and `dispute.lost` webhook event exist.
8. Assert replay is rejected.

Targeted regressions:

- `CHB-04`;
- `CHB-03`;
- `LDG-05`.

## 4. Explicitly Out Of Scope

Do not implement:

- merchant accept / terminal `LOST`;
- merchant deadline expiry;
- real S3 attachment upload/download;
- frontend UI;
- chargeback rate metrics;
- broad case-management refactor.
