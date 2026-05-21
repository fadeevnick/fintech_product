# Phase 08 Slice 05 — Source-of-Funds Deposit Threshold — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow Phase 08 slice after `AML-04`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 08 slice 05`:

```text
Implement WLT-03 only: deposits over EUR 10,000 require a Source-of-Funds declaration before they enter manual operator review.
Do not implement two-eyes approval, document storage, SoF review queue or frontend UI.
```

This converts the previous honest high-value deposit refusal into a real backend gate.

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Allow deposit requests above EUR 10,000 in `wallet.deposit_requests`.
2. Keep low-value deposits moving directly to `PENDING_OPERATOR_REVIEW`.
3. Keep high-value deposits in `REQUESTED` until SoF declaration is submitted.
4. Add persisted SoF declaration data linked to the deposit request.
5. Add end-user endpoint:

```http
POST /api/v1/deposits/{depositId}/source-of-funds
```

6. Require deposit owner session, `REQUESTED` deposit state and amount above EUR 10,000.
7. On accepted SoF declaration, move the deposit to `PENDING_OPERATOR_REVIEW`.
8. Write audit rows for deposit creation and SoF declaration submission.
9. Add retained runtime script `product/scripts/runtime/reg_phase08_sof_deposit_threshold.sh`.

## 3. Explicitly Out Of Scope

Do not implement:

- second-operator approval (`WLT-04`);
- SoF backoffice review queue;
- object-storage document upload;
- frontend UI;
- high-value withdrawals/transfers;
- real banking instruction generation.

Do not claim:

- `WLT-04`;
- frontend `UI-*`.
