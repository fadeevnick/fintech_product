# Phase 09 Slice 02 — Provisional Cardholder Credit — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow Phase 09 slice after `CHB-01` cardholder dispute initiation.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 02`:

```text
Implement CHB-02 only: successful cardholder dispute initiation posts one provisional cardholder credit.
Do not implement merchant evidence, arbitration, reserve release/reversal or frontend UI.
```

This turns the existing `CHB-01` durable dispute creation into the first value-moving chargeback step required by `CHB-FR-04`.

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add a platform ledger account `ACQUIRER_DISPUTE_RESERVE`:
   - currency `EUR`;
   - account type `ACQUIRER_DISPUTE_RESERVE`;
   - normal side `DEBIT`;
   - no owner.
2. Add `chargeback.disputes.provisional_credit_journal_id` referencing `ledger.journal_entries`.
3. On first successful dispute initiation:
   - resolve the cardholder wallet account;
   - post balanced journal `CARDHOLDER_PROVISIONAL_CREDIT`;
   - reference type `CHARGEBACK`;
   - reference id = dispute id;
   - debit `ACQUIRER_DISPUTE_RESERVE`;
   - credit cardholder wallet ledger account;
   - amount = dispute/payment amount.
4. Persist the provisional journal id on the dispute row in the same transaction.
5. Preserve duplicate-initiation idempotence:
   - repeated same-cardholder initiation returns the existing dispute;
   - no second provisional-credit journal is posted.
6. Keep `CHB-01` eligibility gates unchanged.
7. Keep `dispute.created` webhook and `chargeback.initiated` audit semantics unchanged, while adding the provisional journal id to audit metadata if convenient.
8. Update retained runtime script `product/scripts/runtime/reg_phase09_chargeback_initiation.sh` so it proves both `CHB-01` and `CHB-02`.

## 3. Runtime Verification Contract

`CHB-02` should pass only if the retained script proves:

1. Successful dispute initiation:
   - creates a `MERCHANT_NOTIFIED` dispute;
   - marks payment intent `DISPUTED`;
   - persists exactly one `CARDHOLDER_PROVISIONAL_CREDIT` journal for that dispute;
   - journal debits `ACQUIRER_DISPUTE_RESERVE` and credits the cardholder wallet ledger account for the disputed amount;
   - `chargeback.disputes.provisional_credit_journal_id` points to that journal.
2. Duplicate same-cardholder initiation:
   - returns the existing dispute id;
   - does not create a second dispute;
   - does not create a second provisional-credit journal.
3. Denied initiation paths:
   - unsupported reason, non-cardholder, non-approved KYC, non-settled payment and expired-window denials do not post provisional-credit journals.
4. Regressions:
   - ledger reconciliation (`LDG-05`) passes;
   - settlement happy path (`SET-01`) still passes.

## 4. Explicitly Out Of Scope

Do not implement:

- merchant evidence submission (`CHB-03`);
- arbitration `WON`/`LOST` (`CHB-04`, `CHB-05`);
- reversal of provisional credit on `WON`;
- permanent merchant debit / reserve release on `LOST`;
- attachment storage;
- frontend UI;
- chargeback rate dashboard/metric.

Do not claim:

- `CHB-03` through `CHB-05`;
- final chargeback reserve correctness;
- merchant evidence or arbitration runtime evidence;
- frontend `UI-*`.

## 5. Resolution Notes

1. **Journal timing** — post provisional credit during first successful initiation, in the same transaction as dispute creation.
2. **Reserve account shape** — use one platform-level `ACQUIRER_DISPUTE_RESERVE` debit-normal account for MVP.
3. **Duplicate initiation** — existing same-cardholder duplicate initiation returns the existing dispute and does not post another journal.
