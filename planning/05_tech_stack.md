# 05 Tech Stack — Mini Fintech Platform

Status: **APPROVED v0.4** (approved by project owner 2026-05-15).

History:
- v0.1 — first stack draft на основе APPROVED `01_business_requirements.md` v0.3, `02_user_journeys.md` v0.2, `03_functional_requirements.md` v0.2, `04_architecture.md` v0.2. Includes recommended stack, version baseline, build/buy/internal-simulate matrix, and open questions for owner review.
- v0.2 — backend recommendation changed from Node.js/NestJS to **Kotlin + Spring Boot** after owner challenged fintech stack realism. Java + Spring Boot kept as conservative alternative; JVM stack now drives DB, Kafka, build and test tooling.
- v0.3 — owner accepted jOOQ/JdbcClient/raw SQL for DB layer. Tooling line changed from latest JVM line to majority-existing-projects line: **Java 21 LTS + Spring Boot 3.5.x + Gradle 8.x**. LocalStack added to object-storage discussion as AWS emulator, but not default runtime object storage.
- v0.4 — remaining open questions resolved: SeaweedFS S3 API accepted as local object storage default, LocalStack optional for AWS-emulation tests, majority-existing-projects tooling line accepted.

---

## 1. Stack Decision Principles

Stack выбирается не как “самый модный”, а как обучающий и production-shaped набор инструментов для 8-12 месяцев single-developer work.

Критерии:
- **Domain correctness over speed** — ledger, audit, outbox, idempotency, RBAC и state machines должны быть явными.
- **Fintech-aligned backend stack** — backend should resemble stacks commonly used for payments/banking systems, not only optimize for single-language convenience.
- **Explicit SQL where money moves** — ledger and audit требуют прозрачных constraints, transactions, `FOR UPDATE`, `SKIP LOCKED`, triggers.
- **Local-first but production-shaped** — Docker Compose, real Postgres/Kafka/OIDC/object storage/observability, no in-memory substitutes.
- **Stable major lines** — использовать supported/LTS линии, pin exact patch versions in lockfiles/container tags at bootstrap.

## 2. Executive Recommendation

Recommended baseline:

| Area | Choice | Why |
|---|---|---|
| Backend language | **Kotlin on Java 21 LTS** | JVM backend is more industry-aligned for fintech; Java 21 is the widely adopted LTS baseline, Kotlin reduces Java boilerplate while staying in the Spring ecosystem. |
| Backend framework | **Spring Boot 3.5.x** | Mature transaction, security, Kafka, observability and operational ecosystem; closer to the majority of existing Spring projects than fresh Spring Boot 4.x. |
| DB access | **jOOQ + Spring JDBC/JdbcClient + raw SQL where needed** | Type-safe SQL for most queries, explicit SQL for ledger/audit/outbox invariants; avoids ORM-first mutation model for money movement. |
| DB migrations | **Flyway SQL migrations per service** | Language-independent, boring, reliable; keeps migrations close to service DB ownership and supports raw PostgreSQL features. |
| Frontend | **React 19 + Vite 8** | Mature SPA ecosystem; good fit for three operational UIs; avoids SSR complexity. |
| Monorepo | **Gradle multi-project for backend + pnpm workspace for SPAs** | JVM backend gets first-class build/test dependency management; frontend remains standard TypeScript tooling. |
| Kafka client | **Spring for Apache Kafka** | Standard Spring integration over Apache Kafka clients; fits transactions, consumers, error handling and observability. |
| OIDC | **Self-hosted Keycloak 26.x** | Matches approved self-hosted OIDC direction; real OIDC provider without SaaS dependency. |
| Edge | **Traefik 3.6 + nginx static containers** | Matches 04; Traefik routes by host/path, nginx serves each SPA independently. |
| Observability | **OpenTelemetry Java agent/SDK + Micrometer + Prometheus + Grafana + Tempo + Loki + Vector** | Matches 04; Spring Actuator/Micrometer is a strong production default. |
| Local object storage | **SeaweedFS S3 API, not MinIO by default** | Keeps S3-compatible local behavior, but avoids MinIO upstream archival risk discovered during 05 drafting. |

## 3. Backend Stack

### 3.1 Runtime and language

Recommended:
- **Java 21 LTS** as runtime for all backend application services.
- **Kotlin 2.3.x** as backend language, unless Spring Boot 3.5.x dependency management strongly favors a 2.2.x patch line during bootstrap.
- Compile Kotlin JVM target for Java 21.

Rationale:
- Java/Spring is the default mental model in many banking, payments, risk and compliance teams.
- Kotlin gives null-safety, data classes, sealed interfaces and expressive state-machine modeling while staying interoperable with Java libraries.
- Java 21 LTS is the better "what most projects are actually running" baseline than Java 25 LTS in 2026.
- Spring Boot 3.5.x supports Java 17 through Java 25, so Java 21 is comfortably inside the supported range.
- Kotlin 2.3.x is stable, but exact Kotlin patch should follow Spring Boot plugin/dependency compatibility at bootstrap.

### 3.2 Framework

Recommended:
- **Spring Boot 3.5.x** for `platform`, `acquirer`, `network`, `issuer`, `vault`.
- Spring MVC servlet stack as default, not WebFlux, unless a specific slice proves reactive IO is needed.
- Same Docker image per service with multiple process commands where needed: `web`, `worker`, `scheduler`.

Rationale:
- Spring Boot is the most recognizable production backend stack for conservative fintech systems.
- Spring transactions, Spring Security, Spring Kafka, Actuator, Micrometer and Testcontainers support match this project's needs directly.
- Kotlin + Spring lets the code model domain state clearly without giving up the enterprise ecosystem.

Rules:
- Do not turn the domain into an anemic JPA entity graph. Money-moving behavior lives in application/domain services.
- Controllers only handle HTTP/session/API concerns and call application services.
- Use Spring integration where it adds real operational value; keep domain code framework-light.

### 3.3 DB access

Recommended:
- **jOOQ OSS** for typed SQL where generated schema models are useful.
- **Spring JDBC / JdbcClient** for explicit SQL paths.
- **Raw SQL** for ledger inserts, audit triggers, append-only grants, advisory locks, outbox locking, and DB-level constraints.

Not recommended:
- JPA/Hibernate as primary persistence layer for ledger/audit/outbox. It is useful for simple aggregates, but entity mutation and persistence-context behavior are the wrong default for append-only money movement.
- Exposed as primary DB layer. It is Kotlin-native, but jOOQ has stronger SQL coverage and more mature schema-generated query modeling for this project.

Allowed:
- JPA can be used selectively for low-risk CRUD modules only if it reduces obvious boilerplate and does not cross into ledger/audit/card-data paths.

### 3.4 Migrations

Recommended:
- **Flyway** per service.
- One migration directory per service:

```text
product/apps/platform/migrations/
product/apps/acquirer/migrations/
product/apps/network/migrations/
product/apps/issuer/migrations/
product/apps/vault/migrations/
```

Migration rules:
- Migrations are SQL-first.
- No application startup auto-DDL.
- Migration filenames use Flyway versioning: `V001__create_identity_schema.sql`.
- Repeatable migrations only for views/functions where useful: `R__ledger_balance_views.sql`.
- Destructive migrations require explicit review and ADR if they touch money/audit/card data.

### 3.5 Validation and API contracts

Recommended:
- Kotlin request/response DTOs with Bean Validation / Jakarta Validation.
- OpenAPI generated from Spring controllers for public and SPA-facing HTTP APIs.
- Kafka event DTOs in backend shared contract modules, serialized as JSON.
- Zod remains frontend-side validation only where useful.

Pattern:
- Public API request/response DTOs live in `backend/libs/contracts-public`.
- Internal service DTOs live in `backend/libs/contracts-internal`.
- Kafka event DTOs live in `backend/libs/contracts-events`.
- Domain types may import schemas only at boundaries; domain core should not depend on HTTP/controller types.

### 3.6 Security libraries

Recommended:
- Password hashing: **Argon2id** through Spring Security-compatible password encoder or vetted JVM binding.
- Tokens/signatures: **Spring Security OAuth2 Resource Server + Nimbus JOSE JWT** for JWT/JWKS/OIDC/service token validation.
- API/webhook signatures: Java crypto HMAC SHA-256 with fixed serialization rules.
- Sessions: opaque random tokens stored server-side in Postgres; cookie is `HttpOnly`, `Secure`, `SameSite=Strict`.
- CSRF: Spring Security CSRF for cookie-auth write endpoints, with explicit SPA integration.

### 3.7 Background jobs

Recommended:
- Separate Spring Boot process commands/profiles:
  - `web` — HTTP server.
  - `worker` — Kafka consumers and outbox dispatchers.
  - `scheduler` — clearing, settlement, hold expiry, periodic reconciliation triggers.
- Postgres advisory locks for singleton scheduled jobs.
- `SELECT ... FOR UPDATE SKIP LOCKED` for outbox worker leasing.

Not in MVP:
- Redis as required baseline. Add later only if Postgres-based queues become a bottleneck or delayed jobs become hard to maintain.

## 4. Frontend Stack

### 4.1 Framework and build

Recommended:
- **React 19**.
- **Vite 8**.
- Three separate SPAs:
  - `apps/spa-enduser`
  - `apps/spa-merchant`
  - `apps/spa-backoffice`

Rationale:
- React has the broadest ecosystem for operational UIs.
- Vite is the simplest mature SPA build baseline.
- SSR/Next.js is unnecessary for this product; these are authenticated workflow apps, not content/SEO pages.

### 4.2 Frontend libraries

Recommended:
- Routing: **TanStack Router**.
- Server state: **TanStack Query**.
- Tables: **TanStack Table**.
- Forms: **React Hook Form + Zod resolver**.
- UI primitives: **Radix UI**.
- Styling: **Tailwind CSS** with a shared design tokens package.

Rules:
- Build three real workflow apps, not a landing page.
- Shared UI components live in `frontend/packages/ui`.
- Shared API clients live in `frontend/packages/api-client`.
- Frontend authorization checks are UX only; server remains source of truth.

### 4.3 Backoffice UI decision

Recommended:
- **Build native backoffice SPA** rather than Retool/Appsmith/Forest Admin.

Rationale:
- The project goal is to learn case management, RBAC, audit, two-eyes review, sanctions/AML queues and chargeback arbitration.
- A generic admin builder would hide the most valuable workflow design and authorization work.

## 5. Monorepo and Repository Layout

Recommended under `product/`:

```text
product/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
├── package.json
├── pnpm-workspace.yaml
├── apps/
│   ├── platform/
│   ├── acquirer/
│   ├── network/
│   ├── issuer/
│   ├── vault/
│   ├── spa-enduser/
│   ├── spa-merchant/
│   └── spa-backoffice/
├── backend/
│   └── libs/
│       ├── contracts-public/
│       ├── contracts-internal/
│       ├── contracts-events/
│       ├── db/
│       ├── observability/
│       └── service-auth/
├── frontend/
│   └── packages/
│       ├── ui/
│       └── api-client/
├── deploy/
│   ├── docker-compose.yml
│   ├── docker-compose.observability.yml
│   └── traefik/
└── scripts/
    └── runtime/
```

Tooling:
- **Gradle 8.x** with Kotlin DSL for backend multi-project builds.
- **pnpm 10.x** initially for frontend workspaces. `pnpm 11` is recent enough that first bootstrap should stay on the mature 10.x line unless owner chooses otherwise.
- ESLint flat config + Prettier for frontend packages.
- JUnit 5 + AssertJ/Kotest-style assertions for backend tests.
- Vitest for frontend packages.
- Backend integration tests use real Postgres/Kafka containers, not mocks, once product code starts.

## 6. Infrastructure Versions

This v0.3 pins major/minor baselines. Exact patch tags should be locked during `product/` bootstrap and refreshed before the first runtime evidence pass.

| Component | Baseline | Notes |
|---|---:|---|
| Java | `21 LTS` | Runtime baseline for backend services; closer to the majority of existing projects than Java 25. |
| Kotlin | `2.3.x` preferred, Spring-compatible patch pinned at bootstrap | Avoid Kotlin EAP/Beta for bootstrap. |
| Spring Boot | `3.5.x` | Widely deployed current-generation Spring Boot line; avoid fresh 4.x migration churn. |
| Gradle | `8.x` | Kotlin DSL multi-project backend build; choose mature 8.x wrapper rather than early 9.x unless required. |
| Node.js | `24.x LTS` | Used for frontend tooling and runtime verification scripts, not backend services. |
| TypeScript | `6.0.x` | Frontend and optional script/compiler line; do not start on TypeScript 7 beta. |
| React | `19.x` | SPA-only, no React Server Components in MVP. |
| Vite | `8.x` | Supported current major. |
| PostgreSQL | `18.x` | Use latest supported major; extensions include `pgcrypto`. |
| Apache Kafka | `4.2.x` or latest stable KRaft image at bootstrap | Single-node KRaft in local Compose. |
| Keycloak | `26.6.x` | Self-hosted for backoffice OIDC. |
| Traefik | `3.6.x` | Latest active-support minor from Traefik docs at drafting time. |
| nginx | stable official image | Used only for static SPA serving. |
| Grafana | `12.4.x` or `13.0.x` | Conservative default: 12.4.x; 13.0.x acceptable after smoke test. |
| Prometheus | `3.x` | Pin exact patch at bootstrap. |
| Tempo | `2.10.x` | Local single-binary mode. |
| Loki | `3.7.x` preferred if image/docs stable; `3.6.x` acceptable | Pin exact patch after Compose test. |
| OpenTelemetry Collector | current stable contrib image | Use contrib image for common receivers/exporters. |
| Vector | `0.55.x` | Logs container stdout → Loki. |
| Object storage | SeaweedFS S3 API | Replaces MinIO default unless owner rejects. |

Version notes verified during drafting:
- Spring Boot 3.5 system requirements list Java 17-25 support, so Java 21 LTS is supported.
- Spring Boot 4.0.6 is current, but this project intentionally does not start there because most existing Spring projects are still on the 3.x generation.
- Kotlin official docs list 2.3.21 as latest stable and Kotlin 2.3.0 as supporting Java 25.
- Java 21 and Java 25 are both LTS release lines; Java 21 is the more established production baseline.
- Node.js official releases page lists Node 24 as LTS and Node 26 as Current on 2026-05-15; Node remains frontend/script tooling only.
- PostgreSQL official site lists 18.4 as latest supported major-line release on 2026-05-14.
- Vite docs list `vite@8.0` as current supported line.
- Apache downloads list Kafka 4.2.0 among current release directories.
- Traefik docs list 3.6 as active support.
- OpenTelemetry Java instrumentation + Micrometer/Spring Actuator are the backend telemetry baseline; app logs remain structured JSON shipped by Vector, not OTel Logs as source of truth.
- MinIO official GitHub repository is archived/read-only as of 2026-04-25; this draft recommends SeaweedFS to keep the S3-compatible local requirement without taking MinIO maintenance risk.

## 7. Communication and Serialization

### 7.1 HTTP

Recommended:
- HTTP/JSON for public, SPA and internal service APIs.
- Spring MVC controllers for all backend services.
- Internal APIs under `/internal/<context>/<operation>` as approved in 04.
- OpenAPI docs for public Payments API and SPA-facing APIs.

### 7.2 Kafka

Recommended:
- Apache Kafka in KRaft mode.
- Client: Spring for Apache Kafka over Apache Kafka client libraries.
- Payloads: JSON with strict topic versioning, as approved in 04.
- No Schema Registry in MVP.

Topic rules:
- Topic name format: `<domain>.<event_name>.v<version>`.
- Partition key by aggregate/entity ID.
- Each consumer persists processed event IDs for idempotency.
- Event DTOs live in `backend/libs/contracts-events`.

## 8. Data Storage Choices

### 8.1 PostgreSQL

Recommended:
- PostgreSQL 18 for all service DBs, plus dedicated Keycloak Postgres.
- `NUMERIC(20,4)` for money.
- `TIMESTAMPTZ` for timestamps.
- `JSONB` only for provider payload snapshots, webhook raw payloads, metadata and audit snapshots; not for primary domain state where relational schema is clearer.
- `pgcrypto` for vault PAN column encryption.

### 8.2 Object storage

Recommended:
- Use **SeaweedFS S3 API** in local Compose.
- Keep adapter name generic: `ObjectStoragePort`, not `MinioPort` or `SeaweedPort`.
- Use AWS SDK for Java S3 client against S3-compatible endpoint.

Buckets:
- `kyc-documents`
- `chargeback-evidence`
- `sof-documents`
- `sar-pdfs` later scope only

Rationale:
- The project needs S3 semantics: upload, download, object metadata, presigned URLs, bucket separation.
- It does not need MinIO-specific APIs.
- MinIO was a reasonable earlier default, but current upstream status makes it a poor new baseline.
- LocalStack is useful when testing a broader AWS surface, but for this project it would be an AWS emulator used only for S3. That adds weight and auth/licensing/setup concerns without much benefit over a simple S3-compatible object store.

## 9. External Vendor Integrations

| Capability | Provider / Tool | Mode | Adapter owner |
|---|---|---|---|
| KYC | Sumsub sandbox | Real vendor sandbox | `platform/adapters/sumsub` |
| Sanctions | OpenSanctions API | Real external API | `platform/adapters/opensanctions` |
| Merchant onboarding / payout | Stripe Connect test mode | Real vendor sandbox | `platform/adapters/stripe_connect` |
| Backoffice OIDC | Keycloak self-hosted | Real OIDC provider | `platform/adapters/keycloak` |
| Object storage | SeaweedFS S3 locally, AWS S3-compatible later; LocalStack optional for AWS-emulation tests | Real S3-compatible API | `platform/adapters/object_storage` |
| Email | Local Mailpit in dev; real SMTP later | Local dev first | `platform/adapters/email` |

Email note:
- Password reset and verification need email mechanics.
- MVP local development should use **Mailpit** container.
- Real SMTP provider remains later scope unless needed for cloud demo.

## 10. Build / Buy / Internally Simulate Matrix

This matrix is the explicit stack-level expression of the project framing from 01.

| Capability | Decision | Implementation / Provider | Why |
|---|---|---|---|
| Ledger | **Build** | Platform service, PostgreSQL, jOOQ/JdbcClient/raw SQL | Core fintech skill; must own invariants and postings. |
| Wallet | **Build** | Platform wallet domain | Core domain, manual banking rails substitute. |
| Audit log | **Build** | Platform audit schema + sync/async writers | Core compliance pattern. |
| Case management | **Build** | Platform cases domain | Central workflow abstraction for KYC/AML/sanctions/chargebacks. |
| AML rules | **Build** | Hand-coded rules + rule provider interface | Learning value; 3 simple production rules. |
| Idempotency | **Build** | Postgres-backed middleware | Core payments API pattern. |
| Transactional outbox | **Build** | Per-service outbox tables + workers | Core reliability pattern. |
| Public Payments API | **Build** | Acquirer service | Core merchant acquiring surface. |
| Webhook delivery | **Build** | Acquirer worker + DLQ | Core platform pattern. |
| Three UIs | **Build** | React/Vite SPAs | User journey and workflow learning value. |
| KYC provider | **Integrate** | Sumsub sandbox | Industry buys this; real sandbox keeps realism. |
| Sanctions feed | **Integrate** | OpenSanctions API | Industry often buys/uses external lists; real API. |
| Merchant KYB helper | **Integrate** | Stripe Connect sandbox | Industry buys onboarding/payout rails; test mode is realistic. |
| OIDC provider | **Integrate** | Keycloak self-hosted | Do not build SSO provider. |
| Object storage | **Integrate** | S3-compatible SeaweedFS locally | Do not build object storage. |
| Observability | **Integrate** | OTel + Prometheus/Grafana/Tempo/Loki/Vector | Industry uses OSS/managed observability; do not build telemetry platform. |
| DB migrations | **Integrate** | Flyway | Commodity tool; do not build migration framework. |
| Card network | **Internally simulate** | Issuer + Network + Acquirer + Vault services | Real network requires licenses/relationships; simulation teaches domain. |
| Cardholder bank approval | **Internally simulate** | Issuer authorization domain | Banking rails substitute. |
| Clearing/settlement messaging | **Internally simulate** | Network service + Kafka | Network rails substitute. |
| Merchant settlement bank outflow | **Manual / sandbox integrate** | Manual operator or Stripe Connect test payout | Real money movement is out of scope. |
| End-user deposit/withdraw rails | **Manual** | Backoffice operations | Real banking rails require partner bank/license. |

## 11. Source Tree Ownership

Recommended app ownership:

| App/package | Owns |
|---|---|
| `apps/platform` | Identity, KYC, Sanctions, AML, Cases, Ledger, Wallet, Merchant onboarding, Audit. |
| `apps/acquirer` | Public Payments API, payment intents, merchant settlement projection, webhook delivery. |
| `apps/network` | BIN routing, clearing, settlement positions. |
| `apps/issuer` | Cards, authorization, holds, chargeback initiation. |
| `apps/vault` | Tokenization, detokenize, encrypted PAN storage. |
| `apps/spa-enduser` | End-user wallet/card/transfer/dispute UI. |
| `apps/spa-merchant` | Merchant dashboard, payments, refunds, webhooks, disputes, settlements. |
| `apps/spa-backoffice` | Operator/compliance workflows. |
| `backend/libs/contracts-*` | API/internal/event DTOs and generated OpenAPI artifacts. |
| `backend/libs/service-auth` | Service token creation/validation. |
| `backend/libs/observability` | OpenTelemetry/Micrometer/log masking helpers. |
| `backend/libs/db` | Shared DB helpers, jOOQ generation conventions, not shared domain repositories. |
| `frontend/packages/ui` | Shared visual primitives for SPAs. |
| `frontend/packages/api-client` | Generated/typed HTTP clients for SPAs. |

Rule:
- Shared libs may contain infrastructure helpers and boundary contracts.
- Shared libs must not become a hidden shared monolith for business logic across services.

## 12. Testing and Runtime Verification Tooling

Recommended test layers:
- Backend unit tests: JUnit 5 + AssertJ or Kotest assertions.
- Backend integration tests: Spring Boot Test + Testcontainers for Postgres/Kafka/SeaweedFS where needed.
- Frontend unit tests: Vitest.
- API smoke checks: retained scripts under `product/scripts/runtime/reg_*.mjs` or JVM-based `reg_*.kts`; choose per slice for fastest reliable evidence.
- Browser UI checks: Playwright only when SPAs exist and a slice needs UI evidence.
- Ledger reconciliation: retained SQL/JVM/Node script linked to runtime checklist.

Runtime evidence rules:
- No check is counted done until recorded in `planning/runtime_evidence_log.md`.
- Product code cannot start before `06_implementation_guide.md`, design-details, runtime checklists, and first slice planning note.

## 13. Decisions Closed By This Draft

These are recommended for approval unless owner changes them in review:

1. Backend stack: Kotlin + Java 21 LTS + Spring Boot 3.5.x.
2. DB access: jOOQ + Spring JDBC/JdbcClient + raw SQL, not JPA as primary layer for ledger/audit/outbox. Owner accepted this decision.
3. Migrations: Flyway SQL migrations per service.
4. Frontend: React 19 + Vite 8, three separate SPAs.
5. Monorepo: Gradle multi-project for backend + pnpm workspace for frontend.
6. Kafka client: Spring for Apache Kafka.
7. Backoffice UI: native React SPA, not Retool/Appsmith/Forest Admin.
8. OIDC: Keycloak self-hosted.
9. Observability: OTel Java + Micrometer + Prometheus/Grafana/Tempo/Loki + Vector.
10. Object storage recommendation: SeaweedFS S3 API instead of MinIO due current upstream status.

## Source Notes

Current-version checks used official/primary sources where possible:
- Spring Boot docs: `https://docs.spring.io/spring-boot/system-requirements.html`
- Spring Boot 3.5 system requirements: `https://docs.spring.io/spring-boot/3.5/system-requirements.html`
- Kotlin releases: `https://kotlinlang.org/docs/releases.html`
- Java releases: `https://www.oracle.com/java/technologies/java-se-support-roadmap.html`
- Node.js releases: `https://nodejs.org/en/about/previous-releases`
- PostgreSQL releases: `https://www.postgresql.org/`
- Vite releases: `https://vite.dev/releases`
- jOOQ releases/docs: `https://www.jooq.org/download/`
- Gradle releases/docs: `https://gradle.org/releases/`
- Apache Kafka downloads: `https://downloads.apache.org/kafka/`
- Traefik releases: `https://doc.traefik.io/traefik/deprecation/releases/`
- OpenTelemetry Java docs: `https://opentelemetry.io/docs/zero-code/java/agent/`
- LocalStack docs: `https://docs.localstack.cloud/aws/getting-started/installation/`
- MinIO repository status: `https://github.com/minio/minio`
- SeaweedFS S3 quickstart: `https://github.com/seaweedfs/seaweedfs`

## v0.2 Resolution Notes

1. **Backend stack** — owner challenged Node.js as insufficiently fintech-aligned; recommendation changed to **Kotlin + Spring Boot**. Original Node/NestJS recommendation was chosen for one-language single-dev convenience, but JVM/Spring better serves the project's stated learning goal: realistic fintech/backend architecture.
2. **Java vs Kotlin** — Kotlin chosen over Java because it keeps the same Spring/JVM industry baseline while improving expressiveness for state machines, DTOs and null-heavy domain boundaries. Java remains acceptable if maximum conservatism is preferred, but it is no longer the recommended path.
3. **Node.js role** — Node remains in the project only for frontend tooling and optional runtime scripts; it is no longer the backend platform.

## v0.3 Resolution Notes

1. **DB layer** — owner accepted recommendation: jOOQ + Spring JDBC/JdbcClient + raw SQL as primary DB layer. JPA is allowed only case-by-case for low-risk CRUD and must not enter ledger/audit/outbox/card-data paths.
2. **Tooling line** — owner prefers the stack most existing projects run on. Recommendation changed from Java 25 + Spring Boot 4.x + Gradle 9.x to **Java 21 LTS + Spring Boot 3.5.x + Gradle 8.x**.
3. **LocalStack** — considered for local object storage. It remains a valid optional AWS-emulation test tool, but not the default runtime object storage because the project only needs S3 semantics and a focused S3-compatible store is simpler for local-first development.

## v0.4 Resolution Notes

1. **Object storage** — SeaweedFS S3 API accepted as default local object storage. LocalStack stays optional for future AWS-emulation tests if project later adds AWS services beyond S3. MinIO is not the default because of upstream archive/read-only risk.
2. **Tooling line** — majority-existing-projects line accepted: Java 21 LTS, Spring Boot 3.5.x, Gradle 8.x, Node 24 LTS for frontend/scripts, pnpm 10.x. Latest-major lines remain later upgrade options, not baseline.
3. **Open questions** — all v0.3 Open Questions resolved; 05 is ready for explicit owner approval.
