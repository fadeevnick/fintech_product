# Phase 03 Slice 02 — Wallet Account and Manual Deposit — Planning Note

Status: **DRAFT v0.1**.

This document fixes the second implementation slice inside `Phase 03 — Ledger and Wallet Manual Operations`.

It is a pre-code scope contract. It is not implementation status and not runtime evidence.

---

## 1. Decision

`Phase 03 slice 02`:

```text
Implement end-user wallet account creation and the manual deposit workflow under EUR 10k, end to end through balanced ledger postings, with backoffice operator approve/reject and actor-control write block (`FROZEN` and `BLOCKED`).
```

This means:

- Each end user gets exactly one default EUR wallet account, linked to a per-user ledger account.
- An end user can create a deposit request through the existing session API, with amount strictly under EUR 10k.
- A backoffice operator can list pending deposits and submit `APPROVE` or `REJECT` decisions through the manual-ops surface.
- `APPROVE` posts a balanced journal through the existing `ledger.post_journal(...)` (debit external clearing account, credit user wallet ledger account) and transitions the deposit to `COMPLETED` in one transaction.
- `REJECT` transitions the deposit to `REJECTED` without ledger movement.
- An end user whose `identity.actor_controls.state` is `FROZEN` or `BLOCKED` cannot create a deposit request and cannot have a pending deposit approved into ledger movement, which gives the first real wallet-write proof path for `WLT-02`.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` §8 puts wallet account creation and manual deposit immediately after ledger foundation inside Phase 03.
- Phase 03 Slice 01 already made the ledger trustworthy (`LDG-01`, `LDG-04`, `LDG-05` pass), but `LDG-02` remained unclaimed because there was no real deposit workflow that uses `ledger.post_journal(...)` from a domain caller.
- `WLT-02` was unclaimed in Phase 02 Slice 04 because no real wallet writes existed; this slice introduces the first wallet write path and the matching enforcement.
- Backoffice OIDC/RBAC (`AUTH-03`, `AUD-01`) and the read-audit + actor-control primitives are already in place, which unblocks both the operator decision endpoint and the actor-control wallet-write hook without inventing new identity surfaces.
- `planning/design-details/state_machines.md` §7 already specifies the deposit request states this slice implements (`REQUESTED → PENDING_OPERATOR_REVIEW → APPROVED → COMPLETED`, plus `REJECTED`).
- ADR-002 (SQL-first persistence) and ADR-004 (ledger as central source of truth) require this slice to consume the ledger through `ledger.post_journal(...)` and to derive balance from postings, not from a wallet-side counter.

If the next slice were withdraw, transfer, SoF or two-eyes instead of deposit, the slice would either depend on missing primitives (withdraw needs hold semantics, transfer needs two wallets) or would expand into the full Phase 03 batch and lose runtime verifiability.

## 3. Exact Scope

In this slice:

1. Add Platform DB migration for `wallet` schema:
   - `wallet.wallet_accounts` — one default EUR wallet per end user, references `ledger.accounts.id`;
   - `wallet.deposit_requests` — state machine `REQUESTED → PENDING_OPERATOR_REVIEW → APPROVED → COMPLETED`, plus `REQUESTED|PENDING_OPERATOR_REVIEW → REJECTED`;
   - DB-level constraint `amount < 10000.0000` on `numeric(20,4)` (the `< EUR 10k` boundary for this slice; high-value path is out of scope);
   - idempotent seed of one system ledger account `EXTERNAL_DEPOSIT_CLEARING` used as the counter-account for manual deposits.
2. Auto-provision wallet account on first deposit attempt:
   - create matching ledger account (`account_type = 'WALLET_USER'`, `normal_side = 'CREDIT'`, `currency = 'EUR'`, `owner_type = 'END_USER'`, `owner_id = user.id`) and the `wallet.wallet_accounts` row in the same transaction, when missing.
3. End-user API through the existing session cookie:
   - `POST /api/v1/deposits` — create deposit request (amount < EUR 10k, currency EUR). Persists the row in `REQUESTED` and immediately transitions it to `PENDING_OPERATOR_REVIEW` inside the same transaction, since no SoF gate exists in this slice. The HTTP response returns the request already in `PENDING_OPERATOR_REVIEW`. The intermediate `REQUESTED` state is never observable from outside this transaction in this slice.
   - `GET /api/v1/wallet` — minimum: balance derived from ledger postings + list of own deposit requests.
4. Backoffice manual-ops API (existing OIDC + role `backoffice_operator` or higher):
   - `GET /api/v1/backoffice/manual-ops/deposits` — pending queue with read-audit;
   - `POST /api/v1/backoffice/manual-ops/deposits/{id}/decision` — `APPROVE` or `REJECT` with required `reason`.
5. Approval path: backoffice decision calls `ledger.post_journal(...)` with two postings (`DEBIT EXTERNAL_DEPOSIT_CLEARING`, `CREDIT WALLET_USER(owner=user)`) and updates `deposit_requests.state = COMPLETED` inside one transaction. Failure of the ledger call rolls back the whole decision.
6. Actor-control hook: check `identity.actor_controls` (Phase 02 Slice 04 primitive) before creating a deposit request and before approving an existing pending deposit. If the end user's `actor_controls.state` is `FROZEN` or `BLOCKED`, the action is refused with a structured 4xx and a denied-write audit row; the ledger is not touched. Both `FROZEN` and `BLOCKED` are treated as wallet-write blocks for this slice; semantic distinction between freeze and block is owned by later compliance/AML slices.
7. Audit:
   - write-audit on create / approve / reject deposit and on actor-control denial (`FROZEN` or `BLOCKED`);
   - sensitive read-audit on backoffice `GET .../deposits` through the existing read-audit primitive.
8. Retained runtime scripts under `product/scripts/runtime/reg_phase03_*` for happy path, reject path, actor-control block (covering `FROZEN` and `BLOCKED`), and double-decision idempotency.
9. After verification: append evidence to `planning/runtime_evidence_log.md`, update `planning/implementation_status.md`, `CURRENT.md`, `README.md`, `product/README.md`.

## 4. Explicitly In Scope

### Code Changes

- Platform Flyway migration, likely `product/apps/platform/src/main/resources/db/migration/V6__wallet_manual_deposit.sql`.
- Platform wallet Kotlin package, likely:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletModels.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletRepository.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletService.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletController.kt`
- Platform manual-ops Kotlin code (under the same wallet package or a sibling `manualops` package):
  - `ManualOpsRepository.kt`
  - `ManualOpsService.kt`
  - `ManualOpsController.kt`
- Reuse of existing `LedgerService` / `ledger.post_journal(...)`; no duplicated posting logic.
- Reuse of existing `audit.audit_log`, `audit.read_audit_log`, `identity.actor_controls`.
- Retained runtime scripts under `product/scripts/runtime/reg_phase03_*`.

### Behavioral Outcomes

- An end user can create a deposit request, observed in `PENDING_OPERATOR_REVIEW`, and see it in `GET /api/v1/wallet`.
- A pending deposit appears in the backoffice manual-ops queue.
- `APPROVE` atomically increases derived wallet balance by `+amount` through balanced ledger postings and transitions the deposit to `COMPLETED`.
- `REJECT` leaves the ledger unchanged and transitions the deposit to `REJECTED`.
- An end user whose `actor_controls.state` is `FROZEN` or `BLOCKED` cannot create a deposit; an existing pending deposit whose owner becomes `FROZEN` or `BLOCKED` cannot be approved into ledger movement.
- A second decision on a terminal deposit is refused with 409 and produces no second ledger movement.
- Amount ≥ EUR 10k is refused as `unsupported_high_value` at the API/DB level — this is honest refusal, not SoF/two-eyes implementation.

### Verification Outcomes

This slice exercises:

- `LDG-02` — valid deposit postings: `APPROVE` creates a balanced two-posting journal through `ledger.post_journal(...)` and increases derived wallet balance by exactly the deposit amount.
- `WLT-02` — frozen/blocked account write block: an end user whose `actor_controls.state` is `FROZEN` or `BLOCKED` cannot `POST /api/v1/deposits`; backoffice cannot approve an existing pending deposit whose owner is `FROZEN` or `BLOCKED`.
- `LDG-99` (foundation) — global ledger invariants survive a full deposit cycle, re-checked through the existing reconciliation script.
- `AUD-01` — audit entries for deposit create / approve / reject and for actor-control denial.
- `AUD-03` — read-audit row written synchronously before backoffice deposits queue returns data.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- manual withdraw workflow (`LDG-03`);
- internal end-user → end-user transfer (`WLT-01`);
- two-eyes / second-actor approval for high-value operations (`WLT-04`);
- Source of Funds declaration and the > EUR 10k threshold (`WLT-03`);
- AML rule trip and AML auto-freeze (`AML-01..04`);
- card / payment authorization holds;
- merchant settlement and acquirer projection;
- public Payments API;
- Stripe / Sumsub / OpenSanctions integrations;
- backoffice manual deposits SPA frontend implementation (HTML prototype `06_backoffice_manual_deposits.html` is accepted, but frontend code is a separate later slice);
- end-user wallet/deposit SPA frontend implementation (no accepted HTML prototype yet).

Specifically:

- do not change `ledger.post_journal(...)` signature or semantics; this slice only composes calls to it;
- do not introduce new ledger account types beyond `WALLET_USER` and `EXTERNAL_DEPOSIT_CLEARING`;
- do not add async outbox / Kafka for deposits; manual ops are sync DB transactions in this slice.

## 6. Proposed Wallet and Deposit Shape

### 6.1 Wallet Account

Minimum fields:

```text
id uuid primary key
user_id uuid references identity.end_users(id) unique
ledger_account_id uuid references ledger.accounts(id) unique
currency text default 'EUR' check (currency = 'EUR')
created_at timestamptz
```

Rules:

- exactly one `wallet_accounts` row per end user;
- created lazily on first deposit attempt, in the same transaction as the matching ledger account row.

### 6.2 Deposit Request

Minimum fields:

```text
id uuid primary key
user_id uuid references identity.end_users(id)
amount numeric(20,4) check (amount > 0 and amount < 10000.0000)
currency text default 'EUR' check (currency = 'EUR')
state text check in ('REQUESTED','PENDING_OPERATOR_REVIEW','APPROVED','COMPLETED','REJECTED')
reason text nullable
journal_entry_id uuid nullable references ledger.journal_entries(id)
created_at timestamptz
decided_at timestamptz nullable
decided_by_actor_type text nullable
decided_by_actor_id uuid nullable
```

Rules:

- `journal_entry_id` is set only on `COMPLETED`;
- transitions are guarded in application code; DB-level state guard added if it stays narrow.

### 6.3 System Ledger Account

A single seeded ledger account:

```text
code = 'EXTERNAL_DEPOSIT_CLEARING'
account_type = 'EXTERNAL_CLEARING'
normal_side = 'DEBIT'
currency = 'EUR'
owner_type = NULL
owner_id = NULL
```

Seeded once through migration in an idempotent way.

### 6.4 Approve Posting Contract

On `APPROVE`:

- DEBIT `EXTERNAL_DEPOSIT_CLEARING` for `amount` EUR;
- CREDIT `WALLET_USER(owner=user)` for `amount` EUR;
- single call to `ledger.post_journal(...)`;
- `journal_type = 'WALLET_DEPOSIT'`, `reference_type = 'WALLET_DEPOSIT_REQUEST'`, `reference_id = deposit_request.id`.

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` (after verification)
- `product/README.md`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/ledger/LedgerService.kt` — only if a narrow caller helper for wallet deposit is genuinely needed; otherwise leave unchanged.

### 7.2 Create

- Platform migration:
  - `product/apps/platform/src/main/resources/db/migration/V6__wallet_manual_deposit.sql`
- End-user wallet surface:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletModels.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletRepository.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletService.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/WalletController.kt`
- Backoffice manual-ops surface:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/ManualOpsRepository.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/ManualOpsService.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/wallet/ManualOpsController.kt`
- Retained runtime scripts:
  - `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_reject.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_actor_control_block.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md`..`planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved inputs (real mismatch must be raised through ADR, not a backdated edit).
- `planning/runtime_checklists.md` — `LDG-02`, `WLT-02`, `AUD-01`, `AUD-03` already exist; this slice introduces no new IDs.
- `product/apps/platform/src/main/resources/db/migration/V5__ledger_foundation.sql` — ledger foundation migration is immutable.
- `product/apps/{acquirer,network,issuer,vault}/**` — other services are not part of manual deposit.
- `product/apps/spa-*/**` — frontend is out of scope.

## 8. Recommended Change Order

1. Add `V6__wallet_manual_deposit.sql`: `wallet` schema, `wallet_accounts`, `deposit_requests`, FKs into `identity.end_users` and `ledger.accounts`, idempotent seed for `EXTERNAL_DEPOSIT_CLEARING`.
2. Add Kotlin models and repositories for wallet and deposit request.
3. Implement end-user `POST /api/v1/deposits` and `GET /api/v1/wallet` (including lazy wallet provisioning and actor-control guard for `FROZEN`/`BLOCKED`).
4. Implement backoffice `GET /api/v1/backoffice/manual-ops/deposits` (read-audit) and `POST .../{id}/decision` (write-audit + atomic ledger posting on approve, actor-control guard on approve).
5. Add retained runtime scripts (happy path, reject, actor-control block covering `FROZEN` and `BLOCKED`, double decision).
6. Rebuild Platform image and recreate `platform`.
7. Phase 01/02/03 regression subset:
   - `reg_phase01_runtime_health.sh`
   - `reg_phase02_backoffice_oidc.sh`
   - `reg_phase02_read_audit_probe.sh`
   - `reg_phase02_actor_control_enduser.sh`
   - `reg_phase02_auth_audit.sh`
   - `reg_phase03_ledger_unbalanced_rejection.sh`
   - `reg_phase03_ledger_append_only.sh`
   - `reg_phase03_ledger_reconciliation.sh`
8. Run new wallet/deposit scripts.
9. Append evidence to `planning/runtime_evidence_log.md` per check ID with honest tags.
10. Update `planning/implementation_status.md`, `README.md`, `product/README.md`, `CURRENT.md`.
11. Commit the completed backend/runtime sub-scope as a separate logical commit; frontend implementation is a future slice.

## 9. Linked Runtime Checks

This slice exercises:

- `LDG-02` — valid deposit postings (balanced journal + derived balance increase).
- `WLT-02` — frozen/blocked account write block: end user with `actor_controls.state ∈ {FROZEN, BLOCKED}` cannot create deposit; approve on a pending deposit whose owner is `FROZEN` or `BLOCKED` refuses.
- `AUD-01` — audit rows for deposit create / approve / reject / actor-control denial.
- `AUD-03` — read-audit row for backoffice deposits queue read.

Expected result tags:

- `LDG-02`: `pass` if approve produces exactly one balanced journal entry and derived wallet balance equals the sum of approved deposits.
- `WLT-02`: `pass` only if all four sub-branches pass — deposit creation refused for `FROZEN` end user, deposit creation refused for `BLOCKED` end user, approve refused for pending deposit whose owner is `FROZEN`, approve refused for pending deposit whose owner is `BLOCKED`. `partial` if any sub-branch is missing.
- `AUD-01`: `pass` if each of create / approve / reject / actor-control denial produces an audit row.
- `AUD-03`: `pass` if `GET .../manual-ops/deposits` writes a read-audit row synchronously before returning data.

Foundation regression (not claimed as new evidence by this slice):

- `LDG-99` — global ledger invariants re-checked through `reg_phase03_ledger_reconciliation.sh` after a deposit cycle.

Not claimed by this slice:

- `LDG-03` (withdraw hold/final debit) — withdraw absent.
- `WLT-01` (internal transfer) — transfer absent.
- `WLT-03` (SoF deposit threshold) — high-value path excluded.
- `WLT-04` (two-eyes) — second-actor approval out of scope.

## 10. Linked UI Prototype

- Backoffice manual deposits: `prototypes/ui/06_backoffice_manual_deposits.html` — accepted. Frontend implementation for that screen is **not** in this slice; it is gated to a later frontend slice.
- End-user wallet/deposit: no accepted HTML prototype yet. Frontend implementation for the end-user wallet is therefore already gated and is not blocked by this backend/runtime slice.

Terminology note:

- The end-user `POST /api/v1/deposits` and `GET /api/v1/wallet` and the backoffice `/api/v1/backoffice/manual-ops/deposits*` endpoints are backend artifacts, not user-facing UI.
- Frontend wallet and manual-ops screens remain gated by their respective accepted standalone HTML prototypes.

## 11. Verification Shape

After implementation, run:

- Platform image rebuild:
  - `docker compose -f deploy/docker-compose.yml build platform`
- Platform recreate:
  - `docker compose -f deploy/docker-compose.yml up -d platform`
- Regression scripts:
  - `product/scripts/runtime/reg_phase01_runtime_health.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`
  - `product/scripts/runtime/reg_phase02_read_audit_probe.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`
  - `product/scripts/runtime/reg_phase02_auth_audit.sh`
  - `product/scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh`
  - `product/scripts/runtime/reg_phase03_ledger_append_only.sh`
  - `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`
- New scripts:
  - `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_reject.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_actor_control_block.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

## 12. Open Questions

1. **High-value path scope split**

   Recommendation: keep amount ≥ EUR 10k fully out of this slice and refuse it as `unsupported_high_value` at the API/DB level; SoF declaration, two-eyes approval and `WLT-03`/`WLT-04` move to a later `phase_03_slice_XX_high_value_deposit_controls`.

   Reason: high-value path needs SoF UX, two-eyes role separation and backoffice secondary review — three independent scopes that would inflate this slice and lose runtime verifiability.

2. **System clearing account seeding**

   Recommendation: seed `EXTERNAL_DEPOSIT_CLEARING` ledger account through the V6 migration in an idempotent way (insert if missing) rather than through a runtime endpoint.

   Reason: this account is not test data; it is part of the chart of accounts and must exist before any deposit can post. Migration ownership is honest production behavior, not a test fixture.

3. **`WLT-02` proof scope**

   Recommendation: cover both `FROZEN` and `BLOCKED` states from `identity.actor_controls.state` and both action branches (deposit create and operator approve). Mark `WLT-02` as `pass` only if all four sub-branches pass (create+FROZEN, create+BLOCKED, approve+FROZEN, approve+BLOCKED); mark `partial` otherwise. Treat semantic distinction between freeze and block as later-scope and out of this slice.

   Reason: `runtime_checklists.md` defines `WLT-02` as "frozen/blocked account cannot perform wallet writes", and `identity.actor_controls.state` already permits both values. Proving only `FROZEN` would silently leave a hole. Both states behave identically from this slice's perspective: refuse the wallet write and write an audit row. Distinct UX/semantics belong to compliance/AML slices that own the freeze vs block lifecycle.

4. **Wallet balance computation**

   Recommendation: compute balance live from postings through the existing derived-balance view added in Phase 03 Slice 01; do not store balance on `wallet.wallet_accounts`.

   Reason: ADR-004 makes ledger the source of truth; storing a wallet-side balance would either drift or duplicate the ledger contract.

5. **Frontend gating for backoffice manual deposits**

   Recommendation: keep backoffice manual deposits frontend implementation as a separate later slice gated by the already-accepted `prototypes/ui/06_backoffice_manual_deposits.html`; this slice remains backend/runtime sub-scope only, in line with Phase 02 Slice 02..04 and Phase 03 Slice 01.

   Reason: bundling frontend implementation here would mix two slice shapes and contradict the established backend-first cadence; the prototype is accepted but the frontend slice has its own scope (API client, error handling, RBAC visibility, UX states).

## 13. Next Planned Step

Owner reviews this draft and approves or changes the open questions. Only after approval should product code for Phase 03 Slice 02 start.
