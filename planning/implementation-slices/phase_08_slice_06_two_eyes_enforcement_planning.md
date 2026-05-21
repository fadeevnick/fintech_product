# Phase 08 Slice 06 — Two-Eyes Enforcement — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow Phase 08 slice after `WLT-03` Source-of-Funds deposit threshold.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 08 slice 06`:

```text
Implement WLT-04 only: high-value manual wallet operations require two distinct backoffice actors before value is released.
Do not implement senior override, frontend UI, SoF review queue, document storage or wider case-management refactor.
```

This converts the existing high-value-control placeholder into a real backend gate while keeping the slice small enough to verify with retained runtime scripts.

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add durable two-eyes metadata to `wallet.deposit_requests` and `wallet.withdraw_requests`:
   - first-review actor type/id/reference;
   - first-review reason;
   - first-review timestamp.
2. Extend allowed states:
   - deposits: add `READY_FOR_SECOND_REVIEW`;
   - withdrawals: add `READY_FOR_SECOND_REVIEW`.
3. Keep low-value deposit behavior unchanged:
   - low-value deposit goes to `PENDING_OPERATOR_REVIEW`;
   - one operator `APPROVE` completes it and posts the existing deposit ledger journal.
4. Keep low-value withdrawal behavior unchanged:
   - withdrawal request creates a hold and moves to `HELD`;
   - one operator `COMPLETE` completes it and posts the existing completion ledger journal.
5. For deposits with `amount >= 10000.0000`:
   - after SoF declaration, deposit remains reviewable in the manual deposit queue;
   - first operator `APPROVE` from `PENDING_OPERATOR_REVIEW` moves it to `READY_FOR_SECOND_REVIEW` and writes no ledger journal;
   - second distinct operator `APPROVE` from `READY_FOR_SECOND_REVIEW` completes it and posts the existing deposit ledger journal;
   - same actor attempting the second approval is rejected with `403 two_eyes_same_actor_denied`.
6. For withdrawals with `amount >= 10000.0000`:
   - allow end-user withdrawal requests above the previous `< 10000` placeholder limit, up to the already accepted wallet maximum for this slice;
   - hold is still posted at request time before backoffice review;
   - first operator `COMPLETE` from `HELD` moves it to `READY_FOR_SECOND_REVIEW` and writes no completion ledger journal;
   - second distinct operator `COMPLETE` from `READY_FOR_SECOND_REVIEW` completes it and posts the existing withdrawal completion ledger journal;
   - same actor attempting the second completion is rejected with `403 two_eyes_same_actor_denied`.
7. Rejection remains one-actor:
   - deposit `REJECT` is allowed from `PENDING_OPERATOR_REVIEW` and `READY_FOR_SECOND_REVIEW`;
   - withdrawal `REJECT` is allowed from `HELD` and `READY_FOR_SECOND_REVIEW` and releases the hold exactly once;
   - rejection after first review writes who rejected and does not require a second operator.
8. Update backoffice manual-ops list APIs so high-value records in `READY_FOR_SECOND_REVIEW` remain visible to operators in the same queues.
9. Write audit rows for first-review marks and final second-actor approvals/completions:
   - `wallet.deposit_marked_for_second_review`;
   - `wallet.withdrawal_marked_for_second_review`;
   - retain existing `wallet.deposit_approved`, `wallet.withdrawal_completed`, `wallet.deposit_rejected`, `wallet.withdrawal_rejected` final event semantics.
10. Add retained runtime script `product/scripts/runtime/reg_phase08_two_eyes_enforcement.sh` proving `WLT-04`.

## 3. Runtime Verification Contract

`WLT-04` should pass only if the retained script proves:

1. High-value deposit path:
   - end user creates deposit `>= 10000.0000`;
   - SoF declaration moves it to `PENDING_OPERATOR_REVIEW`;
   - first backoffice actor approval moves it to `READY_FOR_SECOND_REVIEW`;
   - no deposit ledger journal exists after first approval;
   - same actor second approval is rejected;
   - second distinct backoffice actor approval moves it to `COMPLETED`;
   - exactly one `WALLET_DEPOSIT` journal exists for the deposit.
2. High-value withdrawal path:
   - end user has sufficient funded wallet balance;
   - end user creates withdrawal `>= 10000.0000` and hold journal is posted;
   - first backoffice actor completion moves it to `READY_FOR_SECOND_REVIEW`;
   - no withdrawal completion journal exists after first completion mark;
   - same actor second completion is rejected;
   - second distinct backoffice actor completion moves it to `COMPLETED`;
   - exactly one `WALLET_WITHDRAW_COMPLETE` journal exists for the withdrawal.
3. Low-value regressions:
   - existing low-value deposit happy path still completes with one operator;
   - existing low-value withdrawal happy path still completes with one operator;
   - ledger reconciliation (`LDG-05`) passes after high-value and low-value flows.

## 4. Explicitly Out Of Scope

Do not implement:

- senior compliance override / bypass;
- dedicated SoF backoffice review queue;
- object-storage document upload;
- frontend UI;
- wider unified case-management refactor;
- banking statement upload threshold;
- high-value internal transfers;
- chargebacks (`CHB-*`);
- refunds (`SET-04`);
- real banking instruction generation.

Do not claim:

- senior override runtime evidence from Phase 08 exit criteria;
- `CHB-*`;
- `SET-04`;
- frontend `UI-*`.

## 5. Resolution Notes

1. **Operation scope** — implement deposits and withdrawals in one `WLT-04` slice.
2. **Senior compliance override** — defer override to a separate Phase 08 follow-up slice.
3. **Same-actor second approval error shape** — return `403 two_eyes_same_actor_denied`.
4. **Role scope** — any mapped backoffice role currently accepted by manual-ops endpoints can perform either step, provided the second actor is distinct from the first actor.
