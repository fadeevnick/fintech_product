# 04 Architecture — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

History:
- v0.1 — first draft architecture на основе APPROVED `01_business_requirements.md` v0.3, `02_user_journeys.md` v0.2, `03_functional_requirements.md` v0.2, плюс 8 архитектурных решений принятых в два round'а Q&A: selective services (5 deployment units), schema-per-context per service, in-process function calls внутри monolith, Kafka async bus, Public Payments API в Acquirer service, Ledger в monolith, choreography + state machines, hybrid identity (Keycloak для backoffice).
- v0.2 — open questions v0.1 разрешены: accept ledger HTTP latency для MVP с local projection как later scope, Acquirer local merchant settlement projection с reconciliation, sync HTTP для read-audit на compliance-sensitive entities, separate nginx containers per SPA, `/internal/<context>/<operation>` namespace, JSON для Kafka payloads с strict versioning, long-lived shared secrets для service tokens в MVP, idempotency TTL 24h, public API host = `api.miniefin.local`, monolith multi-process per deployment unit.

---

## 1. Architectural Drivers

Архитектура подчинена ключевым constraints из 01:
- **Industry-aligned build/buy/integrate split** (01 §1.2): архитектура должна explicitly выражать три категории по-разному.
- **Three-party card network simulation** как отдельные deployment units для realism.
- **PCI-like vault isolation** — Vault обязан быть отдельным сервисом, отдельной DB, restricted network.
- **Real test-mode vendor integrations** (Sumsub, OpenSanctions, Stripe) — каждая через adapter pattern.
- **Single-developer cadence**, 8-12 месяцев — операционная сложность контролируется (не full microservices).
- **Production-pattern coverage** (01 §5.2): idempotency, outbox, saga, audit, RBAC, hexagonal/ports-and-adapters, structured observability.
- **Quality bar**: no fakes — каждый production-pattern должен реально работать.

---

## 2. Deployment Topology

### 2.1 Services overview

Платформа состоит из **5 application services** + supporting infrastructure:

| Service | Responsibility | Why separate |
|---|---|---|
| **`platform`** (monolith) | Identity, KYC adapter, Sanctions adapter, AML, Case Management, Ledger, Wallet operations, Merchant onboarding, Audit log, all background jobs | Core business domain; modules with internal function calls between contexts |
| **`acquirer`** | Public Payments API, payment_intent lifecycle, merchant settlement balance management, outbound webhook delivery to merchants | Owns merchant-facing surface; realistic merchant acquirer role |
| **`network`** | BIN routing, clearing batch engine, settlement positions calculation, fee расщепление dispatch | Simulates card network as message router; separation teaches inter-bank messaging |
| **`issuer`** | Card record store, authorization decisions, hold management, chargeback initiation | Plays issuing bank role; calls Vault для detokenize, calls Platform Ledger для hold/debit postings |
| **`vault`** | Card data store, tokenization, detokenize API (restricted), forward-to-issuer | PCI-like isolation requirement; only one service (Issuer) authorized для detokenize |

### 2.2 Infrastructure components

| Component | Purpose |
|---|---|
| Postgres × 5 | One DB instance per service (platform, acquirer, network, issuer, vault) |
| Postgres × 1 | Dedicated Postgres для Keycloak |
| Kafka (KRaft mode, no Zookeeper) | Async event bus для outbox dispatch, clearing/settlement events, AML events, webhook dispatcher |
| Keycloak | OIDC provider для backoffice SSO (single `backoffice` realm) |
| MinIO | S3-compatible object storage для KYC documents, chargeback evidence, SoF supporting docs |
| Traefik | Edge reverse proxy — TLS termination, host-based routing, static frontend serving |
| Prometheus | Metrics collection |
| Grafana | Visualization, dashboards |
| Tempo | Distributed trace storage |
| Loki | Log aggregation |
| OpenTelemetry Collector | Trace + metric pipeline |
| Vector | Log shipping (containers → Loki) |

### 2.3 Frontends

Три отдельных SPA, каждый собирается в static bundle:
- `spa-enduser` — end-user web (e.g., `app.miniefin.local`)
- `spa-merchant` — merchant dashboard (e.g., `merchants.miniefin.local`)
- `spa-backoffice` — backoffice workflow UI (e.g., `back.miniefin.local`)

Каждая SPA serve'ится через **отдельный nginx-static container** (не Traefik file middleware) — даёт независимый health check, cache control и матчится с realistic production setup. Backend APIs reached через одни и те же hosts с path routing (например, `app.miniefin.local/api/v1/*` → Platform service). Public Payments API для merchant'ов выделен на отдельный host `api.miniefin.local` (Stripe-style pattern) — даёт чистое разделение public-API surface от dashboard UI и более чистый CORS / rate-limit scope.

### 2.4 Docker Compose layout (sketch)

```text
docker-compose.yml
├── platform                  # monolith app service
├── acquirer                  # acquirer service
├── network                   # network simulation service
├── issuer                    # issuer simulation service
├── vault                     # PCI vault service
├── postgres-platform         # main Postgres (schemas: identity, kyc, sanctions, aml, cases, ledger, wallet, merchant, audit, ...)
├── postgres-acquirer         # acquirer's DB
├── postgres-network          # network's DB
├── postgres-issuer           # issuer's DB
├── postgres-vault            # vault's DB (PAN encryption at rest)
├── postgres-keycloak         # Keycloak's DB
├── keycloak                  # OIDC provider (backoffice realm)
├── kafka                     # KRaft single-node для dev
├── minio                     # object storage
├── traefik                   # edge router + TLS
├── spa-enduser
├── spa-merchant
├── spa-backoffice
├── prometheus
├── grafana
├── tempo
├── loki
├── otel-collector
└── vector
```

Total ~25 containers — heavy but manageable on a single dev workstation (16GB+ RAM recommended).

### 2.5 Edge routing (Traefik)

Hosts:
- `app.miniefin.local` → spa-enduser (static), `/api/*` → platform service
- `merchants.miniefin.local` → spa-merchant (static), `/api/*` → platform service, `/v1/*` (public Payments API) → acquirer service
- `back.miniefin.local` → spa-backoffice (static), `/api/*` → platform service, OIDC redirects → keycloak
- `api.miniefin.local` (public API host для merchants) → acquirer service
- `vault.internal` → vault service (internal-only network, не exposed)
- `keycloak.miniefin.local` → keycloak
- `grafana.miniefin.local` → grafana (basic-auth-protected в local dev)

TLS termination at Traefik с self-signed certs для local; ACME / Let's Encrypt — later scope.

### 2.6 External managed components (not in Compose)

- **Sumsub sandbox** — accessed via HTTPS API
- **OpenSanctions API** — accessed via HTTPS API
- **Stripe Connect sandbox** — accessed via HTTPS API
- Webhooks от Sumsub / Stripe — приходят на public-exposed endpoint (via ngrok / Cloudflare Tunnel в local dev, через real domain в cloud deploy)

---

## 3. Bounded Contexts Map

### 3.1 Contexts list

| # | Context | Service location | Schema |
|---|---|---|---|
| 1 | Identity & Sessions | platform | `identity` |
| 2 | KYC | platform | `kyc` |
| 3 | Sanctions Screening | platform | `sanctions` |
| 4 | AML Monitoring | platform | `aml` |
| 5 | Case Management | platform | `cases` |
| 6 | Ledger | platform | `ledger` |
| 7 | Wallet & Manual Operations | platform | `wallet` |
| 8 | Merchant Onboarding | platform | `merchant` |
| 9 | Audit | platform | `audit` |
| 10 | Webhook Delivery (outbound) | acquirer | `webhook` |
| 11 | Acquirer (Public Payments API, payment_intent lifecycle, settlement) | acquirer | `acquirer` |
| 12 | Network (BIN routing, clearing, settlement positions) | network | `network` |
| 13 | Issuer (cards, authorization, holds, chargeback init) | issuer | `issuer` |
| 14 | Vault (card data store, tokenization) | vault | `vault` |
| 15 | Chargeback Lifecycle | spans acquirer (state) + issuer (initiation) + cases (review case) | shared via events |

### 3.2 Context relationships

Used DDD context-map terminology:

- **Identity** is upstream of almost everything — supplies user/role to всем contexts (in monolith) и to services через session/JWT validation.
- **Ledger** is upstream of Wallet, Acquirer, Issuer (everyone calls Ledger для postings); **Ledger** is **conformist** target (all callers conform к Ledger API contract).
- **Case Management** is upstream of KYC review, Sanctions hit review, AML alert review, Chargeback dispute review, SoF declaration. Used as **published kernel** (one model shared, не per-domain duplication).
- **Audit** is downstream of all contexts (everyone writes audit events). Uses **published events** pattern.
- **Vault** is **conformist** для Issuer (Issuer adapts к Vault contract); other contexts have no relationship с Vault.
- **Issuer ↔ Network ↔ Acquirer** form **customer-supplier** chain (Acquirer is customer of Network, Network is customer of Issuer для authorization).
- **Sumsub / OpenSanctions / Stripe Connect** integration через **anti-corruption layer** (port + adapter в платформе; external vendor data structures не утекают в domain).

### 3.3 Module structure within monolith (platform service)

```
platform/
├── shared/
│   ├── ledger/                # Ledger context (внутренний API для других contexts)
│   ├── case/                  # Case management
│   ├── audit/                 # Audit writer
│   ├── identity/              # Identity & sessions
│   ├── outbox/                # Transactional outbox
│   └── idempotency/           # Idempotency layer
├── domains/
│   ├── kyc/                   # KYC adapter (Sumsub) + flows
│   ├── sanctions/             # Sanctions adapter (OpenSanctions) + matching
│   ├── aml/                   # Rule engine + alert handling
│   ├── wallet/                # Manual deposit/withdraw + transfers + SoF
│   ├── merchant/              # Merchant onboarding (Stripe Connect adapter) + merchant lifecycle
│   └── chargeback/            # Chargeback case review side (arbitration UI logic)
├── adapters/
│   ├── sumsub/                # vendor adapter
│   ├── opensanctions/         # vendor adapter
│   ├── stripe_connect/        # vendor adapter
│   ├── minio/                 # object storage adapter
│   ├── kafka/                 # producer/consumer wrappers
│   └── keycloak/              # OIDC verifier
└── http/
    ├── enduser_api/           # /api/* для end-user SPA
    ├── merchant_api/          # /api/* для merchant SPA (managing keys, viewing, etc — НЕ Public Payments API)
    └── backoffice_api/        # /api/* для backoffice SPA
```

**Internal communication discipline**: domains call shared module APIs (`ledger.post(...)`, `case.open(...)`, `audit.write(...)`) directly. Cross-domain calls (e.g., wallet ↔ aml) идут через published events на in-process event bus (с outbox для anything that triggers external action).

### 3.4 Module structure within Acquirer service

```
acquirer/
├── public_api/                # /v1/* (Payments API for merchants)
├── payment_intents/           # payment_intent state machine + workflow
├── merchant_settlement/       # settlement balance (local projection of platform ledger)
├── webhook_delivery/          # outbound webhook dispatcher (consumes Kafka, signs, retries, DLQ)
├── network_client/            # HTTP client to network service
├── ledger_client/             # HTTP client to platform ledger API
└── adapters/
    ├── kafka/
    └── postgres/
```

### 3.5 Module structure within Network service

```
network/
├── auth_router/               # sync HTTP: routes authorization request acquirer → issuer; routes response back
├── bin_registry/              # BIN → issuer routing table (MVP: один internal BIN)
├── clearing_engine/           # batch processor (consumes Kafka topic for captures)
├── settlement_positions/      # calculates net positions, fee расщепление
└── adapters/
    ├── kafka/
    ├── issuer_client/         # HTTP client to issuer
    ├── acquirer_client/       # HTTP callbacks to acquirer (для async events)
    └── postgres/
```

### 3.6 Module structure within Issuer service

```
issuer/
├── cards/                     # card records + state machine
├── authorization/             # auth decision logic (calls ledger for available balance + hold)
├── holds/                     # hold management + expiration
├── chargeback_initiation/     # creates chargeback record, calls network
└── adapters/
    ├── vault_client/          # HTTP to vault для detokenize/forward
    ├── ledger_client/         # HTTP to platform ledger
    ├── network_client/        # HTTP to network для chargeback dispatch
    └── postgres/
```

### 3.7 Module structure within Vault service

```
vault/
├── tokenization/              # generate token, store PAN encrypted
├── detokenize/                # restricted endpoint (mTLS-only)
├── forward_to_issuer/         # for hosted-form path
└── adapters/
    └── postgres/              # с encryption-at-rest column-level
```

---

## 4. Persistence

### 4.1 Per-service Postgres

Каждый из 5 application services имеет own Postgres instance:
- `postgres-platform`: один DB (`mfp_platform`) с множественными schemas (identity, kyc, sanctions, aml, cases, ledger, wallet, merchant, audit, outbox, idempotency_keys).
- `postgres-acquirer`: один DB (`mfp_acquirer`) с schemas (acquirer, payment_intents, merchant_settlement, webhook).
- `postgres-network`: один DB (`mfp_network`) с schema (`network`) — clearing batches, settlement positions, BIN registry.
- `postgres-issuer`: один DB (`mfp_issuer`) с schemas (cards, authorization, holds).
- `postgres-vault`: один DB (`mfp_vault`) с schema (`vault`); PAN column encrypted via `pgcrypto` (column-level AES) + master key via env var (later scope: KMS).

### 4.2 Schemas within platform Postgres

```text
mfp_platform (DB)
├── identity
│   ├── end_users
│   ├── merchant_employees
│   ├── backoffice_users (mirror of Keycloak users, populated at login)
│   ├── sessions
│   └── email_verifications
├── kyc
│   ├── kyc_cases
│   ├── kyc_documents (refs to MinIO objects)
│   └── sumsub_applicants (vendor entity link table)
├── sanctions
│   ├── sanctions_hits
│   ├── cleared_exceptions
│   └── opensanctions_cache (cached match results)
├── aml
│   ├── aml_alerts
│   ├── aml_rules_config
│   ├── account_freezes
│   └── whitelist_exemptions
├── cases
│   ├── cases
│   ├── case_actions
│   ├── case_attachments
│   └── case_assignments
├── ledger
│   ├── accounts
│   ├── journal_entries
│   ├── postings
│   └── (views for balance calc)
├── wallet
│   ├── wallet_accounts (1-to-1 to ledger accounts)
│   ├── deposit_requests
│   ├── withdraw_requests
│   ├── internal_transfers
│   └── sof_declarations
├── merchant
│   ├── merchants
│   ├── api_keys
│   ├── webhook_endpoints
│   └── stripe_account_links (link table to Stripe vendor entity)
├── audit
│   └── audit_log (append-only enforced via triggers + DB permissions; no UPDATE/DELETE grants)
├── outbox
│   └── outbox_events (transactional outbox)
└── idempotency
    └── idempotency_keys (request fingerprint cache)
```

### 4.3 Cross-service data access discipline

Strict rule: **no cross-database joins, no shared schema between services**. Cross-service data access only via HTTP API of owning service (либо async events for downstream consumption).

Каждый service exposes **internal API** для других services. Examples:
- Platform exposes `POST /internal/ledger/postings` — used by Acquirer/Issuer для postings.
- Platform exposes `GET /internal/ledger/balance?account_id=...` — used by Issuer для balance check на auth.
- Platform exposes `GET /internal/wallet/account/:user_id` — used by Issuer для wallet resolution.
- Platform exposes `POST /internal/case/open` — used by Acquirer для chargeback case creation.
- Platform exposes `POST /internal/audit/write` — used by Issuer/Network/Acquirer/Vault для audit (или через Kafka — см. §5).

Internal API namespace отделён от public API (`/internal/*` vs `/api/*` vs `/v1/*`).

### 4.4 DB migrations

Each service имеет own migration directory; migrations applied на startup или CI step. Specific tool decided в 05 (depends на backend stack — Flyway / Atlas / Liquibase / framework-native).

### 4.5 Ledger schema design overview

Минимальная shape (details для design-details / 06):

```sql
ledger.accounts (
  id BIGSERIAL PK,
  account_type VARCHAR,        -- user_wallet | user_wallet_hold | merchant_settlement | issuer_interchange | network_assessment | acquirer_margin | suspense_bank_inflow | suspense_bank_outflow | stripe_payout_clearing | external_settled | dispute_reserve
  reference_id VARCHAR,        -- e.g., user_id или merchant_id
  currency VARCHAR DEFAULT 'EUR',
  created_at TIMESTAMPTZ
)

ledger.journal_entries (
  id BIGSERIAL PK,
  external_reference VARCHAR,  -- payment_intent_id / transfer_id / chargeback_id / etc
  event_source VARCHAR,        -- "wallet.deposit" / "card.auth" / "settlement" / etc
  description TEXT,
  created_at TIMESTAMPTZ
)

ledger.postings (
  id BIGSERIAL PK,
  journal_entry_id BIGINT FK,
  account_id BIGINT FK,
  direction CHAR(1),           -- 'D' (debit) или 'C' (credit)
  amount NUMERIC(20, 4),
  currency VARCHAR DEFAULT 'EUR',
  created_at TIMESTAMPTZ
)
-- DB-level constraint: каждый journal_entry должен иметь Σ debits == Σ credits (enforced via deferred trigger или checked-procedure-only insert)
-- DB-level permission: no UPDATE / DELETE on ledger.* tables.
```

Balance derivation — sum of postings per account (или materialized view с invalidation).

---

## 5. Communication Patterns

### 5.1 Sync internal HTTP (between services on fast path)

Use cases:
- Acquirer → Network: forward authorization request.
- Network → Issuer: route authorization request.
- Issuer → Vault: detokenize / forward-to-network operation.
- Issuer → Platform Ledger: balance check + hold posting + actual debit.
- Acquirer → Platform Ledger: settlement postings + chargeback ledger movements.
- Acquirer / Issuer → Platform Case Management: open case.
- Acquirer / Issuer → Platform Audit: synchronous audit (или async via Kafka).
- Network → Issuer / Acquirer: bidirectional auth response routing.

Protocol: HTTP/JSON; sync request-response. Timeouts: 5s default; 30s для batch operations.

### 5.2 Async via Kafka

Use cases:
- **Outbox dispatch** — each service has outbox table; outbox dispatcher pushes events to Kafka.
- **Clearing batch events** — Acquirer publishes `capture_finalized` events; Network consumes for clearing.
- **Settlement events** — Network publishes `settlement_position_computed` events; Acquirer и Platform consume.
- **AML events** — Platform domains (wallet, internal transfer) publish events; AML consumer evaluates rules.
- **Webhook outbound** — Acquirer publishes `outbound_webhook_pending` events; webhook delivery consumer dispatches.
- **Audit events** (async path) — services publish `audit_event` events; audit consumer в Platform writes.
- **Vendor webhook receipt** — Sumsub / Stripe webhooks received by Platform, validated, posted to Kafka для downstream processing.

Topics naming convention: `<domain>.<event_name>.v<version>`. Examples:
- `acquirer.capture_finalized.v1`
- `network.settlement_computed.v1`
- `aml.alert_raised.v1`
- `webhook.outbound_pending.v1`

Partitioning: by entity ID для ordering guarantee на entity scope (e.g., partition by `payment_intent_id` для capture events, by `user_id` для AML events).

Consumer groups: per-consumer-service (e.g., `aml-engine`, `webhook-dispatcher`, `audit-writer`).

**Payload format**: JSON в MVP с строгим versioning convention. Breaking change → новый topic с `.v2` суффиксом; старые consumers продолжают читать `.v1` до полной миграции. Schema Registry (Confluent / Apicurio) + Avro / Protobuf — later scope, когда количество topics и consumer'ов выйдет за реальный manual review.

### 5.3 In-process function calls within monolith

Между contexts внутри platform service — direct function calls между module APIs:
- `wallet.deposit_service.process_deposit(...)` calls `ledger.posting_service.post(...)` directly.
- `kyc.review_service.approve(...)` calls `case.service.close_case(...)` directly.
- `wallet.transfer_service.execute(...)` calls `aml.rule_engine.pre_check(...)` directly.

Cross-domain side effects (e.g., wallet → aml) идут через **published events** на in-process event bus, который в свою очередь записывает в outbox для async fan-out за пределы monolith.

### 5.4 External vendor adapters via HTTPS

Each external vendor имеет adapter в `platform/adapters/<vendor>/`:
- **Sumsub** — `sumsub_adapter.create_applicant(...)`, `.upload_document(...)`, `.handle_webhook(...)`. Webhook receiver validates HMAC; raw vendor payload mapped в domain model на adapter boundary.
- **OpenSanctions** — `opensanctions_adapter.match(...)`. Sync call с timeout 3s, fail-closed.
- **Stripe Connect** — `stripe_adapter.create_account(...)`, `.create_account_link(...)`, `.initiate_payout(...)`, `.handle_webhook(...)`.
- **Keycloak** — `keycloak_adapter.validate_token(...)`, `.fetch_userinfo(...)`. JWKS endpoint cached.
- **MinIO** — `minio_adapter.upload(...)`, `.download(...)`, `.presigned_url(...)`.

Adapter layer **не утекает vendor types** в domain. Domain types defined в `domains/<x>/types.ts` (или .py / .kt — depends на 05).

### 5.5 Vault detokenize: restricted protocol

- Vault exposes detokenize endpoint только на internal Docker network (`vault.internal:8443`).
- Authentication: signed service tokens (HS256/HS512 with shared secret), `aud=vault`, `iss=issuer`, short TTL (60s). Specific token issuance scheme — в 05.
- Authorization: Vault rejects requests with non-`issuer` issuer.
- All detokenize calls logged в Vault's narrow audit log с caller service identity, token, timestamp.
- Future production: mTLS, hardware-backed key, dedicated KMS.

### 5.6 Service-to-service authentication

Service-to-service HTTP calls authenticate via **signed service tokens** (JWT HS256 или HS512). Each service имеет shared secret pair с each target service (e.g., `acquirer ↔ platform`, `issuer ↔ vault`). Token payload: `iss` (caller service), `aud` (target service), `exp`, `request_id`.

Token rotation policy — defined в 05 / design-details. MVP: long-lived shared secrets in env (simple). Production-grade с rotation — later scope.

---

## 6. Saga / Process Management

### 6.1 Choreography pattern

No central orchestrator. Each context владеет своим state machine и реагирует на published events. Cross-context coordination через events on Kafka (Kafka guarantees at-least-once delivery; consumers must be idempotent).

### 6.2 Major state machines

| Entity | States | Owner |
|---|---|---|
| `payment_intent` | `CREATED → AUTHORIZED → CAPTURED → SETTLED` (alt: `FAILED`, `EXPIRED`, `REFUNDED`, `DISPUTED`, `CHARGED_BACK`) | acquirer |
| `card_authorization` | `REQUESTED → APPROVED → HELD → CAPTURED → SETTLED` (alt: `DECLINED`, `EXPIRED`) | issuer |
| `chargeback` | `INITIATED → MERCHANT_NOTIFIED → EVIDENCE_SUBMITTED → ARBITRATION → WON | LOST` (alt: `ACCEPTED_BY_MERCHANT`, `DEADLINE_EXPIRED`) | shared (acquirer holds outer state, issuer holds initiation state) |
| `kyc_case` | `NOT_STARTED → SUBMITTED → IN_REVIEW → APPROVED | REJECTED | NEEDS_RESUBMIT` | platform/kyc |
| `sanctions_hit` | `OPEN → IN_REVIEW → CLEARED_FALSE_POSITIVE | TRUE_MATCH` | platform/sanctions |
| `aml_alert` | `OPEN → IN_REVIEW → CLOSED_FALSE_POSITIVE | ESCALATED | MARKED_FOR_SAR | ACCOUNT_FROZEN_PERMANENT` | platform/aml |
| `withdraw_request` | `PENDING → READY_FOR_SECOND_REVIEW → APPROVED → COMPLETED` (alt: `REJECTED`, `HELD_FOR_REVIEW`) | platform/wallet |
| `deposit_request` | similar to withdraw | platform/wallet |
| `payout` (merchant) | `INITIATED → PAYOUT_PENDING → PAYOUT_SUCCEEDED | PAYOUT_FAILED` | acquirer (через Stripe adapter) |
| `sumsub_applicant` | mirror of Sumsub state, link to `kyc_case` | platform/kyc |
| `stripe_account` | mirror of Stripe account state, link to merchant | platform/merchant |

Each state machine defined как explicit transition table; transitions audited.

### 6.3 Compensating actions

Choreography requires explicit compensation logic per failure scenario. Examples:
- If Issuer hold succeeds но Network response timeout → Issuer expires hold after configured timeout (default 90s for auth, longer for capture).
- If Acquirer creates payment_intent но Network call fails → Acquirer marks `FAILED`, no ledger postings происходили (hold not placed because issuer never reached).
- If settlement positions calculated but Acquirer settlement посting fails → settlement positions remain `pending`, replay job picks up.

Compensations are **explicit code paths** во владеющем context'е, не magic from external orchestrator.

### 6.4 Outbox / transactional consistency

Каждое service применяет **transactional outbox pattern**:
- При state change в DB transaction also writes outbox event row в same transaction.
- Separate outbox dispatcher (background job) reads new outbox rows, publishes to Kafka, marks dispatched.
- Guarantees: atomicity (state + event published atomically), at-least-once (dispatcher retries), in-order per entity (outbox processed in insertion order per `aggregate_id`).

---

## 7. Security Boundaries

### 7.1 Network segmentation (Docker Compose networks)

```
networks:
  edge      # Traefik exposed externally, frontends, public-facing APIs
  app       # all application services (platform, acquirer, network, issuer)
  secure    # vault + issuer (only services что нужны для card data path)
  data      # all Postgres instances (исключая через app network)
  infra     # Kafka, observability
```

Vault Postgres lives ONLY on `data` network, accessed только vault. Vault application service lives on `secure` + `app` networks. Issuer lives на `secure` (for vault access) + `app` (для network communication). Other services NOT на `secure` network — can't reach Vault.

### 7.2 Vault isolation

- Vault service binds на internal-only network (`secure`).
- Vault Postgres binds on dedicated network, only Vault application reads.
- Vault detokenize endpoint requires signed service token с `iss=issuer`.
- All vault operations audited.
- PAN encryption: column-level via pgcrypto (AES-256-GCM); master key in env var (later scope: KMS).
- CVV transient в RAM only, никогда не persisted.

### 7.3 Identity zones

Three identity sources:
- **End-user identity** — platform-built, password+email, sessions stored в `identity.sessions`. Cookie-based auth.
- **Merchant identity** — platform-built, same identity infra as end-user. Disjoint user pool (global email uniqueness enforced).
- **Backoffice operator identity** — Keycloak SSO. Platform receives OIDC tokens, validates against Keycloak JWKS, mirrors user in `identity.backoffice_users` on login.

Vault clients (Issuer service) authenticate с signed service tokens — отдельный identity zone, не пересекается с user identity.

### 7.4 RBAC enforcement points

- **Inbound API on platform/acquirer/etc.**: middleware verifies session (cookie) или service token, attaches user/service identity к request context.
- **Per-endpoint authorization**: endpoint declarations specify required role (e.g., `@requires_role("compliance_officer")`). Authorization service checks identity roles.
- **Resource-level ownership**: endpoints that operate on per-user / per-merchant resources verify ownership (e.g., wallet operations check `wallet.user_id == session.user_id`).
- **DB-level constraints**: append-only audit log via DB grants (no UPDATE/DELETE on `audit.audit_log`).

### 7.5 PCI-like data separation

- PAN never leaves Vault unless to internal Issuer service via authenticated detokenize.
- PAN never logged in application logs (logging frameworks apply masking pattern `XXXX-XXXX-XXXX-1234`).
- Acquirer service NEVER receives PAN — payment intent creation accepts `card_token` only (hosted form path) или passes raw card data straight to Vault для tokenization (s2s path, не stored).
- Hosted payment form serves on Vault subdomain (`pay.miniefin.local`) с CSP that prevents data leak.

### 7.6 Secrets management

- Local dev: `.env` files per service, gitignored. Example `.env.example` checked in.
- Vault adapter master encryption key in env var.
- Service-to-service shared secrets in env vars.
- API keys для vendors (Sumsub, OpenSanctions, Stripe, Keycloak admin) in env vars.
- Production-grade secret store (Doppler / Vault by HashiCorp / SSM Parameter Store): later scope.

---

## 8. Cross-Cutting Infrastructure

### 8.1 Audit log

- Central audit log в `platform.audit.audit_log` schema. Append-only enforced via DB trigger + restricted grants.
- **Write-audit (regular operations)**: services publish audit events via Kafka topic `audit.event.v1`; platform consumer writes them. Eventually consistent (~seconds), acceptable для regular state changes.
- **Read-audit (compliance-sensitive entities)** — sanctions hits, AML alerts (HIGH/CRITICAL), frozen accounts review, SAR-marked cases (later scope), full PAN reveal — uses **sync HTTP** (`POST /internal/audit/write`). Sync transport guarantees audit entry persisted before sensitive data возвращается caller'у — это и есть основа tipping-off discipline и regulator-audit traceability. Middleware на compliance endpoints emits sync audit before response.
- Hybrid pattern explicit: sync для guarantees (read-audit + critical state changes), async для throughput (regular write-audit).

### 8.2 Observability stack

```
Each service ──► OpenTelemetry SDK ──► OTel Collector ──┬──► Prometheus (metrics)
                                                         ├──► Tempo (traces)
                                                         └──► Loki via Vector (logs)
                                                                    │
                                                              Grafana (visualization)
```

- Every service emits structured JSON logs (stdout) → Vector tail/sidecar → Loki.
- Every service exposes `/metrics` endpoint (Prometheus scrape).
- Trace context propagated через HTTP headers (W3C TraceContext) и Kafka headers (W3C TraceContext via OTel).
- Critical dashboards в Grafana: payment success rate, KYC throughput, AML alert rate, chargeback rate, webhook delivery success, outbox lag, vault detokenize rate.
- Alert rules — later scope (MVP: visual dashboards only).

### 8.3 Idempotency store

Centralized в `platform.idempotency.idempotency_keys`:
```
key VARCHAR (Idempotency-Key from header)
merchant_id BIGINT (scope)
endpoint VARCHAR
request_fingerprint VARCHAR (sha256 of body)
response_body JSONB
response_status_code INT
created_at TIMESTAMPTZ (expiry 24h)
```

Acquirer и Platform middleware check idempotency on every write endpoint. Repeated request same key + same fingerprint → cached response. Same key + different fingerprint → 409.

### 8.4 Outbound webhook delivery

Owned by Acquirer service. Acquirer subscribes к relevant Kafka topics (payment_intent state changes, chargeback events, payout events), constructs webhook payload, signs, dispatches.

- Retry policy: exponential backoff with jitter (1m, 5m, 30m, 2h, 12h, 24h, 48h, 72h); max 8 attempts.
- DLQ stored в `acquirer.webhook.dlq` table. Merchant dashboard exposes DLQ list with replay button.
- Webhook signing: HMAC SHA-256 with per-merchant secret, signature in header `X-Webhook-Signature: t=<unix_ts>,v1=<hex>`.

### 8.5 Rate limiting

- Per-merchant API rate limits in Acquirer service (sliding window in Redis или in-Postgres counter — TBD в 05).
- Per-IP login rate limit в Platform identity middleware (login attempts, password reset).
- Vault detokenize rate limit per card per user (CRD-FR-06).

### 8.6 Background jobs / schedulers

- **Clearing batch job** — daily в Network service. Aggregates captures, computes clearing.
- **Settlement batch job** — daily в Network service. Computes settlement positions, dispatches к Acquirer.
- **OFAC SDN refresh** — daily в Platform sanctions context (downloads OFAC, refreshes cache, но OpenSanctions API replaces direct OFAC ingest — оставлено как fallback).
- **Outbox dispatcher** — continuous в each service.
- **Hold expiration** — every 5 min в Issuer service.
- **Reconciliation script** — manual / on-demand; verifies ledger invariants.

Schedule mechanism: либо cron container, либо in-process scheduled tasks (depends на 05 framework choice).

---

## 9. External Integrations

### 9.1 Sumsub (KYC)

Pattern: **port + adapter**.
- Domain port `KycVerificationProvider`: `create_applicant(...)`, `get_verification_status(...)`, `handle_verdict_webhook(...)`.
- Adapter implementation: `SumsubAdapter` в `platform/adapters/sumsub/`.
- Inbound webhook endpoint: `/webhooks/sumsub/v1` validates HMAC, deserializes, transforms к domain event, posts to Kafka `kyc.verdict_received.v1`.
- KYC domain consumer reads event, updates `kyc_case` state machine.

### 9.2 OpenSanctions

Pattern: port + adapter, sync call.
- `SanctionsScreeningProvider`: `match(name, dob, country)`.
- `OpenSanctionsAdapter` calls API, caches recent matches в `sanctions.opensanctions_cache` for performance.
- Sync timeout 3s; on timeout — fail-closed (sanctions hit case opened с reason `SCREENING_UNAVAILABLE`).

### 9.3 Stripe Connect

Pattern: port + adapter.
- `MerchantOnboardingProvider`: `create_account(...)`, `create_account_link(...)`.
- `MerchantPayoutProvider`: `initiate_payout(...)`.
- `StripeConnectAdapter` calls Stripe API, persists `stripe_account_links`.
- Inbound webhook endpoint: `/webhooks/stripe/v1` validates Stripe signature, posts events to Kafka.

### 9.4 Keycloak (OIDC for backoffice)

Pattern: standard OIDC validator.
- Backoffice SPA does OIDC code flow with Keycloak.
- Platform validates JWTs against Keycloak JWKS (cached).
- User mirroring в `identity.backoffice_users` on login (id, email, roles).
- Role mapping: Keycloak roles (`operator`, `compliance_officer`, `senior_compliance`) → platform RBAC roles.

### 9.5 MinIO (S3-compatible)

Pattern: thin adapter over standard S3 SDK.
- Buckets: `kyc-documents`, `chargeback-evidence`, `sof-documents`, `sar-pdfs` (later scope).
- Access via presigned URLs для downloads.
- KYC document access ALSO behind application-layer rate limit (КYC-FR-08).

### 9.6 Adapter discipline (ports and adapters)

All external integration code лежит ONLY в `adapters/<vendor>/`. Domain code reaches vendors через interfaces defined в `domains/<x>/ports/`. This makes:
- Easy to swap vendor (e.g., replace Sumsub with Onfido — only adapter changes).
- Easy to unit-test domain (mock adapter port).
- No vendor types leak в domain code.

---

## 10. Failure Modes and Resilience

### 10.1 Fail-closed vs fail-open

| Failure | Mode | Reason |
|---|---|---|
| Vault unreachable | fail-closed | No card data → no auth possible; PAN safety paramount |
| Sumsub unreachable | fail-closed | KYC verdict not received; user stays IN_REVIEW |
| OpenSanctions unreachable | fail-closed | Sanctions check incomplete → block dependent action, open case |
| Stripe Connect unreachable | fail-closed (for onboarding); retry-with-backoff (for payouts) | KYB cannot complete без Stripe; payouts can retry |
| Kafka unreachable | degraded (outbox writes still happen) | Eventual delivery once Kafka recovers; sync paths unaffected |
| Postgres unreachable | hard fail (service down) | DB is core dependency |
| Issuer unreachable from Acquirer | sync auth fails | Surfaced к merchant as `AUTH_TIMEOUT` |
| Network unreachable from Acquirer | sync auth fails | Same |

### 10.2 Retry policies

- **Outbound vendor calls** (Sumsub/Stripe write ops): retry 3x с exponential backoff (1s, 5s, 15s); permanent failure → operational alert (Grafana annotation в MVP, real PagerDuty в production).
- **Outbound webhooks к merchants**: retry policy в §8.4 (8 attempts over 3 days).
- **Inter-service auth path** (sync HTTP): no retry inside request (would compound latency); fail-fast, merchant retries idempotently.
- **Async event consumers**: retry 3x with backoff; on permanent failure → DLQ for that consumer.

### 10.3 DLQ ownership

- Webhook delivery DLQ — Acquirer service (`acquirer.webhook.dlq`).
- Per-consumer DLQ — owned by service hosting the consumer (e.g., AML consumer DLQ в platform).
- Vendor webhook receipt DLQ — platform (`platform.webhook_inbound.dlq`).
- DLQ retention: 30 days.
- DLQ replay: manual (operator action) or scripted via verification tooling.

### 10.4 Timeout boundaries

| Call type | Timeout |
|---|---|
| Inter-service auth (acquirer/network/issuer) | 5s |
| Platform ledger sync calls | 2s |
| Vault detokenize | 2s |
| Sumsub API | 10s |
| OpenSanctions API | 3s |
| Stripe API | 15s |
| Outbound webhook | 30s |
| Kafka producer ack | 5s |

### 10.5 Circuit breakers

Not implemented в MVP. Pattern documented для later scope.

### 10.6 Reconciliation

- **Ledger invariants**: retained script (`product/scripts/runtime/reg_ledger_invariants.sh`) verifies `Σ debits == Σ credits` global + per-account derivation correctness. Manually triggered.
- **Vendor state reconciliation**: scripts comparing platform state vs vendor state (e.g., `reg_sumsub_state_reconcile.sh`, `reg_stripe_account_reconcile.sh`).
- **Inter-service consistency**: `reg_ledger_vs_acquirer_settlement.sh` compares platform ledger merchant settlement balance vs acquirer service's local projection.

---

## 11. Local Development & Deployment

### 11.1 Docker Compose layout

Single `docker-compose.yml` в `product/deploy/` (root of product folder), starts весь stack. Optional override files (`docker-compose.observability.yml`, `docker-compose.minimal.yml` для slim setup).

### 11.2 Secrets handling

- `.env.example` checked in — describes required vars без values.
- Local `.env` gitignored, copied from example, filled с vendor sandbox API keys.
- Each service reads own `.env` (либо combined root `.env` with namespacing).

### 11.3 Service startup order

Compose `depends_on` chain:
1. Postgres instances + Kafka + MinIO + Keycloak start first
2. Vault starts (depends on its Postgres)
3. Platform starts (depends on its Postgres, Kafka, MinIO, Keycloak readiness)
4. Issuer / Network / Acquirer start (depend on Platform, Kafka, own Postgres; Issuer also depends on Vault)
5. Traefik starts last
6. SPA containers start (depend on Traefik so DNS resolves)

Healthchecks via Docker healthcheck directive.

### 11.4 Per-service process structure

Каждый application service (platform / acquirer / network / issuer) запускается **multi-process** под одним deployment unit (один Docker image, multiple `command` overrides в Compose):

- `web` process — HTTP server (inbound API).
- `worker` process — Kafka consumers (per consumer group в составе сервиса).
- `scheduler` process — background jobs (clearing batch, hold expiration, OFAC refresh, outbox dispatcher).

Это industry-standard pattern для Rails / Django / Spring / NestJS monoliths (heroku-style `Procfile`). Shared codebase, independent scaling, изолированные crash boundaries (worker fail не валит web). Vault — single-process (только HTTP).

### 11.5 Reset / seed

- Reset script `reg_reset_dev.sh` — drops all DBs, recreates schemas, runs migrations.
- Seed script `reg_seed_dev.sh` — creates: 3 test end users (one KYC approved, one in review, one rejected), 2 test merchants (one onboarded, one pending KYB), cards for approved user, sample payment intent.

---

## 12. Out of Scope (for 04)

Эти решения откладываются явно:

- Backend programming language / framework selection — в **05**.
- Frontend framework + monorepo tooling — в **05**.
- Specific DB migration tool — в **05**.
- Specific service-to-service token rotation mechanism — в **05** / design-details.
- Specific Traefik configuration syntax — в **05** / `product/deploy/`.
- Specific Kafka topic schemas (JSON shape, Avro/Protobuf decision) — в **design-details**.
- Cloud deployment topology (k8s manifests, Terraform) — в **later scope** (MVP — single-machine docker-compose).
- Alert rules в Grafana / Prometheus — в **later scope** (MVP — dashboards only).
- Secret rotation automation — в **later scope**.
- Circuit breaker implementation — в **later scope**.
- Multi-region replication — в **later scope**.

---

## v0.2 Resolution Notes

Open questions из v0.1 разрешены (project owner confirmed 2026-05-15):

1. **Ledger HTTP latency** — accepted для MVP. Issuer ↔ Platform Ledger sync HTTP calls на authorization path (~10-30ms each). Combined Issuer auth path target P95 ≤ 50ms. Local ledger projection в Issuer — later scope, если P95 не достигается реальным measurement.
2. **Acquirer merchant settlement** — local projection в Acquirer (own `merchant_settlement` schema), Kafka events propagate ledger changes (eventual consistency ~seconds). Reconciliation script `reg_ledger_vs_acquirer_settlement.sh` verifies projection match (§10.6).
3. **Read-audit transport** — hybrid: sync HTTP `POST /internal/audit/write` для compliance-sensitive read access (sanctions hits, AML HIGH/CRITICAL, frozen accounts, full PAN reveal) — даёт strong guarantee для tipping-off discipline; async Kafka для regular write-audit (eventually consistent acceptable). Reflected в §8.1.
4. **Frontend serving** — three separate nginx-static containers per SPA, Traefik routes к ним. Independent health checks, per-SPA cache control, realistic production setup. Reflected в §2.3.
5. **Internal API namespace** — `/internal/<context>/<operation>` convention across all services. Discoverable, simplifies tracing, easier eventual gRPC migration. Reflected в §4.3.
6. **Kafka payload format** — JSON в MVP с strict versioning (`<topic>.v1` → `.v2` при breaking changes). Schema Registry (Confluent / Apicurio) + Avro / Protobuf — later scope. Reflected в §5.2.
7. **Service token rotation** — long-lived shared HMAC secrets per service-pair в env vars; manual rotation. Automated rotation — later scope. Reflected в §5.6.
8. **Idempotency TTL** — 24h, matches Stripe industry default. Reflected в §8.3.
9. **Public API host** — `api.miniefin.local` (Stripe-style); separate from `merchants.miniefin.local` (dashboard host) для clean CORS / rate-limit isolation. Reflected в §2.3, §2.5.
10. **Monolith process structure** — multi-process per deployment unit (web + worker + scheduler), one Docker image, different `command` overrides. Industry-standard Procfile pattern. Reflected в §11.4.
