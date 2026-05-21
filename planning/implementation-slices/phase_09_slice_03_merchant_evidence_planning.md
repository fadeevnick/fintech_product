# Phase 09 Slice 03 — Merchant Evidence Submission — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow Phase 09 slice after `CHB-02` provisional cardholder credit.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 03`:

```text
Implement CHB-03 only: the merchant can submit evidence for a MERCHANT_NOTIFIED dispute.
Do not implement actual object upload, merchant accept/lost, arbitration outcome or frontend UI.
```

This creates the merchant representment step and state transition to `EVIDENCE_SUBMITTED` while keeping file handling as metadata references for MVP runtime verification.

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add persistence for merchant evidence:
   - one evidence submission per dispute in this slice;
   - submitter merchant employee id;
   - narrative;
   - attachment metadata rows with filename/content type/storage key/size;
   - created timestamp.
2. Add merchant dashboard endpoint:
   - `POST /api/v1/merchant/disputes/{disputeId}/evidence`;
   - request body: `narrative`, optional `attachments[]` metadata;
   - authenticated with existing merchant session cookie.
3. Enforce access and state:
   - merchant employee must be `ACTIVE`;
   - dispute must belong to the employee's merchant;
   - state must be `MERCHANT_NOTIFIED`;
   - merchant response deadline must not be expired;
   - narrative is required and length-limited;
   - attachment metadata is optional and capped for MVP.
4. On successful submission:
   - insert evidence row and attachment metadata rows;
   - transition dispute state to `EVIDENCE_SUBMITTED`;
   - write audit row `chargeback.evidence_submitted`;
   - persist merchant webhook event `dispute.evidence_received`.
5. Preserve existing `CHB-01`/`CHB-02` behavior and ledger postings.
6. Add retained runtime script `product/scripts/runtime/reg_phase09_merchant_evidence.sh`.

## 3. Runtime Verification Contract

`CHB-03` should pass only if the retained script proves:

1. Happy path:
   - create a settled payment;
   - cardholder initiates a dispute and gets provisional credit;
   - merchant submits evidence with narrative and at least one attachment metadata item;
   - dispute state becomes `EVIDENCE_SUBMITTED`;
   - evidence row and attachment metadata are persisted;
   - audit row `chargeback.evidence_submitted` is written;
   - webhook outbox event `dispute.evidence_received` is persisted.
2. Access/state denials:
   - different merchant cannot submit evidence;
   - repeated submission is rejected;
   - expired merchant deadline is rejected.
3. Regressions:
   - `CHB-01`/`CHB-02` retained script still passes;
   - ledger reconciliation (`LDG-05`) passes.

## 4. Explicitly Out Of Scope

Do not implement:

- actual S3/MinIO multipart upload/download;
- merchant accept / terminal `LOST`;
- arbitration (`CHB-04`, `CHB-05`);
- provisional credit reversal or merchant debit;
- frontend UI;
- chargeback rate metrics.

Do not claim:

- `CHB-04` or `CHB-05`;
- real object storage evidence upload;
- final chargeback ledger outcome correctness;
- frontend `UI-*`.

## 5. Resolution Notes

1. **Attachment scope** — store attachment metadata only (`storageKey` points to a simulated/object-storage key); no binary upload in this slice.
2. **Submission cardinality** — one evidence submission per dispute for MVP.
3. **State transition** — `MERCHANT_NOTIFIED -> EVIDENCE_SUBMITTED` directly on successful merchant submission.
