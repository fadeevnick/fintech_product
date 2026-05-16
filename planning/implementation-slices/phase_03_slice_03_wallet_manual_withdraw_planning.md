# Phase 03 Slice 03 — Wallet Manual Withdraw Hold and Final Debit — Planning Note

Status: **DRAFT v0.1**.

This document fixes the third implementation slice inside `Phase 03 — Ledger and Wallet Manual Operations`.

It is a pre-code scope contract. Runtime evidence must be recorded separately in `planning/runtime_evidence_log.md`.

---

## 1. Decision

`Phase 03 slice 03`:

```text
Implement the manual withdraw request under EUR 10k with ledger hold, operator completion, final debit, rejection release, and actor-control write block.
```

This means:

- An end user can request a EUR wallet withdrawal only when available wallet balance is sufficient.
- A valid request immediately places a ledger hold that reduces available wallet balance without pretending a real bank payout happened.
- A backoffice operator can complete or reject the held request through the manual-ops surface.
- Completion finalizes the debit from the hold to an external withdrawal clearing account through balanced postings.
- Rejection releases the held amount back to the end-user wallet through balanced postings.
- `FROZEN` and `BLOCKED` actor-control states block withdraw creation and completion.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` puts manual withdraw request, hold and operator completion immediately after wallet deposit inside Phase 03.
- Phase 03 Slice 02 already created the wallet account model, lazy wallet provisioning, manual-ops operator surface pattern, actor-control wallet-write enforcement, amount validation shape and runtime script conventions.
- `LDG-03` remains pending and specifically requires a withdraw hold/final debit flow with balanced postings.
- The accepted standalone UI prototype `prototypes/ui/07_backoffice_manual_withdrawals.html` exists, but frontend implementation remains deferred; backend/runtime work is not blocked by UI.
- Implementing withdraw before internal transfer keeps the next ledger behavior focused on hold/final/release semantics, which later card authorization holds can reuse conceptually.

If the next slice were internal transfer, SoF or two-eyes instead, it would skip the missing hold primitive that `LDG-03` and later card authorization flows depend on.

## 3. Exact Scope

In this slice:

1. Add `wallet.withdraw_requests` with a narrow state model for under-EUR-10k manual withdrawals:
   - `PENDING` after request validation starts;
   - `HELD` after hold journal is posted;
   - `COMPLETED` after operator completion finalizes the debit;
   - `REJECTED` after operator rejection releases the hold.
2. Add or seed withdrawal ledger accounts:
   - per-user hold account `WALLET_WITHDRAW_HOLD:<userId>`;
   - system account `EXTERNAL_WITHDRAWAL_CLEARING`.
3. End-user API:
   - `POST /api/v1/withdrawals` creates a request under EUR 10k, verifies sufficient available wallet balance, posts the hold journal, and returns the request in `HELD`.
   - `GET /api/v1/wallet` includes own withdrawal history alongside balance/deposit history if this can be done without widening the UI contract.
4. Backoffice manual-ops API:
   - `GET /api/v1/backoffice/manual-ops/withdrawals` lists held withdrawal requests and writes synchronous read-audit rows.
   - `POST /api/v1/backoffice/manual-ops/withdrawals/{id}/decision` accepts `COMPLETE` or `REJECT` with required reason.
5. Hold posting contract:
   - DEBIT `WALLET_USER:<userId>`;
   - CREDIT `WALLET_WITHDRAW_HOLD:<userId>`;
   - `journal_type = 'WALLET_WITHDRAW_HOLD'`;
   - `reference_type = 'WALLET_WITHDRAW_REQUEST'`.
6. Completion posting contract:
   - DEBIT `WALLET_WITHDRAW_HOLD:<userId>`;
   - CREDIT `EXTERNAL_WITHDRAWAL_CLEARING`;
   - `journal_type = 'WALLET_WITHDRAW_COMPLETE'`;
   - same withdraw request reference.
7. Rejection release posting contract:
   - DEBIT `WALLET_WITHDRAW_HOLD:<userId>`;
   - CREDIT `WALLET_USER:<userId>`;
   - `journal_type = 'WALLET_WITHDRAW_RELEASE'`;
   - same withdraw request reference.
8. Actor-control hook:
   - block withdraw creation for `FROZEN` or `BLOCKED` end users;
   - block operator completion if the owner becomes `FROZEN` or `BLOCKED` while the request is held;
   - rejection remains allowed so a blocked/frozen user can have reserved funds released.
9. Audit:
   - write-audit for withdraw create/hold, complete, reject/release and denied write attempts;
   - read-audit for backoffice withdrawal queue access.
10. Retained runtime scripts prove happy path, rejection release, insufficient funds, actor-control blocks and double-decision refusal.

## 4. Explicitly In Scope

### Code Changes

- Platform Flyway migration, likely `product/apps/platform/src/main/resources/db/migration/V7__wallet_manual_withdraw.sql`.
- Add withdrawal persistence and service methods to the existing `com.minifin.platform.wallet` package.
- Extend existing wallet response models only where needed for withdrawal history.
- Extend the existing manual-ops controller/service/repository pattern with withdrawal endpoints.
- Reuse `LedgerService` / `ledger.post_journal(...)`; do not duplicate posting logic.
- Reuse existing session auth, backoffice OIDC/RBAC, audit and actor-control primitives.
- Add retained runtime scripts under `product/scripts/runtime/reg_phase03_wallet_withdraw_*.sh`.
- Update `product/README.md`, `planning/implementation_status.md`, `planning/runtime_evidence_log.md`, `CURRENT.md` after implementation and verification.

### Behavioral Outcomes

- A user with enough available wallet balance can create a withdrawal request and immediately see the amount held.
- The hold reduces available wallet balance through ledger postings, not through a mutable wallet-side counter.
- A held withdrawal appears in the backoffice manual withdrawals queue.
- `COMPLETE` finalizes the withdrawal through a balanced final-debit journal and transitions to `COMPLETED`.
- `REJECT` releases the hold through a balanced release journal and transitions to `REJECTED`.
- A second terminal decision returns 409 and creates no additional ledger movement.
- A user with insufficient available balance receives a structured 4xx and no hold journal.
- A `FROZEN` or `BLOCKED` user cannot create a withdrawal; a held request for such a user cannot be completed.

### Verification Outcomes

- `LDG-03` — withdraw creates hold and final debit flow with balanced postings.
- `WLT-02` — frozen/blocked wallet write block extends to withdrawal create and completion.
- `AUD-01` — audit rows exist for withdrawal create/hold, complete, reject/release and actor-control denial.
- `AUD-03` — backoffice withdrawal queue writes synchronous read-audit rows.
- `LDG-99` / `LDG-05` — reconciliation still passes after hold, complete and release paths.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- real bank payout rails, IBAN validation, SEPA files or Stripe payouts;
- Source of Funds declarations (`WLT-03`);
- high-value withdrawal amount >= EUR 10k;
- two-eyes / second-actor approval (`WLT-04`);
- `READY_FOR_SECOND_REVIEW` and `HELD_FOR_REVIEW` branches from the full state machine;
- AML rule trip, sanctions check or auto-freeze;
- internal end-user to end-user transfer (`WLT-01`);
- card / payment authorization holds;
- merchant settlement or acquirer payout;
- frontend implementation, even though `07_backoffice_manual_withdrawals.html` exists.

Specifically:

- do not edit approved baseline docs under `planning/01..06`, `planning/design-details/**` or `planning/runtime_checklists.md`;
- do not change `ledger.post_journal(...)` semantics;
- do not add async outbox/Kafka for withdrawals;
- do not model external payout success/failure as if money left a real bank account.

## 6. Proposed Wallet and Ledger Shape

### 6.1 Withdraw Request

Minimum fields:

```text
id uuid primary key
user_id uuid references identity.end_users(id)
amount numeric(20,4) check (amount > 0 and amount < 10000.0000)
currency text default 'EUR' check (currency = 'EUR')
state text check in ('PENDING','HELD','COMPLETED','REJECTED')
hold_journal_entry_id uuid nullable references ledger.journal_entries(id)
completion_journal_entry_id uuid nullable references ledger.journal_entries(id)
release_journal_entry_id uuid nullable references ledger.journal_entries(id)
reason text nullable
created_at timestamptz
held_at timestamptz nullable
decided_at timestamptz nullable
decided_by_actor_type text nullable
decided_by_actor_id uuid nullable
```

Rules:

- `hold_journal_entry_id` is set when state reaches `HELD`.
- `completion_journal_entry_id` is set only on `COMPLETED`.
- `release_journal_entry_id` is set only on `REJECTED`.
- terminal states reject further operator decisions.

### 6.2 User Hold Account

For each user that creates the first withdrawal:

```text
code = 'WALLET_WITHDRAW_HOLD:<userId>'
account_type = 'WALLET_HOLD'
normal_side = 'CREDIT'
currency = 'EUR'
owner_type = 'END_USER'
owner_id = user.id
```

The hold account is created idempotently and reused for all later withdrawals by the same user.

### 6.3 External Withdrawal Clearing Account

A single seeded ledger account:

```text
code = 'EXTERNAL_WITHDRAWAL_CLEARING'
account_type = 'EXTERNAL_CLEARING'
normal_side = 'CREDIT'
currency = 'EUR'
owner_type = NULL
owner_id = NULL
```

This account represents the local manual payout clearing sink only. It is not a real bank rail.

### 6.4 Available Balance

Available balance for this slice is:

```text
derived WALLET_USER balance
```

Because the hold posting debits `WALLET_USER` and credits `WALLET_WITHDRAW_HOLD`, held funds are already removed from available balance. The hold account balance is still visible to runtime checks so the final debit/release path can be proven.

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` after verification
- `product/README.md`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletModels.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletController.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/ManualOpsRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/ManualOpsService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/ManualOpsController.kt`

### 7.2 Create

- `product/apps/platform/src/main/resources/db/migration/V7__wallet_manual_withdraw.sql`
- `product/scripts/runtime/reg_phase03_wallet_withdraw_hold_complete.sh`
- `product/scripts/runtime/reg_phase03_wallet_withdraw_reject_releases_hold.sh`
- `product/scripts/runtime/reg_phase03_wallet_withdraw_insufficient_funds.sh`
- `product/scripts/runtime/reg_phase03_wallet_withdraw_actor_control_block.sh`
- `product/scripts/runtime/reg_phase03_wallet_withdraw_double_decision.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — already approved baseline.
- `planning/design-details/**` — already approved baseline; this slice interprets the existing withdraw state machine narrowly.
- `planning/runtime_checklists.md` — `LDG-03`, `WLT-02`, `AUD-01`, `AUD-03`, `LDG-05` and `LDG-99` already exist.
- SPA implementation files — frontend implementation is a separate later slice.

## 8. Recommended Change Order

1. Add migration for `wallet.withdraw_requests`, hold account support and `EXTERNAL_WITHDRAWAL_CLEARING`.
2. Add repository helpers for idempotent hold account provisioning, balance lookup, withdraw create and terminal decisions.
3. Add service logic for create/hold, complete/final debit, reject/release and actor-control checks.
4. Add end-user and backoffice routes.
5. Add retained runtime scripts for the five withdrawal paths.
6. Build and start the `platform` service.
7. Run withdrawal scripts plus ledger reconciliation and selected deposit regressions.
8. Record runtime evidence and update status files.

## 9. Linked Runtime Checks

This slice exercises:

- `LDG-03` — withdraw hold and final debit are balanced and reflected in derived ledger balances.
- `WLT-02` — frozen/blocked actor-control states block wallet withdrawal writes.
- `AUD-01` — withdrawal write actions and denials are audit logged.
- `AUD-03` — backoffice withdrawal queue performs synchronous read-audit.
- `LDG-05` — ledger reconciliation remains clean after withdrawal flows.
- `LDG-99` — cross-phase ledger invariants remain intact.

## 10. Linked ADRs

- ADR-002 — SQL-first persistence: withdrawal states, constraints and ledger references are DB-visible.
- ADR-004 — Ledger as central source of truth: wallet available balance is derived from postings, not maintained as a mutable counter.

## 11. Verification Shape

After implementation in this slice, run:

- build/start:
  - `docker compose -f deploy/docker-compose.yml build platform`
  - `docker compose -f deploy/docker-compose.yml up -d platform`
- new scripts:
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_hold_complete.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_reject_releases_hold.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_insufficient_funds.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_actor_control_block.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_double_decision.sh`
- regression scripts:
  - `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh`
  - `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`

Evidence must be appended to `planning/runtime_evidence_log.md`.

## 12. Pass Criteria

Slice is considered implemented when:

- code changes from sections 7.1 and 7.2 are present;
- all new withdrawal runtime scripts pass against local compose;
- linked check IDs from section 9 have `pass` or honest partial entries in `planning/runtime_evidence_log.md`;
- `planning/implementation_status.md`, `CURRENT.md`, `README.md` and `product/README.md` reflect the factual state;
- the completed slice is committed as a logical Git commit.
