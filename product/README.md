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
- Phase 02 Slice 04 read-audit/account controls backend/runtime sub-scope in `platform`: append-only read-audit log, end-user/merchant actor controls and write-guard probes.
- Phase 03 Slice 01 ledger foundation backend/runtime sub-scope in `platform`: ledger accounts, journal entries, postings, balanced journal SQL function, append-only ledger protection, derived balances and reconciliation proof.
- Phase 03 Slice 02 wallet account + manual deposit backend/runtime sub-scope in `platform`: `wallet.wallet_accounts`, `wallet.deposit_requests` (under EUR 10k), end-user `POST /api/v1/deposits` and `GET /api/v1/wallet`, backoffice `GET/POST /api/v1/backoffice/manual-ops/deposits[/{id}/decision]`, balanced approve through `ledger.post_journal(...)`, actor-control wallet-write block (`FROZEN` ∪ `BLOCKED`).
- Phase 03 Slice 03 wallet manual withdraw backend/runtime sub-scope in `platform`: `wallet.withdraw_requests` (under EUR 10k), end-user `POST /api/v1/withdrawals`, backoffice `GET/POST /api/v1/backoffice/manual-ops/withdrawals[/{id}/decision]`, ledger hold/final-debit/release postings, insufficient-funds guard and actor-control wallet-write block.
- Phase 03 Slice 04 wallet internal transfer backend/runtime sub-scope in `platform`: `wallet.internal_transfers` (under EUR 10k), end-user `POST /api/v1/transfers`, sender debit / receiver credit through `ledger.post_journal(...)`, sufficient-funds guard, `Idempotency-Key` duplicate protection and actor-control wallet-write block for sender and receiver.

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
scripts/runtime/reg_phase02_read_audit_probe.sh
scripts/runtime/reg_phase02_actor_control_enduser.sh
scripts/runtime/reg_phase02_actor_control_merchant.sh
scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh
scripts/runtime/reg_phase03_ledger_balanced_posting.sh
scripts/runtime/reg_phase03_ledger_append_only.sh
scripts/runtime/reg_phase03_ledger_reconciliation.sh
scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh
scripts/runtime/reg_phase03_wallet_deposit_reject.sh
scripts/runtime/reg_phase03_wallet_deposit_actor_control_block.sh
scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh
scripts/runtime/reg_phase03_wallet_deposit_amount_validation.sh
scripts/runtime/reg_phase03_wallet_provisioning_idempotency.sh
scripts/runtime/reg_phase03_wallet_withdraw_hold_complete.sh
scripts/runtime/reg_phase03_wallet_withdraw_reject_releases_hold.sh
scripts/runtime/reg_phase03_wallet_withdraw_insufficient_funds.sh
scripts/runtime/reg_phase03_wallet_withdraw_actor_control_block.sh
scripts/runtime/reg_phase03_wallet_withdraw_double_decision.sh
scripts/runtime/reg_phase03_wallet_transfer_happy_path.sh
scripts/runtime/reg_phase03_wallet_transfer_insufficient_funds.sh
scripts/runtime/reg_phase03_wallet_transfer_actor_control_block.sh
scripts/runtime/reg_phase03_wallet_transfer_idempotency.sh
```
