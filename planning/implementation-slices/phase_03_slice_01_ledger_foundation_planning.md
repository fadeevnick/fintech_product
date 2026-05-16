# Phase 03 Slice 01 — Ledger Foundation — Planning Note

Status: **EXECUTED v0.2**.

This document fixes the first implementation slice inside `Phase 03 — Ledger and Wallet Manual Operations`.

It started as a pre-code scope contract. Runtime evidence is recorded separately in `planning/runtime_evidence_log.md`.

---

## 1. Decision

`Phase 03 slice 01`:

```text
Implement the Platform double-entry ledger foundation: accounts, journal entries, postings, stored-procedure-only balanced journal insertion, append-only protections, balance derivation, and retained reconciliation/runtime checks.
```

This means:

- The first Phase 03 slice makes ledger trustworthy before wallet workflows.
- It does not implement deposit, withdraw, internal transfer, card holds, merchant settlement or UI.
- It creates the central money movement primitive that later wallet/payment/card slices must use.
- It targets ledger invariant checks first:
  - unbalanced journal rejection;
  - append-only ledger protection;
  - reconciliation over seeded ledger state.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` says Phase 03 starts by making ledger trustworthy before wallet operations.
- `planning/design-details/adr/adr-004-ledger-central-source-of-truth.md` makes Platform Ledger the authoritative source of truth.
- `planning/design-details/adr/adr-002-sql-first-persistence.md` requires SQL-first persistence for ledger/audit/outbox critical paths.
- `planning/design-details/schema_drafts.md` says balanced-entry enforcement should use a stored-procedure-only insert path.
- Phase 02 now has identity/RBAC/audit/control primitives needed before money writes.

## 3. Exact Scope

In this slice:

1. Add Platform DB migration for:
   - `ledger.accounts`;
   - `ledger.journal_entries`;
   - `ledger.postings`;
   - append-only protections for journal entries and postings;
   - stored procedure or function that inserts a journal and postings only when debits equal credits.
2. Define minimal account model:
   - UUID account id;
   - account type/category needed for seeded runtime checks;
   - currency default `EUR`;
   - owner reference fields where useful, but no wallet account table yet unless needed for seeded ledger accounts.
3. Implement ledger application/repository code:
   - create account if missing;
   - post balanced journal through stored procedure only;
   - reject unbalanced postings;
   - derive account balance from postings;
   - return journal/posting ids for runtime checks.
4. Add internal/runtime-proof API endpoints or application runner hooks:
   - endpoint names must be clearly local/runtime/internal;
   - no public money movement API in this slice.
5. Add retained runtime scripts:
   - unbalanced journal rejection;
   - valid balanced journal and derived balances;
   - append-only protection;
   - reconciliation over seeded ledger state.
6. Record evidence and status after verification.

## 4. Explicitly In Scope

### Code Changes

- Platform Flyway migration for ledger schema/procedure.
- Platform ledger Kotlin package, likely:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/ledger/LedgerModels.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/ledger/LedgerRepository.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/ledger/LedgerService.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/ledger/LedgerController.kt`
- Retained runtime scripts under `product/scripts/runtime/reg_phase03_*`.
- Status/evidence docs after verification.

### Behavioral Outcomes

- Valid balanced journal entries persist atomically.
- Unbalanced journal entries are rejected and not persisted.
- Ledger journal entries and postings are append-only.
- Account balance is derived from postings, not mutated directly.
- Reconciliation script detects any imbalance and passes for seeded state.

### Verification Outcomes

This slice exercises:

- `LDG-01` — unbalanced journal rejection.
- `LDG-04` — ledger append-only protection.
- `LDG-05` — ledger reconciliation script for seeded state.

It may provide a foundation for `LDG-02`, but should not mark deposit postings passed because manual deposit workflow is not implemented yet.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- wallet account creation for end users;
- manual deposits;
- manual withdrawals and holds;
- internal transfers;
- high-value/two-eyes manual operation states;
- frozen wallet write blocking as `WLT-02`;
- card authorization holds;
- merchant settlement;
- public/internal service-token APIs for issuer/acquirer;
- frontend implementation.

Do not add fake wallet/payment flows. If a capability is not in scope, it must remain absent or explicitly return a real unsupported response.

## 6. Proposed Ledger Shape

### 6.1 Accounts

Minimum account fields:

```text
id uuid primary key
code text unique
currency text default 'EUR'
account_type text
normal_side text check in ('DEBIT', 'CREDIT')
owner_type text nullable
owner_id uuid nullable
created_at timestamptz
```

### 6.2 Journal Entry

Minimum journal fields:

```text
id uuid primary key
journal_type text
reference_type text
reference_id uuid
currency text
description text
created_at timestamptz
```

### 6.3 Posting

Minimum posting fields:

```text
id uuid primary key
journal_entry_id uuid references ledger.journal_entries(id)
account_id uuid references ledger.accounts(id)
side text check in ('DEBIT', 'CREDIT')
amount numeric(20,4)
currency text
created_at timestamptz
```

### 6.4 Stored Procedure Contract

Implementation may choose exact SQL function signature, but it must enforce:

- postings are inserted in the same transaction as the journal;
- at least two postings;
- all amounts are positive;
- one currency per journal;
- total debit amount equals total credit amount;
- direct app path cannot insert postings bypassing the function if feasible.

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` after verification
- `product/README.md`

### 7.2 Create

- Platform migration, likely:
  - `product/apps/platform/src/main/resources/db/migration/V5__ledger_foundation.sql`
- Platform ledger code under:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/ledger/**`
- Retained scripts, likely:
  - `product/scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh`
  - `product/scripts/runtime/reg_phase03_ledger_balanced_posting.sh`
  - `product/scripts/runtime/reg_phase03_ledger_append_only.sh`
  - `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved inputs, unless implementation discovers a real mismatch and owner approves an update.
- `planning/runtime_checklists.md` — approved check IDs; do not churn IDs during this slice.
- `product/apps/spa-*` — frontend implementation is out of scope.
- Acquirer/Issuer/Network/Vault behavior.
- Wallet deposit/withdraw/transfer workflows.

## 8. Recommended Change Order

1. Add ledger schema migration and append-only protections.
2. Add stored procedure/function for balanced journal insertion.
3. Add Kotlin ledger repository/service/controller around the stored procedure.
4. Add balance derivation query.
5. Add retained runtime scripts.
6. Rebuild Platform image and recreate `platform`.
7. Run Phase 01/02 regression subset:
   - runtime health;
   - backoffice OIDC;
   - actor control probes;
   - audit append-only.
8. Run new ledger scripts.
9. Record evidence in `planning/runtime_evidence_log.md`.
10. Update `planning/implementation_status.md`, `README.md`, `product/README.md` and `CURRENT.md`.
11. Commit completed backend/runtime slice separately from future wallet workflows.

## 9. Linked Runtime Checks

This slice targets:

- `LDG-01` — unbalanced journal rejection.
- `LDG-04` — ledger append-only protection.
- `LDG-05` — ledger reconciliation script.

Expected result tags:

- `LDG-01`: `pass` if an unbalanced journal call is rejected and no journal/postings are persisted.
- `LDG-04`: `pass` if direct update/delete attempts against ledger journal/posting tables fail.
- `LDG-05`: `pass` if retained reconciliation proves all persisted journals are balanced and account derivations match postings.

Not claimed:

- `LDG-02` deposit postings.
- `LDG-03` withdraw hold/final debit.
- `WLT-01` internal transfer.
- `WLT-02` frozen account wallet write block.

## 10. Linked UI Prototype

No product frontend implementation is in scope.

Terminology note:

- Ledger proof endpoints/scripts are backend/runtime artifacts, not user-facing UI.
- Frontend wallet screens remain gated by accepted standalone HTML prototypes and later wallet slices.

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
- New scripts:
  - `product/scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh`
  - `product/scripts/runtime/reg_phase03_ledger_balanced_posting.sh`
  - `product/scripts/runtime/reg_phase03_ledger_append_only.sh`
  - `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

## 12. Resolution Notes

1. **Stored procedure as only write path**

   Resolution: accepted. Kotlin application code posts journals through `ledger.post_journal(...)`; direct Kotlin inserts into `ledger.journal_entries` and `ledger.postings` were not added.

   Note: DB append-only triggers protect update/delete. Direct insert bypass prevention can be tightened later with role separation and `SECURITY DEFINER` if needed.

2. **Runtime proof endpoints**

   Resolution: accepted. Added narrow `/internal/ledger/runtime/*` endpoints only for local proof scripts until real internal service-token APIs are introduced.

   Note: these are not public money movement APIs.

3. **Account seed strategy**

   Resolution: accepted. Runtime scripts create isolated accounts per run through the internal runtime endpoint.

   Note: no static ledger account seed data was added for wallet/payment workflows.

4. **Result tags**

   Resolution: accepted. This slice marks only `LDG-01`, `LDG-04` and `LDG-05`.

   Note: balanced posting evidence is recorded as ledger foundation evidence only; `LDG-02` remains unclaimed.

## 13. Next Planned Step

Draft the next Phase 03 wallet/manual-operation slice before writing more product code.
