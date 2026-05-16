# Runtime Evidence Log

Last updated: 2026-05-16.

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
- Separately inspect and accept/commit `prototypes/ui/03_enduser_auth.html`, then draft the next non-frontend Phase 02 slice while frontend implementation remains gated by accepted standalone HTML prototypes.
