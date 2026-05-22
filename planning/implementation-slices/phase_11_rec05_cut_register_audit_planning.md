# Phase 11 — REC-05 Cut Register Final Audit — Planning Note

Status: **EXECUTED v0.1 — static audit pass runtime verified**.

This document fixes the REC-05 cut register audit pass for the mini-fintech-platform codebase as of 2026-05-22.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after execution.

---

## 1. Decision

`Phase 11 REC-05`:

```text
Run a static audit of the production source tree to confirm no hidden fake implementations remain.
Produce an honest register of every known placeholder, local-mode stub and proof endpoint.
No new product code is written.
```

## 2. Audit Method

The retained script `product/scripts/runtime/reg_phase11_cut_register_audit.sh`:

1. Greps production Kotlin source under `src/main/` for undocumented fake indicators (`TODO`, `FIXME`, `HACK`, `XXX`, `fake`, `stub`, `mock`). Any match is an immediate fail.
2. Confirms each known honest placeholder is present exactly as documented — correct file, correct property default, correct behaviour gate.
3. Emits the full cut register table to stdout.
4. Emits a final `REC-05 cut register audit pass` line on success.

## 3. Known Honest Placeholders — Cut Register

Every item below is a documented, intentional placeholder. Nothing in this list is hidden.

### 3.1 Vendor credential gaps

| Ref | Description | Evidence |
|---|---|---|
| `MRC-01` | No Stripe Connect onboarding start. Real `Account.create` / `AccountLink.create` calls do not exist. `merchant.stripe_account_links` rows are only seeded by retained runtime scripts for webhook verification. | Phase 04 Slice 02 planning note; no Stripe client SDK in production code. |
| `KYC-01` partial | Sumsub KYC start returns `sumsub_not_configured` when `SUMSUB_APP_TOKEN` is absent. No fake vendor success. Real Sumsub API is called only when both credentials are configured. | `SumsubProperties.kt`; `SumsubClient.kt` credential guard. |

### 3.2 Local-mode adapters (dev/test tooling in production code)

| Ref | Description | Default | Evidence |
|---|---|---|---|
| OpenSanctions local mode | `OpenSanctionsClient` checks `OPENSANCTIONS_LOCAL_MODE` before making real HTTP calls. Default is `disabled`, which means real HTTP calls are attempted. Local modes `no_match`, `match`, `unavailable`, `timeout` are only active when the env var is explicitly set to a non-`disabled` value. | `disabled` (real path) | `OpenSanctionsProperties.kt` line 10; `docker-compose.yml` `OPENSANCTIONS_LOCAL_MODE: ${OPENSANCTIONS_LOCAL_MODE:-disabled}`. |

### 3.3 Runtime proof / smoke endpoints (internal only)

These endpoints were created for Phase 01/03 runtime verification scripts. They are protected by the service-auth header and are not reachable from public routes.

| Endpoint | Service | Purpose |
|---|---|---|
| `GET /internal/runtime/kafka` | platform | Phase 01 RUN-03 Kafka connectivity smoke |
| `POST /internal/ledger/runtime/accounts` | platform | Phase 03 LDG-01/LDG-02 ledger proof |
| `POST /internal/ledger/runtime/journals` | platform | Phase 03 LDG-01/LDG-02 ledger proof |
| `GET /internal/ledger/runtime/accounts/{id}/balance` | platform | Phase 03 ledger proof |
| `GET /internal/ledger/runtime/reconciliation` | platform | Phase 03 LDG-05 reconciliation (still used by retained scripts) |

### 3.4 Local default secrets

All secrets use `change-me` / `local-*` default values that are safe for local Docker development and must be overridden in any deployed environment. None are hardcoded in production Kotlin source; all are externalized via environment variables.

| Env var | Default |
|---|---|
| `SERVICE_AUTH_SECRET` | `local-service-secret` |
| `VAULT_PAN_ENCRYPTION_KEY` | `local-vault-pan-key-change-me` |
| `WEBHOOK_SIGNING_SECRET_ENCRYPTION_KEY` | `local-webhook-signing-secret-key-change-me` |
| `STRIPE_WEBHOOK_SIGNING_SECRET` | `whsec_local_test_secret` |
| `SUMSUB_WEBHOOK_SECRET` | `local-sumsub-webhook-secret` |

### 3.5 Test BIN

`VAULT_TEST_BIN` defaults to `400000` (a real Visa BIN range used for local card issuance). Environment-configurable.

### 3.6 Frontend scaffolds

All three SPAs (`spa-enduser`, `spa-merchant`, `spa-backoffice`) are build-verified shells with minimal checkpoint UIs. Full SPA implementation is Phase 10, explicitly deferred throughout every implementation slice. Phase 10 checks `UI-02`..`UI-05`, `UI-99` are not claimed.

## 4. Runtime Verification

Target check: `REC-05` — no hidden fake implementations remain.

The retained script must:
1. Find zero `TODO`/`FIXME`/`HACK`/`XXX` hits in production Kotlin.
2. Find zero `fake`/`stub`/`mock` hits in production Kotlin.
3. Confirm `SumsubProperties` credential guard is present.
4. Confirm `OpenSanctionsProperties` local mode default is `disabled`.
5. Confirm no Stripe account-creation client code exists in production Kotlin.
6. Emit the cut register table.
7. Emit `REC-05 cut register audit pass`.

No targeted regressions — this is a standalone static audit script.

## 5. Explicitly Out Of Scope

- `REC-03` vendor reconciliation;
- `REC-04` dashboards;
- `LDG-99` / `AUD-99` cross-phase sweeps;
- any new product code;
- frontend `UI-*`.
