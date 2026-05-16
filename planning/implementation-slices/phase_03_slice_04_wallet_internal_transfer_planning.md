# Phase 03 Slice 04 — Wallet Internal Transfer — Planning Note

Status: **BACKEND/RUNTIME SUB-SCOPE EXECUTED v0.2; frontend not in scope**.

This document fixes the fourth implementation slice inside `Phase 03 — Ledger and Wallet Manual Operations`.

It started as a pre-code scope contract. Runtime evidence is recorded separately in `planning/runtime_evidence_log.md`.

---

## 1. Decision

`Phase 03 slice 04`:

```text
Implement end-user internal wallet transfer under EUR 10k with atomic sender debit, receiver credit, actor-control blocks and retained runtime evidence for WLT-01.
```

This means:

- The authenticated end user is always the sender.
- The request body identifies the receiver by `receiverUserId`.
- A valid transfer posts one balanced ledger journal: DEBIT sender `WALLET_USER:<senderId>`, CREDIT receiver `WALLET_USER:<receiverId>`.
- The sender must have sufficient available wallet balance.
- `FROZEN` or `BLOCKED` sender is blocked.
- `FROZEN` or `BLOCKED` receiver is also blocked conservatively, because receiving value mutates the receiver wallet balance.
- Optional `Idempotency-Key` duplicate protection is supported for this endpoint: same sender + same key + same normalized request returns the original transfer; same key with different body returns `409`.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` lists internal end-user transfer as the remaining Phase 03 wallet operation after deposit and withdraw.
- `planning/runtime_checklists.md` keeps `WLT-01` pending until an atomic sender debit / receiver credit path exists.
- Phase 03 Slice 02 and Slice 03 already created wallet provisioning, amount validation, ledger posting, actor-control and runtime script conventions that this slice can reuse.
- Transfer is still backend/domain/runtime work and is not blocked by missing frontend implementation.

If SoF, high-value controls or two-eyes were implemented first, they would widen the phase before the basic internal movement primitive is proven.

## 3. Exact Scope

In this slice:

1. Add `wallet.internal_transfers`.
2. Add end-user `POST /api/v1/transfers`.
3. Reuse lazy wallet provisioning for sender and receiver.
4. Validate amount is positive, EUR-only and `< 10000.0000`.
5. Reject self-transfer.
6. Reject missing receiver.
7. Check sender derived wallet balance before posting.
8. Require actor-control write allowance for sender and receiver.
9. Post one balanced ledger journal through `ledger.post_journal(...)`.
10. Persist the completed transfer and expose own sent/received transfers in `GET /api/v1/wallet`.
11. Add retained runtime scripts for happy path, insufficient funds, actor-control block, idempotency conflict/replay and ledger reconciliation regression.

## 4. Explicitly In Scope

### Code Changes

- Platform Flyway migration `V8__wallet_internal_transfer.sql`.
- Request/response models for internal transfers.
- Repository methods for inserting, reading and listing transfers.
- Wallet service transfer method.
- Wallet controller route `POST /api/v1/transfers`.
- Runtime scripts under `product/scripts/runtime/reg_phase03_wallet_transfer_*.sh`.
- Status/evidence updates after verification.

### Behavioral Outcomes

- A funded sender can transfer EUR to another active end user.
- Sender balance decreases and receiver balance increases through one balanced ledger journal.
- The same successful transfer appears in sender and receiver wallet summaries.
- Insufficient sender funds returns structured `409 insufficient_funds` and creates no transfer journal.
- Sender `FROZEN` / `BLOCKED` blocks transfer creation.
- Receiver `FROZEN` / `BLOCKED` also blocks transfer creation.
- Reusing an `Idempotency-Key` with the same normalized request returns the same transfer instead of posting a second journal.
- Reusing an `Idempotency-Key` with a different request returns `409 transfer_idempotency_conflict`.

### Verification Outcomes

- `WLT-01` — pass for atomic internal transfer debit/credit.
- `WLT-02` — pass extension for sender and receiver actor-control blocks.
- `AUD-01` — pass for transfer success and actor-control denial audit rows.
- `LDG-05` / `LDG-99` — reconciliation remains clean after transfer journals.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- Source of Funds (`WLT-03`);
- high-value operation amount `>= EUR 10k`;
- two-eyes / second-actor approval (`WLT-04`);
- AML velocity/structuring rules;
- sanctions checks;
- fees;
- transfer cancellation or reversal;
- merchant settlement or card/payment movement;
- frontend implementation.

Specifically:

- do not edit approved baseline docs under `planning/01..06`, `planning/design-details/**` or `planning/runtime_checklists.md`;
- do not touch prototype artifacts;
- do not change `ledger.post_journal(...)` semantics;
- do not add async outbox/Kafka for transfers.

## 6. Transfer Shape

### 6.1 API

```http
POST /api/v1/transfers
Cookie: MFP_SESSION=...
Idempotency-Key: optional-client-key
Content-Type: application/json

{
  "receiverUserId": "uuid",
  "amount": "12.0000",
  "currency": "EUR"
}
```

Response:

```json
{
  "data": {
    "transferId": "uuid",
    "senderUserId": "uuid",
    "receiverUserId": "uuid",
    "amount": "12.0000",
    "currency": "EUR",
    "state": "COMPLETED",
    "journalEntryId": "uuid",
    "createdAt": "iso-8601"
  },
  "errors": []
}
```

### 6.2 Ledger Posting Contract

For each successful transfer:

```text
journal_type = 'WALLET_INTERNAL_TRANSFER'
reference_type = 'WALLET_INTERNAL_TRANSFER'
reference_id = internal_transfer.id
DEBIT  WALLET_USER:<senderId>
CREDIT WALLET_USER:<receiverId>
```

Both wallet accounts use `normal_side = CREDIT`, so the debit reduces sender available balance and the credit increases receiver available balance.

### 6.3 Receiver Control Decision

Receiver `FROZEN` / `BLOCKED` is treated as a wallet write block. This is intentionally conservative: accepting inbound value changes the receiver ledger-derived balance and could bypass compliance freezes if allowed.

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `product/README.md`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletModels.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletController.kt`
- `product/scripts/runtime/lib_phase03_wallet_deposit.sh`

### 7.2 Create

- `product/apps/platform/src/main/resources/db/migration/V8__wallet_internal_transfer.sql`
- `product/scripts/runtime/reg_phase03_wallet_transfer_happy_path.sh`
- `product/scripts/runtime/reg_phase03_wallet_transfer_insufficient_funds.sh`
- `product/scripts/runtime/reg_phase03_wallet_transfer_actor_control_block.sh`
- `product/scripts/runtime/reg_phase03_wallet_transfer_idempotency.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved baseline.
- `planning/runtime_checklists.md` — `WLT-01`, `WLT-02`, `AUD-01`, `LDG-05` and `LDG-99` already exist.
- `prototypes/ui/**` — owned by UI/UX prototype workflow.
- SPA implementation files — frontend implementation is a later gated slice.

## 8. Recommended Change Order

1. Add migration for `wallet.internal_transfers`.
2. Add transfer models and repository methods.
3. Add wallet service logic for validation, actor-control, idempotency and ledger journal posting.
4. Add `POST /api/v1/transfers`.
5. Extend wallet summary with own sent/received transfer history.
6. Add retained runtime scripts.
7. Build and start `platform`.
8. Run transfer scripts and ledger reconciliation regression.
9. Record runtime evidence and update status files.

## 9. Linked Runtime Checks

This slice exercises:

- `WLT-01` — atomic internal transfer debits sender and credits receiver.
- `WLT-02` — frozen/blocked account write block applies to transfer sender and receiver.
- `AUD-01` — transfer success and actor-control denial create audit rows.
- `LDG-05` — ledger reconciliation remains clean.
- `LDG-99` — cross-phase ledger invariants remain intact.

## 10. Verification Shape

After implementation in this slice, run:

- `docker compose -f deploy/docker-compose.yml build platform`
- `docker compose -f deploy/docker-compose.yml up -d platform`
- `product/scripts/runtime/reg_phase03_wallet_transfer_happy_path.sh`
- `product/scripts/runtime/reg_phase03_wallet_transfer_insufficient_funds.sh`
- `product/scripts/runtime/reg_phase03_wallet_transfer_actor_control_block.sh`
- `product/scripts/runtime/reg_phase03_wallet_transfer_idempotency.sh`
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`

Evidence is appended to `planning/runtime_evidence_log.md`.

## 11. Pass Criteria

Slice considered implemented when:

- `POST /api/v1/transfers` exists and passes the retained runtime scripts;
- `wallet.internal_transfers` persists completed transfer facts;
- wallet summary shows own sent/received transfers;
- linked check IDs have evidence entries;
- `planning/implementation_status.md`, `planning/runtime_evidence_log.md`, `CURRENT.md`, `README.md` and `product/README.md` reflect the factual state;
- a logical Git commit exists on `impl/p03s04-internal-transfer`.
