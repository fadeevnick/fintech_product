# Phase 01 Slice 01 — Product Skeleton — Planning Note

Status: **APPROVED v0.1** (approved 2026-05-15).

This document fixes the first coding slice inside `Phase 01 — Product Skeleton and Local Runtime`.

It is a pre-code scope contract. It is not implementation status and not runtime evidence.

---

## 1. Decision

`Phase 01 slice 01`:

```text
Create the product skeleton with Gradle backend workspace, pnpm frontend workspace, empty Spring Boot service shells, empty Vite SPA shells, Docker Compose core infrastructure, health endpoints, initial migrations, and retained Phase 01 smoke scripts.
```

This means:

- `product/` appears for the first time.
- All five approved application service boundaries exist from the start.
- All three approved SPA deployment shells exist from the start.
- The slice proves runtime wiring only: health, migrations, Kafka, Keycloak, SeaweedFS, logs, metrics, traces, SPA serving.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` is APPROVED v0.2 and names this as first coding slice.
- `planning/design-details/` is APPROVED v0.2.
- `planning/runtime_checklists.md` is APPROVED v0.1 and contains `RUN-01`..`RUN-08`, `UI-01`.
- Every later slice depends on repo layout, build tooling, Docker Compose service names, health endpoints and baseline observability.

If implementation starts with a domain feature instead, the project will create implicit structure before proving the approved 5-service topology.

## 3. Exact Scope

In this slice:

1. Create `product/` root with backend Gradle multi-project skeleton.
2. Create five Spring Boot application modules:
   - `platform`
   - `acquirer`
   - `network`
   - `issuer`
   - `vault`
3. Create shared backend lib placeholders:
   - contracts public/internal/events
   - db
   - observability
   - service-auth
4. Add minimal health/readiness endpoints for each backend service.
5. Add initial Flyway migration wiring and one baseline migration per service DB.
6. Create frontend pnpm workspace with three Vite/React SPA shells:
   - `spa-enduser`
   - `spa-merchant`
   - `spa-backoffice`
7. Create Docker Compose core infra:
   - five app services
   - Postgres per app service
   - Keycloak + Keycloak Postgres
   - Kafka KRaft
   - SeaweedFS S3
   - Traefik
   - nginx static containers for SPA shells
   - Prometheus, Grafana, Tempo, Loki, OTel Collector, Vector
8. Add `.env.example` with required local variables and no secrets.
9. Add `product/README.md` with local setup/run commands.
10. Add retained runtime smoke scripts for Phase 01 checks.

## 4. Explicitly In Scope

### Code Changes

- Build files and module skeletons.
- Minimal backend health controllers.
- Minimal app configuration per service.
- Minimal Flyway baseline migrations.
- Minimal React/Vite shells that identify the app surface.
- Docker Compose and infra config needed to start local runtime.
- Runtime smoke scripts under `product/scripts/runtime/reg_phase01_*`.

### Behavioral Outcomes

- Backend services can start with configured ports and DB connections.
- Each service can run migrations against its own DB.
- Kafka, Keycloak and SeaweedFS are reachable by smoke checks.
- Logs/metrics/traces have first observable path.
- Three SPA shells build and serve through static containers.

### Verification Outcomes

This slice exercises:

- `RUN-01`
- `RUN-02`
- `RUN-03`
- `RUN-04`
- `RUN-05`
- `RUN-06`
- `RUN-07`
- `RUN-08`
- `UI-01`

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- user registration/login/session behavior;
- Keycloak realm import beyond minimum reachability if needed for health smoke;
- RBAC;
- ledger schema beyond placeholder/baseline migration;
- wallet, card, payment, merchant, KYC, sanctions, AML, chargeback domains;
- vendor sandbox integrations;
- real UI workflows;
- webhook delivery;
- transactional outbox behavior;
- production secrets management;
- CI/CD.

Do not add fake versions of these. If a domain endpoint is not in scope, it must not exist as a hardcoded success path.

## 6. Concrete Files To Touch

### 6.1 Modify

- `README.md`
- `CURRENT.md`

### 6.2 Create

- `product/README.md`
- `product/settings.gradle.kts`
- `product/build.gradle.kts`
- `product/gradle/`
- `product/.env.example`
- `product/package.json`
- `product/pnpm-workspace.yaml`
- `product/apps/platform/**`
- `product/apps/acquirer/**`
- `product/apps/network/**`
- `product/apps/issuer/**`
- `product/apps/vault/**`
- `product/apps/spa-enduser/**`
- `product/apps/spa-merchant/**`
- `product/apps/spa-backoffice/**`
- `product/backend/libs/contracts-public/**`
- `product/backend/libs/contracts-internal/**`
- `product/backend/libs/contracts-events/**`
- `product/backend/libs/db/**`
- `product/backend/libs/observability/**`
- `product/backend/libs/service-auth/**`
- `product/frontend/packages/ui/**`
- `product/frontend/packages/api-client/**`
- `product/deploy/docker-compose.yml`
- `product/deploy/docker-compose.observability.yml` if split is cleaner than one file
- `product/deploy/traefik/**`
- `product/deploy/prometheus/**`
- `product/deploy/grafana/**`
- `product/deploy/otel/**`
- `product/deploy/vector/**`
- `product/scripts/runtime/reg_phase01_runtime_health.*`
- `product/scripts/runtime/reg_phase01_migrations.*`
- `product/scripts/runtime/reg_phase01_kafka.*`
- `product/scripts/runtime/reg_phase01_keycloak.*`
- `product/scripts/runtime/reg_phase01_object_storage.*`
- `product/scripts/runtime/reg_phase01_logs.*`
- `product/scripts/runtime/reg_phase01_metrics.*`
- `product/scripts/runtime/reg_phase01_traces.*`
- `product/scripts/runtime/reg_phase01_spa_shells.*`

### 6.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline, no edits during coding slice.
- `planning/design-details/**` — approved implementation-near inputs, no edits unless implementation discovers a real mismatch and user approves design update.
- `planning/runtime_checklists.md` — approved check IDs; do not churn IDs during implementation.

## 7. Recommended Change Order

1. Create `product/` root and backend Gradle multi-project.
2. Add five Spring Boot service shells with health endpoints.
3. Add DB/Flyway baseline per service.
4. Add frontend pnpm workspace and three Vite shell apps.
5. Add Docker Compose with DBs and core app services.
6. Add Kafka, Keycloak, SeaweedFS.
7. Add Traefik and SPA static containers.
8. Add observability stack.
9. Add runtime smoke scripts.
10. Start local stack and run checks.
11. Record evidence in `planning/runtime_evidence_log.md`.
12. Create/update `planning/implementation_status.md`.

## 8. Linked Runtime Checks

This slice exercises:

- `RUN-01` — all five backend service health endpoints.
- `RUN-02` — service database migrations.
- `RUN-03` — Kafka worker connectivity.
- `RUN-04` — Keycloak connectivity.
- `RUN-05` — SeaweedFS S3 connectivity.
- `RUN-06` — logs pipeline.
- `RUN-07` — metrics pipeline.
- `RUN-08` — traces pipeline.
- `UI-01` — three SPA shells build and serve through nginx/Traefik.

## 9. Linked ADRs

- ADR-001 — JVM/Spring/Kotlin backend baseline.
- ADR-002 — SQL-first persistence.
- ADR-003 — Selective services topology.
- ADR-005 — Local object storage via SeaweedFS S3 API.

## 10. Verification Shape

After implementation, run:

- Gradle build/test for backend skeleton.
- pnpm install/build for frontend shells.
- Docker Compose startup for core local stack.
- Runtime scripts:
  - `product/scripts/runtime/reg_phase01_runtime_health.*`
  - `product/scripts/runtime/reg_phase01_migrations.*`
  - `product/scripts/runtime/reg_phase01_kafka.*`
  - `product/scripts/runtime/reg_phase01_keycloak.*`
  - `product/scripts/runtime/reg_phase01_object_storage.*`
  - `product/scripts/runtime/reg_phase01_logs.*`
  - `product/scripts/runtime/reg_phase01_metrics.*`
  - `product/scripts/runtime/reg_phase01_traces.*`
  - `product/scripts/runtime/reg_phase01_spa_shells.*`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

If Tempo tracing is wired but cannot be directly queried yet, `RUN-08` may be recorded as `pass-indirect` only with an explicit follow-up gap.

## 11. Pass Criteria

Slice considered implemented when:

- files in §6.2 exist;
- linked check IDs have evidence entries with `pass` or honest `pass-indirect` where allowed;
- `planning/runtime_evidence_log.md` exists and records the Phase 01 Slice 01 evidence;
- `planning/implementation_status.md` exists and states factual implementation/runtime state;
- `CURRENT.md` points to the next exact slice or follow-up check.
