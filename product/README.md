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
- Phase 04 Slice 01 merchant API keys and public API idempotency backend/runtime sub-scope in `platform`: `merchant.api_keys` (one-time-visible key, SHA-256 `key_hash`, 16-hex `fingerprint`), `merchant.payment_intents` shell (`REQUIRES_PAYMENT_METHOD` only), `idempotency.idempotency_keys` append-only on update; merchant dashboard `POST/GET /api/v1/merchant/api-keys`, `POST /api/v1/merchant/api-keys/{id}/revoke` (`merchant_admin`); public `POST /v1/payment_intents` and `GET /v1/payment_intents/{id}` behind `Authorization: Bearer mfp_live_*`; deterministic public idempotency replay/conflict primitive with atomic concurrent replay, per-merchant/per-route scope and append-only finalized rows. Public API endpoints currently hosted in `platform`; moving them into `acquirer` is a deferred later Phase 04 slice gated on a real service-to-service auth primitive.
- Phase 04 Slice 02 Stripe webhook backend/runtime sub-scope in `platform`: `merchant.stripe_account_links`, `merchant.stripe_webhook_events`, `POST /webhooks/stripe/v1`, real Stripe-format HMAC-SHA256 signature verification, timestamp tolerance, duplicate Stripe event id idempotency, `account.updated` KYB state mapping and audit rows. Stripe Connect onboarding-start remains blocked on real sandbox credentials; no fake Stripe API path exists.

## Local Commands

From `product/`:

```bash
docker compose -f deploy/docker-compose.yml up --build
```

Parallel agent runtime slots can override the Compose project name and published ports without changing the default owner stack:

```bash
COMPOSE_PROJECT_NAME=mini-fintech-platform-a1 \
PLATFORM_HTTP_HOST_PORT=18181 \
PLATFORM_DB_HOST_PORT=15433 \
KAFKA_HOST_PORT=19092 \
KEYCLOAK_HOST_PORT=28080 \
docker compose -f deploy/docker-compose.yml up -d platform
```

Use matching `PLATFORM_BASE_URL` when running runtime scripts against a slot.

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
scripts/runtime/reg_phase04_api_key_lifecycle.sh
scripts/runtime/reg_phase04_public_api_response_shape.sh
scripts/runtime/reg_phase04_public_api_idempotency_replay.sh
scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh
scripts/runtime/reg_phase04_public_api_concurrent_idempotency.sh
scripts/runtime/reg_phase04_public_api_idempotency_per_route.sh
scripts/runtime/reg_phase04_idempotency_append_only.sh
scripts/runtime/reg_phase04_dashboard_api_key_idempotency.sh
scripts/runtime/reg_phase04_stripe_webhook_signature_valid.sh
scripts/runtime/reg_phase04_stripe_webhook_signature_invalid.sh
scripts/runtime/reg_phase04_stripe_webhook_timestamp_tolerance.sh
scripts/runtime/reg_phase04_stripe_webhook_idempotency.sh
scripts/runtime/reg_phase04_stripe_webhook_bad_then_valid_retry.sh
```

## Stripe Webhook Local Runtime

Phase 04 Slice 02 implements the inbound Stripe webhook receiver only. For local runtime the default signing secret is:

```bash
STRIPE_WEBHOOK_SIGNING_SECRET=whsec_local_test_secret
STRIPE_WEBHOOK_TOLERANCE_SECONDS=300
```

Retained scripts under `product/scripts/runtime/reg_phase04_stripe_webhook_*.sh` sign local Stripe-format payloads with this secret and verify valid signature, invalid signature rejection, timestamp tolerance and duplicate event id idempotency. Real Stripe Connect onboarding start (`MRC-01`) is blocked until sandbox credentials are provided.
