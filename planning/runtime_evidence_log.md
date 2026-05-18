# Runtime Evidence Log

Last updated: 2026-05-18.

This file records factual verification only. A runtime check is not marked passed unless the corresponding runtime command actually ran.

---

## 2026-05-15 — Phase 01 Slice 01 Static Verification

Scope:
- Product skeleton files created.
- Docker runtime not started.

Commands run:
- `docker compose -f deploy/docker-compose.yml config --quiet` from `product/`.
- Node JSON parsing for root/frontend `package.json` files.
- File existence checks for service baseline migrations.
- Executable checks for Phase 01 runtime scripts.

Evidence:
- Docker Compose configuration parsed successfully.
- Package JSON manifests parsed successfully.
- Five baseline Flyway migrations exist:
  - `product/apps/platform/src/main/resources/db/migration/V1__baseline.sql`
  - `product/apps/acquirer/src/main/resources/db/migration/V1__baseline.sql`
  - `product/apps/network/src/main/resources/db/migration/V1__baseline.sql`
  - `product/apps/issuer/src/main/resources/db/migration/V1__baseline.sql`
  - `product/apps/vault/src/main/resources/db/migration/V1__baseline.sql`
- Nine Phase 01 scripts exist and are executable:
  - `product/scripts/runtime/reg_phase01_runtime_health.sh`
  - `product/scripts/runtime/reg_phase01_migrations.sh`
  - `product/scripts/runtime/reg_phase01_kafka.sh`
  - `product/scripts/runtime/reg_phase01_keycloak.sh`
  - `product/scripts/runtime/reg_phase01_object_storage.sh`
  - `product/scripts/runtime/reg_phase01_logs.sh`
  - `product/scripts/runtime/reg_phase01_metrics.sh`
  - `product/scripts/runtime/reg_phase01_traces.sh`
  - `product/scripts/runtime/reg_phase01_spa_shells.sh`

Not claimed:
- `RUN-01` through `RUN-08` are not marked passed.
- `UI-01` is not marked passed.

Historical blocker:
- `docker compose -f deploy/docker-compose.yml build platform` failed before build execution because this session cannot access `/var/run/docker.sock`.
- `sudo -n docker ps` also failed because sudo requires a password.
- This was later resolved by running Docker commands through Codex escalation; see runtime verification entry below.

Next planned step at that time:
- Resolve Docker execution path, then run Docker build/runtime checks and append real `RUN-*` / `UI-01` evidence.

---

## 2026-05-15 — Phase 01 Slice 01 Runtime Verification

Scope:
- Full Phase 01 Slice 01 local runtime verification.
- Stack started from `product/deploy/docker-compose.yml`.

Preconditions:
- Docker commands run with Codex escalation because normal sandboxed Docker CLI cannot access the daemon.
- `product/` skeleton exists.

Build evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml build acquirer network issuer vault spa-enduser spa-merchant spa-backoffice` passed.

Runtime evidence:
- `docker compose -f deploy/docker-compose.yml up -d` passed after replacing unavailable `bitnami/kafka:3.9` with `apache/kafka:3.9.1`.
- `docker compose -f deploy/docker-compose.yml ps` shows all Phase 01 containers up; service DB containers are healthy.

Runtime script evidence:
- `product/scripts/runtime/reg_phase01_runtime_health.sh`:
  - `RUN-01` — pass.
  - all five backend services returned health responses.
- `product/scripts/runtime/reg_phase01_migrations.sh`:
  - `RUN-02` — pass.
  - all five service DBs contain successful Flyway baseline migration state and runtime marker rows.
- `product/scripts/runtime/reg_phase01_kafka.sh`:
  - `RUN-03` — pass.
  - `platform` backend connected to Kafka via AdminClient metadata check; Kafka CLI metadata command also succeeded.
- `product/scripts/runtime/reg_phase01_keycloak.sh`:
  - `RUN-04` — pass.
  - Keycloak OIDC discovery endpoint returned issuer metadata.
- `product/scripts/runtime/reg_phase01_object_storage.sh`:
  - `RUN-05` — pass.
  - SeaweedFS S3 endpoint accepted smoke bucket/object write and returned the same object payload on read.
- `product/scripts/runtime/reg_phase01_logs.sh`:
  - `RUN-06` — pass.
  - Vector health endpoint responded and Loki query returned a `platform` container log stream with `runtime="mini-fintech-platform"`.
- `product/scripts/runtime/reg_phase01_metrics.sh`:
  - `RUN-07` — pass.
  - Prometheus readiness endpoint passed and `platform` `/actuator/prometheus` exposed JVM metrics.
- `product/scripts/runtime/reg_phase01_traces.sh`:
  - `RUN-08` — pass-indirect.
  - OTel Collector health endpoint and Tempo readiness endpoint passed.
  - Remaining gap: no backend application span is emitted yet; later slices should add app-level OTel instrumentation evidence.
- `product/scripts/runtime/reg_phase01_spa_shells.sh`:
  - `UI-01` — pass.
  - end-user, merchant and backoffice SPA shells are served through nginx containers on ports `3000`, `3001`, `3002`.

Final full-suite command:

```bash
scripts/runtime/reg_phase01_runtime_health.sh &&
scripts/runtime/reg_phase01_migrations.sh &&
scripts/runtime/reg_phase01_kafka.sh &&
scripts/runtime/reg_phase01_keycloak.sh &&
scripts/runtime/reg_phase01_object_storage.sh &&
scripts/runtime/reg_phase01_logs.sh &&
scripts/runtime/reg_phase01_metrics.sh &&
scripts/runtime/reg_phase01_traces.sh &&
scripts/runtime/reg_phase01_spa_shells.sh
```

Final full-suite output:

```text
RUN-01 network health pass
RUN-01 issuer health pass
RUN-01 acquirer health pass
RUN-01 platform health pass
RUN-01 vault health pass
RUN-02 network migration pass
RUN-02 issuer migration pass
RUN-02 acquirer migration pass
RUN-02 platform migration pass
RUN-02 vault migration pass
RUN-03 kafka connectivity pass
RUN-04 keycloak connectivity pass
RUN-05 seaweedfs connectivity pass
RUN-06 logs pipeline pass
RUN-07 metrics pipeline pass
RUN-08 traces pipeline pass-indirect
UI-01 enduser spa shell pass
UI-01 merchant spa shell pass
UI-01 backoffice spa shell pass
```

Next planned step:
- Draft the first Phase 02 implementation slice planning note before writing identity/auth code.

---

## 2026-05-15 — Phase 02 Slice 01 Runtime Verification

Scope:
- End-user identity foundation in Platform.
- Minimal end-user login/register UI prototype checkpoint.

Preconditions:
- Phase 01 local compose stack was already running.
- Docker commands run with Codex escalation because normal sandboxed Docker CLI cannot access the daemon.
- `planning/implementation-slices/phase_02_slice_01_identity_foundation_planning.md` — APPROVED v0.1.

Build evidence:
- `docker compose -f deploy/docker-compose.yml build platform spa-enduser` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform spa-enduser` passed.

Phase 01 regression evidence:
- `product/scripts/runtime/reg_phase01_runtime_health.sh` — `RUN-01` pass.
- `product/scripts/runtime/reg_phase01_migrations.sh` — `RUN-02` pass.
- `product/scripts/runtime/reg_phase01_logs.sh` — `RUN-06` pass.
- `product/scripts/runtime/reg_phase01_metrics.sh` — `RUN-07` pass.
- `product/scripts/runtime/reg_phase01_spa_shells.sh` — `UI-01` pass.

Phase 02 runtime script evidence:
- `product/scripts/runtime/reg_phase02_enduser_auth.sh`:
  - `AUTH-01` — pass.
  - Registered a new end user, used local verification token, verified email, logged in with `MFP_SESSION`, and authenticated `GET /api/v1/enduser/me`.
- `product/scripts/runtime/reg_phase02_protected_endpoint.sh`:
  - `AUTH-05` — partial.
  - Unauthenticated `GET /api/v1/enduser/me` returned 401 with `unauthenticated` error code.
  - Remaining gap: wrong-role checks require merchant/backoffice roles from later Phase 02 slices.
- `product/scripts/runtime/reg_phase02_auth_audit.sh`:
  - `AUD-01` — pass.
  - Auth audit events exist for registration, email verification and login success.
  - `AUD-02` — pass.
  - Direct `UPDATE audit.audit_log ...` is rejected by DB trigger with `audit.audit_log is append-only`.
- `product/scripts/runtime/reg_phase02_email_uniqueness.sh`:
  - `AUTH-04` — partial.
  - Duplicate end-user email registration is rejected through the global `identity.email_reservations` mechanism.
  - Remaining gap: full cross-pool proof requires merchant/backoffice identity pools from later Phase 02 slices.

Final Phase 02 script output:

```text
AUTH-01 end-user register verify login me pass
AUTH-05 protected endpoint denial partial
AUD-01 auth audit events pass
AUD-02 audit append-only protection pass
AUTH-04 global email uniqueness foundation partial
```

Final stack evidence:
- `docker compose -f deploy/docker-compose.yml ps` shows all Phase 01 containers running.
- `platform` and `spa-enduser` were recreated from Phase 02 images.
- Platform DB smoke counts after scripts: `identity.end_users = 2`, `audit.audit_log = 4`.

Next planned step:
- Draft Phase 02 Slice 02 planning note for the next identity/RBAC segment before writing more product code.

---

## 2026-05-16 — Phase 02 Slice 02 Backend/Runtime Verification

Scope:
- Merchant identity backend/runtime sub-scope in Platform.
- No `spa-merchant` frontend implementation was added in this verification; frontend work remains gated by accepted standalone HTML prototypes.

Preconditions:
- Phase 01 local compose stack was already running.
- Docker commands run with Codex escalation because normal sandboxed Docker CLI cannot access the daemon.
- `prototypes/ui/02_merchant_auth.html` exists as the standalone `MDB-UI-01` visual prototype artifact.

Build evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- Platform startup applied `V3__merchant_identity_foundation.sql` through Flyway.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

Phase 01 regression evidence:
- `product/scripts/runtime/reg_phase01_runtime_health.sh`:
  - `RUN-01` — pass.
  - all five backend services returned health responses.

Phase 02 regression and slice script evidence:
- `product/scripts/runtime/reg_phase02_enduser_auth.sh`:
  - `AUTH-01` — pass.
  - Existing end-user register/verify/login/me flow still works after generalized session actor handling.
- `product/scripts/runtime/reg_phase02_protected_endpoint.sh`:
  - `AUTH-05` — partial.
  - Unauthenticated `GET /api/v1/enduser/me` still returns 401 with `unauthenticated`.
- `product/scripts/runtime/reg_phase02_email_uniqueness.sh`:
  - `AUTH-04` — partial regression.
  - Duplicate end-user registration is still rejected through `identity.email_reservations`.
- `product/scripts/runtime/reg_phase02_merchant_auth.sh`:
  - `AUTH-02` — pass.
  - Registered merchant, received local verification token, rejected unverified login, verified merchant employee email, logged in with `MFP_SESSION`, and authenticated `GET /api/v1/merchant/me`.
- `product/scripts/runtime/reg_phase02_cross_pool_email_uniqueness.sh`:
  - `AUTH-04` — pass for end-user vs merchant employee pools.
  - End-user email cannot be reused for merchant registration, and merchant employee email cannot be reused for end-user registration.
- `product/scripts/runtime/reg_phase02_cross_role_denial.sh`:
  - `AUTH-05` — partial.
  - End-user session is rejected by `GET /api/v1/merchant/me` with 403 `forbidden_actor_type`.
  - Merchant employee session is rejected by `GET /api/v1/enduser/me` with 403 `forbidden_actor_type`.
  - Remaining gap: full `AUTH-05` waits for backoffice OIDC/RBAC.
- `product/scripts/runtime/reg_phase02_auth_audit.sh`:
  - `AUD-01` — pass.
  - Existing end-user auth audit rows still exist.
  - `AUD-02` — pass.
  - Direct `UPDATE audit.audit_log ...` is rejected by DB trigger with `audit.audit_log is append-only`.
- `product/scripts/runtime/reg_phase02_merchant_auth_audit.sh`:
  - `AUD-01` — pass.
  - Merchant registration, email verification, login success and login failure audit rows exist.

Final Phase 02 script output:

```text
AUTH-02 merchant register verify login me pass
AUD-01 merchant auth audit events pass
AUTH-01 end-user register verify login me pass
AUTH-05 protected endpoint denial partial
AUTH-04 global email uniqueness foundation partial
AUTH-04 end-user merchant cross-pool email uniqueness pass
AUTH-05 end-user merchant wrong-role denial partial
AUD-01 auth audit events pass
AUD-02 audit append-only protection pass
RUN-01 network health pass
RUN-01 issuer health pass
RUN-01 acquirer health pass
RUN-01 platform health pass
RUN-01 vault health pass
```

Implementation note:
- An audit rollback bug was found during verification: expected auth failures wrote audit rows inside a transaction that rolled back with `IdentityException`.
- Fixed by setting `noRollbackFor = [IdentityException::class]` on end-user and merchant login flows, then rebuilding/recreating `platform` and rerunning the checks above.

Next planned step:
- Draft the next non-frontend Phase 02 slice while frontend implementation remains gated by accepted standalone HTML prototypes.

---

## 2026-05-16 — Phase 02 Slice 03 Backoffice OIDC/RBAC Runtime Verification

Scope:
- Backoffice OIDC token authentication against local Keycloak.
- Platform role mapping for backoffice RBAC roles.
- Minimal protected backoffice identity endpoint.
- No backoffice SPA/frontend implementation was added in this verification; frontend work remains gated by accepted standalone HTML prototypes.

Preconditions:
- Phase 01 local compose stack was already running.
- Docker commands run with Codex escalation because normal sandboxed Docker CLI cannot access the daemon.
- `planning/implementation-slices/phase_02_slice_03_backoffice_oidc_rbac_planning.md` was approved by owner with `го`.
- `prototypes/ui/04_backoffice_oidc_login.html` exists as the standalone `BOF-UI-01` visual prototype artifact.

Build evidence:
- `docker compose -f deploy/docker-compose.yml config --quiet` passed.
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

Implementation evidence:
- Platform now validates bearer JWTs for `/api/v1/backoffice/**` through Keycloak JWKS.
- Existing end-user and merchant cookie endpoints remain app-handled and are not converted to Spring Security sessions.
- Runtime helper `product/scripts/runtime/lib_phase02_backoffice_keycloak.sh` upserts local Keycloak realm/client/users/roles through Keycloak admin API.

Phase 01 regression evidence:
- `product/scripts/runtime/reg_phase01_keycloak.sh`:
  - `RUN-04` — pass.
- `product/scripts/runtime/reg_phase01_runtime_health.sh`:
  - `RUN-01` — pass.

Phase 02 regression and slice script evidence:
- `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`:
  - `AUTH-03` — pass.
  - Local Keycloak realm/client/users/roles were upserted, a backoffice token was acquired, and `GET /api/v1/backoffice/me` returned `backoffice_operator`.
- `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh`:
  - `AUTH-05` — pass.
  - Missing bearer token returned 401 with `unauthenticated`.
  - Valid token without an approved backoffice role returned 403 with `forbidden_role`.
  - End-user and merchant cookie sessions were rejected by `GET /api/v1/backoffice/me` with 401 `unauthenticated`.
- `product/scripts/runtime/reg_phase02_backoffice_auth_audit.sh`:
  - `AUD-01` — pass.
  - Backoffice auth success and role-denial failure audit rows exist.
- `product/scripts/runtime/reg_phase02_enduser_auth.sh`:
  - `AUTH-01` — pass regression.
- `product/scripts/runtime/reg_phase02_merchant_auth.sh`:
  - `AUTH-02` — pass regression.
- `product/scripts/runtime/reg_phase02_cross_role_denial.sh`:
  - `AUTH-05` — pass regression for end-user/merchant wrong-role denial.
- `product/scripts/runtime/reg_phase02_auth_audit.sh`:
  - `AUD-01` — pass regression.
  - `AUD-02` — pass regression.
- `product/scripts/runtime/reg_phase02_merchant_auth_audit.sh`:
  - `AUD-01` — pass regression.

Final Phase 02 script output:

```text
AUTH-03 backoffice OIDC role mapping pass
AUTH-05 backoffice unauthenticated and wrong-role denial pass
AUD-01 backoffice auth audit events pass
RUN-04 keycloak connectivity pass
RUN-01 network health pass
RUN-01 issuer health pass
RUN-01 acquirer health pass
RUN-01 platform health pass
RUN-01 vault health pass
AUTH-01 end-user register verify login me pass
AUTH-02 merchant register verify login me pass
AUTH-05 end-user merchant wrong-role denial partial
AUD-01 auth audit events pass
AUD-02 audit append-only protection pass
AUD-01 merchant auth audit events pass
```

Implementation note:
- Initial Keycloak direct-grant token request failed with `Account is not fully set up`.
- Fixed the retained seed helper to explicitly set `firstName`, `lastName`, `emailVerified`, empty `requiredActions`, and reset a non-temporary password for each smoke user.

Next planned step:
- Draft the next non-frontend Phase 02 slice for the remaining identity/audit foundation work, while frontend implementation remains gated by accepted standalone HTML prototypes.

---

## 2026-05-16 — Phase 02 Slice 04 Read-Audit and Actor Controls Runtime Verification

Scope:
- Read-audit backend primitive for future compliance-sensitive reads.
- Generic end-user and merchant actor controls.
- Write-guard probe for blocked/frozen actors.
- No wallet, payment, KYC, AML, sanctions, audit viewer or frontend implementation was added.

Preconditions:
- Phase 01 local compose stack was already running.
- Docker commands run with Codex escalation because normal sandboxed Docker CLI cannot access the daemon.
- `planning/implementation-slices/phase_02_slice_04_read_audit_account_controls_planning.md` was approved by owner with `го`.

Build evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- Platform startup applied `V4__read_audit_actor_controls.sql` through Flyway.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

Implementation evidence:
- Added append-only `audit.read_audit_log`.
- Added `identity.actor_controls` for `END_USER` and `MERCHANT` actor states.
- Added backoffice read-audit probe endpoint:
  - `GET /api/v1/backoffice/read-audit/probe/{resourceId}`
- Added backoffice actor control mutation endpoint:
  - `POST /api/v1/backoffice/actor-controls`
- Added write-guard probe endpoints:
  - `POST /api/v1/enduser/write-guard/probe`
  - `POST /api/v1/merchant/write-guard/probe`

Phase 02 slice script evidence:
- `product/scripts/runtime/reg_phase02_read_audit_probe.sh`:
  - `AUD-03` — partial/foundation.
  - Backoffice read-audit probe wrote one `audit.read_audit_log` row before the response was accepted.
  - `AUD-99` — partial/foundation.
  - Direct `UPDATE audit.read_audit_log ...` is rejected by DB trigger with `audit.audit_log is append-only`.
- `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`:
  - actor-control precursor — pass.
  - Active end-user session passed write-guard probe.
  - Backoffice set end-user control to `FROZEN`.
  - Frozen end-user write-guard probe returned 403 with `actor_control_blocked`.
  - Backoffice restored state to `ACTIVE`; probe passed again.
  - Control changes and denied probe wrote audit rows.
- `product/scripts/runtime/reg_phase02_actor_control_merchant.sh`:
  - actor-control precursor — pass.
  - Active merchant session passed write-guard probe.
  - Backoffice set merchant control to `BLOCKED`.
  - Blocked merchant write-guard probe returned 403 with `actor_control_blocked`.
  - Backoffice restored state to `ACTIVE`; probe passed again.
  - Control changes and denied probe wrote audit rows.

Regression evidence:
- `product/scripts/runtime/reg_phase01_runtime_health.sh`:
  - `RUN-01` — pass.
- `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`:
  - `AUTH-03` — pass.
- `product/scripts/runtime/reg_phase02_enduser_auth.sh`:
  - `AUTH-01` — pass.
- `product/scripts/runtime/reg_phase02_merchant_auth.sh`:
  - `AUTH-02` — pass.
- `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh`:
  - `AUTH-05` — pass.
- `product/scripts/runtime/reg_phase02_auth_audit.sh`:
  - `AUD-01` — pass.
  - `AUD-02` — pass.
- `product/scripts/runtime/reg_phase02_backoffice_auth_audit.sh`:
  - `AUD-01` — pass.

Final Phase 02 script output:

```text
AUD-03 read-audit primitive partial
AUD-99 read-audit append-only foundation partial
ACT-CTRL end-user blocked write-guard probe pass
ACT-CTRL merchant blocked write-guard probe pass
RUN-01 network health pass
RUN-01 issuer health pass
RUN-01 acquirer health pass
RUN-01 platform health pass
RUN-01 vault health pass
AUTH-03 backoffice OIDC role mapping pass
AUTH-01 end-user register verify login me pass
AUTH-02 merchant register verify login me pass
AUTH-05 backoffice unauthenticated and wrong-role denial pass
AUD-01 auth audit events pass
AUD-02 audit append-only protection pass
AUD-01 backoffice auth audit events pass
```

Result tag notes:
- `AUD-03` remains partial/foundation because the primitive is real, but real compliance-sensitive reads arrive in later phases.
- `AUD-99` remains partial/foundation until all sensitive read paths exist.
- `WLT-02` is not claimed; this slice proves only the actor-control hook and write-guard probe, not real wallet writes.

Next planned step:
- Draft the next non-frontend implementation slice.

---

## 2026-05-16 — Phase 03 Slice 01 Ledger Foundation Runtime Verification

Scope:
- Platform double-entry ledger foundation.
- Ledger accounts, journal entries and postings.
- Stored-procedure-only supported journal insertion path from application code.
- Append-only protection for journal entries and postings.
- Balance derivation and reconciliation proof.
- No wallet, deposit, withdrawal, transfer, card/payment hold, merchant settlement or frontend implementation was added.

Preconditions:
- Phase 01 local compose stack was already running.
- Docker/runtime commands run with Codex escalation where needed because normal sandboxed scripts cannot access the local Docker daemon or localhost runtime consistently.
- `planning/implementation-slices/phase_03_slice_01_ledger_foundation_planning.md` was approved by owner with `го`.

Build evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- Platform startup applied `V5__ledger_foundation.sql` through Flyway.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

Implementation evidence:
- Added `ledger.accounts`.
- Added `ledger.journal_entries`.
- Added `ledger.postings`.
- Added append-only DB triggers rejecting update/delete on ledger journal/posting tables.
- Added SQL function `ledger.post_journal(...)` enforcing at least two postings, positive amounts, single currency, existing same-currency accounts and debit/credit equality.
- Added `ledger.account_balances` view deriving balances from postings.
- Added narrow local runtime proof endpoints:
  - `POST /internal/ledger/runtime/accounts`
  - `POST /internal/ledger/runtime/journals`
  - `GET /internal/ledger/runtime/accounts/{accountId}/balance`
  - `GET /internal/ledger/runtime/reconciliation`

Phase 03 slice script evidence:
- `product/scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh`:
  - `LDG-01` — pass.
  - Unbalanced journal was rejected with `ledger_journal_unbalanced`.
  - No journal row persisted for the rejected reference id.
- `product/scripts/runtime/reg_phase03_ledger_balanced_posting.sh`:
  - ledger foundation balanced posting — pass.
  - Balanced journal persisted atomically with two postings.
  - Derived balances returned `10.0000` for both normal-side test accounts.
  - This is foundation evidence only and does not claim `LDG-02`.
- `product/scripts/runtime/reg_phase03_ledger_append_only.sh`:
  - `LDG-04` — pass.
  - Direct journal update and posting delete attempts were rejected with `ledger tables are append-only`.
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`:
  - `LDG-05` — pass.
  - Runtime reconciliation returned balanced journals.
  - DB cross-check found zero imbalanced journals.

Regression evidence:
- `product/scripts/runtime/reg_phase01_runtime_health.sh`:
  - `RUN-01` — pass.
- `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`:
  - `AUTH-03` — pass.
- `product/scripts/runtime/reg_phase02_read_audit_probe.sh`:
  - `AUD-03` — partial/foundation still passes.
  - `AUD-99` — partial/foundation still passes.
- `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`:
  - actor-control precursor — pass.
- `product/scripts/runtime/reg_phase02_auth_audit.sh`:
  - `AUD-01` — pass.
  - `AUD-02` — pass.

Final Phase 03 script output:

```text
LDG-01 unbalanced journal rejection pass
LDG foundation balanced posting and derived balances pass
LDG-04 ledger append-only protection pass
LDG-05 ledger reconciliation pass
RUN-01 network health pass
RUN-01 issuer health pass
RUN-01 acquirer health pass
RUN-01 platform health pass
RUN-01 vault health pass
AUTH-03 backoffice OIDC role mapping pass
AUD-03 read-audit primitive partial
AUD-99 read-audit append-only foundation partial
ACT-CTRL end-user blocked write-guard probe pass
AUD-01 auth audit events pass
AUD-02 audit append-only protection pass
```

Result tag notes:
- `LDG-01` is passed.
- `LDG-04` is passed.
- `LDG-05` is passed.
- `LDG-02`, `LDG-03`, `WLT-01` and `WLT-02` are not claimed; real wallet/deposit/withdraw/transfer workflows do not exist yet.

Next planned step:
- Draft the next Phase 03 wallet/manual-operation slice, likely wallet account creation and manual deposit request lifecycle, while product frontend implementation remains gated by accepted standalone HTML prototypes.

---

## 2026-05-16 — Phase 03 Slice 02 Wallet Manual Deposit Runtime Verification

Scope:
- Backend/runtime sub-scope of Phase 03 Slice 02.
- Targets `LDG-02`, `WLT-02`, `AUD-01`, `AUD-03`, and Phase 01..03 regression subset.
- No frontend work in this slice.

Preconditions:
- `git rev-parse HEAD` before this verification = `1d3c811` (draft slice commit; product code uncommitted at verification time, committed in the slice execution commit that records this evidence).
- Migration `V6__wallet_manual_deposit.sql` applied at platform boot (Flyway log: `Migrating schema "public" to version "6 - wallet manual deposit"` → `Successfully applied 1 migration ... now at version v6`).
- Local docker stack up (`docker compose -f deploy/docker-compose.yml ps`), platform health endpoint returned `{"service":"platform","status":"UP"}` on `http://127.0.0.1:8081/internal/health`.
- Host-to-bridge connectivity was previously broken (firewalld wiped docker iptables chains); restored by `sudo systemctl restart docker` before verification ran. No code change related to this fix.

Build evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed; final image `mini-fintech-platform-platform` rebuilt.
- `docker compose -f deploy/docker-compose.yml up -d platform` recreated only the platform container; other services unchanged.

New Phase 03 Slice 02 script evidence:
- `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh`:
  - `LDG-02` — pass.
  - `AUD-01` — pass (deposit create + deposit approve audit rows).
  - `AUD-03` — pass (read-audit row for `WALLET_DEPOSIT_REQUEST` written synchronously when operator listed pending deposit queue while deposit was still `PENDING_OPERATOR_REVIEW`).
  - Concrete observations:
    - End-user `POST /api/v1/deposits` with amount `12.0000` returned state `PENDING_OPERATOR_REVIEW`.
    - Operator `GET /api/v1/backoffice/manual-ops/deposits` returned the deposit in pending queue.
    - Operator `POST .../{id}/decision` with `APPROVE` returned state `COMPLETED` and `journalEntryId`.
    - DB cross-check: ledger journal row exists for the deposit; exactly 2 postings exist; debit total = credit total = `12.0000`; debit account code = `EXTERNAL_DEPOSIT_CLEARING`; credit account code = `WALLET_USER:<userId>`.
    - End-user `GET /api/v1/wallet` returned derived `balance = "12.0000"` and deposit state `COMPLETED`.
    - Reconciliation script after the deposit cycle returned `balancedJournals=true, imbalancedJournalCount=0`.
- `product/scripts/runtime/reg_phase03_wallet_deposit_reject.sh`:
  - Deposit reject path — pass.
  - Concrete observations:
    - Operator `POST .../{id}/decision` with `REJECT` returned state `REJECTED` and `journalEntryId = null`.
    - DB cross-check: zero ledger postings exist for the rejected deposit reference.
    - End-user `GET /api/v1/wallet` returned `balance = "0.0000"`, deposit state `REJECTED`.
    - Audit row `wallet.deposit_rejected` exists for the deposit.
- `product/scripts/runtime/reg_phase03_wallet_deposit_actor_control_block.sh`:
  - `WLT-02` — pass (all four sub-branches).
  - Sub-branches:
    - create + `FROZEN`: end-user `POST /api/v1/deposits` returned HTTP 403 with body code `actor_control_blocked`; zero deposit rows persisted for this user; `identity.actor_control_write_denied` audit row present.
    - create + `BLOCKED`: same observations as `FROZEN` create branch.
    - approve + `FROZEN`: operator `POST .../{id}/decision APPROVE` returned HTTP 403 with body code `actor_control_blocked`; deposit remained in `PENDING_OPERATOR_REVIEW`; zero ledger postings for this deposit reference.
    - approve + `BLOCKED`: same observations as `FROZEN` approve branch.
- `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh`:
  - Double-decision idempotency — pass.
  - Concrete observations:
    - First `APPROVE` returned state `COMPLETED`.
    - Second `APPROVE` on the same deposit returned HTTP 409 with body code `deposit_already_decided`.
    - Late `REJECT` on the completed deposit also returned HTTP 409.
    - DB cross-check: exactly one `wallet.deposit_approved` audit row and exactly one ledger journal entry exist for the deposit.

Foundation regression (re-run with new wallet code present):
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`:
  - `LDG-05` — pass (`LDG-99` foundation regression).

Phase 01/02/03 regression subset evidence:
- `product/scripts/runtime/reg_phase01_runtime_health.sh`:
  - `RUN-01` — pass (all five backend services healthy after restart).
- `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`:
  - `AUTH-03` — pass.
- `product/scripts/runtime/reg_phase02_read_audit_probe.sh`:
  - `AUD-03` — partial/foundation still passes.
  - `AUD-99` — partial/foundation still passes.
- `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`:
  - actor-control precursor — pass.
- `product/scripts/runtime/reg_phase02_auth_audit.sh`:
  - `AUD-01` — pass.
  - `AUD-02` — pass.
- `product/scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh`:
  - `LDG-01` — pass.
- `product/scripts/runtime/reg_phase03_ledger_append_only.sh`:
  - `LDG-04` — pass.

Final Phase 03 Slice 02 script output:

```text
WLT deposit happy path pass
WLT deposit reject pass
WLT-02 actor-control block (FROZEN+BLOCKED, create+approve) pass
WLT deposit double decision pass
LDG-05 ledger reconciliation pass
RUN-01 network/issuer/acquirer/platform/vault health pass
AUTH-03 backoffice OIDC role mapping pass
AUD-03 read-audit primitive partial
AUD-99 read-audit append-only foundation partial
ACT-CTRL end-user blocked write-guard probe pass
AUD-01 auth audit events pass
AUD-02 audit append-only protection pass
LDG-01 unbalanced journal rejection pass
LDG-04 ledger append-only protection pass
```

Result tag notes:
- `LDG-02` is passed.
- `WLT-02` is passed (all four sub-branches: create+FROZEN, create+BLOCKED, approve+FROZEN, approve+BLOCKED).
- `AUD-01` is passed for wallet deposit create / approve / reject and actor-control denial audit rows.
- `AUD-03` is passed for backoffice manual deposits queue read producing synchronous read-audit row.
- `LDG-03`, `WLT-01`, `WLT-03`, `WLT-04` are not claimed; withdraw, transfer, SoF and two-eyes workflows do not exist yet.

Next planned step:
- Draft the next Phase 03 wallet/manual-operation slice (likely manual withdraw with hold/final-debit flow), continuing to gate frontend implementation by accepted standalone HTML prototypes.

---

## 2026-05-16 — Phase 03 Slice 02 Wallet Hardening Verification

Scope:
- Hardening follow-up for Phase 03 Slice 02.
- No new product capability added.
- Focused on structured money amount validation and idempotent lazy wallet provisioning.

Build/runtime evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

New hardening script evidence:
- `product/scripts/runtime/reg_phase03_wallet_deposit_amount_validation.sh`:
  - pass.
  - `POST /api/v1/deposits` with `1.12345` returned HTTP 400 with `invalid_amount`, not an internal error.
  - Non-numeric, zero and negative amounts returned HTTP 400 with `invalid_amount`.
  - `10000.0000` returned HTTP 400 with `unsupported_high_value`.
  - DB cross-check found zero `wallet.deposit_requests` rows for the probing user.
- `product/scripts/runtime/reg_phase03_wallet_provisioning_idempotency.sh`:
  - pass.
  - Before the probe, the user had zero wallet rows and zero `WALLET_USER:<userId>` ledger rows.
  - Eight parallel `GET /api/v1/wallet` calls all returned HTTP 200.
  - DB cross-check found exactly one `wallet.wallet_accounts` row for the user and exactly one `ledger.accounts` row with code `WALLET_USER:<userId>`.
  - Every response referenced the same ledger account id.

Regression evidence:
- `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh` — pass.
- `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh` — pass.
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` — `LDG-05` pass.

Final hardening script output:

```text
WLT deposit amount validation pass
WLT wallet provisioning idempotency pass
WLT deposit happy path pass
WLT deposit double decision pass
LDG-05 ledger reconciliation pass
```

Result tag notes:
- Existing `LDG-02`, `WLT-02`, `AUD-01`, `AUD-03` claims remain unchanged.
- This entry records robustness evidence for invalid amount handling and lazy wallet provisioning idempotency.

Next planned step:
- Draft the next Phase 03 wallet/manual-operation slice, likely manual withdraw with hold/final-debit flow targeting `LDG-03`.

---

## 2026-05-16 — Phase 03 Slice 03 Wallet Manual Withdraw Runtime Verification

Scope:
- Phase 03 Slice 03 backend/runtime sub-scope.
- Manual withdrawal under EUR 10k with ledger hold, final debit, rejection release, insufficient-funds guard and actor-control write block.
- No real payout rails, Source of Funds, two-eyes or frontend implementation.

Build/runtime evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

New withdrawal script evidence:
- `product/scripts/runtime/reg_phase03_wallet_withdraw_hold_complete.sh`:
  - `LDG-03` — pass.
  - `AUD-01` — pass for withdrawal hold and completion audit rows.
  - `AUD-03` — pass for backoffice manual withdrawals queue read-audit.
  - Concrete observations:
    - End-user `POST /api/v1/withdrawals` returned state `HELD` and `holdJournalEntryId`.
    - Hold journal had two balanced postings: DEBIT `WALLET_USER:<userId>`, CREDIT `WALLET_WITHDRAW_HOLD:<userId>`.
    - Wallet balance moved from `50.0000` to `38.0000` after a `12.0000` hold.
    - Backoffice queue contained the held withdrawal and wrote read-audit.
    - `COMPLETE` returned state `COMPLETED` and `completionJournalEntryId`.
    - Completion journal had two balanced postings: DEBIT `WALLET_WITHDRAW_HOLD:<userId>`, CREDIT `EXTERNAL_WITHDRAWAL_CLEARING`.
    - Hold account balance returned to `0.0000`; wallet balance stayed `38.0000`.
- `product/scripts/runtime/reg_phase03_wallet_withdraw_reject_releases_hold.sh`:
  - Rejection release — pass.
  - Concrete observations:
    - Held withdrawal reduced wallet balance from `40.0000` to `25.0000`.
    - `REJECT` returned state `REJECTED` and `releaseJournalEntryId`.
    - Release journal had two balanced postings: DEBIT `WALLET_WITHDRAW_HOLD:<userId>`, CREDIT `WALLET_USER:<userId>`.
    - Wallet balance returned to `40.0000`; hold account balance returned to `0.0000`.
- `product/scripts/runtime/reg_phase03_wallet_withdraw_insufficient_funds.sh`:
  - Insufficient-funds guard — pass.
  - Concrete observations:
    - User funded with `5.0000`.
    - Withdrawal request for `8.0000` returned HTTP 409 with `insufficient_funds`.
    - No `wallet.withdraw_requests` row was persisted for the probing user.
    - Wallet balance remained `5.0000`.
- `product/scripts/runtime/reg_phase03_wallet_withdraw_actor_control_block.sh`:
  - `WLT-02` — pass for withdrawal create and completion.
  - Sub-branches:
    - create + `FROZEN`: `POST /api/v1/withdrawals` returned HTTP 403 `actor_control_blocked`; zero withdrawal rows persisted.
    - create + `BLOCKED`: same observations as `FROZEN` create branch.
    - complete + `FROZEN`: operator `COMPLETE` returned HTTP 403 `actor_control_blocked`; withdrawal remained `HELD`; zero completion journals; `REJECT` remained allowed and released held funds.
    - complete + `BLOCKED`: same observations as `FROZEN` complete branch.
- `product/scripts/runtime/reg_phase03_wallet_withdraw_double_decision.sh`:
  - Double-decision idempotency — pass.
  - Concrete observations:
    - First `COMPLETE` returned state `COMPLETED`.
    - Second `COMPLETE` returned HTTP 409 with `withdrawal_already_decided`.
    - Late `REJECT` also returned HTTP 409.
    - DB cross-check found exactly one `WALLET_WITHDRAW_COMPLETE` journal and zero `WALLET_WITHDRAW_RELEASE` journals for the withdrawal.

Regression evidence:
- `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh` — pass.
- `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh` — pass.
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` — `LDG-05` pass (`LDG-99` foundation regression).

Final Phase 03 Slice 03 script output:

```text
WLT withdraw hold complete pass
WLT withdraw reject releases hold pass
WLT withdraw insufficient funds pass
WLT-02 withdrawal actor-control block (FROZEN+BLOCKED, create+complete) pass
WLT withdraw double decision pass
WLT deposit happy path pass
WLT deposit double decision pass
LDG-05 ledger reconciliation pass
```

Result tag notes:
- `LDG-03` is passed for manual withdrawal hold/final-debit.
- `WLT-02` is extended to withdrawal create and completion.
- `AUD-01` is passed for withdrawal hold / complete / reject and actor-control denial audit rows.
- `AUD-03` is passed for backoffice manual withdrawals queue read producing synchronous read-audit row.
- `LDG-05` / `LDG-99` remain passed after withdrawal flows.
- At this point in history, `WLT-01`, `WLT-03`, `WLT-04` were not claimed; transfer was implemented in the following slice.

Next planned step:
- Completed by Phase 03 Slice 04 below.

---

## 2026-05-16 — Phase 03 Slice 04 Wallet Internal Transfer Runtime Verification

Scope:
- Phase 03 Slice 04 backend/runtime sub-scope.
- End-user internal wallet transfer under EUR 10k with atomic sender debit / receiver credit.
- Includes sufficient-funds guard, sender/receiver actor-control block and optional `Idempotency-Key` duplicate protection.
- No Source of Funds, two-eyes, AML rules, transfer reversal or frontend implementation.

Build/runtime evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

New transfer script evidence:
- `product/scripts/runtime/reg_phase03_wallet_transfer_happy_path.sh`:
  - `WLT-01` — pass.
  - `AUD-01` — pass for transfer success audit row.
  - Concrete observations:
    - Sender wallet funded with `40.0000`.
    - `POST /api/v1/transfers` returned state `COMPLETED`, amount `15.0000` and `journalEntryId`.
    - Transfer journal had two balanced postings: DEBIT `WALLET_USER:<senderId>`, CREDIT `WALLET_USER:<receiverId>`.
    - Sender balance became `25.0000`; receiver balance became `15.0000`.
    - Sender and receiver `GET /api/v1/wallet` responses both included the transfer.
- `product/scripts/runtime/reg_phase03_wallet_transfer_insufficient_funds.sh`:
  - Insufficient-funds guard — pass.
  - Concrete observations:
    - Unfunded sender transfer for `10.0000` returned HTTP 409 with `insufficient_funds`.
    - No `wallet.internal_transfers` row and no transfer journal were persisted for the probing sender.
- `product/scripts/runtime/reg_phase03_wallet_transfer_actor_control_block.sh`:
  - `WLT-02` — pass for sender and receiver write blocks.
  - Sub-branches:
    - sender + `FROZEN`: `POST /api/v1/transfers` returned HTTP 403 `actor_control_blocked`; zero transfer rows persisted.
    - sender + `BLOCKED`: same observations as sender `FROZEN`.
    - receiver + `FROZEN`: `POST /api/v1/transfers` returned HTTP 403 `actor_control_blocked`; zero transfer rows persisted.
    - receiver + `BLOCKED`: same observations as receiver `FROZEN`.
- `product/scripts/runtime/reg_phase03_wallet_transfer_idempotency.sh`:
  - Idempotency guard — pass.
  - Concrete observations:
    - First request with `Idempotency-Key` returned a completed transfer and journal.
    - Replaying the same sender/key/body returned the same transfer id and journal id.
    - Reusing the same sender/key with different amount returned HTTP 409 `transfer_idempotency_conflict`.
    - DB cross-check found exactly one `wallet.internal_transfers` row and exactly one `WALLET_INTERNAL_TRANSFER` journal for the key.

Regression evidence:
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` — `LDG-05` pass (`LDG-99` foundation regression).

Final Phase 03 Slice 04 script output:

```text
WLT internal transfer happy path pass
WLT internal transfer insufficient funds pass
WLT internal transfer actor-control block pass
WLT internal transfer idempotency pass
LDG-05 ledger reconciliation pass
```

Result tag notes:
- `WLT-01` is passed for atomic internal transfer debit/credit.
- `WLT-02` is extended to transfer sender and receiver blocks.
- `AUD-01` is passed for transfer success and actor-control denial audit rows.
- `LDG-05` / `LDG-99` remain passed after transfer flows.
- `WLT-03`, `WLT-04` are not claimed; SoF and two-eyes workflows do not exist yet.

Next planned step:
- Review the parallel high-value controls planning branch, then decide whether to continue Phase 03 placeholders or move to the next approved implementation slice.

---

## 2026-05-16 — Phase 04 Slice 02 Stripe Webhook Runtime Verification

Scope:
- Phase 04 Slice 02 backend/runtime webhook-only sub-scope.
- Real local Stripe-format webhook signature verification using `Stripe-Signature: t=<unix_ts>,v1=<hmac_sha256>`.
- Idempotent event processing by Stripe `event.id`.
- `account.updated` webhook-driven merchant KYB state changes.
- Audit rows for webhook-driven state changes, invalid signatures, timestamp rejections and duplicate deliveries.

Explicit blocker:
- `MRC-01` — Stripe Connect onboarding start is not claimed. Real Stripe sandbox credentials (`STRIPE_SECRET_KEY` / Connect account access) were unavailable; no fake Stripe API call or fake onboarding response was introduced.

Build/runtime evidence:
- `docker build --progress=plain -t mini-fintech-platform-platform --build-arg APP_MODULE=platform -f backend/Dockerfile .` from `product/` passed after fixing duplicate-event transaction handling.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- `curl -fsS http://localhost:8081/internal/health` returned `{"service":"platform","status":"UP"}`.
- Flyway applied `V10__merchant_stripe_webhooks.sql`; `flyway_schema_history` now lists `10:merchant stripe webhooks` after V1..V8.

Local DB hygiene note:
- The local `platform-db` had a previously-applied, branch-external ghost Flyway row `9:merchant api keys and idempotency` from a sibling branch run.
- With owner confirmation, the local-only ghost artifacts were removed before verification:
  - dropped `merchant.api_keys`, `merchant.payment_intents`, `idempotency.idempotency_keys` if present;
  - deleted `public.flyway_schema_history` row `version='9'`.
- This was local dev DB cleanup only; no repository migration or committed state was removed.

New webhook script evidence:
- `product/scripts/runtime/reg_phase04_stripe_webhook_signature_valid.sh`:
  - `MRC-02` — pass for valid Stripe-format HMAC-SHA256 signature.
  - `AUD-01` — pass for `stripe.account_updated` audit row.
  - Concrete observations:
    - Script registered a merchant and seeded `merchant.stripe_account_links` with a local `acct_*` id because real onboarding-start is blocked.
    - Signed a raw JSON `account.updated` payload with local `STRIPE_WEBHOOK_SIGNING_SECRET=whsec_local_test_secret`.
    - `POST /webhooks/stripe/v1` returned outcome `PROCESSED`.
    - Merchant `kyb_status` transitioned to `VERIFIED` when `charges_enabled=true` and `payouts_enabled=true`.
    - Exactly one `merchant.stripe_webhook_events` row exists for the event id with outcome `PROCESSED`.
- `product/scripts/runtime/reg_phase04_stripe_webhook_signature_invalid.sh`:
  - `MRC-02` — pass for invalid signature rejection.
  - `AUD-01` — pass for signature-failure audit row.
  - Concrete observations:
    - Payload signed with `whsec_wrong_secret` returned HTTP 400 with `stripe_webhook_signature_invalid`.
    - Merchant `kyb_status` remained `NOT_STARTED`.
    - No `merchant.stripe_webhook_events` row exists for that event id, so a later correctly signed retry is not poisoned.
    - `audit.audit_log` contains `stripe.webhook_signature_invalid` with outcome `FAILURE`.
- `product/scripts/runtime/reg_phase04_stripe_webhook_timestamp_tolerance.sh`:
  - `MRC-02` — pass for Stripe timestamp tolerance rejection.
  - `AUD-01` — pass for timestamp-failure audit row.
  - Concrete observations:
    - Payload signed correctly but with `t=` two hours in the past returned HTTP 400 with `stripe_webhook_timestamp_outside_tolerance`.
    - Merchant `kyb_status` remained `NOT_STARTED`.
    - No `merchant.stripe_webhook_events` row exists for that event id; `stripe.webhook_timestamp_outside_tolerance` failure audit row exists.
- `product/scripts/runtime/reg_phase04_stripe_webhook_idempotency.sh`:
  - `MRC-02` — pass for duplicate Stripe event id idempotency.
  - `AUD-01` — pass for first state-change audit and duplicate-delivery audit.
  - Concrete observations:
    - First delivery returned outcome `PROCESSED` and moved merchant `kyb_status` to `PENDING` for `details_submitted=true` with `charges_enabled=false`, `payouts_enabled=false`.
    - Second delivery of the exact same signed payload/event id returned outcome `DUPLICATE`.
    - DB cross-check found exactly one `merchant.stripe_webhook_events` row for that event id.
    - DB cross-check found exactly one `stripe.account_updated` audit row and one `stripe.webhook_duplicate` audit row for the event id.

Regression evidence:
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` — `LDG-05` pass (`LDG-99` foundation regression); webhook slice does not touch ledger postings.

Final script output:

```text
MRC-02 Stripe webhook valid signature pass
MRC-02 Stripe webhook invalid signature rejection pass
MRC-02 Stripe webhook timestamp tolerance rejection pass
MRC-02 Stripe webhook event idempotency pass
MRC-02 Stripe webhook bad-then-valid retry pass
LDG-05 ledger reconciliation pass
```

Result tag notes:
- `MRC-02` — pass.
- `AUD-01` — pass extension for webhook-driven KYB state-change, signature-failure, timestamp-failure and duplicate-delivery audit rows.
- `MRC-01` — blocked; not claimed.
- `MRC-03`, `PAY-01`, `PAY-02`, `PAY-03` were not in this slice; they are covered by Phase 04 Slice 01.

Next planned step:
- Either provide real Stripe Connect sandbox credentials and implement `MRC-01`, or continue with the separate merchant API key / public API idempotency branch without changing this webhook proof.

### 2026-05-16 Follow-up — Rejected Delivery Must Not Poison Stripe Event Idempotency

Reviewer finding:
- A rejected delivery with a parseable `event.id` could occupy `merchant.stripe_webhook_events.stripe_event_id`; a later valid retry of the same event id would then be treated as `DUPLICATE` and skip side effects.

Fix:
- Rejected signature/timestamp/payload deliveries now write audit rows only and do **not** insert into `merchant.stripe_webhook_events`.
- `merchant.stripe_webhook_events` is reserved for verified events that have passed Stripe signature/timestamp checks and can safely participate in side-effect idempotency.
- Added retained regression script `product/scripts/runtime/reg_phase04_stripe_webhook_bad_then_valid_retry.sh`.

Verification result after fix:
- Bad signature for an event id returns HTTP 400 `stripe_webhook_signature_invalid`.
- No `merchant.stripe_webhook_events` row exists after the rejected delivery.
- Retrying the same payload/event id with a valid signature returns outcome `PROCESSED`.
- Merchant `kyb_status` transitions to `VERIFIED`.
- Exactly one `PROCESSED` event row exists after the valid retry.

---

## 2026-05-16 — Phase 04 Slice 01 Merchant API Keys and Public API Idempotency Runtime Verification

Scope:
- Phase 04 Slice 01 backend/runtime sub-scope.
- Merchant dashboard API key lifecycle (create / list / revoke), public API authentication via merchant API key, public write idempotency primitive, minimal public payment-intent creation shell.
- No Stripe Connect onboarding, no Stripe webhook receiver, no card authorization/capture/refund/settlement, no frontend implementation.

Build/runtime evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed.
- `docker compose -f deploy/docker-compose.yml up -d platform` passed.
- `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.
- Flyway applied `V11__merchant_api_keys_and_idempotency.sql` cleanly. The migration is intentionally numbered V11 because a sibling Stripe webhook branch owns V10.

New Phase 04 script evidence:
- `product/scripts/runtime/reg_phase04_api_key_lifecycle.sh`:
  - `MRC-03` — pass.
  - `AUD-01` — pass for `merchant.api_key_created` and `merchant.api_key_revoked` audit rows.
  - Concrete observations:
    - `POST /api/v1/merchant/api-keys` returned a one-time `mfp_live_*` secret, an `apiKeyId`, a `keyPrefix` and a 16-hex `fingerprint`; the raw key was never persisted; `key_hash` in `merchant.api_keys` matched `sha256(rawKey)`.
    - `GET /api/v1/merchant/api-keys` returned the key with prefix/fingerprint/status only, never the raw key.
    - Active key successfully created a payment intent via `POST /v1/payment_intents`.
    - `POST /api/v1/merchant/api-keys/{id}/revoke` flipped the key to `REVOKED` and set `revoked_at`; a second revoke returned `REVOKED` idempotently without a second audit row.
    - Revoked key on `POST /v1/payment_intents` returned HTTP 401 `invalid_api_key`.
    - Missing `Authorization` header returned HTTP 401 `unauthenticated`.
    - Malformed bearer token returned HTTP 401 `invalid_api_key`.
- `product/scripts/runtime/reg_phase04_public_api_response_shape.sh`:
  - `PAY-01` — pass.
  - Concrete observations:
    - Success response was `{"data":{...payment_intent...},"errors":[]}` with `state=REQUIRES_PAYMENT_METHOD`.
    - `GET /v1/payment_intents/{id}` returned the same shape.
    - Missing `Idempotency-Key` returned HTTP 400 with `errors:[{code:"idempotency_key_required",...}]` and `data:null`.
    - Invalid amount returned HTTP 400 with `errors:[{code:"invalid_amount", field:"amount",...}]` and `data:null`.
    - Unauthenticated request returned HTTP 401 with `errors:[{code:"unauthenticated",...}]` and `data:null`.
    - Cross-merchant `GET /v1/payment_intents/{id}` returned HTTP 404 with `errors:[{code:"payment_intent_not_found",...}]`.
- `product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh`:
  - `PAY-02` — pass.
  - Concrete observations:
    - Same `Idempotency-Key` + identical body returned byte-identical response body and `id`/`createdAt`.
    - Stored `idempotency.idempotency_keys.response_status` = `201`.
    - Exactly one row in `merchant.payment_intents` for the merchant after the replay pair.
    - A different `Idempotency-Key` with the same body created a separate payment intent (independent op).
    - Same `Idempotency-Key` reused by a different merchant created an independent payment intent (per-merchant scope).
- `product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh`:
  - `PAY-03` — pass.
  - Concrete observations:
    - Same `Idempotency-Key` with a different amount returned HTTP 409 `idempotency_conflict`.
    - Same `Idempotency-Key` with a different `description` returned HTTP 409 `idempotency_conflict`.
    - No second `merchant.payment_intents` row was created.
    - Replaying the original body still returned HTTP 201 with the original `id` and an unchanged stored response.

Regression evidence:
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` — `LDG-05` pass.

Final Phase 04 Slice 01 script output:

```text
MRC-03 api key lifecycle pass
MRC-03 dashboard api key idempotency pass
PAY-01 public API response shape pass
PAY-02 public API idempotency replay pass
PAY-03 public API idempotency conflict pass
PAY-02 atomic concurrent idempotency pass
PAY-02 PAY-03 idempotency per-merchant per-route scope pass
Idempotency append-only (no-update on finalized + no-delete) pass
LDG-05 ledger reconciliation pass
```

Result tag notes:
- `MRC-03` is passed for API key one-time visibility, hashed/fingerprinted storage and revoked-key rejection.
- `PAY-01` is passed for public API `{data, errors}` shape on success and on errors.
- `PAY-02` is passed for same key/body replay returning the cached response, including concurrent callers and per-route/per-merchant scope.
- `PAY-03` is passed for same route/key/different body returning HTTP 409, while same-key reuse on a different route is independent.
- `MRC-01` remains blocked/unclaimed. `MRC-02` is covered by Phase 04 Slice 02.

Next planned step:
- Provide real Stripe Connect sandbox credentials to implement `MRC-01`, or choose the next approved backend/runtime slice toward Phase 05 card path; the API key, public idempotency and inbound webhook primitives are now available as foundations.

---

## 2026-05-16 — Post-merge Reviewer Verification for Phase 04 Slice 01 + Slice 02

Scope:
- Reviewer merge of `task/p04s01-api-keys-idempotency` into `orchestration` after Phase 04 Slice 02 was already accepted.
- Verified combined `platform` image containing both `V10__merchant_stripe_webhooks.sql` and `V11__merchant_api_keys_and_idempotency.sql`.

Build/runtime evidence:
- `docker compose -f deploy/docker-compose.yml build platform` passed on the merged tree.
- Isolated review compose project on alternate ports started successfully.
- `curl -fsS http://127.0.0.1:28082/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.
- Fresh isolated `platform-db` Flyway history contains V1..V8, `10:merchant stripe webhooks`, `11:merchant api keys and idempotency`; both V10 and V11 applied cleanly in version order.

Reviewer runtime checks on isolated review stack:

```text
MRC-03 api key lifecycle pass
MRC-03 dashboard api key idempotency pass
PAY-01 public API response shape pass
PAY-02 public API idempotency replay pass
PAY-03 public API idempotency conflict pass
PAY-02 atomic concurrent idempotency pass
PAY-02 PAY-03 idempotency per-merchant per-route scope pass
Idempotency append-only (no-update on finalized + no-delete) pass
MRC-02 Stripe webhook valid signature pass
LDG-05 ledger reconciliation pass
```

Security merge check:
- `SecurityConfig` now keeps the ordered `/v1/**` public API key filter chain and the normal chain still permits `/webhooks/**`, so API key auth does not block Stripe webhook delivery.

Local DB hygiene note:
- The default local `platform-db` had a branch-run artifact: V11 was already applied without V10, so the merged app initially failed Flyway validation with "resolved migration not applied: 10".
- The missing V10 SQL was applied manually to the default local DB and a matching Flyway history row was inserted using the checksum observed from the isolated fresh DB.
- This was local runtime hygiene only; repository migrations are V10 + V11 and validate cleanly on a fresh DB.
- Default stack was restored afterward; `curl -fsS http://127.0.0.1:8081/actuator/health` returned `{"status":"UP","groups":["liveness","readiness"]}`.

---

## 2026-05-17 — Phase 04 Slice 03 Merchant Dashboard Payments/Webhook Config Runtime Verification

Scope:
- Merchant dashboard payment-intent read shell over existing `merchant.payment_intents` rows.
- Merchant webhook endpoint configuration CRUD model under `/api/v1/merchant/**`.
- No frontend implementation, no Stripe Connect onboarding, no payment authorization/capture/refund/settlement, no outbound webhook delivery.

Runtime slot:
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2`
- `PLATFORM_HTTP_HOST_PORT=28181`
- `PLATFORM_DB_HOST_PORT=25433`
- `KAFKA_HOST_PORT=29092`
- `KEYCLOAK_HOST_PORT=38080`

Build/start evidence:
- `DOCKER_BUILDKIT=0 COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 PLATFORM_HTTP_HOST_PORT=28181 PLATFORM_DB_HOST_PORT=25433 KAFKA_HOST_PORT=29092 KEYCLOAK_HOST_PORT=38080 docker compose -f deploy/docker-compose.yml build platform` passed; image `mini-fintech-platform-a2-platform:latest` built as `f1107cd49635`.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 ... docker compose -f deploy/docker-compose.yml up -d platform` passed.
- Platform applied Flyway migration `V12__merchant_dashboard_read_shell.sql`; DB query `select version from flyway_schema_history order by installed_rank desc limit 1;` returned `12`.
- `docker compose exec -T platform curl -fsS http://127.0.0.1:8080/internal/health` returned `{"service":"platform","status":"UP"}`.

Environment note:
- In this branch runtime, host-published port `28181` accepted TCP but host curl did not receive HTTP responses. Internal compose-network curl to `http://platform:8080` succeeded.
- Retained Phase 04 scripts were updated to support `PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default`, while DB checks still use the assigned compose project.

Runtime script evidence:
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default scripts/runtime/reg_phase04_merchant_payment_reads.sh`:
  - `MRC-04` — pass.
  - Created merchant A, API key and public payment intent.
  - Merchant A dashboard list/detail returned the created shell payment intent.
  - Merchant B dashboard list excluded merchant A's payment intent.
  - Merchant B detail lookup for merchant A's payment intent returned 404 `payment_intent_not_found`.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default scripts/runtime/reg_phase04_merchant_webhook_config.sh`:
  - `MRC-05` — pass.
  - Merchant admin created, listed, updated and soft-deleted webhook endpoint config.
  - `merchant_member` write attempt returned 403 `forbidden_role` while read/list remained available.
  - Another merchant could not list or delete the first merchant's endpoint; delete returned 404.
  - DB row ended in `status='DELETED'`.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default scripts/runtime/reg_phase04_api_key_lifecycle.sh`:
  - `MRC-03` — pass regression.
- Targeted public API regression chain:
  - `scripts/runtime/reg_phase04_public_api_response_shape.sh`
  - `scripts/runtime/reg_phase04_public_api_idempotency_replay.sh`
  - `scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh`
  - Result: command chain exit code `0`; `PAY-01`, `PAY-02`, `PAY-03` pass regression.

Not claimed:
- `MRC-01` remains blocked on real Stripe Connect sandbox credentials.
- `WBH-01`, `WBH-02`, `WBH-03` are not claimed; this slice stores webhook endpoint configuration only and does not deliver outbound webhooks.
- No frontend/UI runtime check is claimed.

Next planned step:
- Provide real Stripe Connect sandbox credentials to implement `MRC-01`, or move to the next approved backend/runtime slice.

---

## 2026-05-17 — Phase 05 Slice 01 Vault/Card Issuance Verification

Scope:
- Phase 05 Slice 01 backend/runtime implementation for Vault tokenization, Issuer card records and Platform end-user card issuance.
- Isolated runtime slot used for Docker commands: `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1`, `PLATFORM_HTTP_HOST_PORT=18181`, `PLATFORM_DB_HOST_PORT=15433`, `ISSUER_HTTP_HOST_PORT=18184`, `ISSUER_DB_HOST_PORT=15436`, `VAULT_HTTP_HOST_PORT=18185`, `VAULT_DB_HOST_PORT=15437`, `KAFKA_HOST_PORT=19092`, `KEYCLOAK_HOST_PORT=18080`.

Implemented artifacts checked statically:
- Vault migration: `product/apps/vault/src/main/resources/db/migration/V2__vault_card_tokenization.sql`.
- Issuer migration: `product/apps/issuer/src/main/resources/db/migration/V2__issuer_cards.sql`.
- Retained scripts:
  - `product/scripts/runtime/lib_phase05_vault_card.sh`
  - `product/scripts/runtime/reg_phase05_card_issue_pan_isolation.sh`
  - `product/scripts/runtime/reg_phase05_vault_detokenize_restriction.sh`
  - `product/scripts/runtime/reg_phase05_pan_log_masking.sh`

Commands run:
- `docker run ... gradle :apps:vault:compileKotlin --no-daemon --info` — pass.
- `docker run ... gradle :apps:issuer:compileKotlin --no-daemon --info` — pass.
- `docker run ... gradle :apps:platform:compileKotlin --no-daemon --info` — pass.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1 ... docker compose -f product/deploy/docker-compose.yml up -d --no-build platform issuer vault` — pass after the long compose build client was stopped and already-built images were reused.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1 docker compose -f deploy/docker-compose.yml config --quiet` from `product/` — pass.
- `bash -n scripts/runtime/lib_phase05_vault_card.sh scripts/runtime/reg_phase05_card_issue_pan_isolation.sh scripts/runtime/reg_phase05_vault_detokenize_restriction.sh scripts/runtime/reg_phase05_pan_log_masking.sh` from `product/` — pass.

Runtime check results:
- `VLT-01` — pass. A new end-user issued card `a16c3cfe-05ba-4ce5-804c-8f96cab1ec1c`; Platform returned safe metadata only, Issuer row was `card_tok_N1it17iXYb-8VgpHwRZfDxxFT_K_xycH|7386|ACTIVE`, Vault had one encrypted PAN row for that token/last4, and Platform/Issuer schemas had zero PAN/CVV columns.
- `VLT-02` — pass. Issuer detokenize returned a 16-digit test PAN; Platform detokenize returned HTTP 403 with `service_auth_denied`; Vault audit rows existed for issuer `ALLOWED` and platform `DENIED`.
- `VLT-03` — pass. Platform/Issuer/Vault general logs were searched after detokenization and did not contain the raw PAN.

Result tag notes:
- Host-to-container HTTP port forwarding accepted connections but did not return responses in this local environment; service health and HTTP checks succeeded from inside the compose network. The retained `reg_phase05_*` scripts remain in the repo, while this evidence used equivalent compose-network HTTP calls plus DB/log assertions against the same isolated stack.
- `PAY-04` and `PAY-05` are not claimed; authorization/capture/settlement remain deferred to the later card authorization slice.

Reviewer verification after branch review:
- `COMPOSE_PROJECT_NAME=mfp-review-p05 ... docker compose -f deploy/docker-compose.yml build platform issuer vault` passed for the reviewed tree.
- `COMPOSE_PROJECT_NAME=mfp-review-p05 ... docker compose -f deploy/docker-compose.yml up -d --no-build platform issuer vault` started an isolated review stack on ports `48181`, `48084`, `48085`, `45433`, `45436`, `45437`, `49092`, `58080`.
- Retained scripts were run directly against the review stack and passed:
  - `scripts/runtime/reg_phase05_card_issue_pan_isolation.sh` — `VLT-01 pass`;
  - `scripts/runtime/reg_phase05_vault_detokenize_restriction.sh` — `VLT-02 pass`;
  - `scripts/runtime/reg_phase05_pan_log_masking.sh` — `VLT-03 pass`.
- The review stack was stopped with `docker compose down -v` after verification.

Post-merge combined-tree verification:
- After accepting Phase 04 Slice 03 and Phase 05 Slice 01 into `orchestration`, `COMPOSE_PROJECT_NAME=mfp-review-merged ... docker compose -f deploy/docker-compose.yml config --quiet` passed.
- `bash -n` passed for the new Phase 04 and Phase 05 retained runtime scripts.
- `COMPOSE_PROJECT_NAME=mfp-review-merged ... docker compose -f deploy/docker-compose.yml build platform issuer vault` passed for the combined tree, proving `platform` compiles with both the merchant dashboard API additions and the card issuance entrypoint.

---

## 2026-05-18 — Phase 05 Slice 02 Card Authorization Verification

Scope:
- Phase 05 Slice 02 backend/runtime implementation for public card authorization, Acquirer/Network/Issuer routing and approved ledger holds.
- Isolated runtime slot used for Docker commands: `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1`, Platform `18181`, Acquirer `18182`, Network `18183`, Issuer `18184`, Vault `18185`, DB ports `15433`–`15437`, Kafka `19092`, Keycloak `28080`.

Implemented artifacts checked:
- Platform migration `V13__card_authorization_hold_support.sql`.
- Acquirer migration `V2__payment_authorization_foundation.sql`.
- Network migration `V2__authorization_routing_foundation.sql`.
- Issuer migration `V3__card_authorization_and_holds.sql`.
- Retained scripts:
  - `product/scripts/runtime/lib_phase05_card_authorization.sh`.
  - `product/scripts/runtime/reg_phase05_authorization_approved_hold.sh`.
  - `product/scripts/runtime/reg_phase05_authorization_structured_declines.sh`.

Commands run:
- `docker run ... gradle --no-daemon --max-workers=1 --project-cache-dir /tmp/project-cache-root :apps:issuer:compileKotlin` — pass.
- `docker run ... gradle --no-daemon --max-workers=1 --project-cache-dir /tmp/project-cache-root :apps:vault:compileKotlin` — pass.
- `docker run ... gradle --no-daemon --max-workers=1 --project-cache-dir /tmp/project-cache-root2 :apps:platform:compileKotlin :apps:network:compileKotlin :apps:acquirer:compileKotlin` — pass.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1 ... docker compose -f product/deploy/docker-compose.yml up -d --build platform acquirer network issuer vault` — pass.
- `docker compose -f product/deploy/docker-compose.yml ps` showed Platform, Acquirer, Network, Issuer, Vault and DB dependencies running; service DBs were healthy.
- `product/scripts/runtime/reg_phase05_authorization_approved_hold.sh` — pass when run from a temporary `node:22-bookworm` container on `mini-fintech-platform-a1_default` with temporary DB helper rewiring to Postgres service DNS.
- `product/scripts/runtime/reg_phase05_authorization_structured_declines.sh` — pass with the same compose-network runner.

Runtime check results:
- `PAY-04` — pass. Approved public authorization returned `AUTHORIZED` and `AUTH_APPROVED`, Issuer hold count increased by one, Platform ledger had a `CARD_AUTHORIZATION_HOLD` journal and `LDG-05` reconciliation passed.
- `PAY-05` — pass. Structured declines covered insufficient funds, blocked actor, inactive card and unknown card token; each returned `FAILED`/`AUTH_DECLINED` with the expected decline code and did not create a hold.

Result tag notes:
- Host-to-container published HTTP ports in this local environment accepted TCP connections but did not return response bytes, while HTTP inside the compose network returned normally. Runtime script verification therefore used an equivalent temporary compose-network runner against the same isolated stack.
- Temporary runner-only changes were not copied into the repository except for product script fixes needed by direct script correctness: API key extraction accepts the current `key` response field, and the PAY-05 actor-control seed supplies required audit columns.

## 2026-05-18 — Phase 06 Slice 01 Outbound Webhook Delivery Runtime Verification

Scope:
- Phase 06 Slice 01 backend/runtime implementation for outbound merchant webhook signing and delivery.
- Isolated runtime slot: `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2`, `PLATFORM_HTTP_HOST_PORT=28181`, `PLATFORM_DB_HOST_PORT=25433`, `ACQUIRER_HTTP_HOST_PORT=28182`, `ACQUIRER_DB_HOST_PORT=25434`, `NETWORK_HTTP_HOST_PORT=28183`, `NETWORK_DB_HOST_PORT=25435`, `ISSUER_HTTP_HOST_PORT=28184`, `ISSUER_DB_HOST_PORT=25436`, `VAULT_HTTP_HOST_PORT=28185`, `VAULT_DB_HOST_PORT=25437`, `KAFKA_HOST_PORT=29092`, `KEYCLOAK_HOST_PORT=38080`.

Implemented artifacts checked:
- Platform migration: `product/apps/platform/src/main/resources/db/migration/V14__merchant_outbound_webhook_delivery.sql`.
- Outbound webhook implementation under `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/`.
- Payment-intent event producer in `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/PaymentIntentService.kt`.
- Retained scripts:
  - `product/scripts/runtime/lib_phase06_webhooks.sh`;
  - `product/scripts/runtime/phase06_webhook_receiver.js`;
  - `product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh`.

Commands run:
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 ... docker compose -f product/deploy/docker-compose.yml build platform` — pass; Platform image built successfully.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 ... docker compose -f product/deploy/docker-compose.yml up -d --no-build platform` — pass; Platform container was recreated and started with migration `V14`.
- `bash -n product/scripts/runtime/lib_phase06_webhooks.sh product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh` — pass.
- `node --check product/scripts/runtime/phase06_webhook_receiver.js` — pass.
- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 COMPOSE_FILE=deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default scripts/runtime/reg_phase06_webhook_signing_delivery.sh` — pass.
- Targeted regressions:
  - `scripts/runtime/reg_phase04_merchant_webhook_config.sh` — pass;
  - `scripts/runtime/reg_phase04_public_api_response_shape.sh` — pass;
  - `scripts/runtime/reg_phase04_public_api_idempotency_replay.sh` — pass;
  - `scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh` — pass.

Runtime check results:
- `WBH-01` — pass.
- Actor used:
  - authenticated `merchant_admin` created webhook endpoint config and rotated the endpoint signing secret;
  - merchant API key caller created payment intents through `POST /v1/payment_intents`;
  - local test receiver ran as a real HTTP process in the same Docker network and validated MiniFin HMAC signature headers.
- Preconditions:
  - Platform service running in isolated `mini-fintech-platform-a2` stack;
  - merchant exists with active API key;
  - active webhook endpoint subscribed to `payment_intent.created`;
  - receiver had the one-time signing secret returned by endpoint creation/rotation.
- Action taken:
  - created webhook endpoint and verified list route did not return raw `signingSecret`;
  - rotated signing secret and started local receiver with the rotated secret;
  - created `payment_intent.created` event through real public API payment-intent creation;
  - Platform delivered signed JSON payload to receiver;
  - receiver validated `MiniFin-Webhook-Signature`;
  - DB showed `merchant.webhook_events.status = 'DELIVERED'` and `merchant.webhook_delivery_attempts.status = 'SUCCEEDED'` with HTTP 200.
- Idempotency proof:
  - same-key/same-body public payment-intent replay returned cached response;
  - DB count for webhook events for the replayed payment intent remained exactly one.

Runtime-discovered fixes:
- Initial `WBH-01` run exposed missing `status='PENDING'` in `merchant.webhook_events` insert; fixed before final pass.
- Host-only receiver binding was not reachable from Platform container via `host.docker.internal` in this environment; retained script now runs the local receiver as a container on `mini-fintech-platform-a2_default` and configures the endpoint URL to its Docker DNS name for real HTTP delivery.
- `/tmp` receiver artifact ownership differs when receiver runs in a container; cleanup now uses a containerized `rm` in compose-network mode.
- Reviewer merge resolution replaced hash-as-HMAC-key behavior with encrypted-at-rest webhook signing secret material. Delivery now signs with the one-time `mfp_whsec_*` secret returned to the merchant, while hash/prefix remain non-secret metadata.

Reviewer merge verification:
- `rg` conflict-marker scan over `CURRENT.md`, `planning`, `product` and `README.md` found no unresolved merge markers; the temporary task file was removed from the final tree.
- `git diff --cached --check` passed.
- `docker compose -f product/deploy/docker-compose.yml config --quiet` passed.
- `bash -n` passed for retained Phase 05 and Phase 06 runtime shell scripts.
- `node --check product/scripts/runtime/phase06_webhook_receiver.js` passed.
- `COMPOSE_PROJECT_NAME=mfp-review-merged-2 ... docker compose -f product/deploy/docker-compose.yml build platform` passed after the webhook signing fix.
- No `mfp-review-merged-2` containers were left running after verification; this review used build-only Compose verification.

Result tag notes:
- `WBH-02` is deferred. Retry/DLQ scheduling and terminal DLQ handling are not implemented or claimed.
- `WBH-03` is not claimed.
- `MRC-01`, `PAY-04`, `PAY-05`, `SET-*`, `CHB-*` and frontend checks are not claimed.

---

## 2026-05-18 — Phase 06 Slice 02 Outbound Webhook Retry/DLQ Runtime Verification

Runtime phase:
- Phase 06 Slice 02 backend/runtime implementation for outbound webhook retry scheduling and terminal DLQ.

Check IDs:
- `WBH-02` — pass.
- Targeted regressions:
  - `WBH-01` — pass.
  - `MRC-05` — pass.
  - `PAY-01` — pass.
  - `PAY-02` — pass.
  - `PAY-03` — pass.

Actor used:
- Merchant admin created through local merchant registration/login flow.
- Merchant API key caller created through dashboard API key lifecycle.
- Real local MiniFin webhook receiver container on the isolated compose network.

Preconditions:
- Isolated runtime network `mini-fintech-platform-a1_default`.
- Platform DB migrated through `V15__merchant_outbound_webhook_retry_dlq.sql`.
- Platform app running from rebuilt jar/image with `WEBHOOK_MAX_ATTEMPTS=3` and `WEBHOOK_RETRY_DELAYS_SECONDS=1,2`.
- Active merchant webhook endpoint subscribed to `payment_intent.created`.
- Receiver configured with the endpoint one-time signing secret and deterministic HTTP 500 responses.

Action taken:
- Created a payment intent through public `POST /v1/payment_intents`.
- Observed the initial signed delivery fail against the HTTP 500 receiver.
- Invoked the retained due-retry dispatcher through the `WBH-02` script until retry exhaustion.
- Asserted final event state and delivery attempts in Platform DB.
- Ran targeted regression scripts for signed delivery, merchant webhook config and public API idempotency checks.

Commands run:

```bash
docker run --name minifin-platform-compile2 \
  -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace \
  -w /workspace \
  -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2' \
  gradle:8.14.3-jdk21 \
  gradle --no-daemon --console=plain :apps:platform:compileKotlin

docker run --name minifin-platform-bootjar \
  -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace \
  -w /workspace \
  -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2' \
  gradle:8.14.3-jdk21 \
  gradle --no-daemon --console=plain :apps:platform:bootJar

COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mini-fintech-platform-a1 \
PLATFORM_BASE_URL=http://mini-fintech-platform-a1-platform-manual:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a1_default \
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh
```

Targeted regression commands used the same `COMPOSE_FILE`, `COMPOSE_PROJECT_NAME`, `PLATFORM_BASE_URL` and `PLATFORM_CURL_CONTAINER_NETWORK` values with:

```bash
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_merchant_webhook_config.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_public_api_response_shape.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh
```

Actual result:
- Compile check passed: `:apps:platform:compileKotlin`.
- Boot jar build passed: `:apps:platform:bootJar`.
- `WBH-02` script output:

```text
WBH-02 webhook retry dlq pass event_id=ca30bcb4-0cef-4583-a46f-09721d7ffa63 endpoint_id=d579916d-28d4-4ab2-9dfe-337f2ab2185c attempts=3 final_status=DLQ
```

DB assertion:

```text
event_id=ca30bcb4-0cef-4583-a46f-09721d7ffa63
endpoint_id=d579916d-28d4-4ab2-9dfe-337f2ab2185c
attempt_count=3
final_status=DLQ
dlq_at_present=true
next_retry_at_null=true
last_http_status=500
```

Receiver assertion:

```text
evt_ca30bcb4-0cef-4583-a46f-09721d7ffa63 attempt=1 payment_intent.created
evt_ca30bcb4-0cef-4583-a46f-09721d7ffa63 attempt=2 payment_intent.created
evt_ca30bcb4-0cef-4583-a46f-09721d7ffa63 attempt=3 payment_intent.created
```

The receiver validates MiniFin signature headers before recording each request, so repeated recorded rows prove repeated real HTTP attempts with valid signatures.

Targeted regression results:

```text
WBH-01 outbound webhook signing/delivery pass
MRC-05 merchant webhook endpoint config pass
PAY-01 public API response shape pass
PAY-02 public API idempotency replay pass
PAY-03 public API idempotency conflict pass
```

Result tags:
- `WBH-02` — pass.
- `WBH-01` — pass.
- `MRC-05` — pass.
- `PAY-01` — pass.
- `PAY-02` — pass.
- `PAY-03` — pass.

Notes:
- Runtime verification used a compose-network runner pattern because this environment has previously shown unreliable host-to-container published HTTP behavior.
- `WBH-03` remains unclaimed. DLQ replay is not implemented.

---

## 2026-05-18 — Phase 07 Slice 01 KYC/Sumsub Foundation Runtime Verification

Scope:
- Platform backend/runtime implementation for `KYC-01` and `KYC-02`.
- End-user `POST /api/v1/kyc/start` auth/config/persistence gate.
- Sumsub-style inbound webhook signature verification and vendor event id idempotency.

Build evidence:
- `docker run --rm -v ... gradle:8.14.3-jdk21 gradle --no-daemon :apps:platform:bootJar --stacktrace --console=plain` completed and produced `product/apps/platform/build/libs/platform-0.1.0-SNAPSHOT.jar`.
- Platform runtime started from the built jar in an isolated container on compose network `mini-fintech-platform-a2_default`.
- Flyway applied `V16__kyc_sumsub_foundation.sql`; Platform DB reached version `v16`.

Runtime shape:
- Used isolated a2 resources.
- Host-published HTTP curls hung in this environment, so retained checks used the established compose-network runner pattern:
  - `PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default`
  - `PLATFORM_BASE_URL=http://minifin-phase07-platform:8080`
- Real Sumsub sandbox credentials were **not** configured or used.

Command run:

```bash
cd product
COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/deploy/docker-compose.yml \
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default \
PLATFORM_BASE_URL=http://minifin-phase07-platform:8080 \
scripts/runtime/reg_phase07_kyc_start.sh
```

Output:

```text
KYC-01 Sumsub KYC start partial (credentials absent; local auth/config/persistence gate proven)
```

Result:
- `KYC-01` — **partial**.
- Actor used: authenticated, email-verified, active end-user created by the runtime script.
- Proven locally: end-user session gate, active/email-verified user path, actor-control write guard call path, one KYC profile persisted, failed start session persisted with `failure_code = 'sumsub_not_configured'`, and no fake Sumsub applicant/access-token success.
- Full pass remains blocked until real Sumsub sandbox credentials are configured and actually used.

Command run:

```bash
cd product
COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/deploy/docker-compose.yml \
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default \
PLATFORM_BASE_URL=http://minifin-phase07-platform:8080 \
scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh
```

Output:

```text
INSERT 0 1
INSERT 0 1
KYC-02 Sumsub webhook signature/idempotency pass profile_id=264fdfdc-3d82-401b-a5f5-ef1f5995a8b2 applicant_id=sumsub-applicant-1779064705056225789 event_id=sumsub-event-1779064705057517135
```

Result:
- `KYC-02` — **pass**.
- Webhook payload: deterministic local Sumsub-format `applicantReviewed` JSON fixture with `reviewResult.reviewAnswer = GREEN`.
- Signature assertion: invalid `X-Payload-Digest` rejected with HTTP `401` / `invalid_sumsub_signature`; valid HMAC-SHA256 signature accepted.
- Idempotency assertion: duplicate vendor event id returned idempotent success with `duplicate = true`; exactly one `kyc.sumsub_webhook_events` row exists for the event id.
- State assertion: seeded KYC profile for applicant `sumsub-applicant-1779064705056225789` moved from `SUBMITTED` to `APPROVED`.

Targeted regression:
- Merchant session calling `POST /api/v1/kyc/start` returned `403` with `forbidden_actor_type`.

Not claimed:
- Real Sumsub sandbox start full pass.
- OpenSanctions.
- AML alerts/freezes/SoF.
- Document preview/read-audit.
- Case attachments or SeaweedFS KYC document storage.
- Backoffice KYC queue/manual decisions.
- Frontend UI.

Reviewer merge verification:
- Conflict-marker scan over `CURRENT.md`, `planning`, `product` and `README.md` found no unresolved merge markers; temporary task files were removed from the final tree.
- `docker compose -f product/deploy/docker-compose.yml config --quiet` passed.
- `bash -n` passed for retained Phase 06 and Phase 07 runtime shell scripts.
- `node --check product/scripts/runtime/phase06_webhook_receiver.js` passed.
- `COMPOSE_PROJECT_NAME=mfp-review-merge-kyc ... docker compose -f product/deploy/docker-compose.yml build platform` passed after merging Phase 06 Slice 02 and Phase 07 Slice 01.
- No `mfp-review-merge-kyc` containers were left running after verification; this review used build-only Compose verification.

---

## 2026-05-18 — Phase 07 Slice 02 Backoffice KYC Manual Review Runtime Verification

Scope:
- Platform backend/runtime implementation for `KYC-03`.
- Backoffice KYC case queue/detail and manual decisions.
- No frontend, document preview, document read-audit, OpenSanctions, AML or SeaweedFS document storage work was added.

Build/runtime evidence:
- `docker run ... gradle:8.14.3-jdk21 gradle --no-daemon --console=plain :apps:platform:compileKotlin` passed.
- `docker run ... gradle:8.14.3-jdk21 gradle --no-daemon --console=plain :apps:platform:bootJar` passed and produced `product/apps/platform/build/libs/platform-0.1.0-SNAPSHOT.jar`.
- Platform runtime started from the built jar in isolated Docker network `mfp_agent8_kyc03_default` with alias `platform-manual`.
- Flyway applied `V18__kyc_manual_review.sql`; Platform DB reached version `v18`.

Runtime shape:
- Used isolated resources with `COMPOSE_PROJECT_NAME=mfp_agent8_kyc03`.
- Host-published HTTP access to Keycloak/Platform was unreliable in this environment, so checks used compose-network curl runners.
- Keycloak helper was extended to support `KEYCLOAK_CURL_CONTAINER_NETWORK` for in-network retained checks.

Target command run:

```bash
PLATFORM_BASE_URL=http://platform-manual:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default \
KEYCLOAK_BASE_URL=http://keycloak:8080 \
KEYCLOAK_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent8_kyc03 \
/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/scripts/runtime/reg_phase07_kyc_manual_review.sh
```

Actual result:
- Script output included seeded KYC case inserts and completed all assertions.
- Queue response contained the seeded `IN_REVIEW` case.
- Detail response returned KYC status/vendor metadata and no document payload.
- Short rationale returned `400 invalid_rationale`.
- Valid manual approval returned `APPROVED`.
- Repeat decision returned `409 invalid_state`.
- DB assertions passed for:
  - `kyc.kyc_profiles.status = 'APPROVED'`;
  - one `kyc.kyc_manual_decisions` row with `decision = 'APPROVE'` and `resulting_status = 'APPROVED'`;
  - one `audit.audit_log` row with `event_type = 'kyc.manual_decision_recorded'`.
- End-user and merchant cookie sessions were rejected by backoffice KYC routes with `401 unauthenticated`.

Targeted regression commands:

```bash
PLATFORM_BASE_URL=http://platform-manual:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default \
SUMSUB_WEBHOOK_SECRET=local-sumsub-webhook-secret \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent8_kyc03 \
/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh
```

Output:

```text
KYC-02 Sumsub webhook signature/idempotency pass profile_id=2743d177-8d3b-46c3-a1cb-b845f8ed2d5a applicant_id=sumsub-applicant-1779073722899420642 event_id=sumsub-event-1779073722900573843
```

Additional targeted checks:
- Merchant-session wrong-role denial for `POST /api/v1/kyc/start` returned `403 forbidden_actor_type`.
- `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh` was run with in-network Keycloak/Platform settings; missing bearer token, unsupported backoffice role, end-user cookie and merchant cookie cases returned the expected denial responses.

Result tags:
- `KYC-03` — pass.
- `KYC-02` — pass regression.
- KYC start merchant-session wrong-role denial — pass regression.
- Backoffice unauthenticated/wrong-role denial — pass regression.

Not claimed:
- `KYC-01` full pass; real Sumsub sandbox credentials were not configured or used.
- `AUD-03`, `SNX-*`, `AML-*`, frontend `UI-*`.
