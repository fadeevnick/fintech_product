# 01 Business Requirements — Mini Fintech Platform

Status: **APPROVED v0.3** (approved by project owner 2026-05-15).

History:
- v0.1 — first draft, имела внутреннее противоречие («production-grade fintech» vs «никаких реальных провайдеров никогда»). Resolved через explicit build/buy/integrate split.
- v0.2 — scope зафиксирован под 4 ключевых решения: real sandbox integrations, compliance scope (KYC+Sanctions+AML), all three UIs, three-party card model.
- v0.3 — open questions v0.2 разрешены: currency = EUR test mode, KYB = Stripe-only для MVP, frontend = три отдельных SPA, Vault = separate service, chargeback initiation = end-user web, AML rules = velocity / structuring / dormancy-break.

---

## 1. Product Statement

Mini Fintech Platform — production-grade fintech backend, реализующий **классический merchant acquiring + digital wallet + double-entry ledger** в одной кодовой базе с **internal simulation card network**:

- **Digital Wallet** — пользовательские счета (end users) с балансами; депозит, вывод, internal transfer.
- **Merchant Payment Processor (Acquirer side)** — приём card payments через API merchant'ом; full lifecycle authorization → clearing → settlement → optional chargeback.
- **Three-Party Card Network Simulation** — внутри одной кодовой базы реализованы как **отдельные bounded contexts**: Issuer (играет банк cardholder'а), Network (играет роль Visa/MC — async router и clearing engine), Acquirer (наша платформа со стороны merchant'а), Vault (PCI-isolated card data store с tokenization).
- **Double-Entry Ledger** — единственный источник истины по балансам и движениям, включая fee расщепление (interchange + assessment + acquirer margin) и settlement positions.

### 1.1 Realism boundary

Платформа интегрируется с **реальными test-mode провайдерами** там, где индустрия покупает готовое (см. build/buy decision matrix в `05_tech_stack.md`):

- **Stripe Connect sandbox** — для merchant onboarding flow и optional outbound payouts to merchants (merchant'ы получают settlement в test-mode деньгах Stripe-side, в дополнение к нашему internal settlement в наш ledger).
- **Sumsub sandbox** — для KYC verification (real document upload → real OCR + liveness + verdict webhook).
- **OpenSanctions API** — для sanctions screening (production-grade aggregated watchlist feed, открытый и бесплатный).
- **Real OIDC provider** для backoffice SSO (Keycloak self-hosted или managed — решается в 05).
- **Real S3-compatible storage** (MinIO локально, S3 для cloud later).

**Единственная граница «не настоящего»** — это **движение реальных денег по реальным банковским рельсам** (требует banking license / partner bank, чего у проекта быть не может). Эта граница реализуется так:

- Deposit / withdraw на end-user wallet: **manual operation** backoffice-оператора (оператор подтверждает «принят перевод от Васи на N»). Это и есть последняя миля, которая в реальном fintech была бы BaaS partner или partner bank — в нашем проекте это explicit manual step.
- Settlement money outflow merchant'у: либо manual operator approval, либо via Stripe Connect test-mode payout (которая работает в Stripe sandbox реально).

Всё остальное (внутренние ledger movements, holds, authorizations, clearing, chargebacks, fee расщепление) — реально работающий код без заглушек.

### 1.2 Build vs Integrate vs Internal-Simulate split

Проект **зеркалит реальный индустриальный split** между «build core», «buy commodity», «internally simulate banking rails»:

- **Build (наш core domain)** — Ledger, Case Management, AML rule engine, Audit log, Idempotency layer, Transactional Outbox, Backoffice workflow UI, Merchant dashboard, End-user web, Webhook delivery infra.
- **Integrate (real sandbox providers)** — KYC verification (Sumsub), Sanctions feed (OpenSanctions), Merchant onboarding helper (Stripe Connect), Object storage (S3/MinIO), Auth provider (OIDC), DB migrations (Flyway/Atlas/etc.), Observability (OSS stack: Grafana/Prometheus/Tempo/Loki).
- **Internally simulate (otherwise requires banking license)** — Card network (Issuer + Network + Acquirer + Vault subsystems внутри нашей же кодовой базы); cardholder bank approval; clearing/settlement messaging; chargeback initiation from issuer side; merchant settlement outbound на real banking rails.

## 2. Project Goal

Цель — **не product-market fit, не customer pain, не speed to launch**. Цель — глубоко разобраться в классическом fintech через построение настоящего, не fake, production backend, **зеркалящего реальный индустриальный split** между build / buy / simulate.

После завершения проекта project owner должен:

- понимать архитектуру и operations production fintech-систем (ledger, payments, KYC, AML, sanctions, case management, merchant acquiring, settlement, observability);
- уметь обсуждать на equal footing с senior fintech-инженерами архитектурные и vendor-integration decisions;
- понимать, **что строится in-house, а что покупается у вендоров**, и почему (build/buy decision skill);
- владеть hexagonal/ports-and-adapters паттерном на уровне рефлекса (мы будем интегрироваться с 3-5 реальными vendors);
- иметь serious portfolio-проект, демонстрирующий владение production-паттернами на уровне реального fintech mid+ engineer'а.

## 3. Project Owner / Primary Beneficiary

Один разработчик (Nickolay), Linux + Docker, single-developer cadence, evenings/weekends.

Реалистичный таймлайн при таких вводных и выбранном scope — **8-12 месяцев**. Это **сознательно длинный проект** ради глубины fintech-понимания, а не speed-to-launch.

## 4. System Roles (simulated)

Реальных конечных пользователей нет. Архитектурно система обслуживает четыре роли, каждая фигурирует и в коде, и хотя бы в одном UI:

- **End user** — владелец кошелька (физлицо). Имеет один wallet account и хотя бы одну выпущенную карту. Делает deposit/withdraw/transfer/card payments. **Имеет end-user web UI.**
- **Merchant** — компания, принимающая card payments через Payments API. Имеет merchant account, API-ключи, settlement balance. **Имеет merchant dashboard UI.**
- **Backoffice operator** — сотрудник платформы. Делает manual deposit/withdraw operations, разруливает спорные ситуации, ведёт audit. **Имеет backoffice workflow UI.**
- **Compliance officer** — RBAC-разрез над backoffice. Видит KYC queue, AML alerts, sanctions hits, case management. Может делать account freeze. **Имеет тот же backoffice UI с компонентами под свою роль.**

## 5. Success Criteria

### 5.1 Domain coverage

Реализованы (через build / integrate / internally-simulate, согласно §1.2) следующие fintech-домены:

- **Identity & sessions** — registration, login, sessions; OIDC SSO для backoffice.
- **KYC** — vendor-integrated через Sumsub sandbox, + manual review для `consider` verdicts (case management).
- **Sanctions screening** — vendor-integrated через OpenSanctions API: на onboarding, на каждого нового payment counterparty.
- **AML transaction monitoring** — hand-coded rule engine (3 базовых правила: velocity, structuring, dormancy-break), alert queue, case workflow, auto-freeze on critical severity.
- **Case Management** — unified bounded context для KYC reviews, AML alerts, sanctions hits, chargebacks. Specialized case types на одной абстракции.
- **Double-entry Ledger** — atomic posting model, balance invariants (`Σ debits == Σ credits` on every entry), реконсилируемые suspense/clearing accounts для in-flight money.
- **End-user Wallet** — wallet account, deposit (manual), withdraw (manual), internal transfer.
- **Card Issuance (internal)** — выдача карт на end-user wallet, card state (active/blocked/expired), CVV/expiration management.
- **Card Network Simulation** — Issuer / Network / Acquirer / Vault как отдельные bounded contexts с async messaging между ними.
- **Card Payment Lifecycle** — authorization → clearing → settlement, разнесённые во времени events; T+1 settlement batch processing.
- **Fee Расщепление** — каждая card транзакция корректно расщепляет MDR на interchange (issuer) + assessment (network) + acquirer margin в ledger postings.
- **Merchant Onboarding** — Stripe Connect sandbox flow + наш собственный merchant model (API keys, settlement account).
- **Merchant Payments API** — payment intent → capture → refund, idempotent endpoints, real webhook delivery to merchants с signed payloads.
- **Chargebacks** — full lifecycle: initiation (через issuer subsystem), evidence submission (через merchant dashboard), arbitration outcome (через backoffice), ledger movements per outcome.
- **PCI-like Vault** — отдельная subsystem с tokenization; card PAN живёт только в vault; остальная application видит только tokens.
- **Backoffice operations** — manual deposit/withdraw, KYC review queue, AML alert review, sanctions hit review, account freeze, audit log viewer.
- **Audit log** — append-only, на каждое доменное событие + на read access к sensitive entities (compliance).
- **Observability** — structured JSON logs, Prometheus metrics, OpenTelemetry traces на критических path'ах.

### 5.2 Production-pattern coverage

В коде реально работают (не stub):

- **Idempotency keys** на critical write endpoints (payments, transfers, deposits, refunds, chargebacks).
- **Transactional outbox** для исходящих доменных событий.
- **Saga / process manager** для multi-step async операций (минимум: card payment lifecycle через issuer↔network↔acquirer; KYC review через Sumsub webhook).
- **Hexagonal / ports-and-adapters** для каждой external integration (Sumsub, OpenSanctions, Stripe, OIDC, S3).
- **Idempotent webhook handlers** (Sumsub, Stripe) с signature verification + replay protection.
- **Reconciliation tooling** между нашим state и provider state (retained verification scripts).
- **Append-only audit log** с actor/cause, + read-audit на compliance-sensitive entities.
- **RBAC** для всех ролей (end user, merchant, backoffice operator, compliance officer).
- **Structured logs + metrics + traces** через OpenTelemetry baseline coverage.
- **Dead letter queue** для failed async events.
- **Retries with exponential backoff + jitter** для outbound calls и async processing.
- **DB migrations** через migration tool (Flyway/Atlas/Liquibase/etc. — решается в 05), без ручных DDL.
- **Ledger invariants** проверяются автоматическим reconciliation script'ом (retained, run-on-demand).
- **PCI-style vault isolation** — отдельный сервис с отдельной DB, restricted network access, tokenization layer на границе.

### 5.3 Quality bar (non-negotiable)

Никаких fake-реализаций. Каждая capability в коде реально делает своё дело. Если что-то не успевается как simple production:

- либо **cut** — вычеркнуто из scope, зафиксировано в `planning/design-details/cut_register.md`;
- либо **manual** — есть в коде, но операция выполняется руками backoffice-оператора (применяется только там, где это и в реальном fintech была бы manual operation последней мили).

Запрещены: `console.log` вместо отправки события, in-memory volatile хранилища вместо БД, hardcoded ответы вместо реальных вычислений, mock-провайдеры в production code path, заглушки без записи в ledger.

## 6. Scope

### 6.1 In Scope (MVP)

**Identity:**
- End-user registration, login, sessions, password reset.
- Merchant employee accounts (отдельный user pool, отдельный login).
- Backoffice operator accounts через OIDC SSO.
- RBAC: end-user / merchant / operator / compliance-officer roles.

**KYC (vendor-integrated):**
- Sumsub sandbox integration: real applicant creation, real document upload (через Sumsub web SDK или server upload), real webhook handling с signature verification.
- State machine: `NOT_STARTED` → `IN_REVIEW` → `APPROVED` | `REJECTED` | `NEEDS_RESUBMIT`.
- Manual review queue для `consider` verdicts.
- KYC status контролирует wallet operation limits.

**Sanctions screening (vendor-integrated):**
- OpenSanctions API integration на KYC submission и на каждый новый payment counterparty.
- Match handling: hit → block + case open для manual review.
- Cleared exceptions для подтверждённых false positives.

**AML transaction monitoring (built):**
- Event-driven rule engine, 3 hand-coded rules: velocity, sub-threshold structuring, dormancy break.
- Alert queue + manual review case workflow.
- Account freeze capability (audit-tracked).
- Hook point для future ML/vendor integration.

**Case Management (built — unified abstraction):**
- `cases` / `case_actions` / `case_attachments` / `case_assignments` bounded context.
- Specialized case types: KYC review, AML alert, sanctions hit, chargeback dispute.

**Wallet:**
- One wallet account per end user в EUR (single currency для MVP; multi-currency — later scope).
- **Manual deposit** через backoffice operator (последняя миля, эквивалент banking rails).
- **Manual withdraw** через backoffice operator.
- Internal transfer end-user → end-user.

**Card Issuance:**
- Issue card on end-user wallet (через end-user web).
- Card state machine (active/blocked/expired/lost).
- Card data → vault, в основной БД только token.

**Three-Party Card Network Simulation (built as separate bounded contexts):**
- **Issuer subsystem**: card holding, authorization decision, hold management, chargeback initiation.
- **Network subsystem**: async router по BIN, clearing batch processing, settlement positions calculation, fee расщепление dispatch.
- **Acquirer subsystem**: merchant onboarding, payment intake from merchant API, forward to network, settlement receipt, merchant balance management.
- **Vault subsystem**: deployed как **отдельный Docker Compose сервис** с **отдельной БД** и restricted network access — для реалистичной PCI-style isolation. Tokenization service с очень узким API; только Issuer subsystem имеет detokenize-доступ.

**Card Payment Lifecycle:**
- Payment intent (acquirer side) → authorization request (network → issuer) → auth response.
- Clearing batch (end-of-day): merchant submits captures, acquirer aggregates, network distributes to issuers.
- Settlement (T+1): money flows через ledger между issuer/acquirer/merchant accounts с fee расщеплением.
- Refund flow: partial / full, через ledger reversal с правильным fee handling.

**Chargebacks (full lifecycle):**
- Initiation: end user opens dispute через end-user web → issuer subsystem → network → acquirer → merchant.
- State machine: `INITIATED` → `MERCHANT_NOTIFIED` → `EVIDENCE_SUBMITTED` → `ARBITRATION` → `WON` | `LOST`.
- Evidence submission через merchant dashboard.
- Arbitration outcome: backoffice operator выставляет outcome (это и есть наша simulation card network decision).
- Ledger movements per outcome.
- Per-merchant chargeback rate metric.

**Merchant Onboarding:**
- Stripe Connect sandbox onboarding flow (KYB через Stripe).
- Наш собственный merchant model (settlement account in our ledger, API keys, configuration).
- API key generation / rotation / revocation.

**Merchant Payments API:**
- Public REST API с signed requests / idempotency keys.
- Endpoints: create payment intent, capture, refund, get status.
- Outbound webhooks к merchant'у на lifecycle events: signed payloads, retries, DLQ, replay-from-failures endpoint.

**Three UIs (минимальные, но реальные) — деплоятся как три отдельных SPA с независимым auth flow и независимыми RBAC scopes:**
- **End-user web** (frontend stack решается в 05): login, wallet view, request deposit, transfer, card list + issue, transaction history, chargeback initiation.
- **Merchant dashboard** (тот же frontend stack): login, API keys management, payment list, refund button, settlement view, chargeback evidence submission, webhook endpoint configuration.
- **Backoffice workflow UI** (тот же frontend stack либо Retool — решается в 05): user list, KYC review queue, AML alert review queue, sanctions hit review, manual deposit/withdraw form, account freeze, audit log viewer, chargeback arbitration.

**Audit log:**
- Append-only записи на каждое доменное событие с actor + cause + timestamp.
- Read-audit на compliance-sensitive entities (sanctions hits, AML alerts, frozen accounts).

**Observability (baseline):**
- Structured JSON logs.
- Prometheus metrics: critical paths + business metrics (payment success rate, chargeback rate, KYC throughput).
- OpenTelemetry traces.
- Local OSS stack (Grafana + Prometheus + Tempo + Loki).

### 6.2 Later Scope (зафиксировано, но не делается в MVP)

- **SAR-style filing** — case management для compliance reporting (form, PDF generation, no-tipping-off RBAC, read-audit). Hook points (case mgmt, RBAC, audit) уже в MVP.
- **Multi-currency** wallet + ledger.
- **Multi-factor authentication** для всех ролей.
- **PEP screening** (Politically Exposed Persons enhanced due diligence).
- **ML-based fraud detection** (vendor integration or in-house).
- **KYB (Know Your Business)** beyond Stripe Connect's built-in verification.
- **Automated settlement scheduling** (вместо manual operator-triggered batches).
- **Organization-level identities** (multi-user merchant accounts с role separation).
- **3DS / Strong Customer Authentication** в card payment flow.
- **Real production deployment** на cloud (k8s/managed services) — MVP остаётся docker-compose-based.
- **Real Stripe production tier** (vs sandbox/test mode).

### 6.3 Non-Goals

Эти вещи **никогда** не будут частью проекта:

- **Реальное движение денег по банковским рельсам** (требует banking license / partner bank).
- **Реальная PCI DSS сертификация** (мы реализуем PCI-like patterns без формальной сертификации).
- **Реальные regulator filings** (FinCEN BSA XML, etc.) — без regulated entity status невозможно.
- **Polished consumer UX**, mobile native app, marketing site.
- **High availability / multi-region**, real 24x7 oncall.
- **Production load** под realistic traffic, sharding, archival, hot/cold storage.

## 7. Constraints

- **Single developer**, evenings/weekends cadence.
- **Local-first**: Linux + Docker Compose для MVP. Cloud deploy опционально как later scope.
- **Currency**: real fiat labels (EUR test mode) во всём ledger и UI. Это матчится со Stripe sandbox и даёт реалистичную доменную терминологию (interchange / MDR в bps of EUR, settlement в EUR).
- **No real PII**: тестовые пользователи с синтетическими данными (Sumsub sandbox принимает test documents).
- **Vendor sandbox limits**: Sumsub free tier, OpenSanctions free tier, Stripe test mode — все имеют rate limits и/или feature limits. Это OK, MVP не упирается в production-volume.
- **Realistic timeline**: 8-12 месяцев single-dev evenings/weekends. Не «MVP за месяц».

## 8. Decisions Deferred To Later Artifacts

Сюда — чтобы не забыть, но решения принимаются в указанных артефактах:

- Backend language / framework (NestJS vs Spring Boot vs other) → **05**.
- Frontend stack для трёх отдельных SPA (React/Vue/Svelte) → **05**.
- Monorepo vs polyrepo для трёх UIs + backend → **05**.
- Backoffice UI: build native vs Retool/Forest/Appsmith adapter → **05**.
- OIDC provider: Keycloak self-hosted vs managed (Auth0 free, Authentik) → **05**.
- API стиль public Payments API: REST vs gRPC → **04**.
- Internal communication между bounded contexts: sync (HTTP/gRPC) vs async (Kafka events) vs hybrid → **04**.
- DB migration tool: Flyway / Atlas / Liquibase / framework-native → **05**.
- Один модульный monolith vs multiple services (current lean: modular monolith в одном repo с bounded contexts как modules; only Vault deployed как separate service для PCI-style isolation) → **04**.

---

## v0.3 Resolution Notes

Open questions из v0.2 разрешены (project owner confirmed 2026-05-15):

1. **Currency** — real fiat (EUR test mode). Матчится со Stripe sandbox; реалистичная доменная терминология для fee расщепления.
2. **KYB** — Stripe Connect's built-in KYB достаточно для MVP. In-house KYB review остаётся в later scope, hook через case management уже в MVP (по аналогии с KYC `consider` verdicts).
3. **Frontend shape** — три отдельных SPA (end-user / merchant / backoffice). Независимые deployment'ы, независимые auth flows, независимые RBAC scopes.
4. **Vault** — отдельный сервис в Docker Compose с отдельной БД и restricted network access. Tokenization service с очень узким API; только Issuer subsystem имеет detokenize-доступ.
5. **Chargeback initiation** — через end-user web (issuer subsystem имеет UI integration с end-user web; классический dispute UX, аналог «оспорить операцию»).
6. **AML rules для MVP** — velocity / sub-threshold structuring / dormancy break (3 hand-coded rules как initial set; расширение rule set — later scope).

