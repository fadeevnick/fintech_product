# Mini Fintech Platform Product

This directory contains the product implementation.

## Runtime Shape

- Backend: five Kotlin/Spring Boot services: `platform`, `acquirer`, `network`, `issuer`, `vault`.
- Frontend: three React/Vite SPA shells: end-user, merchant, backoffice.
- Infra: Postgres per service, Kafka KRaft, Keycloak, SeaweedFS S3 API, Traefik, Prometheus, Grafana, Tempo, Loki, OTel Collector, Vector.

Implemented runtime behavior so far:

- Phase 01 runtime skeleton for five backend services, three SPA shells and local infra.
- Phase 02 Slice 01 end-user register/email verify/login/logout/me in `platform`.
- Phase 02 Slice 02 merchant identity backend/runtime sub-scope in `platform`: merchant register/email verify/login/logout/me, first employee as `merchant_admin`, cross-pool email uniqueness and end-user/merchant wrong-role denial.
- Phase 02 Slice 03 backoffice OIDC/RBAC backend/runtime sub-scope in `platform`: Keycloak bearer token validation, approved backoffice role mapping, `GET /api/v1/backoffice/me` and all-pool protected endpoint denial.

## Local Commands

From `product/`:

```bash
docker compose -f deploy/docker-compose.yml up --build
```

Backend build, if Gradle is available:

```bash
gradle build
```

Frontend build, if pnpm is available:

```bash
pnpm install
pnpm build
```

Runtime checks:

```bash
scripts/runtime/reg_phase01_runtime_health.sh
scripts/runtime/reg_phase01_migrations.sh
scripts/runtime/reg_phase01_kafka.sh
scripts/runtime/reg_phase01_keycloak.sh
scripts/runtime/reg_phase01_object_storage.sh
scripts/runtime/reg_phase01_logs.sh
scripts/runtime/reg_phase01_metrics.sh
scripts/runtime/reg_phase01_traces.sh
scripts/runtime/reg_phase01_spa_shells.sh
scripts/runtime/reg_phase02_enduser_auth.sh
scripts/runtime/reg_phase02_merchant_auth.sh
scripts/runtime/reg_phase02_cross_pool_email_uniqueness.sh
scripts/runtime/reg_phase02_cross_role_denial.sh
scripts/runtime/reg_phase02_backoffice_oidc.sh
scripts/runtime/reg_phase02_backoffice_role_denial.sh
scripts/runtime/reg_phase02_auth_audit.sh
scripts/runtime/reg_phase02_merchant_auth_audit.sh
scripts/runtime/reg_phase02_backoffice_auth_audit.sh
```
