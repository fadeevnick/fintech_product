# Phase 09 Slice 04 — Arbitration WON — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow Phase 09 slice after `CHB-03` merchant evidence submission.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 04`:

```text
Implement CHB-04 only: backoffice arbitration WON reverses the cardholder provisional credit.
Do not implement LOST, merchant accept, deadline expiry, frontend UI or reserve finalization beyond reversal.
```

This provides the first terminal arbitration outcome and proves the reversal side of `CHB-FR-04`.

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add arbitration metadata to `chargeback.disputes`:
   - arbitration outcome;
   - arbitration rationale;
   - arbitration decided-by subject/role;
   - arbitration decided timestamp;
   - arbitration journal id.
2. Add backoffice endpoint:
   - `POST /api/v1/backoffice/disputes/{disputeId}/arbitration`;
   - request body: `outcome`, `rationale`;
   - support only `WON` in this slice.
3. Enforce:
   - caller has an accepted backoffice role;
   - dispute exists;
   - dispute state is `EVIDENCE_SUBMITTED`;
   - provisional credit journal exists;
   - rationale is required and length-limited.
4. On `WON`:
   - post `CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL`;
   - reference type `CHARGEBACK`;
   - reference id = dispute id;
   - debit cardholder wallet ledger account;
   - credit `ACQUIRER_DISPUTE_RESERVE`;
   - amount = dispute amount;
   - transition dispute state to `WON`;
   - persist arbitration metadata and journal id;
   - write audit row `chargeback.arbitration_won`;
   - persist webhook outbox event `dispute.won`.
5. Preserve `CHB-01`/`CHB-02`/`CHB-03` behavior.
6. Add retained runtime script `product/scripts/runtime/reg_phase09_arbitration_won.sh`.

## 3. Runtime Verification Contract

`CHB-04` should pass only if the retained script proves:

1. Happy path:
   - create settled payment;
   - cardholder initiates dispute and gets provisional credit;
   - merchant submits evidence;
   - backoffice decides `WON`;
   - dispute state becomes `WON`;
   - exactly one reversal journal exists for the dispute;
   - reversal debits cardholder wallet and credits `ACQUIRER_DISPUTE_RESERVE` for the dispute amount;
   - provisional credit and reversal net the cardholder wallet impact back to zero;
   - audit row and `dispute.won` webhook outbox event are persisted.
2. Denials:
   - repeated `WON` decision is rejected;
   - `LOST` is rejected as out of scope;
   - deciding before evidence submission is rejected.
3. Regressions:
   - `CHB-03` retained script still passes;
   - ledger reconciliation (`LDG-05`) passes.

## 4. Explicitly Out Of Scope

Do not implement:

- arbitration `LOST` (`CHB-05`);
- merchant accept / terminal `LOST`;
- merchant deadline expiry;
- reserve release/permanent merchant debit;
- frontend UI;
- chargeback rate metrics.

Do not claim:

- `CHB-05`;
- merchant debit finalization;
- frontend `UI-*`.

## 5. Resolution Notes

1. **Role scope** — any accepted backoffice role may decide `WON` in this slice.
2. **State scope** — only `EVIDENCE_SUBMITTED -> WON` is supported.
3. **Ledger scope** — `WON` only reverses the provisional cardholder credit.
