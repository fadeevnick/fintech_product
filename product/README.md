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
- Phase 04 Slice 03 merchant dashboard payments/webhook config backend/runtime sub-scope in `platform`: `merchant.webhook_endpoints`, merchant dashboard `GET /api/v1/merchant/payment-intents`, `GET /api/v1/merchant/payment-intents/{id}`, and `GET/POST/PUT/DELETE /api/v1/merchant/webhook-endpoints[/{id}]`; payment reads are merchant-scoped with cross-merchant 404 behavior, webhook config writes require `merchant_admin`, and `merchant_member` remains read-only.
- Phase 05 Slice 01 Vault/Card issuance backend/runtime sub-scope across `platform`, `issuer` and `vault`: end-user `POST /api/v1/cards`, Platform→Issuer and Issuer→Vault service-auth calls, Issuer token/last4 card records, Vault encrypted PAN tokenization, restricted detokenize for `service:issuer` and detokenize audit rows. `VLT-01`, `VLT-02` and `VLT-03` have runtime evidence.
- Phase 05 Slice 02 card authorization backend/runtime sub-scope across `platform`, `acquirer`, `network` and `issuer`: public `POST /v1/payment_intents/{id}/authorize`, Platform→Acquirer→Network→Issuer routing, approved authorization ledger holds, and structured declines for insufficient funds, blocked actor, inactive card and unknown card token. `PAY-04` and `PAY-05` have runtime evidence.
- Phase 05 Slice 03 payment capture foundation in `platform`: public `POST /v1/payment_intents/{id}/capture`, full-capture-only transition from `AUTHORIZED` to `CAPTURED`, durable capture metadata and existing public API idempotency behavior. `PAY-06` has runtime evidence.
- Phase 06 Slice 01 outbound merchant webhook delivery foundation in `platform`: webhook endpoints now have one-time `mfp_whsec_*` signing secrets, encrypted-at-rest delivery secret material, hash/prefix metadata, secret rotation endpoint, persisted `merchant.webhook_events` and `merchant.webhook_delivery_attempts`, real signed HTTP delivery of `payment_intent.created` after public payment-intent creation, and retained `WBH-01` runtime verification with a local signature-validating receiver.
- Phase 06 Slice 02 outbound merchant webhook retry/DLQ in `platform`: failed deliveries retry on a persisted schedule, HTTP failure metadata is retained, exhausted attempts move events to `DLQ`, and retained `WBH-02` runtime verification passed.
- Phase 06 Slice 03 outbound merchant webhook DLQ replay in `platform`: merchant dashboard APIs list/detail retained webhook events and replay one `DLQ` event, replay reuses the signed outbound delivery path with current endpoint secret material, successful replay moves the event to `DELIVERED`, and retained `WBH-03` runtime verification passed.
- Phase 06 Slice 04 capture-to-settlement foundation in `platform`: captured payment intents can be processed by `POST /internal/settlement/process-captured`, creating `settlement.settlement_batches` / `settlement.settlement_items`, marking payment intents `SETTLED`, and posting a minimal balanced `CARD_PAYMENT_SETTLEMENT` ledger journal from `CARD_SETTLEMENT_CLEARING` to a per-merchant settlement account. `SET-01` has runtime evidence.
- Phase 07 Slice 01 KYC/Sumsub foundation in `platform`: end-user `POST /api/v1/kyc/start`, KYC profile/session/vendor-event persistence, Sumsub adapter boundary, inbound `POST /webhooks/sumsub/v1` signature verification and vendor event id idempotency. `KYC-02` has runtime evidence; `KYC-01` is partial until real Sumsub sandbox credentials are configured.
- Phase 07 Slice 02 backoffice KYC manual review in `platform`: backoffice KYC queue/detail/manual decision APIs, rationale validation, `kyc.kyc_manual_decisions` persistence and `kyc.manual_decision_recorded` audit rows. `KYC-03` has runtime evidence.
- Phase 07 Slice 03 OpenSanctions fail-closed foundation in `platform`: `sanctions.sanctions_hits`, OpenSanctions adapter boundary with explicit local modes, fail-closed screening before KYC manual approval, persisted hit/audit rows for unavailable and possible-match outcomes, and no-match approval pass-through. `SNX-01` has runtime evidence.
- Phase 07 Slice 04 sanctions false-positive exception in `platform`: compliance-only backoffice sanctions hit list/detail/decision APIs, `CLEAR_FALSE_POSITIVE` decision with rationale, `sanctions.sanctions_hit_decisions`, `sanctions.sanctions_false_positive_exceptions`, audit rows, and suppression of the same future OpenSanctions match for the same end user/vendor entity during KYC manual approval. `SNX-02` has runtime evidence.
- Phase 08 Slice 01 AML velocity alert foundation in `platform`: `aml.aml_alerts` / `aml.aml_rule_evaluations`, internal `POST /internal/aml/evaluate-velocity`, deterministic local completed-activity velocity rule, duplicate suppression for same user/rule/window open alerts, and `aml.alert_created` audit rows. `AML-01` has runtime evidence.

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
scripts/runtime/reg_phase04_merchant_payment_reads.sh
scripts/runtime/reg_phase04_merchant_webhook_config.sh
scripts/runtime/reg_phase05_card_issue_pan_isolation.sh
scripts/runtime/reg_phase05_vault_detokenize_restriction.sh
scripts/runtime/reg_phase05_pan_log_masking.sh
scripts/runtime/reg_phase05_authorization_approved_hold.sh
scripts/runtime/reg_phase05_authorization_structured_declines.sh
scripts/runtime/reg_phase05_payment_capture.sh
scripts/runtime/reg_phase06_webhook_signing_delivery.sh
scripts/runtime/reg_phase06_capture_to_settlement.sh
scripts/runtime/reg_phase07_kyc_start.sh
scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh
scripts/runtime/reg_phase07_opensanctions_fail_closed.sh
scripts/runtime/reg_phase07_sanctions_false_positive.sh
scripts/runtime/reg_phase08_aml_velocity_alert.sh
```

## Stripe Webhook Local Runtime

Phase 04 Slice 02 implements the inbound Stripe webhook receiver only. For local runtime the default signing secret is:

```bash
STRIPE_WEBHOOK_SIGNING_SECRET=whsec_local_test_secret
STRIPE_WEBHOOK_TOLERANCE_SECONDS=300
```

Retained scripts under `product/scripts/runtime/reg_phase04_stripe_webhook_*.sh` sign local Stripe-format payloads with this secret and verify valid signature, invalid signature rejection, timestamp tolerance and duplicate event id idempotency. Real Stripe Connect onboarding start (`MRC-01`) is blocked until sandbox credentials are provided.


## Phase 05 Vault/Card Local Runtime

Phase 05 Slice 01 adds the first real Vault/Issuer card path:

- Platform exposes end-user `POST /api/v1/cards`.
- Issuer stores `card_token`, `last4`, expiration, BIN and state only.
- Vault stores encrypted PAN and restricts detokenize to `service:issuer`.

Local runtime defaults:

```bash
SERVICE_AUTH_SECRET=local-service-secret
VAULT_PAN_ENCRYPTION_KEY=local-vault-pan-key-change-me
VAULT_TEST_BIN=400000
ISSUER_BASE_URL=http://issuer:8080
VAULT_BASE_URL=http://vault:8080
```

Retained scripts:

```bash
scripts/runtime/reg_phase05_card_issue_pan_isolation.sh
scripts/runtime/reg_phase05_vault_detokenize_restriction.sh
scripts/runtime/reg_phase05_pan_log_masking.sh
scripts/runtime/reg_phase05_authorization_approved_hold.sh
scripts/runtime/reg_phase05_authorization_structured_declines.sh
scripts/runtime/reg_phase05_payment_capture.sh
```

These scripts target `VLT-01`, `VLT-02` and `VLT-03`. `PAY-04` and `PAY-05` are covered by the Phase 05 Slice 02 authorization scripts listed above. `reg_phase05_payment_capture.sh` targets `PAY-06` and has runtime evidence.

## Phase 06 Outbound Webhook Local Runtime

Phase 06 Slice 01 adds signed outbound merchant webhook delivery for the current payment-intent shell. Phase 06 Slice 02 adds persisted failed-delivery retry scheduling and terminal DLQ state. Phase 06 Slice 03 adds merchant-scoped DLQ inspection and single-event replay:

- webhook endpoint creation returns `signingSecret` once;
- `POST /api/v1/merchant/webhook-endpoints/{id}/rotate-secret` returns a new one-time secret;
- Platform stores encrypted delivery secret material plus hash/prefix metadata;
- `POST /v1/payment_intents` emits `payment_intent.created`;
- Platform signs and POSTs the payload to active endpoints subscribed to that event;
- delivery events and attempts are persisted in `merchant.webhook_events` and `merchant.webhook_delivery_attempts`;
- failed deliveries are retried on persisted `next_retry_at` schedule and move to `DLQ` after retry exhaustion;
- merchant dashboard backend routes under `/api/v1/merchant/webhook-events` list/detail DLQ events and replay one DLQ event for `merchant_admin`.

Retained script:

```bash
scripts/runtime/reg_phase06_webhook_signing_delivery.sh
scripts/runtime/reg_phase06_webhook_retry_dlq.sh
scripts/runtime/reg_phase06_webhook_dlq_replay.sh
```

Local runtime default:

```bash
WEBHOOK_SIGNING_SECRET_ENCRYPTION_KEY=local-webhook-signing-secret-key-change-me
WEBHOOK_MAX_ATTEMPTS=3
WEBHOOK_RETRY_DELAYS_SECONDS=1,2
```

For isolated compose-network verification, use:

```bash
COMPOSE_PROJECT_NAME=mini-fintech-platform-a2 \
COMPOSE_FILE=deploy/docker-compose.yml \
PLATFORM_BASE_URL=http://platform:8080 \
PLATFORM_CURL_CONTAINER_NETWORK=mini-fintech-platform-a2_default \
scripts/runtime/reg_phase06_webhook_signing_delivery.sh
```

`WBH-01`, `WBH-02` and `WBH-03` are implemented and verified.

## Phase 06 Capture-to-Settlement Local Runtime

Phase 06 Slice 04 adds the first captured-payment settlement path:

- `POST /internal/settlement/process-captured?limit=50` processes eligible `CAPTURED` payment intents;
- settlement batches/items are persisted under the `settlement` schema;
- each processed payment intent becomes queryable as `SETTLED`;
- one minimal balanced `CARD_PAYMENT_SETTLEMENT` ledger journal is posted per settlement item.

Retained script:

```bash
scripts/runtime/reg_phase06_capture_to_settlement.sh
```

`SET-01` is implemented and verified. Fee splits, payouts, refunds, chargebacks and merchant dashboard settlement UI are not implemented in this slice.


## Phase 07 KYC/Sumsub Local Runtime

Phase 07 Slice 01 adds the first Platform KYC/Sumsub foundation:

- end-user `POST /api/v1/kyc/start`;
- persisted KYC profile/session state in `kyc.*`;
- real Sumsub API boundary when sandbox credentials are configured;
- no fake successful Sumsub applicant/access-token path when credentials are absent;
- inbound `POST /webhooks/sumsub/v1` HMAC-SHA256 payload verification and vendor event id idempotency.

Local runtime defaults:

```bash
SUMSUB_BASE_URL=https://api.sumsub.com
SUMSUB_APP_TOKEN=
SUMSUB_SECRET_KEY=
SUMSUB_WEBHOOK_SECRET=local-sumsub-webhook-secret
SUMSUB_LEVEL_NAME=basic-kyc-level
```

Retained scripts:

```bash
scripts/runtime/reg_phase07_kyc_start.sh
scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh
scripts/runtime/reg_phase07_opensanctions_fail_closed.sh
scripts/runtime/reg_phase07_sanctions_false_positive.sh
scripts/runtime/reg_phase08_aml_velocity_alert.sh
scripts/runtime/reg_phase07_kyc_manual_review.sh
scripts/runtime/reg_phase07_opensanctions_fail_closed.sh
scripts/runtime/reg_phase07_sanctions_false_positive.sh
```

OpenSanctions local runtime defaults:

```bash
OPENSANCTIONS_BASE_URL=https://api.opensanctions.org
OPENSANCTIONS_API_KEY=
OPENSANCTIONS_TIMEOUT_MS=3000
OPENSANCTIONS_LOCAL_MODE=disabled
OPENSANCTIONS_MATCH_THRESHOLD=0.85
```

`OPENSANCTIONS_LOCAL_MODE` supports `disabled`, `unavailable`/`timeout`, `match` and `no_match`; real API success is not faked when local mode is disabled and no API key is configured.

`reg_phase07_kyc_start.sh` records `KYC-01` as partial when real Sumsub credentials are absent. `reg_phase07_sumsub_webhook_signature_idempotency.sh` proves `KYC-02` locally with deterministic Sumsub-format signed fixtures. `reg_phase07_kyc_manual_review.sh` proves `KYC-03` for backoffice queue/detail/manual approval and audit. `reg_phase07_opensanctions_fail_closed.sh` proves `SNX-01` with deterministic OpenSanctions local modes. For isolated compose-network verification, set `PLATFORM_CURL_CONTAINER_NETWORK` and use a compose-network `PLATFORM_BASE_URL`.
