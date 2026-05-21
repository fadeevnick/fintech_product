# Runtime Evidence Log

Last updated: 2026-05-21.

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

## 2026-05-18 — Phase 06 Slice 03 Outbound Webhook DLQ Replay Runtime Verification

Runtime phase:
- Phase 06 Slice 03 backend/runtime implementation for merchant-scoped webhook DLQ list/detail and single-event replay.

Check IDs:
- `WBH-03` — pass.
- Targeted regressions:
  - `WBH-01` — pass.
  - `WBH-02` — pass.
  - `MRC-05` — pass.
  - `PAY-01` — pass.
  - `PAY-02` — pass.
  - `PAY-03` — pass.

Actor used:
- Merchant admin created through local merchant registration/login flow.
- Merchant member simulated by updating the same merchant employee role to `merchant_member` for access-control assertions.
- Second merchant admin used for cross-merchant not-found assertions.
- Merchant API key caller created through dashboard API key lifecycle.
- Real local MiniFin webhook receiver container on isolated compose network.

Preconditions:
- Isolated runtime network `mini-fintech-platform-a7_default`.
- Platform DB migrated through `V17__merchant_webhook_dlq_replay.sql`.
- Platform app running from rebuilt jar/image with `WEBHOOK_MAX_ATTEMPTS=3` and `WEBHOOK_RETRY_DELAYS_SECONDS=1,2`.
- Active merchant webhook endpoint subscribed to `payment_intent.created`.
- Receiver configured first for HTTP 500 until event reaches `DLQ`, then restarted for HTTP 200 replay success.

Action taken:
- Created a payment intent through public `POST /v1/payment_intents`.
- Allowed automatic delivery and due-retry dispatch to exhaust attempts and move the event to `DLQ`.
- Verified merchant-admin DLQ list/detail showed the event.
- Verified `merchant_member` could read detail but received `403 forbidden_role` on replay.
- Verified a second merchant received 404 for detail and replay, hiding cross-merchant event existence.
- Restarted receiver in success mode and called merchant-admin replay API.
- Asserted replay produced a signed attempt 4 with `trigger_type = MANUAL_REPLAY`, HTTP 200 and final event status `DELIVERED`.
- Verified replaying the now non-DLQ event returned 409 `invalid_state`.

Commands run:

```bash
docker run --name minifin-platform-compile-wbh03 \
  -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace \
  -w /workspace \
  -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2' \
  gradle:8.14.3-jdk21 \
  gradle --no-daemon --console=plain :apps:platform:compileKotlin

docker run --name minifin-platform-bootjar-wbh03 \
  -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace \
  -w /workspace \
  -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2' \
  gradle:8.14.3-jdk21 \
  gradle --no-daemon --console=plain :apps:platform:bootJar

COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mini-fintech-platform-a7 \
PLATFORM_BASE_URL=http://mini-fintech-platform-a7-platform-manual:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a7_default \
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase06_webhook_dlq_replay.sh
```

Targeted regression commands used the same `COMPOSE_FILE`, `COMPOSE_PROJECT_NAME`, `PLATFORM_BASE_URL` and `PLATFORM_CURL_CONTAINER_NETWORK` values with:

```bash
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_merchant_webhook_config.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_public_api_response_shape.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh
/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh
```

Actual result:
- Compile check passed: `:apps:platform:compileKotlin`.
- Boot jar build passed: `:apps:platform:bootJar`.
- `WBH-03` script output:

```text
WBH-03 webhook dlq replay pass event_id=0cf6f757-087d-4a04-ad1c-9f0b7acdcfc4 endpoint_id=2d1142d5-2462-49c5-adb7-25645a4f13ee replay_attempt=4 final_status=DELIVERED
```

DB attempt assertion:

```text
1|FAILED|500|AUTO
2|FAILED|500|AUTO
3|FAILED|500|AUTO
4|SUCCEEDED|200|MANUAL_REPLAY
```

Receiver assertion:

```text
evt_0cf6f757-087d-4a04-ad1c-9f0b7acdcfc4 attempt=4 payment_intent.created
```

The receiver validates MiniFin signature headers before recording requests, so the recorded replay row proves a real signed HTTP replay using current endpoint secret material.

Targeted regression results:

```text
WBH-01 outbound webhook signing/delivery pass
WBH-02 webhook retry dlq pass event_id=8ab72b65-8531-485f-a483-04845ee589ec endpoint_id=e555186a-5d49-45a0-ac97-577110dfb311 attempts=3 final_status=DLQ
MRC-05 merchant webhook endpoint config pass
PAY-01 public API response shape pass
PAY-02 public API idempotency replay pass
PAY-03 public API idempotency conflict pass
```

Result tags:
- `WBH-03` — pass.
- `WBH-01` — pass.
- `WBH-02` — pass.
- `MRC-05` — pass.
- `PAY-01` — pass.
- `PAY-02` — pass.
- `PAY-03` — pass.

Notes:
- Runtime verification used a compose-network runner pattern to avoid host-to-container published HTTP ambiguity.
- No frontend DLQ UI, bulk replay or automatic DLQ replay was implemented.

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

---

## 2026-05-19 — Phase 05 Slice 03 Payment Capture Foundation Runtime Verification

Scope:
- Platform backend/runtime implementation for `PAY-06`.
- Public payment-intent capture for already authorized payment intents.
- No clearing, settlement, refunds, payouts, chargebacks, sanctions/KYC/AML or frontend work was added.

Build/static evidence:
- `docker run --rm -v .../product:/workspace -w /workspace gradle:8.14.3-jdk21 gradle --no-daemon --console=plain :apps:platform:compileKotlin` passed.
- The first compile attempt found `MerchantPaymentDashboardRepository` still constructing `PaymentIntentRecord` without the new capture fields; after adding `captured_at`, `captured_amount` and `capture_request_id` to those reads, compile passed.

Runtime shape:
- Used isolated compose project `mfp_agent9_pay06` with non-default host ports and compose network `mfp_agent9_pay06_default`.
- Docker/Gradle `bootJar` builds inside fresh containers stalled during dependency resolution, so runtime verification used:
  - the previous Platform boot jar as a dependency/runtime base;
  - freshly compiled updated Platform classes/resources overlaid into a patched jar at `/tmp/mfp-agent9-platform-pay06.jar`;
  - a small `mfp_agent9_pay06-platform` runtime image built from that patched jar;
  - existing compatible service images tagged for `mfp_agent9_pay06-{acquirer,network,issuer,vault}`.
- Platform startup applied Flyway `V19__payment_capture_foundation.sql`; Platform DB reached version `v19`.
- Host-published HTTP connected but hung in this environment, so retained scripts were run through compose-network curl runner settings. Phase 05 helper and ledger reconciliation scripts were adjusted to support `PLATFORM_CURL_CONTAINER_NETWORK` / `COMPOSE_FILE` for this runtime shape.

Target command run:

```bash
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent9_pay06_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent9_pay06 \
product/scripts/runtime/reg_phase05_payment_capture.sh
```

Output:

```text
PAY-06 payment capture foundation pass intent_id=b8f7f28d-5e27-4179-9607-35f619c3f3b6
```

Targeted regression commands:

```bash
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent9_pay06_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent9_pay06 \
product/scripts/runtime/reg_phase05_authorization_approved_hold.sh

PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent9_pay06_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent9_pay06 \
product/scripts/runtime/reg_phase05_authorization_structured_declines.sh

PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent9_pay06_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent9_pay06 \
product/scripts/runtime/reg_phase04_public_api_response_shape.sh

PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent9_pay06_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent9_pay06 \
product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh

PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent9_pay06_default \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_1/product/deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_agent9_pay06 \
product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh
```

Output:

```text
PAY-04 approved authorization hold pass
PAY-05 structured authorization declines pass
PAY-01 public API response shape pass
PAY-02 public API idempotency replay pass
PAY-03 public API idempotency conflict pass
```

Result tags:
- `PAY-06` — pass.
- `PAY-04` — pass regression.
- `PAY-05` — pass regression.
- `PAY-01` — pass regression.
- `PAY-02` — pass regression.
- `PAY-03` — pass regression.

Not claimed:
- `SET-*`, `CHB-*`, frontend `UI-*`, Stripe Connect onboarding `MRC-01`, sanctions/AML/KYC changes.

---

## 2026-05-19 — Phase 07 Slice 03 OpenSanctions Fail-Closed Runtime Verification

Scope:
- Platform backend/runtime implementation for `SNX-01`.
- Sanctions hit persistence, OpenSanctions adapter boundary and narrow KYC manual approval fail-closed gate.
- No `SNX-02`, sanctions queue UI, AML, permanent freeze/block, frontend or production watchlist ingestion work was added.

Build/runtime evidence:
- A fresh Platform jar was built from the current source using an offline Gradle cache recovered from a previous successful build container:
  - `docker run --rm -e GRADLE_USER_HOME=/home/gradle/.gradle -v /tmp/mfp-build-copy-agent10snx:/workspace -v /tmp/gradle-home-from-kyc03:/home/gradle/.gradle -w /workspace gradle:8.14.3-jdk21 gradle --offline --no-daemon --console=plain --max-workers=1 :apps:platform:bootJar`
  - Result: `BUILD SUCCESSFUL in 9s`.
- The fresh jar was packaged into isolated runtime image `agent10snx-platform` and used only for the `agent10snx` compose project.
- Platform started successfully in isolated Docker network `agent10snx_default`; `/actuator/health` returned `UP`.
- Flyway applied `V20__opensanctions_fail_closed.sql`; Platform DB reached version `v20`.

Runtime shape:
- Used isolated resources with `COMPOSE_PROJECT_NAME=agent10snx` and non-default published ports.
- Verification used compose-network curl runners:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=agent10snx_default`
  - `KEYCLOAK_BASE_URL=http://keycloak:8080`
  - `KEYCLOAK_CURL_CONTAINER_NETWORK=agent10snx_default`
- OpenSanctions success/failure behavior used explicit local modes; no real OpenSanctions production ingestion was used.

Target command run:

```bash
COMPOSE_PROJECT_NAME=agent10snx \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=agent10snx_default \
KEYCLOAK_BASE_URL=http://keycloak:8080 \
KEYCLOAK_CURL_CONTAINER_NETWORK=agent10snx_default \
PLATFORM_HTTP_HOST_PORT=19081 \
ACQUIRER_HTTP_HOST_PORT=19082 \
NETWORK_HTTP_HOST_PORT=19083 \
ISSUER_HTTP_HOST_PORT=19084 \
VAULT_HTTP_HOST_PORT=19085 \
PLATFORM_DB_HOST_PORT=15433 \
ACQUIRER_DB_HOST_PORT=15434 \
NETWORK_DB_HOST_PORT=15435 \
ISSUER_DB_HOST_PORT=15436 \
VAULT_DB_HOST_PORT=15437 \
KEYCLOAK_HOST_PORT=19080 \
/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh
```

Actual output:

```text
SNX-01 OpenSanctions fail-closed pass
```

Runtime assertions passed:
- local `unavailable` mode returned `503 sanctions_screening_unavailable`; KYC profile remained `IN_REVIEW`; one `sanctions.sanctions_hits` row existed with `reason = 'SCREENING_UNAVAILABLE'` and `status = 'OPEN'`; audit event `sanctions.opensanctions_screening_unavailable` existed with `outcome = 'FAIL_CLOSED'`.
- local `match` mode returned `409 sanctions_possible_match`; KYC profile remained `IN_REVIEW`; one `sanctions.sanctions_hits` row existed with `reason = 'POSSIBLE_MATCH'`, `status = 'OPEN'` and `match_score >= 0.85`; audit event `sanctions.opensanctions_screening_blocked` existed with `outcome = 'BLOCKED'`.
- local `no_match` mode allowed manual approval; KYC profile became `APPROVED`; no sanctions hit was created for that profile; audit event `sanctions.opensanctions_screening_passed` existed with `outcome = 'SUCCESS'`.

Targeted regression command:

```bash
COMPOSE_PROJECT_NAME=agent10snx \
COMPOSE_FILE=/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=agent10snx_default \
KEYCLOAK_BASE_URL=http://keycloak:8080 \
KEYCLOAK_CURL_CONTAINER_NETWORK=agent10snx_default \
OPENSANCTIONS_LOCAL_MODE=no_match \
/home/nickf/Documents/sre_projects/mini-fintech-platform_2/product/scripts/runtime/reg_phase07_kyc_manual_review.sh
```

Regression output:

```text
KYC-03 backoffice manual KYC review pass profile_id=0ef6eade-4156-4eff-8224-79311b710737 applicant_id=sumsub-applicant-manual-review-1779158406798852246
```

Result tags:
- `SNX-01` — pass.
- `KYC-03` — pass targeted regression.

Runtime-driven fixes applied:
- `KycBackofficeService.decide(...)` was changed to `noRollbackFor = [SanctionsException::class]` after runtime verification showed sanctions hits/audit rows were otherwise rolled back with fail-closed exceptions.
- `product/scripts/runtime/lib_phase02_backoffice_keycloak.sh` role assignment was made idempotent for repeat local checks.
- `product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh` suppresses `psql` insert command tags while generating seeded IDs.

Not claimed:
- `SNX-02`;
- `AML-*`;
- `AUD-03`;
- frontend `UI-*`;
- real OpenSanctions production watchlist ingestion.

---

## 2026-05-19 — Phase 06 Slice 04 Capture to Settlement Runtime Verification

Scope:
- Phase 06 Slice 04 backend/runtime implementation in `platform`.
- Durable settlement persistence, internal captured-payment processor and minimal balanced settlement ledger movement.

Build evidence:
- Offline Docker Gradle compile/build using cached Gradle home completed for `:apps:platform:compileKotlin`.
- Offline Docker Gradle `bootJar` completed for `platform`, `acquirer`, `network`, `issuer` and `vault`.
- Runtime images were built from the produced jars to avoid the known slow Dockerfile Gradle stage.

Runtime environment:
- Isolated compose project: `mfp_set04`.
- Stack services: `platform`, `acquirer`, `network`, `issuer`, `vault`, service DBs, Kafka and Keycloak.
- Platform startup applied Flyway through `V21__capture_to_settlement_foundation.sql`.

Runtime-driven fixes applied:
- `V21__capture_to_settlement_foundation.sql` settlement item FK was corrected from non-existent `identity.merchants` to `merchant.merchants` after the first isolated Platform startup failed during Flyway migration.

Primary command:

```bash
COMPOSE_FILE=deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=mfp_set04 \
PLATFORM_BASE_URL=http://platform:8080 \
ISSUER_BASE_URL=http://issuer:8080 \
VAULT_BASE_URL=http://vault:8080 \
ACQUIRER_BASE_URL=http://acquirer:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_set04_default \
scripts/runtime/reg_phase06_capture_to_settlement.sh
```

Observed primary output:

```text
SET-01 capture to settlement foundation pass intent_id=<uuid> batch_id=<uuid>
```

Runtime assertions passed:
- the script created a merchant API key, end-user card and funded wallet, then created, authorized and captured a public payment intent;
- `POST /internal/settlement/process-captured?limit=10` returned a batch id and included the captured payment intent id;
- `settlement.settlement_items` contains exactly one `SETTLED` item for the payment intent with `gross_amount = 18.2500` and `currency = 'EUR'`;
- `merchant.payment_intents.state` became `SETTLED`;
- the settlement item references one `CARD_PAYMENT_SETTLEMENT` ledger journal;
- the settlement journal has balanced debit/credit postings and at least two postings;
- public `GET /v1/payment_intents/{id}` returns the intent as queryable with `state = SETTLED` and retained `capturedAt`;
- rerunning the processor does not create a second settlement item for the same payment intent.

Targeted regression commands:

```bash
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=mfp_set04 PLATFORM_BASE_URL=http://platform:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 ACQUIRER_BASE_URL=http://acquirer:8080 PLATFORM_CURL_CONTAINER_NETWORK=mfp_set04_default scripts/runtime/reg_phase05_payment_capture.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=mfp_set04 PLATFORM_BASE_URL=http://platform:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 ACQUIRER_BASE_URL=http://acquirer:8080 PLATFORM_CURL_CONTAINER_NETWORK=mfp_set04_default scripts/runtime/reg_phase05_authorization_approved_hold.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=mfp_set04 PLATFORM_BASE_URL=http://platform:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 ACQUIRER_BASE_URL=http://acquirer:8080 PLATFORM_CURL_CONTAINER_NETWORK=mfp_set04_default scripts/runtime/reg_phase05_authorization_structured_declines.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=mfp_set04 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mfp_set04_default scripts/runtime/reg_phase04_public_api_response_shape.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=mfp_set04 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mfp_set04_default scripts/runtime/reg_phase03_ledger_reconciliation.sh
```

Regression output captured:

```text
PAY-06 payment capture foundation pass intent_id=<uuid>
PAY-04 authorization approved hold pass intent_id=<uuid> card_token=<token>
PAY-05 authorization structured declines pass
PAY-01 public API response shape pass
LDG-05 ledger reconciliation pass
```

Result tags:
- `SET-01` — pass.
- `PAY-06` — pass targeted regression.
- `PAY-04` — pass targeted regression.
- `PAY-05` — pass targeted regression.
- `PAY-01` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Not claimed:
- fee split correctness;
- refunds;
- payouts;
- chargebacks;
- acquirer settlement-file export/projection;
- merchant dashboard settlement UI.

---

## 2026-05-19 — Phase 06 Slice 05 Settlement Fee Split Runtime Verification

Scope:
- Phase 06 Slice 05 backend/runtime implementation in `platform`.
- Deterministic local fee split for settled card payments.
- No acquirer settlement projection, refunds, payouts, chargebacks, scheduled batch orchestration or merchant settlement UI was implemented or claimed.

Build evidence:
- `bash -n product/scripts/runtime/reg_phase06_settlement_fee_split.sh` — pass.
- Offline Docker Gradle `:apps:platform:compileKotlin` completed using cached Gradle home.
- Offline Docker Gradle `:apps:platform:bootJar` completed; the multi-service `bootJar` command stalled after Platform jar rebuild, so the runtime used the freshly rebuilt Platform jar plus existing previously verified Acquirer/Network/Issuer/Vault jars.
- Runtime images were assembled from local jars to avoid the known slow Dockerfile Gradle stage.

Runtime environment:
- Isolated Compose project: `agent-set02`.
- Compose override: `/tmp/mfp-set02-override.yml` used local jar images for `platform`, `acquirer`, `network`, `issuer` and `vault`.
- Runtime scripts used compose-network URLs:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `ACQUIRER_BASE_URL=http://acquirer:8080`
  - `ISSUER_BASE_URL=http://issuer:8080`
  - `VAULT_BASE_URL=http://vault:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default`
- Platform startup applied Flyway through `V23__settlement_fee_split.sql`.
- The isolated compose project was stopped with `docker compose ... down -v --remove-orphans` after verification.

Primary command:

```bash
COMPOSE_FILE=deploy/docker-compose.yml \
COMPOSE_PROJECT_NAME=agent-set02 \
PLATFORM_BASE_URL=http://platform:8080 \
ISSUER_BASE_URL=http://issuer:8080 \
VAULT_BASE_URL=http://vault:8080 \
ACQUIRER_BASE_URL=http://acquirer:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default \
scripts/runtime/reg_phase06_settlement_fee_split.sh
```

Primary output:

```text
SET-02 settlement fee split pass intent_id=e52751ea-263e-4588-9c77-4231dd750f9f
```

Runtime assertions passed:
- the script created a merchant API key, end-user card and funded wallet, then created, authorized and captured a public payment intent for EUR 18.25;
- settlement processor created one settlement item and one `CARD_PAYMENT_SETTLEMENT` journal for the payment intent;
- `settlement.settlement_items` persisted exact deterministic values:
  - gross `18.2500`;
  - merchant net `17.8800`;
  - issuer interchange `0.2200`;
  - network assessment `0.0300`;
  - acquirer margin `0.1200`;
- fee components sum exactly to gross;
- ledger postings include:
  - `CARD_SETTLEMENT_CLEARING` debit `18.2500`;
  - merchant `MERCHANT_SETTLEMENT:<merchantId>` credit `17.8800`;
  - `ISSUER_INTERCHANGE_REVENUE` credit `0.2200`;
  - `NETWORK_ASSESSMENT_REVENUE` credit `0.0300`;
  - `ACQUIRER_MARGIN_REVENUE` credit `0.1200`;
- linked settlement journal is balanced and contains exactly five postings;
- rerunning settlement processor does not create a duplicate settlement item or duplicate `CARD_PAYMENT_SETTLEMENT` journal for the same payment intent.

Targeted regression command:

```bash
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=agent-set02 PLATFORM_BASE_URL=http://platform:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 ACQUIRER_BASE_URL=http://acquirer:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default scripts/runtime/reg_phase06_capture_to_settlement.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=agent-set02 PLATFORM_BASE_URL=http://platform:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 ACQUIRER_BASE_URL=http://acquirer:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default scripts/runtime/reg_phase05_payment_capture.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=agent-set02 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default scripts/runtime/reg_phase04_public_api_response_shape.sh
COMPOSE_FILE=deploy/docker-compose.yml COMPOSE_PROJECT_NAME=agent-set02 PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default scripts/runtime/reg_phase03_ledger_reconciliation.sh
```

Regression output:

```text
SET-01 capture to settlement foundation pass intent_id=e6a3a094-aa44-4ed4-aef9-17d06e8c5057 batch_id=bc6ab45b-8122-4205-8eb9-14d048075a9d
PAY-06 payment capture foundation pass intent_id=7e2901c4-2544-4290-9452-842b90a31eca
PAY-01 public API response shape pass
LDG-05 ledger reconciliation pass
```

Result tags:
- `SET-02` — pass.
- `SET-01` — pass targeted regression.
- `PAY-06` — pass targeted regression.
- `PAY-01` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Not claimed:
- `SET-03`;
- `SET-04`;
- refunds;
- payouts/Stripe Connect;
- chargebacks;
- merchant settlement frontend;
- tenant pricing engine.

---

## 2026-05-19 — Phase 07 Slice 04 Sanctions False-Positive Exception Runtime Verification

Scope:
- Backend/runtime sub-scope of Phase 07 Slice 04.
- Target `SNX-02` plus targeted `SNX-01` regression.
- No frontend sanctions UI, true-match block, freeze/unfreeze, AML, document preview/read-audit or wallet/card/payment sanctions gating beyond KYC manual approval was implemented or claimed.

Implementation evidence:
- Added migration `product/apps/platform/src/main/resources/db/migration/V22__sanctions_false_positive_exception.sql`.
- Added backoffice sanctions hit list/detail/decision APIs in Platform.
- Added `CLEAR_FALSE_POSITIVE` decision persistence, false-positive exception persistence and audit rows.
- Added false-positive exception suppression for the same end user/OpenSanctions matched entity during KYC manual approval.
- Added retained runtime script `product/scripts/runtime/reg_phase07_sanctions_false_positive.sh`.

Verification environment:
- Isolated Compose project: `agent11snx02`.
- Platform host port: `19181`; Keycloak host port: `19180`; Platform DB host port: `19433`.
- Runtime used `agent11snx02-platform` image assembled from the previous runtime boot jar plus freshly compiled changed sanctions classes/resources because Gradle-in-Docker builds stalled in this environment after daemon startup. Flyway applied `V22` and Platform logs showed schema at version `22`.
- Runtime scripts were executed through compose-network curl mode (`PLATFORM_CURL_CONTAINER_NETWORK=agent11snx02_default`, `KEYCLOAK_CURL_CONTAINER_NETWORK=agent11snx02_default`) because host-published HTTP calls hung in this environment.

Commands/evidence:
- `bash -n product/scripts/runtime/reg_phase07_sanctions_false_positive.sh` — pass.
- `bash -n product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh` — pass after script was updated to support an optional compose override for isolated no-build runtime slots.
- `product/scripts/runtime/reg_phase07_sanctions_false_positive.sh` — pass with output:

```text
SNX-02 sanctions false-positive exception pass hit_id=9656e6c7-ffa0-4026-8aaa-4598357fcfb1 end_user_id=82836056-ee88-4b69-a617-82e685aeaf75
```

SNX-02 observations:
- Deterministic OpenSanctions local `match` mode first blocked KYC manual approval with `sanctions_possible_match` and created an `OPEN` `POSSIBLE_MATCH` sanctions hit.
- Compliance token listed and retrieved the sanctions hit detail.
- `backoffice_operator` decision attempt returned `forbidden_role`.
- Compliance `CLEAR_FALSE_POSITIVE` decision returned `CLEARED_FALSE_POSITIVE`, persisted one decision row, one active false-positive exception row and one `sanctions.hit_false_positive_cleared` audit row.
- Re-attempting approval on the same still-`IN_REVIEW` KYC profile with the same deterministic local match succeeded; no additional blocking sanctions hit was created for that KYC profile, and `sanctions.opensanctions_screening_suppressed` audit row was written.

Targeted regression:
- `product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh` — pass with output:

```text
SNX-01 OpenSanctions fail-closed pass
```

SNX-01 regression observations:
- OpenSanctions unavailable path still blocked KYC manual approval and created an `OPEN` `SCREENING_UNAVAILABLE` hit.
- Possible-match path still blocked KYC manual approval and created an `OPEN` `POSSIBLE_MATCH` hit.
- No-match path still allowed approval.

Not claimed:
- `AML-*`, `AUD-03`, frontend `UI-*`, true-match permanent block, freeze/unfreeze, settlement/payment processing.

---

## 2026-05-19 — Phase 08 Slice 01 AML Velocity Alert Foundation Runtime Verification

Scope:
- Backend/runtime sub-scope of Phase 08 Slice 01.
- Target `AML-01` plus targeted `RUN-01` regression.

Implementation evidence:
- Added `V24__aml_velocity_alert_foundation.sql` with `aml.aml_alerts` and `aml.aml_rule_evaluations`.
- Added Platform AML repository/service/controller/model boundary.
- Added internal `POST /internal/aml/evaluate-velocity`.
- Added retained runtime script `product/scripts/runtime/reg_phase08_aml_velocity_alert.sh`.

Build/runtime note:
- Normal host Gradle was unavailable and Docker/Compose Gradle builds stalled at daemon startup.
- Runtime verification used the existing latest Platform boot jar image from the prior verified slice, targeted Kotlin compilation for AML classes in the Gradle Docker image, and a patched local verification image `agent-aml01-platform:latest`.
- Isolated compose project: `agent-aml01`.

Commands run:
- `bash -n product/scripts/runtime/reg_phase08_aml_velocity_alert.sh` — passed.
- Targeted Kotlin compiler invocation for AML classes in `gradle:8.14.3-jdk21` — passed.
- Patched image build: `docker build -t agent-aml01-platform:latest /tmp/aml01-patch` — passed.
- `COMPOSE_PROJECT_NAME=agent-aml01 ... docker compose -f product/deploy/docker-compose.yml -f /tmp/agent-aml01-compose.override.yml up -d platform` — passed.
- `COMPOSE_PROJECT_NAME=agent-aml01 COMPOSE_FILE=product/deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-aml01_default product/scripts/runtime/reg_phase08_aml_velocity_alert.sh` — passed.
- Network-local `RUN-01` health checks for platform/acquirer/network/issuer/vault — passed.

Runtime script output:

```text
AML-01 velocity alert pass alert_id=48380472-7459-4aa6-8182-95464700404d end_user_id=0afd7c01-abc0-4c21-8cfc-e583f0ce271c
RUN-01 platform health pass
RUN-01 acquirer health pass
RUN-01 network health pass
RUN-01 issuer health pass
RUN-01 vault health pass
```

AML-01 observations:
- Synthetic completed wallet deposit activity for one end user tripped the `VELOCITY` rule.
- One `OPEN` `MEDIUM` AML alert was persisted for that end user.
- `aml.alert_created` audit row was persisted.
- A second evaluation for the same user/rule/window returned the same alert and did not create a duplicate open alert.
- `aml.aml_rule_evaluations` retained both evaluations.

Not run:
- `LDG-05`, because AML implementation reads completed wallet activity but does not write ledger tables directly.

Not claimed:
- `AML-02`, `AML-03`, `AML-04`, AML review decisions, account freeze/unfreeze, SoF/two-eyes controls, AML frontend, SAR, settlement/refund/chargeback behavior.

---

## 2026-05-21 — Phase 06 Slice 06 Acquirer Settlement Projection Verification Attempt

Scope:
- Phase 06 Slice 06 acquirer settlement projection backend/runtime implementation.
- Added Acquirer projection persistence/ingestion and Platform projection publication path.

Commands attempted:
- `docker run --rm -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace -w /workspace -e GRADLE_USER_HOME=/tmp/gradle-home -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2' gradle:8.14.3-jdk21 gradle --no-daemon :apps:acquirer:compileKotlin`
- `docker run --rm --user root -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace -w /workspace -e GRADLE_USER_HOME=/tmp/gradle-home -e GRADLE_OPTS='-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -Dkotlin.compiler.execution.strategy=in-process -Dorg.gradle.workers.max=2' gradle:8.14.3-jdk21 gradle --no-daemon :apps:acquirer:compileKotlin --console=plain`
- `docker run --rm --user root -v /home/nickf/Documents/sre_projects/mini-fintech-platform_1/product:/workspace -w /workspace -e GRADLE_USER_HOME=/tmp/gradle-home gradle:8.14.3-jdk21 gradle --no-daemon --project-cache-dir /tmp/project-cache :apps:acquirer:compileKotlin --console=plain --no-watch-fs`

Observed result:
- Each Gradle container printed the Gradle welcome text and `Daemon will be stopped at the end of the build`, then remained running without task progress or compiler output.
- The hanging Gradle containers were stopped to avoid leaking runtime resources.
- Host `gradle` is not installed and the repo has no Gradle wrapper, so a non-container host compile path was unavailable.

Static evidence completed:
- New retained script `product/scripts/runtime/reg_phase06_acquirer_settlement_projection.sh` exists and is executable.
- Bracket/parenthesis balance check over changed Kotlin files passed.
- `planning/runtime_checklists.md` maps `SET-03` to the retained script.

Not claimed:
- `SET-03` is not marked passed in this entry.
- Targeted regressions (`SET-02`, `SET-01`, `PAY-06`, `LDG-05`) were not run in this session because compile/runtime startup could not be completed.

Next verification step:
- Re-run `product/scripts/runtime/reg_phase06_acquirer_settlement_projection.sh` and targeted regressions in an isolated compose slot once Gradle/Docker build progresses past daemon startup.

---

## 2026-05-21 — Phase 06 Slice 06 Acquirer Settlement Projection Runtime Verification

Scope:
- Phase 06 Slice 06 acquirer settlement projection backend/runtime implementation.
- Target `SET-03` plus targeted settlement/payment/ledger regressions.

Build evidence:
- `docker compose -f product/deploy/docker-compose.yml build platform acquirer` — passed.
- Isolated runtime slot build also passed for `platform`, `acquirer`, `network`, `issuer` and `vault` while starting the compose project.

Runtime environment:
- Isolated Compose project: `agent-set03`.
- Compose file: `product/deploy/docker-compose.yml`.
- Scripts used compose-network URLs:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `ACQUIRER_BASE_URL=http://acquirer:8080`
  - `NETWORK_BASE_URL=http://network:8080`
  - `ISSUER_BASE_URL=http://issuer:8080`
  - `VAULT_BASE_URL=http://vault:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=agent-set03_default`

Primary command:

```bash
COMPOSE_PROJECT_NAME=agent-set03 \
COMPOSE_FILE=product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
ACQUIRER_BASE_URL=http://acquirer:8080 \
NETWORK_BASE_URL=http://network:8080 \
ISSUER_BASE_URL=http://issuer:8080 \
VAULT_BASE_URL=http://vault:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=agent-set03_default \
product/scripts/runtime/reg_phase06_acquirer_settlement_projection.sh
```

Observed primary output:

```text
SET-03 acquirer settlement projection pass intent_id=d78b340c-a76a-4c5e-b964-89e95e4803a1 settlement_item_id=b142ebee-4445-4731-9b0d-6d456ac1d644
```

Runtime assertions passed:
- created merchant, API key, funded end user, issued card and payment intent;
- authorized, captured and settled the payment intent;
- `POST /internal/settlement/publish-projections?limit=200` returned successful Acquirer projection counts;
- Acquirer `merchant_settlement.balance_projection` contains exactly one row for the Platform settlement item;
- Acquirer projection values match Platform settlement item values for merchant id, payment intent id, gross amount, merchant net amount, interchange amount, network assessment amount, acquirer margin amount and currency;
- publishing projections again does not duplicate the projection row.

Targeted regression commands:

```bash
COMPOSE_PROJECT_NAME=agent-set03 COMPOSE_FILE=product/deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 ACQUIRER_BASE_URL=http://acquirer:8080 NETWORK_BASE_URL=http://network:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set03_default product/scripts/runtime/reg_phase06_settlement_fee_split.sh
COMPOSE_PROJECT_NAME=agent-set03 COMPOSE_FILE=product/deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 ACQUIRER_BASE_URL=http://acquirer:8080 NETWORK_BASE_URL=http://network:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set03_default product/scripts/runtime/reg_phase06_capture_to_settlement.sh
COMPOSE_PROJECT_NAME=agent-set03 COMPOSE_FILE=product/deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 ACQUIRER_BASE_URL=http://acquirer:8080 NETWORK_BASE_URL=http://network:8080 ISSUER_BASE_URL=http://issuer:8080 VAULT_BASE_URL=http://vault:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set03_default product/scripts/runtime/reg_phase05_payment_capture.sh
COMPOSE_PROJECT_NAME=agent-set03 COMPOSE_FILE=product/deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=agent-set03_default product/scripts/runtime/reg_phase03_ledger_reconciliation.sh
```

Regression output:

```text
SET-02 settlement fee split pass intent_id=c9309b72-9399-4076-8b08-4386b24576be
SET-01 capture to settlement foundation pass intent_id=078b2d2f-51ca-46da-9261-77b77630fe17 batch_id=95217c4c-fcef-45e7-929e-3fbe18ceef84
PAY-06 payment capture foundation pass intent_id=580f3d26-99e7-4ea9-b8ba-971dde5251be
LDG-05 ledger reconciliation pass
```

Result tags:
- `SET-03` — pass.
- `SET-02` — pass targeted regression.
- `SET-01` — pass targeted regression.
- `PAY-06` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Not claimed:
- refunds (`SET-04`);
- payouts;
- chargebacks;
- merchant settlement frontend;
- bank file export;
- scheduled reconciliation jobs.

---

## 2026-05-21 — Phase 08 Slice 02 AML Structuring Alert Runtime Verification

Scope:
- Phase 08 Slice 02 AML structuring alert backend/runtime implementation.
- Target `AML-02` plus targeted `AML-01` and `RUN-01` regressions.

Build evidence:
- `bash -n product/scripts/runtime/reg_phase08_aml_structuring_alert.sh product/scripts/runtime/reg_phase08_aml_velocity_alert.sh` — passed.
- `git diff --check` — passed before documentation updates.
- `docker compose -f product/deploy/docker-compose.yml build platform` — passed.

Runtime environment:
- Isolated Compose project: `mfp-aml02`.
- Compose file: `product/deploy/docker-compose.yml`.
- Scripts used compose-network URLs:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml02_default`

Primary command:

```bash
COMPOSE_PROJECT_NAME=mfp-aml02 \
COMPOSE_FILE=product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml02_default \
product/scripts/runtime/reg_phase08_aml_structuring_alert.sh
```

Observed primary output:

```text
AML-02 structuring alert pass alert_id=a09e0ab4-9834-49f0-9af7-d0132e9dd285 end_user_id=e1febdfa-27dd-4fb2-8a71-a9dbfe8d5c7d
```

Runtime assertions passed:
- synthetic completed wallet activity for one end user included three qualifying EUR 9,500.00 movements and one non-qualifying EUR 8,500.00 movement;
- `POST /internal/aml/evaluate-structuring` returned `ruleCode = STRUCTURING`, `severity = HIGH`, `observedCount = 3` and `alertCreated = true`;
- one `OPEN` `HIGH` AML alert was persisted for that end user/rule/window;
- `aml.alert_created` audit row was persisted;
- a second evaluation for the same user/rule/window returned the existing alert and did not create a duplicate open alert;
- `aml.aml_rule_evaluations` retained both evaluations.

Targeted regression command:

```bash
COMPOSE_PROJECT_NAME=mfp-aml02 COMPOSE_FILE=product/deploy/docker-compose.yml PLATFORM_BASE_URL=http://platform:8080 PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml02_default product/scripts/runtime/reg_phase08_aml_velocity_alert.sh
```

Regression output:

```text
AML-01 velocity alert pass alert_id=024b328c-15a3-429b-a3a4-72de07323ac8 end_user_id=5f94f51c-2957-44a7-9a5c-26c62636a357
```

RUN-01 targeted health output:

```text
platform: {"service":"platform","status":"UP"}
acquirer: {"service":"acquirer","status":"UP"}
network: {"service":"network","status":"UP"}
issuer: {"service":"issuer","status":"UP"}
vault: {"service":"vault","status":"UP"}
```

Result tags:
- `AML-02` — pass.
- `AML-01` — pass targeted regression.
- `RUN-01` — pass targeted regression.

Not run:
- `LDG-05`, because AML implementation reads completed wallet activity but does not write ledger tables directly.

Not claimed:
- `AML-03`;
- `AML-04`;
- AML review decisions;
- account freeze/unfreeze;
- SoF (`WLT-03`);
- two-eyes (`WLT-04`);
- frontend `UI-*`;
- SAR.

---

## 2026-05-21 — Phase 08 Slice 03 AML Dormancy-Break Alert Runtime Verification

Scope:
- Phase 08 Slice 03 AML dormancy-break alert backend/runtime implementation.
- Target `AML-03` plus targeted `AML-02`, `AML-01` and `RUN-01` regressions.

Build/static evidence:
- `bash -n product/scripts/runtime/reg_phase08_aml_dormancy_break_alert.sh product/scripts/runtime/reg_phase08_aml_structuring_alert.sh product/scripts/runtime/reg_phase08_aml_velocity_alert.sh` — passed.
- `git diff --check` — passed before documentation updates.
- `docker compose -f product/deploy/docker-compose.yml build platform` — passed.

Runtime environment:
- Isolated Compose project: `mfp-aml03`.
- Compose file: `product/deploy/docker-compose.yml`.
- Scripts used compose-network URLs:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml03_default`

Primary command:

```bash
COMPOSE_PROJECT_NAME=mfp-aml03 \
COMPOSE_FILE=product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml03_default \
product/scripts/runtime/reg_phase08_aml_dormancy_break_alert.sh
```

Observed primary output:

```text
AML-03 dormancy-break alert pass alert_id=3b48877c-4898-472a-948e-70c87a890013 end_user_id=596adc34-1ac6-4dd9-b692-bc3bd85e6b49
```

Runtime assertions passed:
- synthetic end user had one completed movement 45 days before evaluation;
- synthetic end user had no completed movement during the 30-day dormant gap;
- synthetic end user had two recent completed movements totaling EUR 1,300.00 in the latest 24-hour window;
- `POST /internal/aml/evaluate-dormancy-break` returned `ruleCode = DORMANCY_BREAK`, `severity = HIGH`, `observedCount = 2`, `observedAmount = 1300.00`, `thresholdAmount = 1000.00`, previous activity found and zero dormant-gap activity;
- one `OPEN` `HIGH` AML alert was persisted for that end user/rule/window;
- alert metadata recorded `observedAmount = 1300.00` and `thresholdAmount = 1000.00`;
- `aml.alert_created` audit row was persisted;
- a second evaluation for the same user/rule/window returned the existing alert and did not create a duplicate open alert;
- `aml.aml_rule_evaluations` retained both evaluations.

Targeted regression outputs:

```text
AML-02 structuring alert pass alert_id=2d7dc6a9-cba7-431f-a1e9-9e19bdcc8709 end_user_id=7bdab49b-fcfb-4073-bf26-6dbbc52bdc2a
AML-01 velocity alert pass alert_id=e7952bb7-16fc-47f8-a744-4480bbb42bc9 end_user_id=4a287aac-8596-48a8-aeee-b31d7a767ffe
```

RUN-01 health output:

```text
platform: {"service":"platform","status":"UP"}
acquirer: {"service":"acquirer","status":"UP"}
network: {"service":"network","status":"UP"}
issuer: {"service":"issuer","status":"UP"}
vault: {"service":"vault","status":"UP"}
```

Result tags:
- `AML-03` — pass.
- `AML-02` — pass targeted regression.
- `AML-01` — pass targeted regression.
- `RUN-01` — pass targeted regression.

Not run:
- `LDG-05`, because AML implementation reads completed wallet activity but does not write ledger tables directly.

Not claimed:
- `AML-04`;
- AML review decisions;
- account freeze/unfreeze;
- SoF (`WLT-03`);
- two-eyes (`WLT-04`);
- frontend `UI-*`;
- SAR.

---

## 2026-05-21 — Phase 08 Slice 04 AML Critical Auto-Freeze Runtime Verification

Scope:
- Phase 08 Slice 04 AML critical auto-freeze backend/runtime implementation.
- Target `AML-04` plus targeted `AML-03`, `AML-02`, `AML-01` and `RUN-01` regressions.

Build/static evidence:
- `bash -n product/scripts/runtime/reg_phase08_aml_critical_auto_freeze.sh product/scripts/runtime/reg_phase08_aml_dormancy_break_alert.sh product/scripts/runtime/reg_phase08_aml_structuring_alert.sh product/scripts/runtime/reg_phase08_aml_velocity_alert.sh` — passed.
- `git diff --check` — passed before documentation updates.
- `docker compose -f product/deploy/docker-compose.yml build platform` — passed.

Runtime environment:
- Isolated Compose project: `mfp-aml04`.
- Compose file: `product/deploy/docker-compose.yml`.
- Scripts used compose-network URLs:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml04_default`

Primary command:

```bash
COMPOSE_PROJECT_NAME=mfp-aml04 \
COMPOSE_FILE=product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp-aml04_default \
product/scripts/runtime/reg_phase08_aml_critical_auto_freeze.sh
```

Observed primary output:

```text
AML-04 critical auto-freeze pass alert_id=d35424e5-c2b6-4200-ae28-5e0607304ae7 end_user_id=aadf0412-7793-4fed-a019-47f1d4c62601
```

Runtime assertions passed:
- active end user was registered, verified and logged in;
- one `OPEN` `CRITICAL` AML alert was seeded for that end user;
- `POST /internal/aml/process-critical-auto-freezes` processed exactly one alert and froze exactly one actor;
- `identity.actor_controls` contains `END_USER` / `FROZEN` / `aml_critical_alert`;
- AML alert status moved to `ACCOUNT_FROZEN_PERMANENT`;
- `identity.actor_control_changed` and `aml.critical_alert_auto_frozen` audit rows were persisted;
- end-user `POST /api/v1/deposits` returned `403 actor_control_blocked`;
- no deposit request was created after the freeze;
- processor rerun returned zero processed/frozen rows and did not duplicate the AML auto-freeze audit row.

Targeted regression outputs:

```text
AML-03 dormancy-break alert pass alert_id=0c2a2190-2466-4e7c-970c-5840254c55ca end_user_id=88e17d1b-bd0e-4878-9d44-23e8cc83bc74
AML-02 structuring alert pass alert_id=dd50e24f-5578-4021-91f4-3415189cc633 end_user_id=a873fb70-b02d-44df-9272-31706516a7e9
AML-01 velocity alert pass alert_id=9d4a4f5c-d6f2-475a-9efb-feef6cfa1c93 end_user_id=797d1e40-14ca-4138-a911-7f1a16b5edf3
```

RUN-01 health output:

```text
platform: {"service":"platform","status":"UP"}
acquirer: {"service":"acquirer","status":"UP"}
network: {"service":"network","status":"UP"}
issuer: {"service":"issuer","status":"UP"}
vault: {"service":"vault","status":"UP"}
```

Result tags:
- `AML-04` — pass.
- `AML-03` — pass targeted regression.
- `AML-02` — pass targeted regression.
- `AML-01` — pass targeted regression.
- `RUN-01` — pass targeted regression.

Not run:
- `LDG-05`, because AML auto-freeze updates actor-control state and alert status but does not write ledger tables directly.

Not claimed:
- AML alert review decisions;
- account unfreeze workflow;
- SoF (`WLT-03`);
- two-eyes (`WLT-04`);
- frontend `UI-*`;
- SAR.

---

## 2026-05-21 — Phase 08 Slice 05 Source-of-Funds Deposit Threshold Runtime Verification

Scope:
- Phase 08 Slice 05 Source-of-Funds deposit threshold backend/runtime implementation.
- Target `WLT-03` plus targeted low-value deposit regressions.

Build/static evidence:
- `bash -n product/scripts/runtime/reg_phase08_sof_deposit_threshold.sh product/scripts/runtime/reg_phase03_wallet_deposit_amount_validation.sh product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh` — passed.
- `git diff --check` — passed before documentation updates.
- `docker compose -f product/deploy/docker-compose.yml build platform` — passed.

Runtime environment:
- Isolated Compose project: `mfp-wlt03`.
- Compose file: `product/deploy/docker-compose.yml`.
- Primary WLT-03 script used compose-network URL:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=mfp-wlt03_default`

Primary command:

```bash
COMPOSE_PROJECT_NAME=mfp-wlt03 \
COMPOSE_FILE=product/deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mfp-wlt03_default \
product/scripts/runtime/reg_phase08_sof_deposit_threshold.sh
```

Observed primary output:

```text
WLT-03 source-of-funds deposit threshold pass deposit_id=fbe86008-ad5d-4c77-a7bc-6067affb8ce3 user_id=93f5f570-f6c3-4406-a9f1-1db90fcf25e8
```

Runtime assertions passed:
- end user registered, verified and logged in;
- high-value EUR 15,000.00 deposit was accepted in `REQUESTED` state;
- high-value deposit response returned `sourceOfFundsRequired = true` and `sourceOfFundsSubmitted = false`;
- no SoF declaration existed immediately after deposit creation;
- `POST /api/v1/deposits/{depositId}/source-of-funds` persisted one `wallet.source_of_funds_declarations` row;
- deposit moved to `PENDING_OPERATOR_REVIEW` after SoF declaration;
- `wallet.source_of_funds_submitted` audit row was persisted;
- duplicate SoF submission after transition returned `409 deposit_state_conflict`.

Targeted regression outputs:

```text
WLT deposit happy path pass
WLT deposit amount validation pass
```

Platform health output:

```text
{"service":"platform","status":"UP"}
```

Result tags:
- `WLT-03` — pass.
- Low-value deposit happy path — pass targeted regression.
- Deposit amount validation — pass targeted regression.

Not claimed:
- two-eyes approval (`WLT-04`);
- SoF backoffice review queue;
- document upload/storage;
- high-value withdrawals/transfers;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 08 Slice 06 Two-Eyes Enforcement Runtime Verification

Scope:
- Phase 08 Slice 06 two-eyes enforcement backend/runtime implementation.
- Target `WLT-04` plus targeted `WLT-03` and `LDG-05` regressions.

Build/static evidence:
- `bash -n product/scripts/runtime/reg_phase08_two_eyes_enforcement.sh product/scripts/runtime/reg_phase08_sof_deposit_threshold.sh product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh product/scripts/runtime/reg_phase03_wallet_withdraw_hold_complete.sh` — passed.
- `git diff --check` — passed before documentation updates.
- `cd product && ./gradlew --version` — Gradle Wrapper `8.14.3` passed.
- `cd product && ./gradlew --no-daemon :apps:platform:compileKotlin` — passed.
- `cd product && ./gradlew --no-daemon :apps:platform:bootJar` — passed and produced `product/apps/platform/build/libs/platform-0.1.0-SNAPSHOT.jar`.

Runtime environment:
- Isolated Compose project: `mfp-wlt04`.
- Compose file: `product/deploy/docker-compose.yml` plus local no-host-port/external-network override for this runtime slot.
- Docker bridge network reused for compose-network-only access: `mfp_agent8_kyc03_default`.
- Scripts used compose-network URLs:
  - `PLATFORM_BASE_URL=http://platform:8080`
  - `KEYCLOAK_BASE_URL=http://keycloak:8080`
  - `PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default`
  - `KEYCLOAK_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default`

Runtime setup note:
- Docker Compose bridge network creation and host-port programming failed in this local Docker daemon with missing iptables chains (`DOCKER-FORWARD` / `DOCKER`), so host-published ports were reset and an existing bridge network was reused.
- The Platform runtime image was built from the locally verified `bootJar` output because the Dockerfile's in-container Gradle build path was blocked by dependency/plugin resolution behavior in this environment.
- Platform Flyway applied migration `V26__two_eyes_enforcement.sql` and reported schema version `v26` at startup.

Primary command:

```bash
COMPOSE_PROJECT_NAME=mfp-wlt04 \
COMPOSE_FILE=deploy/docker-compose.yml \
KEYCLOAK_BASE_URL=http://keycloak:8080 \
PLATFORM_BASE_URL=http://platform:8080 \
KEYCLOAK_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default \
PLATFORM_CURL_CONTAINER_NETWORK=mfp_agent8_kyc03_default \
scripts/runtime/reg_phase08_two_eyes_enforcement.sh
```

Observed primary output:

```text
WLT-04 two-eyes enforcement pass deposit_id=bb19df8a-0e21-4734-998e-396c26dd8c86 withdrawal_id=4a17f619-8b2d-408c-88f3-056bcf647441 user_id=a4edcac8-2ac3-46b2-9952-5bf67c4f95a4
```

Runtime assertions passed:
- high-value EUR 15,000.00 deposit was created and remained `REQUESTED` until Source-of-Funds declaration;
- SoF declaration moved the high-value deposit to `PENDING_OPERATOR_REVIEW`;
- first backoffice actor approval moved the deposit to `READY_FOR_SECOND_REVIEW`;
- no `WALLET_DEPOSIT` ledger journal existed after the first deposit approval;
- same actor second deposit approval returned `403 two_eyes_same_actor_denied`;
- second distinct backoffice actor approval moved the deposit to `COMPLETED`;
- exactly one `WALLET_DEPOSIT` ledger journal existed for the high-value deposit;
- `wallet.deposit_marked_for_second_review` and `wallet.deposit_approved` audit rows were persisted;
- high-value EUR 12,000.00 withdrawal was accepted and posted the existing hold journal;
- first backoffice actor completion moved the withdrawal to `READY_FOR_SECOND_REVIEW`;
- no `WALLET_WITHDRAW_COMPLETE` ledger journal existed after the first withdrawal completion mark;
- same actor second withdrawal completion returned `403 two_eyes_same_actor_denied`;
- second distinct backoffice actor completion moved the withdrawal to `COMPLETED`;
- exactly one `WALLET_WITHDRAW_COMPLETE` ledger journal existed for the high-value withdrawal;
- `wallet.withdrawal_marked_for_second_review` and `wallet.withdrawal_completed` audit rows were persisted;
- internal ledger reconciliation endpoint reported balanced journals.

Targeted regression outputs:

```text
WLT-03 source-of-funds deposit threshold pass deposit_id=2f630d5e-59c5-497b-b427-6e0ea9373a12 user_id=91797391-ecec-428e-bbb3-6250448ad5ea
LDG-05 ledger reconciliation pass
```

Result tags:
- `WLT-04` — pass.
- `WLT-03` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Not claimed:
- senior compliance override / bypass;
- dedicated SoF backoffice review queue;
- object-storage document upload;
- frontend `UI-*`;
- high-value internal transfers;
- chargebacks (`CHB-*`);
- refunds (`SET-04`).

---

## 2026-05-21 — Phase 09 Slice 01 Chargeback Initiation Runtime Verification

Scope:
- `CHB-01` backend/runtime implementation for cardholder dispute initiation was added.
- Retained runtime script `product/scripts/runtime/reg_phase09_chargeback_initiation.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_chargeback_initiation.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar
product/gradlew --no-daemon -p product :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb01
PLATFORM_BASE_URL=http://platform:8080
ISSUER_BASE_URL=http://issuer:8080
VAULT_BASE_URL=http://vault:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash product/scripts/runtime/reg_phase09_chargeback_initiation.sh
```

Runtime output / confirmed evidence:

```text
dispute=1
payment_state=DISPUTED
audit=1
webhook=1
CHB-01 chargeback initiation pass dispute_id=027ee472-890a-4234-b734-bb321f144839 payment_intent_id=eb0fbc47-45d2-4839-8ef3-3682beab75c1 user_id=8bec44e7-361e-4ecc-8e6a-b79ee4d98b96
```

Targeted regression outputs:

```text
LDG-05 ledger reconciliation pass
SET-01 capture to settlement foundation pass intent_id=e7b4a495-62c3-4ad0-aaa7-5e2fd43efe00
```

Result tags:
- `CHB-01` — pass.
- `LDG-05` — pass targeted regression.
- `SET-01` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Service images were built from locally verified `bootJar` outputs.

Not claimed:
- provisional cardholder credit (`CHB-02`);
- merchant evidence (`CHB-03`);
- arbitration (`CHB-04`, `CHB-05`);
- chargeback ledger/reserve settlement movements;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 09 Slice 02 Provisional Cardholder Credit Runtime Verification

Scope:
- `CHB-02` backend/runtime implementation for provisional cardholder credit on successful dispute initiation.
- Retained runtime script `product/scripts/runtime/reg_phase09_chargeback_initiation.sh` now verifies both `CHB-01` and `CHB-02`.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_chargeback_initiation.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb02
PLATFORM_BASE_URL=http://platform:8080
ISSUER_BASE_URL=http://issuer:8080
VAULT_BASE_URL=http://vault:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash product/scripts/runtime/reg_phase09_chargeback_initiation.sh
```

Runtime output / confirmed evidence:

```text
dispute=1
journal=1
postings=2
dupe_journals=1
CHB-01/CHB-02 chargeback initiation provisional credit pass dispute_id=342bd75c-66ff-4dd6-a152-97237540e881 payment_intent_id=6a78dd0b-a029-4488-9b77-cb1d27de9fe1 user_id=2e9e8250-9973-4cf1-8d51-fadbcceeb2fa provisional_journal_id=99401ed1-9667-4280-be7d-dee31041791d
```

The runtime verification proved:
- successful dispute initiation still creates one `MERCHANT_NOTIFIED` dispute and marks the payment intent `DISPUTED`;
- `chargeback.disputes.provisional_credit_journal_id` points to the provisional credit journal;
- exactly one `CARDHOLDER_PROVISIONAL_CREDIT` journal exists for the dispute;
- the journal debits `ACQUIRER_DISPUTE_RESERVE` and credits `WALLET_USER:<cardholder>` for EUR 18.2500;
- duplicate same-cardholder initiation does not create a second dispute or provisional-credit journal;
- denied initiation paths do not post provisional-credit journals.

Targeted regression outputs:

```text
LDG-05 ledger reconciliation pass
SET-01 capture to settlement foundation pass intent_id=09636051-f55b-4a65-bcb1-8c690ea2c4e9
```

Result tags:
- `CHB-02` — pass.
- `CHB-01` — pass regression.
- `LDG-05` — pass targeted regression.
- `SET-01` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Platform image was built from the locally verified `bootJar` output.

Not claimed:
- merchant evidence (`CHB-03`);
- arbitration (`CHB-04`, `CHB-05`);
- provisional credit reversal on `WON`;
- permanent merchant debit / reserve release on `LOST`;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 09 Slice 03 Merchant Evidence Submission Runtime Verification

Scope:
- `CHB-03` backend/runtime implementation for merchant evidence submission.
- Retained runtime script `product/scripts/runtime/reg_phase09_merchant_evidence.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_merchant_evidence.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb03
PLATFORM_BASE_URL=http://platform:8080
ISSUER_BASE_URL=http://issuer:8080
VAULT_BASE_URL=http://vault:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash product/scripts/runtime/reg_phase09_merchant_evidence.sh
```

Runtime output / confirmed evidence:

```text
state=EVIDENCE_SUBMITTED
evidence=1
attachments=1
audit=1
webhook=1
CHB-03 merchant evidence submission pass dispute_id=645be81c-0e37-4b3c-8cd3-fdad2b4fb736 evidence_id=62d271e5-c773-48b3-a9c4-ffc1992727f7
```

The runtime verification proved:
- merchant evidence submission moves a merchant-owned `MERCHANT_NOTIFIED` dispute to `EVIDENCE_SUBMITTED`;
- evidence narrative and attachment metadata are persisted;
- `chargeback.evidence_submitted` audit row is written;
- `dispute.evidence_received` webhook outbox event is persisted;
- repeated submission is rejected;
- a different merchant cannot submit evidence for the dispute;
- expired merchant response deadline is rejected without an evidence row.

Targeted regression outputs:

```text
LDG-05 ledger reconciliation pass
CHB-01/CHB-02 chargeback initiation provisional credit pass dispute_id=4c785902-e175-4cc0-8f3b-e745846618e5 provisional_journal_id=6d01a701-f7af-45ea-8af3-4a34055f5ff8
```

Result tags:
- `CHB-03` — pass.
- `CHB-01`/`CHB-02` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Platform image was built from the locally verified `bootJar` output.

Not claimed:
- actual S3/MinIO evidence object upload/download;
- merchant accept / terminal `LOST`;
- arbitration (`CHB-04`, `CHB-05`);
- provisional credit reversal or merchant debit;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 09 Slice 04 Arbitration WON Reversal Runtime Verification

Scope:
- `CHB-04` backend/runtime implementation for backoffice arbitration `WON`.
- Retained runtime script `product/scripts/runtime/reg_phase09_arbitration_won.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_arbitration_won.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb04
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
KEYCLOAK_BASE_URL=http://keycloak:8080
KEYCLOAK_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash product/scripts/runtime/reg_phase09_arbitration_won.sh
```

Runtime output / confirmed evidence:

```text
CHB-04 arbitration WON pass dispute_id=e586d972-a5f9-4d95-9d1e-438229776f1f provisional_journal_id=6f636bdd-5f93-4f99-8dfc-0114973e0f86 reversal_journal_id=5ad1601f-12bb-4d68-8f80-140106fa3296 user_id=ccfa3039-149a-414f-a26f-8f5073389deb
```

Additional database confirmation:

```text
e586d972-a5f9-4d95-9d1e-438229776f1f|WON|WON|5ad1601f-12bb-4d68-8f80-140106fa3296
CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL|CHARGEBACK|e586d972-a5f9-4d95-9d1e-438229776f1f
ACQUIRER_DISPUTE_RESERVE|CREDIT|18.2500
WALLET_USER:ccfa3039-149a-414f-a26f-8f5073389deb|DEBIT|18.2500
audit=1
webhook=1
```

The runtime verification proved:
- a dispute can move `EVIDENCE_SUBMITTED -> WON` through the backoffice arbitration endpoint;
- one `CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL` journal is posted for the dispute;
- the reversal debits the cardholder wallet and credits `ACQUIRER_DISPUTE_RESERVE` for EUR 18.2500;
- the provisional credit and reversal net cardholder wallet impact back to zero;
- arbitration metadata and reversal journal id are persisted on `chargeback.disputes`;
- `chargeback.arbitration_won` audit row is written;
- `dispute.won` webhook outbox event is persisted;
- repeated `WON`, unsupported `LOST`, and deciding before evidence submission are rejected.

Targeted regression outputs:

```text
LDG foundation balanced posting and derived balances pass
CHB-03 merchant evidence submission pass dispute_id=5195d205-66e2-45a1-a56c-f28d308466ee evidence_id=3d58573a-6cc2-444b-a86e-5dd4433efdf2
```

Result tags:
- `CHB-04` — pass.
- `CHB-03` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Backend service images were built from locally verified `bootJar` outputs because Gradle-in-Docker hung in this environment.
- Keycloak runtime helper used `KEYCLOAK_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default` for admin/token calls.

Not claimed:
- arbitration `LOST` (`CHB-05`);
- merchant debit finalization;
- reserve release beyond provisional credit reversal;
- merchant deadline expiry;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 09 Slice 05 Arbitration LOST Merchant Debit Runtime Verification

Scope:
- `CHB-05` backend/runtime implementation for backoffice arbitration `LOST`.
- Retained runtime script `product/scripts/runtime/reg_phase09_arbitration_lost.sh` was added.
- Existing `reg_phase09_arbitration_won.sh` was updated because `LOST` is now a supported outcome.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_arbitration_lost.sh product/scripts/runtime/reg_phase09_arbitration_won.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb05
COMPOSE_FILE=deploy/docker-compose.yml
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
KEYCLOAK_BASE_URL=http://keycloak:8080
KEYCLOAK_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash scripts/runtime/reg_phase09_arbitration_lost.sh
```

Runtime output / confirmed evidence:

```text
CHB-05 arbitration LOST pass dispute_id=d0f8daf2-511a-4053-bd63-5c96862bf418 provisional_journal_id=45c855c2-b2a3-475c-83c7-4e16b73c1f0d merchant_debit_journal_id=50c5a103-ded6-4ed7-ac49-6f0efd6a6586 user_id=9a6e8555-b77e-406f-b142-2083fc3a5aff
```

The runtime verification proved:
- a dispute can move `EVIDENCE_SUBMITTED -> LOST` through the backoffice arbitration endpoint;
- one `CHARGEBACK_MERCHANT_DEBIT` journal is posted for the dispute;
- the merchant debit journal debits `MERCHANT_SETTLEMENT:<merchantId>` and credits `ACQUIRER_DISPUTE_RESERVE` for EUR 18.2500;
- no `CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL` journal is posted for `LOST`;
- the original cardholder provisional credit remains in place;
- arbitration metadata and merchant debit journal id are persisted on `chargeback.disputes`;
- `chargeback.arbitration_lost` audit row is written;
- `dispute.lost` webhook outbox event is persisted;
- repeated `LOST` decision is rejected.

Targeted regression outputs:

```text
CHB-04 arbitration WON pass dispute_id=8fe6c26c-3f94-4909-bf22-1ea1d112e827 provisional_journal_id=932737cb-87fa-4ba5-a091-40b442d845d6 reversal_journal_id=d3139e28-8bcf-46d8-9101-0ce2afb4d512 user_id=28f2658b-f75f-48c0-a0fa-b329e04c1c6d
CHB-03 merchant evidence submission pass dispute_id=1c64c9ee-9709-4522-8e0d-8fba1e517871 evidence_id=0e234a24-c1ae-47ca-832a-48586fffa278 user_id=b266071c-6acb-40cc-96fc-b58c73e893ee
LDG-05 ledger reconciliation pass
```

Result tags:
- `CHB-05` — pass.
- `CHB-04` — pass targeted regression.
- `CHB-03` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Backend service images were built from locally verified `bootJar` outputs because Gradle-in-Docker hung in this environment.
- Temporary compose stack `mfp-chb05` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- merchant accept / terminal `LOST` outside arbitration;
- merchant deadline expiry;
- actual evidence object upload/download;
- frontend `UI-*`;
- chargeback rate metrics.

---

## 2026-05-21 — Phase 09 Slice 06 Merchant Accepts Chargeback Runtime Verification

Scope:
- `CHB-06` backend/runtime implementation for merchant accepted chargeback.
- Retained runtime script `product/scripts/runtime/reg_phase09_merchant_accept.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_merchant_accept.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb06
COMPOSE_FILE=deploy/docker-compose.yml
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash scripts/runtime/reg_phase09_merchant_accept.sh
```

Runtime output / confirmed evidence:

```text
CHB-06 merchant accept pass dispute_id=0fae4be5-d262-431b-a2b6-0770608c906d provisional_journal_id=e63ea832-e4f5-4480-9d88-a78b7084bb84 merchant_debit_journal_id=88db304d-28b5-46db-9584-2b3a56c20833 user_id=b46d1433-87b8-4b4b-b34d-9adf001258c4
```

The runtime verification proved:
- owning `merchant_admin` can move a dispute `MERCHANT_NOTIFIED -> MERCHANT_ACCEPTED`;
- one `CHARGEBACK_MERCHANT_DEBIT` journal is posted for the dispute;
- the merchant debit journal debits `MERCHANT_SETTLEMENT:<merchantId>` and credits `ACQUIRER_DISPUTE_RESERVE` for EUR 18.2500;
- no `CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL` journal is posted for merchant acceptance;
- the original cardholder provisional credit remains in place;
- merchant acceptance metadata and merchant debit journal id are persisted on `chargeback.disputes`;
- `chargeback.merchant_accepted` audit row is written;
- `dispute.lost` webhook outbox event is persisted;
- repeated accept is rejected;
- evidence submission after merchant accept is rejected;
- another merchant cannot accept the dispute.

Targeted regression outputs:

```text
CHB-05 arbitration LOST pass dispute_id=e1e88e05-8fc1-4bfc-ba4f-d39b2b0dcb29 provisional_journal_id=356c44c6-bb4d-4a28-961c-5488531152b1 merchant_debit_journal_id=3406f535-fd4b-4063-9f9d-808a3bfeb21c user_id=25d1e1d5-aaf2-46c5-8006-dacb7c845921
CHB-04 arbitration WON pass dispute_id=96bbf155-2699-42b3-905e-26caa6f12e12 provisional_journal_id=08b7e5fb-c477-42a0-a216-a753a56c1a05 reversal_journal_id=1d71a2f3-fb97-444d-83df-a42c8ddde08a user_id=8e6dd854-0c53-43a6-8c59-d7db2c70c04b
LDG-05 ledger reconciliation pass
```

Result tags:
- `CHB-06` — pass.
- `CHB-05` — pass targeted regression.
- `CHB-04` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Backend service images were built from locally verified `bootJar` outputs because Gradle-in-Docker hung in this environment.
- Initial parallel targeted regression execution produced one transient script failure on the shared temporary stack; `CHB-04` and `LDG-05` passed when rerun sequentially.
- Temporary compose stack `mfp-chb06` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- merchant deadline expiry;
- actual evidence object upload/download;
- frontend `UI-*`;
- chargeback rate metrics.

---

## 2026-05-21 — Phase 09 Slice 07 Merchant Deadline Expiry Runtime Verification

Scope:
- `CHB-07` backend/runtime implementation for merchant response deadline expiry.
- Retained runtime script `product/scripts/runtime/reg_phase09_merchant_deadline_expiry.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_merchant_deadline_expiry.sh
git diff --check
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb07
COMPOSE_FILE=deploy/docker-compose.yml
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
bash scripts/runtime/reg_phase09_merchant_deadline_expiry.sh
```

Runtime output / confirmed evidence:

```text
CHB-07 merchant deadline expiry pass dispute_id=d862e703-5a9f-4429-a342-4766beb39c31 provisional_journal_id=0e9ed923-e72d-4452-a366-6eb08dc2b8ac merchant_debit_journal_id=cf97a9ff-bb8b-464b-8c75-fab146decbdb user_id=27802361-1a18-44e2-8629-321c6769ab14
```

The runtime verification proved:
- the internal processor moves due disputes `MERCHANT_NOTIFIED -> MERCHANT_DEADLINE_EXPIRED`;
- not-yet-due disputes remain `MERCHANT_NOTIFIED`;
- one `CHARGEBACK_MERCHANT_DEBIT` journal is posted for the expired dispute;
- the merchant debit journal debits `MERCHANT_SETTLEMENT:<merchantId>` and credits `ACQUIRER_DISPUTE_RESERVE` for EUR 18.2500;
- no `CARDHOLDER_PROVISIONAL_CREDIT_REVERSAL` journal is posted for deadline expiry;
- the original cardholder provisional credit remains in place;
- deadline-expiry metadata and merchant debit journal id are persisted on `chargeback.disputes`;
- `chargeback.deadline_expired` audit row is written;
- `dispute.lost` webhook outbox event is persisted;
- rerunning the processor does not create duplicate journal/webhook rows;
- evidence submission and merchant accept after deadline expiry are rejected.

Targeted regression outputs:

```text
CHB-06 merchant accept pass dispute_id=9e2b4199-77bc-4ea1-9de4-57612cd3c886 provisional_journal_id=819a709c-2c58-4697-a285-f82f7c5e995c merchant_debit_journal_id=ca4a692e-fc13-4d9c-8228-894b73786f2c user_id=4677fbb6-ead5-490c-859a-cfb88569f677
CHB-05 arbitration LOST pass dispute_id=d7e3ec3f-461e-4acb-9899-7cd4aea87918 provisional_journal_id=c39bae3e-0873-4d2f-9ea2-a13ef18ccbfc merchant_debit_journal_id=741d5fbe-01f4-4ec2-8763-2c5e37fbab6f user_id=2fea5aa3-e9cf-4e02-9d0c-ea2e1bd0291e
LDG-05 ledger reconciliation pass
```

Result tags:
- `CHB-07` — pass.
- `CHB-06` — pass targeted regression.
- `CHB-05` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Backend service images were built from locally verified `bootJar` outputs because Gradle-in-Docker hung in this environment.
- Temporary compose stack `mfp-chb07` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- scheduler/cron wiring;
- actual evidence object upload/download;
- frontend `UI-*`;
- chargeback rate metrics.

---

## 2026-05-21 — Phase 09 Slice 08 Evidence Object Storage Runtime Verification

Scope:
- `CHB-08` backend/runtime implementation for writing chargeback evidence attachment bytes to local SeaweedFS S3-compatible object storage before accepting evidence metadata.
- Retained runtime script `product/scripts/runtime/reg_phase09_evidence_object_storage.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase09_evidence_object_storage.sh product/scripts/runtime/reg_phase09_merchant_evidence.sh product/scripts/runtime/reg_phase09_arbitration_won.sh product/scripts/runtime/reg_phase09_arbitration_lost.sh
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-chb08
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
scripts/runtime/reg_phase09_evidence_object_storage.sh
```

Runtime output / confirmed evidence:

```text
CHB-08 evidence object storage pass dispute_id=fba170d4-bb0c-412a-aed0-cce6d7c3dac9 evidence_id=49a624b0-8c62-43f0-818a-0b11f5c6e22a storage_key=chargeback-evidence/runtime/chb08-1779391002680325002-delivery-proof.txt user_id=181d6b69-45b6-4144-a790-aaaf3502e7aa
```

The runtime verification proved:
- merchant evidence submission accepts one inline base64 attachment;
- invalid base64 and decoded-size mismatch are rejected before evidence metadata is persisted;
- object bytes are written to local SeaweedFS bucket `chargeback-evidence` before evidence metadata is accepted;
- the script reads the stored object back and verifies the payload bytes;
- evidence submission metadata and attachment metadata are persisted.

Targeted regression outputs:

```text
CHB-03 merchant evidence submission pass dispute_id=d7de47e9-661b-4e0d-8f46-37653eae4b11 evidence_id=a52229de-8d9d-4913-aa69-b55310d5ccc5 user_id=2ee4a2ec-246f-4700-84a6-28baae081b70
CHB-04 arbitration WON pass dispute_id=873a6375-90d0-4101-96ec-83c29150180b provisional_journal_id=a90f8687-22cc-44c5-a9b9-58f50bcd4073 reversal_journal_id=2e0b874a-cf01-4ee4-a633-409cb5488078 user_id=1012e6b4-b7bc-463e-8a65-08ea68506102
CHB-05 arbitration LOST pass dispute_id=cfb2b693-2ee3-488d-86db-47a4270d36ba provisional_journal_id=e9ab76ae-3f87-4259-8beb-a3c59ed7b2f1 merchant_debit_journal_id=dbbd2b9f-0f89-4af9-8989-732841de173c user_id=b45e4bab-207d-4e44-acb4-83ec3ecb675b
CHB-07 merchant deadline expiry pass dispute_id=0e290d4d-57ef-46ea-8a2b-e5fa7250d465 provisional_journal_id=847efe64-d752-46e2-bb5e-498f0f363a8a merchant_debit_journal_id=91cdc8e2-65fc-40c7-aa66-83b07c5dc55e user_id=557a55df-b0c3-417f-a8cc-441f09c6f953
LDG-05 ledger reconciliation pass
```

Result tags:
- `CHB-08` — pass.
- `CHB-03` — pass targeted regression.
- `CHB-04` — pass targeted regression.
- `CHB-05` — pass targeted regression.
- `CHB-07` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Backend service images were built from locally verified `bootJar` outputs.
- Temporary compose stack `mfp-chb08` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- browser multipart upload flow;
- presigned URL generation;
- backoffice evidence preview/download UI;
- KYC/SoF document storage;
- frontend `UI-*`;
- chargeback rate metrics.

---

## 2026-05-21 — Phase 06 Slice 07 Refund Bounds Runtime Verification

Scope:
- `SET-04` backend/runtime implementation for settled-payment partial refunds with aggregate refund bounds.
- Retained runtime script `product/scripts/runtime/reg_phase06_refund_bounds.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase06_refund_bounds.sh
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-set04
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
scripts/runtime/reg_phase06_refund_bounds.sh
```

Runtime output / confirmed evidence:

```text
SET-04 refund bounds pass intent_id=155f72e9-9da6-4247-805f-6f7441791665 refund_one_id=ace2a936-bc22-4a2f-98a6-9343325bdb64 refund_two_id=fd6f5c81-33f2-40c3-a2f7-1082cba91df5 user_id=0eb87234-0c4e-4c21-9add-203c25d90b29
```

The runtime verification proved:
- a settled payment accepts a first partial refund and moves to `PARTIALLY_REFUNDED`;
- idempotent replay of the first refund returns the same refund id;
- an over-refund attempt before full refund is rejected with `refund_amount_exceeds_payment`;
- a second partial refund can bring total successful refunds exactly to the captured amount and moves payment to `REFUNDED`;
- additional refund after full refund is rejected;
- refund rows are persisted under `merchant.refunds`;
- each accepted refund posts balanced `CARD_PAYMENT_REFUND` ledger journal;
- refund journal debits `MERCHANT_SETTLEMENT:<merchantId>` and credits `WALLET_USER:<userId>`;
- `payment.refund_succeeded` audit row is written;
- `payment_intent.refunded` webhook outbox event is persisted.

Targeted regression outputs:

```text
SET-01 capture to settlement foundation pass intent_id=a244a958-cb15-4e3b-bfda-99be9e510dfd batch_id=3253369e-a9d5-409a-96b0-d5a3e87dd1b9
PAY-06 payment capture foundation pass intent_id=7d0d0a4d-000a-46d9-8422-6fe60e0492cf
LDG-05 ledger reconciliation pass
```

Result tags:
- `SET-04` — pass.
- `SET-01` — pass targeted regression.
- `PAY-06` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Docker Compose bridge network creation/host port publishing was blocked by the local Docker iptables chain issue, so runtime verification reused existing Docker bridge network `mini-fintech-platform-a2_default`, reset service host ports with compose `!reset []`, and used compose-network URLs.
- Backend service images were built from locally verified `bootJar` outputs.
- Temporary compose stack `mfp-set04` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- merchant dashboard refund route or UI;
- refund-before-settlement netting;
- issuer/network refund rails;
- Acquirer projection refund adjustments;
- payouts or payout adjustments;
- bank files;
- scheduled jobs;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 07 Slice 05 Compliance Read Audit Runtime Verification

Scope:
- `AUD-03` backend/runtime implementation for compliance-sensitive sanctions hit detail reads.
- Retained runtime script `product/scripts/runtime/reg_phase07_compliance_read_audit.sh` was added.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase07_compliance_read_audit.sh
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime command:

```text
COMPOSE_PROJECT_NAME=mfp-aud03
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
KEYCLOAK_BASE_URL=http://keycloak:8080
KEYCLOAK_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
product/scripts/runtime/reg_phase07_compliance_read_audit.sh
```

Runtime output / confirmed evidence:

```text
AUD-03 compliance sanctions detail read audit pass hit_id=ad85a2cb-3c0d-404d-9543-9920dc7de552 end_user_id=3c75c6e4-4903-406e-b34b-4acb8344f8da
```

The runtime verification proved:
- sanctions hit list read as compliance does not create a detail read-audit row for the hit;
- sanctions hit detail read as compliance returns the hit and creates exactly one `audit.read_audit_log` ALLOW row;
- the row is for `subject_type/resource_type = SANCTIONS_HIT`, the same hit id, and `purpose = compliance_sanctions_hit_detail`;
- the row records a backoffice actor and compliance role metadata;
- sanctions hit detail read as `backoffice_operator` returns `403 forbidden_role` and does not create another ALLOW row.

Targeted regression outputs:

```text
SNX-01 OpenSanctions fail-closed pass
SNX-02 sanctions false-positive exception pass hit_id=e57fb69b-5d73-42e0-8c6c-2dcc19a8ea25 end_user_id=3987baf1-6b26-4c42-9b03-c3d017eae51f
KYC-03 backoffice manual KYC review pass profile_id=5c03e318-9673-430e-a40e-af7435874c81 applicant_id=sumsub-applicant-manual-review-1779392810596114989
```

Result tags:
- `AUD-03` — pass.
- `SNX-01` — pass targeted regression.
- `SNX-02` — pass targeted regression.
- `KYC-03` — pass targeted regression.

Runtime environment notes:
- Docker Compose network/host-port conflicts were avoided by using temporary compose project `mfp-aud03`, resetting host ports with compose `!reset []`, and reusing existing Docker bridge network `mini-fintech-platform-a2_default`.
- Dockerfile network dependency resolution failed inside Gradle build containers, so the platform runtime image was built from locally verified `bootJar` output; unchanged issuer/vault images were retagged from local verified images.
- `KYC-03` targeted regression was run with `OPENSANCTIONS_LOCAL_MODE=no_match`; `disabled` mode attempts external OpenSanctions access and fail-closes without sandbox credentials.
- Temporary compose stack `mfp-aud03` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- AML alert backoffice APIs;
- frozen-account viewer;
- audit-log viewer;
- full PAN reveal;
- KYC document preview;
- frontend `UI-*`.

---

## 2026-05-21 — Phase 11 Slice 01 Reset and Seed Runtime Verification

Scope:
- `REC-01` backend/runtime implementation for retained local reset and deterministic demo seed scripts.
- Retained scripts:
  - `product/scripts/runtime/reg_phase11_reset_dev.sh`
  - `product/scripts/runtime/reg_phase11_seed_dev.sh`

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase11_reset_dev.sh
bash -n product/scripts/runtime/reg_phase11_seed_dev.sh
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
```

Observed result:

```text
BUILD SUCCESSFUL
```

Runtime commands:

```text
COMPOSE_PROJECT_NAME=mfp-rec01
COMPOSE_FILE=product/deploy/docker-compose.yml
COMPOSE_OVERRIDE_FILE=/tmp/mfp-rec01-compose.override.yml
MINIFIN_RESET_CONFIRM=reset-dev
product/scripts/runtime/reg_phase11_reset_dev.sh

COMPOSE_PROJECT_NAME=mfp-rec01
COMPOSE_FILE=product/deploy/docker-compose.yml
COMPOSE_OVERRIDE_FILE=/tmp/mfp-rec01-compose.override.yml
product/scripts/runtime/reg_phase11_seed_dev.sh

COMPOSE_PROJECT_NAME=mfp-rec01
COMPOSE_FILE=product/deploy/docker-compose.yml
COMPOSE_OVERRIDE_FILE=/tmp/mfp-rec01-compose.override.yml
product/scripts/runtime/reg_phase11_seed_dev.sh

COMPOSE_PROJECT_NAME=mfp-rec01
COMPOSE_FILE=product/deploy/docker-compose.yml
COMPOSE_OVERRIDE_FILE=/tmp/mfp-rec01-compose.override.yml
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
product/scripts/runtime/reg_phase03_ledger_reconciliation.sh
```

Runtime output / confirmed evidence:

```text
REC-01 reset dev stack started project=mfp-rec01
REC-01 seed dev data pass approved_user=10000000-0000-0000-0000-000000000001 merchant=20000000-0000-0000-0000-000000000001 payment_intent=51000000-0000-0000-0000-000000000001
REC-01 seed dev data pass approved_user=10000000-0000-0000-0000-000000000001 merchant=20000000-0000-0000-0000-000000000001 payment_intent=51000000-0000-0000-0000-000000000001
LDG-05 ledger reconciliation pass
```

Additional guard check:

```text
product/scripts/runtime/reg_phase11_reset_dev.sh
```

without `MINIFIN_RESET_CONFIRM=reset-dev` refused to run:

```text
Refusing destructive reset: set MINIFIN_RESET_CONFIRM=reset-dev
```

The runtime verification proved:
- destructive reset is guarded by an explicit confirmation environment variable;
- reset removes volumes and starts backend/runtime services for a clean compose project;
- seed creates three deterministic demo end users with approved/in-review/rejected KYC profiles;
- seed creates two deterministic demo merchants with verified and pending KYB states;
- seed creates an approved-user wallet, balanced demo funding journal, Platform/Issuer/Vault card records and a sample payment intent;
- seed can run twice without duplicate-key failure;
- ledger reconciliation passes after seeded data.

Result tags:
- `REC-01` — pass.
- `LDG-05` — pass targeted regression.

Runtime environment notes:
- Runtime verification used temporary compose project `mfp-rec01`, reset service host ports with compose `!reset []`, and reused existing Docker bridge network `mini-fintech-platform-a2_default`.
- `mfp-rec01-*` images were local tags of previously verified backend images; this slice changed scripts/docs only and did not require application image rebuild.
- Temporary compose stack `mfp-rec01` was stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- `REC-02` full demo path;
- `REC-03` vendor reconciliation;
- `REC-04` dashboards;
- `REC-05` final cut-register audit;
- frontend `UI-*`;
- real Stripe/Sumsub vendor calls.

---

## 2026-05-21 — Phase 08 Slice 07 AML Alert Review Decisions Runtime Verification

Scope:
- Phase 08 Slice 07 AML alert review decisions backend/runtime implementation.
- Target `AML-05`.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase08_aml_review_decisions.sh
product/gradlew --no-daemon -p product :apps:platform:compileKotlin
```

Observed result:

```text
syntax ok
BUILD SUCCESSFUL in 9s
```

Runtime commands:

```text
product/gradlew --no-daemon -p product :apps:platform:bootJar
DOCKER_BUILDKIT=0 docker build -f /tmp/Dockerfile.mfp-aml05-platform -t mfp-aml05-platform:latest /tmp/mfp-aml05-build/

COMPOSE_PROJECT_NAME=mfp-aml05
COMPOSE_FILE=product/deploy/docker-compose.yml
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
KEYCLOAK_BASE_URL=http://keycloak:8080
KEYCLOAK_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
product/scripts/runtime/reg_phase08_aml_review_decisions.sh
```

Runtime output / confirmed evidence:

```text
AML-05 aml alert review decisions pass run_tag=1779395328502912502-3274802
```

Flyway migration confirmed:

```text
select version from flyway_schema_history order by installed_rank desc limit 3;
→ 34, 33, 32
```

The runtime verification proved:
- `GET /api/v1/backoffice/aml-alerts` lists reviewable alerts in queue for operator token;
- `GET /api/v1/backoffice/aml-alerts/{id}` returns alert detail with `endUserId` and `OPEN` status;
- Short rationale (< 20 chars) rejected with `400 invalid_rationale`;
- `CLOSED_FALSE_POSITIVE` decision by operator succeeds: alert status `CLOSED_FALSE_POSITIVE`, `unfrozeActor=false`, `aml_alert_decisions` row, `aml.alert_reviewed` audit row;
- Repeat decision on already-closed alert rejected with `409 invalid_state`;
- `CLOSED_FALSE_POSITIVE` on `ACCOUNT_FROZEN_PERMANENT` alert with actor `FROZEN` (reason `aml_critical_alert`): `unfrozeActor=true`, `actor_controls.state` returns to `ACTIVE`, `identity.actor_control_changed` audit row written;
- `ESCALATED` decision by operator succeeds: alert status `ESCALATED`;
- `MARKED_FOR_SAR` by operator rejected with `403 forbidden_role`, alert stays `OPEN`;
- `MARKED_FOR_SAR` by compliance officer succeeds: alert status `MARKED_FOR_SAR`, `aml.alert_reviewed` audit row;
- Flyway migration `V34__aml_alert_review_decisions.sql` applied cleanly (latest version = 34).

Result tags:
- `AML-05` — pass.

Runtime environment notes:
- Isolated compose project `mfp-aml05` with external network `mini-fintech-platform-a2_default`; service host ports reset via compose override `!reset []`.
- Platform image built from locally verified `bootJar` output (lean Dockerfile, no in-container Gradle build).
- Other backend service images reused from `mini-fintech-platform-a1-*` and `mini-fintech-platform-a2-*` slot images.
- Temporary compose stack `mfp-aml05` stopped with `down -v --remove-orphans` after runtime checks.

Not claimed:
- SAR filing workflow;
- SoF backoffice review queue;
- frontend `UI-*`.

---

## 2026-05-22 — Phase 11 Slice 02 Full Demo Path Runtime Verification

Scope:
- Phase 11 Slice 02 end-to-end demo path using seeded demo data.
- Target `REC-02`.

Static commands run:

```text
bash -n product/scripts/runtime/reg_phase11_demo_path.sh
```

Runtime commands:

```text
product/gradlew --no-daemon -p product :apps:platform:bootJar
DOCKER_BUILDKIT=0 docker build -f /tmp/Dockerfile.mfp-rec02-platform -t mfp-aml05-platform:latest /tmp/mfp-rec02-build/

COMPOSE_PROJECT_NAME=mfp-rec02
COMPOSE_FILE=product/deploy/docker-compose.yml:/tmp/mfp-rec02-compose.override.yml
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
KEYCLOAK_BASE_URL=http://keycloak:8080
KEYCLOAK_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default
product/scripts/runtime/reg_phase11_demo_path.sh
```

Runtime output / confirmed evidence:

```text
REC-02 full demo path pass run_tag=1779398176588864745-3372160 intent_id=cf71d6cb-ed11-49e1-ab4e-486d16a3aa4c initial_balance=75.0000 final_balance=55.0000
```

The runtime verification proved:
- Seed state pre-check: wallet balance 75.0000 ≥ 30, card `tok_demo_approved_card` in `ACTIVE` state;
- Fresh merchant + live API key registered successfully;
- Payment intent `POST /v1/payment_intents` EUR 30.00 → HTTP 201, state `REQUIRES_PAYMENT_METHOD`;
- Authorization with seeded card → HTTP 200, state `AUTHORIZED`, `CARD_AUTHORIZATION_HOLD` journal created, Platform DB state `AUTHORIZED`;
- Capture `POST /v1/payment_intents/{id}/capture` (with `Idempotency-Key`) → HTTP 200, state `CAPTURED`;
- Settlement `POST /internal/settlement/process-captured?limit=10` → HTTP 200, intent state `SETTLED`, `settlement_items` row confirmed in DB;
- Acquirer projection `POST /internal/settlement/publish-projections?limit=200` → HTTP 200, exactly one `merchant_settlement.balance_projection` row for the `settlement_item_id`;
- Partial refund `POST /v1/payment_intents/{id}/refund` EUR 10.00 → HTTP 200, refund `state=SUCCEEDED`, `ledgerJournalId` present, intent DB state `PARTIALLY_REFUNDED`;
- Wallet balance delta = −20.00 (initial 75.0000 → final 55.0000, tolerance 0.001);
- `LDG-05` reconciliation: `balancedJournals=true`, `journalCount ≥ 1`, zero imbalanced journals via DB cross-check.

Result tags:
- `REC-02` — pass.
- `LDG-05` — pass (targeted regression).

Runtime environment notes:
- Isolated compose project `mfp-rec02` with external network `mini-fintech-platform-a2_default`; service host ports reset via compose override `!reset []`.
- Platform image: `mfp-aml05-platform:latest` (built from current `bootJar`).
- Acquirer image: `mfp-rec02-acquirer:latest` (fresh bootJar build required — old `mini-fintech-platform-a1-acquirer:latest` predated `merchant_settlement` schema from Phase 06 Slice 06).
- Issuer image: `mfp-rec02-issuer:latest` (fresh bootJar build required — old `mini-fintech-platform-a2-issuer:latest` predated `issuer.holds` table from Phase 05 Slice 02).
- Other service images reused from `mini-fintech-platform-a1-*` and `mini-fintech-platform-a2-*` slot images.
- Temporary compose stack `mfp-rec02` stopped with `down -v --remove-orphans` after runtime checks.

Runtime-discovered fixes applied to script before final pass:
- HTTP 201 (not 200) for `POST /v1/payment_intents` — payment intent creation is `201 Created`.
- Capture endpoint requires `Idempotency-Key` header — added `-H "Idempotency-Key: cap-rec02-${run_tag}"`.
- Old issuer image missing `issuer.holds` table — built fresh `mfp-rec02-issuer:latest`.
- Old acquirer image missing `merchant_settlement` schema — built fresh `mfp-rec02-acquirer:latest`.
- Refund response shape: endpoint returns refund object (state `SUCCEEDED`, `ledgerJournalId`) not the payment intent object — fixed node check accordingly.

Not claimed:
- `REC-03` vendor reconciliation;
- `REC-04` dashboards;
- `REC-05` final cut-register audit;
- frontend `UI-*`.
