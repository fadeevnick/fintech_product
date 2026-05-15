# 03 Functional Requirements — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

History:
- v0.1 — first draft FRs по всем bounded contexts на основе `01_business_requirements.md` APPROVED v0.3 и `02_user_journeys.md` APPROVED v0.2.
- v0.2 — open questions v0.1 разрешены: hybrid sync/async communication, per-transaction limits only в MVP, closed-loop in-house acquiring confirmed, global email uniqueness across pools, rate-limited PAN reveal с re-auth, customer support deferred, new PAN на card replacement, chargeback reserve mechanism с 0% default + per-merchant configurable, authorization path = sync HTTP.

---

## Conventions

### FR identifiers

Каждое функциональное требование имеет stable ID формата `{MODULE}-FR-{NN}`:

| Prefix | Module |
|---|---|
| `IDN` | Identity & sessions |
| `KYC` | KYC verification |
| `SNX` | Sanctions screening |
| `AML` | AML transaction monitoring |
| `CSM` | Case management |
| `LDG` | Ledger |
| `WLT` | Wallet (deposit / withdraw / transfer / SoF) |
| `CRD` | Card issuance & state |
| `VLT` | Vault (PCI isolation) |
| `ISU` | Issuer subsystem |
| `NTW` | Network subsystem |
| `ACQ` | Acquirer subsystem |
| `PMT` | Payment lifecycle (cross-context) |
| `SLM` | Settlement & fee splitting |
| `CHB` | Chargeback lifecycle |
| `MRC` | Merchant onboarding & lifecycle |
| `API` | Public Merchant Payments API |
| `WBH` | Outbound webhook delivery |
| `UEW` | End-user Web SPA |
| `MDB` | Merchant Dashboard SPA |
| `BOF` | Backoffice Workflow UI |
| `AUD` | Audit log |
| `OBS` | Observability |
| `RBC` | RBAC |
| `IDM` | Idempotency |
| `LMT` | Transaction limits |
| `2EY` | Two-eyes principle |
| `NFR` | Non-functional requirements |

IDs стабильны: добавлять новые с инкрементом NN, не переиспользовать deprecated. Tracability link к runtime checks через `{MODULE}-{NN}` (см. 04+).

### Level of detail

Каждое FR — testable statement: «the system shall ...». Не описывает *как*, описывает *что*. Архитектурные решения и schema — в 04 / design-details.

---

## 1. Identity & Sessions (IDN)

- **IDN-FR-01** — System shall provide separate user pools для трёх ролей: end users, merchant employees, backoffice operators. Каждый pool имеет independent login flow и independent credentials store.
- **IDN-FR-02** — End-user registration shall accept email + password + name + DOB + country; email shall be **globally unique** across all user pools (end users, merchant employees, backoffice operators) — same email cannot register in multiple pools, чтобы избежать role confusion в audit.
- **IDN-FR-03** — Email verification shall be **hard gate** перед KYC initiation; user shall not be able to start KYC до verified email.
- **IDN-FR-04** — Sessions shall be issued как opaque tokens с server-side store; client получает session cookie с HttpOnly + Secure + SameSite=Strict.
- **IDN-FR-05** — Sessions shall expire через configured TTL (default 7d) с sliding renewal на active usage; explicit logout invalidates session immediately.
- **IDN-FR-06** — Backoffice operator login shall be через external OIDC provider (Keycloak or managed — выбирается в 05); password-based local login для operators недопустим.
- **IDN-FR-07** — Password reset shall use email-link flow с single-use, time-bounded (15 min) token.
- **IDN-FR-08** — Failed login attempts shall be rate-limited per IP и per account; after N failures account temporarily locked (configurable, default 5 failures → 15 min lock).
- **IDN-FR-09** — Account permanent block (по результату compliance review) shall override session validity — blocked accounts receive 403 на всех write endpoints включая login refresh.

## 2. KYC (KYC)

- **KYC-FR-01** — System shall integrate с Sumsub sandbox для identity verification: applicant creation, document submission, verdict reception.
- **KYC-FR-02** — KYC state machine shall be: `NOT_STARTED → SUBMITTED → IN_REVIEW → APPROVED | REJECTED | NEEDS_RESUBMIT`. Transitions logged in audit.
- **KYC-FR-03** — `kyc_status = APPROVED` shall be precondition для: card issuance, deposits > €100, withdraws, transfers > €100, chargeback initiation.
- **KYC-FR-04** — Sumsub `consider` verdict shall route case to backoffice KYC review queue; `kyc_status` stays `IN_REVIEW` до manual decision.
- **KYC-FR-05** — Sumsub webhook receiver shall verify HMAC signature; payloads с invalid signature rejected с 401 и logged.
- **KYC-FR-06** — Sumsub webhook receiver shall be idempotent по vendor event ID — duplicate deliveries не двигают state дважды.
- **KYC-FR-07** — Manual KYC decision (override Sumsub verdict) requires rationale text (≥ 20 characters) от operator; recorded в case.
- **KYC-FR-08** — Document images shall be stored через secure preview link с rate-limited access (max 10 views per operator per case); raw downloads disallowed by default.

## 3. Sanctions Screening (SNX)

- **SNX-FR-01** — System shall integrate с OpenSanctions API для watchlist matching.
- **SNX-FR-02** — Sanctions check shall run на двух событиях: (a) на KYC submission по name + DOB + country; (b) на каждый новый payment counterparty (e.g., first transfer to a user).
- **SNX-FR-03** — Match score above configurable threshold (default 0.85) shall create `sanctions_hit` case `OPEN` и block dependent action (KYC approval, transfer execution).
- **SNX-FR-04** — Sanctions hit case shall require compliance officer (не operator) для decision: `TRUE_MATCH` (permanent block) или `CLEARED_FALSE_POSITIVE` (с rationale + exception record).
- **SNX-FR-05** — Cleared exception records shall suppress re-blocking for the same (user, watchlist entry) pair on subsequent checks.
- **SNX-FR-06** — Failure to reach OpenSanctions (timeout / error) shall be **fail-closed**: dependent action blocked, hit case opened с reason `SCREENING_UNAVAILABLE`, retried automatically.
- **SNX-FR-07** — Sanctions check responses shall be logged с request fingerprint + matched entries для audit traceability.

## 4. AML Monitoring (AML)

- **AML-FR-01** — System shall maintain а rule engine, evaluating each completed money-moving event против configured rule set.
- **AML-FR-02** — MVP rule set: **velocity** (>N transfers per hour, configurable), **structuring** (>N transactions в диапазоне 90-99% от reporting threshold за 24h), **dormancy-break** (no activity 30d, then aggregate > X в 24h).
- **AML-FR-03** — Rule trip shall create AML alert case `OPEN` с severity (`LOW | MEDIUM | HIGH | CRITICAL`) determined by rule logic.
- **AML-FR-04** — `CRITICAL` severity shall auto-trigger account freeze; freeze recorded as `account_freezes.ACTIVE` linked к case.
- **AML-FR-05** — AML alert review requires backoffice operator или compliance officer; decision `CLOSED_FALSE_POSITIVE` / `ESCALATED` / `MARKED_FOR_SAR_FILING` / `ACCOUNT_FROZEN_PERMANENT`, требует rationale text.
- **AML-FR-06** — Whitelist mechanism: operator може mark user as exempt from specific rule (e.g., legitimate high-velocity merchant employee); exemption recorded with expiration.
- **AML-FR-07** — Rule engine shall expose hook point для future ML/vendor integration без code changes в business logic — рекомендованный design: pluggable rule providers.

## 5. Case Management (CSM)

- **CSM-FR-01** — Single unified case management bounded context shall serve КYC reviews, sanctions hits, AML alerts, chargeback disputes, SoF declarations.
- **CSM-FR-02** — Case entity shall have: `case_id`, `case_type`, `subject` (user/merchant reference), `state`, `assigned_to`, `severity`, `opened_at`, `closed_at`, `closed_reason`.
- **CSM-FR-03** — Case actions shall be append-only audit log of state transitions, comments, attachments, attached events; each action records actor + timestamp + rationale.
- **CSM-FR-04** — Case attachments shall be stored через S3/MinIO с file metadata записанным в case; case_attachment record links case к S3 object key.
- **CSM-FR-05** — Case assignment shall support: unassigned (in queue), assigned to specific operator, escalated to compliance officer.
- **CSM-FR-06** — Operators shall see cases в queue filtered по своему role / RBAC scope; compliance-sensitive case types (sanctions hits, SAR markers) скрыты от обычных operators.

## 6. Ledger (LDG)

- **LDG-FR-01** — System shall maintain double-entry ledger как единственный source of truth для money balances и movements.
- **LDG-FR-02** — Every journal entry shall enforce invariant `Σ debits == Σ credits`; violating entries shall be rejected at write time, never persisted.
- **LDG-FR-03** — Ledger entries shall be append-only; updates / deletes prohibited at DB level (через triggers / constraints).
- **LDG-FR-04** — Each posting shall reference: `account_id`, `direction` (debit/credit), `amount`, `currency=EUR`, `journal_entry_id`, `event_source` (origin domain event), `timestamp`.
- **LDG-FR-05** — Account types shall include: user wallet, merchant settlement, suspense/clearing (in-flight), issuer interchange revenue, network assessment revenue, acquirer margin revenue, dispute reserve, Stripe payout clearing, external settled.
- **LDG-FR-06** — Account balance shall be derived (computed sum of postings), не stored как mutable column. Optional cached snapshot для performance — invalidated на каждое новое posting.
- **LDG-FR-07** — Reconciliation script (retained) shall verify ledger invariants on demand: per-account balance consistency, global `Σ debits == Σ credits`, no orphan postings.
- **LDG-FR-08** — Holds (reserved-but-not-yet-debited funds) shall be modeled через explicit posting к hold sub-account, не как mutable column на main account.

## 7. Wallet & Manual Operations (WLT)

- **WLT-FR-01** — Each end user shall have exactly one wallet account в EUR (multi-currency — later scope).
- **WLT-FR-02** — Wallet balance shall be derived from ledger postings (см. LDG-FR-06).
- **WLT-FR-03** — Deposit flow: end user requests amount, system gives bank reference info, end user does external transfer, backoffice operator confirms receipt manually; ledger posting `debit suspense_bank_inflow, credit user_wallet`.
- **WLT-FR-04** — Deposits > €10 000 shall require **Source of Funds declaration** (structured form: source category, employment/business, optional docs) submitted by end user before payment instructions are shown.
- **WLT-FR-05** — SoF declaration shall be linked к deposit request via case management (отдельный case type `SOURCE_OF_FUNDS`).
- **WLT-FR-06** — Deposits ≥ €10 000 shall require two-eyes principle (см. 2EY-FR-*); first operator marks ready-for-review, second operator approves before ledger posting.
- **WLT-FR-07** — Withdraw flow: end user requests amount + bank destination, system places hold on wallet, backoffice operator approves manually, ledger posting `debit user_wallet, credit suspense_bank_outflow`, operator physically transfers funds offline, marks withdraw as `COMPLETED`.
- **WLT-FR-08** — Withdraws ≥ €10 000 shall require two-eyes principle.
- **WLT-FR-09** — Internal transfer (end user → end user) flow: synchronous atomic ledger posting `debit sender_wallet, credit receiver_wallet`; AML pre-check на blocking rules (sanctions on receiver, hard freeze on either party).
- **WLT-FR-10** — Sender и receiver shall both have `kyc_status = APPROVED` для internal transfer.
- **WLT-FR-11** — Transfer recipient search shall accept email и wallet ID (phone — later scope).
- **WLT-FR-12** — Manual operator actions (deposit / withdraw) shall always be linked к ledger postings — direct balance manipulation outside ledger prohibited.

## 8. Card Issuance & State (CRD)

- **CRD-FR-01** — End user shall issue card(s) on their wallet account; multiple cards per user supported.
- **CRD-FR-02** — Card creation shall delegate PAN / CVV / expiration generation к vault service; only `card_token` + `last4` + `expiration_month/year` stored outside vault.
- **CRD-FR-03** — Card state machine: `ACTIVE → BLOCKED | EXPIRED | LOST`. Transitions logged in audit.
- **CRD-FR-04** — End user shall be able to block their own card (state → `BLOCKED`); block reversible (state → `ACTIVE`) for `user-requested-block` only.
- **CRD-FR-05** — Card expiration date shall be enforced — expired cards reject authorization requests.
- **CRD-FR-06** — Full PAN display shall be rate-limited per card per user (default 3 views per 24h); each access logged в audit с user + IP + timestamp.
- **CRD-FR-07** — Card replacement: blocked / lost card → user issues new card with **new PAN** generated by vault (no PAN reuse); old card token deactivated в vault.

## 9. Vault (VLT)

- **VLT-FR-01** — Vault shall be deployed as separate service с отдельной DB и restricted network access; only issuer subsystem service identity authorized для detokenize calls.
- **VLT-FR-02** — Vault API shall expose three operations: `tokenize(pan_or_input_form) → token`, `detokenize(token) → pan` (auth-restricted), `forward_to_issuer(token, amount, ...) → auth_response` (preferred for authorization, чтобы PAN не пересекал границу).
- **VLT-FR-03** — PAN values shall never appear в general application logs; logging frameworks shall apply PAN-masking patterns (`XXXX-XXXX-XXXX-1234`).
- **VLT-FR-04** — CVV shall never be persisted после initial authorization use; vault stores PAN + expiration but не CVV (исключение: CVV transient в RAM only во время real-time authorization).
- **VLT-FR-05** — Vault DB shall use encryption at rest для PAN column (e.g., pgcrypto column-level encryption); key rotation supported.
- **VLT-FR-06** — Vault shall expose narrow audit log: every detokenize call recorded (caller service identity, token, timestamp); accessible только compliance officer.
- **VLT-FR-07** — Card tokens shall be globally unique opaque strings, not derived from PAN (не предсказуемые).

## 10. Issuer Subsystem (ISU)

- **ISU-FR-01** — Issuer shall hold card records: `card_id`, `token`, `wallet_account_link`, `state`, `expiration`, `last4`, `BIN`.
- **ISU-FR-02** — Issuer shall accept authorization requests from network; decision based on: card state, available balance (wallet balance minus existing holds), per-card transaction limits, AML synchronous block check, sanctions block check.
- **ISU-FR-03** — Successful authorization shall place a hold on the wallet account (LDG-FR-08) и respond с `AUTH_APPROVED` + auth_code + auth_expiration (default 7 days).
- **ISU-FR-04** — Declined authorization shall respond с structured decline reason (insufficient funds, card blocked, limit exceeded, sanctions block, fraud rule).
- **ISU-FR-05** — On clearing message receipt (per-transaction): issuer converts hold to actual debit (LDG ledger posting on user wallet).
- **ISU-FR-06** — Expired holds (auth without capture before expiration) shall be automatically released; ledger reversal posted.
- **ISU-FR-07** — Issuer shall handle chargeback initiation: when cardholder disputes a payment, issuer creates chargeback record, provisional credit to cardholder wallet, sends chargeback message via network.

## 11. Network Subsystem (NTW)

- **NTW-FR-01** — Network shall implement **two communication patterns**: (a) **sync internal HTTP** для authorization request-response fast path (acquirer ↔ network ↔ issuer); (b) **async messaging** (Kafka через outbox) для clearing batch, settlement events, fee расщепление dispatch. Final transport choices (HTTP framework, Kafka topic naming) — в 04.
- **NTW-FR-02** — Network shall maintain BIN registry mapping `BIN → issuer_subsystem`. MVP: один internal BIN owned by our issuer (так как мы — единственный issuer в simulation).
- **NTW-FR-03** — Network shall route authorization requests acquirer → issuer based on BIN; route response back to originating acquirer.
- **NTW-FR-04** — Network shall maintain clearing engine: aggregate captures from acquirers, distribute clearing messages к issuers, нightly batch.
- **NTW-FR-05** — Network shall compute net settlement positions: per acquirer-issuer pair compute net flow с fee splits applied; produce settlement batch records.
- **NTW-FR-06** — Network shall enforce message integrity: every routed message recorded в network audit log (separate from app audit).
- **NTW-FR-07** — Network shall apply configurable artificial latency / failure injection для testing (used by reconciliation tests in 04+).

## 12. Acquirer Subsystem (ACQ)

- **ACQ-FR-01** — Acquirer shall expose internal interface для merchant Payments API: accept payment intents, forward auth requests to network.
- **ACQ-FR-02** — Acquirer shall maintain merchant settlement balance accounts (LDG accounts); credit on settlement, debit on chargebacks / refunds / payouts.
- **ACQ-FR-03** — Acquirer shall maintain `payment_intent` lifecycle: `CREATED → AUTHORIZED → CAPTURED → SETTLED` happy path; alternates `FAILED`, `EXPIRED`, `REFUNDED`, `DISPUTED`, `CHARGED_BACK`.
- **ACQ-FR-04** — Acquirer shall aggregate captures per merchant per day для clearing submission to network.
- **ACQ-FR-05** — Acquirer shall maintain merchant fee configuration: MDR rate (% + fixed fee per merchant), applied on settlement.
- **ACQ-FR-06** — Acquirer shall handle chargeback notifications from network: create merchant-side chargeback record, initiate dispute reserve hold on merchant settlement, dispatch webhook.
- **ACQ-FR-07** — Acquirer shall expose merchant-facing dashboard query API: payment list, settlement view, chargeback list — все scoped по `merchant_id`.

## 13. Payment Lifecycle (PMT)

- **PMT-FR-01** — Payment intent shall be created через Payments API (см. API-FR-*) с amount, currency=EUR, card source (token or hosted form session).
- **PMT-FR-02** — Hosted form (default for MVP) shall capture card details client-side и tokenize в vault directly, never exposing PAN к merchant or к acquirer subsystem.
- **PMT-FR-03** — Server-to-server card data ingestion (advanced) shall accept card data through API endpoint и tokenize immediately within request handler; never logged, never persisted outside vault.
- **PMT-FR-04** — Authorization is sync from merchant's POV (immediate `AUTH_APPROVED` or `AUTH_DECLINED`); async internally (acquirer → network → issuer → ответ).
- **PMT-FR-05** — Capture shall be explicit API call from merchant; payment_intent transitions `AUTHORIZED → CAPTURED`. Capture queues entry for next clearing batch.
- **PMT-FR-06** — Authorization expiration: if not captured within 7 days, system auto-expires authorization, releases hold (ISU-FR-06).
- **PMT-FR-07** — Refund flow: merchant calls refund API, acquirer creates refund record, dispatches reversal via network to issuer, issuer credits cardholder wallet, settlement adjustment в next batch.
- **PMT-FR-08** — Partial refunds supported; aggregate refunds ≤ original payment amount.
- **PMT-FR-09** — Each payment lifecycle event shall produce outbox entries для webhook delivery (см. WBH-FR-*).

## 14. Settlement & Fee Splitting (SLM)

- **SLM-FR-01** — Settlement batch shall run daily (configurable schedule); processes all cleared captures от previous day.
- **SLM-FR-02** — For each payment, settlement shall produce 5+ ledger postings: cardholder wallet (full debit), merchant settlement (credit = amount − MDR), issuer interchange revenue (credit = interchange portion), network assessment revenue (credit = assessment portion), acquirer margin revenue (credit = remaining).
- **SLM-FR-03** — Fee structure shall be configurable per merchant per card type (MVP: flat MDR per merchant; tiered — later scope).
- **SLM-FR-04** — MVP fee defaults: interchange ~1.5% (issuer revenue), assessment ~0.1% (network), acquirer margin = MDR − interchange − assessment.
- **SLM-FR-05** — Settlement creates merchant `available_balance` entry; merchant can trigger payout (см. SLM-FR-06).
- **SLM-FR-06** — Merchant payout flow: merchant triggers payout from available balance → Stripe Connect API call → ledger posting `debit merchant_settlement, credit stripe_payout_clearing`; on Stripe webhook confirmation → `debit stripe_payout_clearing, credit external_settled`. State machine `available → payout_pending → payout_succeeded | payout_failed`.
- **SLM-FR-07** — Failed payout shall reverse ledger to `available`; merchant notified via dashboard + webhook.
- **SLM-FR-08** — Per-merchant chargeback reserve mechanism shall be in place: `chargeback_reserve_percent` configurable per merchant via backoffice; default 0% в MVP (mechanism present but inactive). When >0%, that fraction of pending settlements held as reserve, released after dispute window expires. Allows learning the pattern without complicating MVP default behavior.

## 15. Chargeback Lifecycle (CHB)

- **CHB-FR-01** — Chargeback may be initiated by cardholder через end-user web for any payment within configurable window (default 60 days from payment date).
- **CHB-FR-02** — Chargeback reason codes (MVP): `fraud_no_authorization`, `goods_not_received`, `goods_not_as_described`, `duplicate_charge`. Mapping к real Visa/MC codes documented в design-details.
- **CHB-FR-03** — Chargeback state machine: `INITIATED → MERCHANT_NOTIFIED → EVIDENCE_SUBMITTED → ARBITRATION → WON | LOST`. Альт: `MERCHANT_ACCEPTED` (terminal `LOST` без arbitration), `MERCHANT_DEADLINE_EXPIRED` (terminal `LOST`).
- **CHB-FR-04** — On `INITIATED`, issuer shall provisionally credit cardholder; ledger posting `debit acquirer_dispute_reserve, credit cardholder_wallet`. Reverse on `WON`.
- **CHB-FR-05** — Merchant deadline для response shall be configurable per reason code; default 10-30 days.
- **CHB-FR-06** — Merchant submits evidence через merchant dashboard: text narrative + file attachments через S3/MinIO. Evidence linked к chargeback case.
- **CHB-FR-07** — Arbitration outcome shall be set by backoffice operator (simulation card network decision); outcome `WON | LOST` with required rationale.
- **CHB-FR-08** — Per-merchant chargeback rate metric: computed на rolling 30/90 day window; surfaced в merchant dashboard и в backoffice for monitoring.

## 16. Merchant Onboarding & Lifecycle (MRC)

- **MRC-FR-01** — Merchant registration shall create merchant entity + linked Stripe Account через Stripe Connect Express API.
- **MRC-FR-02** — Stripe Connect onboarding flow shall use hosted Account Link redirect; on success redirect, merchant returns to dashboard.
- **MRC-FR-03** — KYB verdict shall come через Stripe webhook (`account.updated`); merchant `kyb_status` updated based on Stripe `charges_enabled && payouts_enabled` boolean.
- **MRC-FR-04** — Merchant API key generation shall produce public/secret key pair; secret shown only once at creation, only HMAC fingerprint stored hashed.
- **MRC-FR-05** — Multiple active API keys per merchant supported; rotation grace period (configurable, default 30 days) before deactivation.
- **MRC-FR-06** — API key revocation immediate; subsequent requests with revoked key receive 401.
- **MRC-FR-07** — Merchant webhook endpoint configuration: single URL per merchant + event filter (subscribed events). Multiple endpoints — later scope.
- **MRC-FR-08** — Webhook signing secret shall be per merchant; rotation supported.

## 17. Public Merchant Payments API (API)

- **API-FR-01** — All write endpoints shall accept `Idempotency-Key` header; repeated requests with same key return cached response without re-execution (см. IDM-FR-*).
- **API-FR-02** — Authentication shall be API key based (Bearer header или basic auth); rate limiting per API key.
- **API-FR-03** — Endpoints (MVP minimum set): `POST /v1/payment_intents`, `POST /v1/payment_intents/{id}/capture`, `POST /v1/payment_intents/{id}/refund`, `GET /v1/payment_intents/{id}`, `GET /v1/payments?cursor=...&limit=...`, `POST /v1/disputes/{id}/submit_evidence`, `GET /v1/settlements?cursor=...`, `POST /v1/payouts`.
- **API-FR-04** — Response format shall be structured JSON: `{data: ..., errors: [...]}` для errors с `code`, `message`, `field` (если applicable).
- **API-FR-05** — Pagination shall be cursor-based, not offset-based; cursor opaque to client.
- **API-FR-06** — API shall version через URL prefix (`/v1/`); breaking changes require `/v2/`.
- **API-FR-07** — Rate limiting: per-merchant per-endpoint limits; on exceed return 429 с `Retry-After` header.

## 18. Outbound Webhook Delivery (WBH)

- **WBH-FR-01** — Outbox pattern shall be used: domain events written к outbox table in same transaction as state change; separate dispatcher reads outbox и delivers.
- **WBH-FR-02** — Webhook payloads shall be signed with HMAC using merchant-specific secret; header `X-Webhook-Signature: t=<timestamp>,v1=<hex>`. Verification details documented for merchants.
- **WBH-FR-03** — Webhook timestamp shall be enforced как replay protection — events older than 5 minutes rejected by spec.
- **WBH-FR-04** — Retry policy: exponential backoff with jitter (1m, 5m, 30m, 2h, 12h, 24h, 48h, 72h); maximum 8 attempts. After failure → DLQ.
- **WBH-FR-05** — DLQ entries shall be retained 30 days; merchant dashboard exposes DLQ events with replay button.
- **WBH-FR-06** — Successful delivery confirmed by HTTP 2xx response within 30s timeout; 4xx (except 408/429) treated as permanent failure → DLQ immediately.
- **WBH-FR-07** — Webhook event types: `payment_intent.created`, `payment_intent.authorized`, `payment_intent.captured`, `payment_intent.settled`, `payment_intent.refunded`, `dispute.created`, `dispute.evidence_received`, `dispute.won`, `dispute.lost`, `payout.succeeded`, `payout.failed`, `account.updated` (KYB).

## 19. End-User Web SPA (UEW)

- **UEW-FR-01** — Deployed as separate SPA with independent auth flow и domain (e.g., `app.miniefin.local`).
- **UEW-FR-02** — Screens (MVP): login, registration, KYC initiation (Sumsub embed), wallet view, deposit request (с SoF form для > €10k), withdraw request, internal transfer, card list, card detail (с rate-limited PAN reveal), transaction history, payment dispute initiation.
- **UEW-FR-03** — User shall be able to view only their own data (LDG-FR-* enforced server-side, not client).
- **UEW-FR-04** — Card PAN reveal shall be behind a confirmation flow (e.g., re-enter password) + rate-limited (CRD-FR-06).
- **UEW-FR-05** — Hosted payment form (для merchant payment ingestion) shall be served on our domain, embedded via iframe / redirect on merchant pages.

## 20. Merchant Dashboard SPA (MDB)

- **MDB-FR-01** — Deployed as separate SPA with independent auth flow и domain (e.g., `merchants.miniefin.local`).
- **MDB-FR-02** — Screens (MVP): login, Stripe Connect onboarding redirect, API keys management, webhook endpoints config, payment list (filter + pagination), payment detail (with refund button), refund history, dispute list, dispute detail (with evidence submission), settlement list (with detail breakdown), payout initiation.
- **MDB-FR-03** — Webhook configuration UI shall allow: setting endpoint URL, selecting event types from MRC-FR-07 subscription list, viewing delivery history, replaying from DLQ.
- **MDB-FR-04** — Settlement detail shall show fee breakdown per payment (interchange, assessment, acquirer margin, MDR total).

## 21. Backoffice Workflow UI (BOF)

- **BOF-FR-01** — Deployed as separate SPA with OIDC SSO auth flow (per IDN-FR-06).
- **BOF-FR-02** — Screens (MVP): user list (с filter / search), user detail (с все linked данными), KYC review queue, AML alert queue, sanctions hit queue, chargeback arbitration queue, manual deposit queue, manual withdraw queue, SoF declarations list, audit log viewer, merchant list, frozen accounts list.
- **BOF-FR-03** — Compliance officer role shall see additional sections: sanctions hits, AML escalated cases, frozen accounts review.
- **BOF-FR-04** — Audit log viewer shall be filter-able (by actor, by subject, by event type, by date range); accessing audit log itself logged as read-audit.
- **BOF-FR-05** — Two-eyes flow UI: high-value pending operations marked в queue, button «Mark for second review» enabled for first operator; second operator sees first operator's identity, action taken, must approve / reject before release.

## 22. Audit Log (AUD)

- **AUD-FR-01** — Append-only audit log shall record every state-changing event с: actor (user/system identity), action, subject (entity reference), timestamp, before/after snapshot (где relevant), correlation_id, IP (для user actions).
- **AUD-FR-02** — Audit log immutability shall be enforced at DB level (no UPDATE or DELETE permissions on audit table; only INSERT).
- **AUD-FR-03** — Read-audit shall be enabled для compliance-sensitive entities: sanctions hits, AML alerts (CRITICAL severity), frozen accounts, full PAN reveal events.
- **AUD-FR-04** — Read-audit entries shall record: who read, what was read, when, IP.
- **AUD-FR-05** — Audit log query API shall be available только для compliance role и senior operator.
- **AUD-FR-06** — Audit log shall not be auto-deleted; retention indefinite в MVP.

## 23. Observability (OBS)

- **OBS-FR-01** — All application services shall emit structured JSON logs с standardized fields: timestamp, level, service, request_id, user_id (if applicable), event, message.
- **OBS-FR-02** — Logs shall be aggregated to local Loki instance; queryable through Grafana.
- **OBS-FR-03** — Prometheus metrics shall expose business metrics: payment success rate, KYC throughput, AML alert volume, chargeback rate, webhook delivery success rate.
- **OBS-FR-04** — Prometheus metrics shall expose infra metrics: HTTP request duration, error rates, queue depth, outbox lag.
- **OBS-FR-05** — OpenTelemetry traces shall be emitted across all critical paths (payment lifecycle, KYC flow, settlement); trace context propagated через Kafka headers и HTTP headers.
- **OBS-FR-06** — Traces shall be sent к local Tempo instance; queryable through Grafana.
- **OBS-FR-07** — PAN, CVV, full card data shall never appear в logs (см. VLT-FR-03).

## 24. RBAC (RBC)

- **RBC-FR-01** — Roles (MVP): `end_user`, `merchant_admin`, `merchant_member`, `backoffice_operator`, `compliance_officer`, `senior_compliance`.
- **RBC-FR-02** — Authorization checks shall be enforced server-side on every endpoint; client-side checks are UX optimization, never security.
- **RBC-FR-03** — End user can only access own wallet, cards, transactions, disputes (resource ownership check).
- **RBC-FR-04** — Merchant employees can only access их own merchant resources (scoped by `merchant_id`).
- **RBC-FR-05** — Backoffice operators can access pending manual operations, KYC queue, AML alerts (low/medium severity); cannot access sanctions hits, high-severity AML, frozen accounts unfreeze action.
- **RBC-FR-06** — Compliance officers can access all backoffice resources + sanctions hits + freeze/unfreeze + SAR markers.
- **RBC-FR-07** — Senior compliance can access all of compliance + two-eyes override (where applicable) + access to historical immutable records.
- **RBC-FR-08** — Vault detokenize API shall accept calls только от issuer subsystem service identity (mTLS or signed service tokens).

## 25. Idempotency (IDM)

- **IDM-FR-01** — All money-moving POST endpoints shall accept `Idempotency-Key` header (case-insensitive).
- **IDM-FR-02** — Idempotency keys shall be scoped per merchant per endpoint per 24 hours; replays beyond window are not idempotent.
- **IDM-FR-03** — Repeat request with same key + same request body shall return previous response with same status code, headers, body.
- **IDM-FR-04** — Repeat request with same key + different request body shall return 409 Conflict.
- **IDM-FR-05** — Webhook receivers (inbound Sumsub, Stripe) shall be idempotent по vendor event ID (already covered in KYC-FR-06, MRC-FR-03).

## 26. Transaction Limits (LMT)

- **LMT-FR-01** — Per-card transaction limits enforced at issuer authorization (ISU-FR-02).
- **LMT-FR-02** — MVP flat limits: unverified user (KYC not approved): €100/transaction, €500 cumulative; approved user: €10 000/transaction (per-day / per-month aggregate — later scope).
- **LMT-FR-03** — Deposit/withdraw thresholds: > €1 000 requires banking statement upload; > €10 000 requires SoF declaration; ≥ €10 000 requires two-eyes principle.
- **LMT-FR-04** — Internal transfer limits: configurable per user tier; MVP flat = €5 000 per transfer для approved users (later scope: tiered).

## 27. Two-Eyes Principle (2EY)

- **2EY-FR-01** — Manual operations ≥ €10 000 (deposit, withdraw) shall require two-eyes approval flow.
- **2EY-FR-02** — First operator reviews and marks `READY_FOR_SECOND_REVIEW`; cannot also approve.
- **2EY-FR-03** — Second operator (different identity, system-enforced) sees first operator's identity + action + reviews independently; approves или rejects.
- **2EY-FR-04** — Both operators recorded в audit log на final operation.
- **2EY-FR-05** — Senior compliance role override: для emergencies, single senior compliance officer może bypass two-eyes; bypass action highly audit-logged с justification.

---

## 28. Non-Functional Requirements (NFR)

### 28.1 Performance

- **NFR-FR-01** — Authorization request (sync from merchant POV) shall complete within P95 < 2s end-to-end. Acquirer ↔ network ↔ issuer authorization path uses **sync internal HTTP** (per NTW-FR-01), что делает таргет реалистичным; async Kafka используется только для clearing/settlement. P99 < 5s.
- **NFR-FR-02** — Payment intent creation API shall respond < 500ms P95.
- **NFR-FR-03** — Read endpoints (payment list, settlement view) shall respond < 300ms P95 for typical merchant.
- **NFR-FR-04** — Settlement batch shall process до 10 000 payments в reasonable time (target: < 5 min на dev machine).

### 28.2 Security

- **NFR-FR-05** — All HTTP traffic in production: TLS 1.2+ minimum; HSTS enforced.
- **NFR-FR-06** — Passwords stored с bcrypt / argon2 (cost factor calibrated, min 10).
- **NFR-FR-07** — API keys, OIDC secrets, vendor API tokens shall live в secret manager (Doppler / Vault / SSM); env vars в .env только для local development.
- **NFR-FR-08** — CSRF protection on all cookie-auth endpoints.
- **NFR-FR-09** — SQL injection prevention via parameterized queries / ORM; no string concatenation в queries.
- **NFR-FR-10** — XSS prevention в frontends: framework defaults (React/Vue/Svelte autoescaping), CSP header configured.

### 28.3 Reliability

- **NFR-FR-11** — All money-moving operations shall be atomic in ledger (single DB transaction).
- **NFR-FR-12** — Outbox dispatcher shall guarantee at-least-once webhook delivery.
- **NFR-FR-13** — Async event consumers shall be idempotent (могут безопасно retry).
- **NFR-FR-14** — Vault failure shall fail-closed (card payment denied) rather than fail-open.
- **NFR-FR-15** — Sumsub / OpenSanctions failure shall fail-closed (KYC remains pending, sanctions case opens).

### 28.4 Observability targets

- **NFR-FR-16** — Every domain event shall be traceable through trace ID across services и async hops.
- **NFR-FR-17** — Outbox lag (delay between event creation and dispatch) shall be visible как Prometheus metric.
- **NFR-FR-18** — Webhook delivery success rate, AML alert rate, chargeback rate shall be visible в Grafana dashboard.

### 28.5 Data retention

- **NFR-FR-19** — Audit log retention indefinite (MVP); production-grade timer-based archival policy — later scope.
- **NFR-FR-20** — Webhook DLQ retention: 30 days (см. WBH-FR-05).

---

## v0.2 Resolution Notes

Open questions из v0.1 разрешены (project owner confirmed 2026-05-15):

1. **Internal communication style** — hybrid: sync HTTP для authorization fast path (acquirer ↔ network ↔ issuer), async Kafka через outbox для clearing / settlement / outbound webhook dispatch. Reflected в NTW-FR-01. Final transport choices — в 04.
2. **Aggregate transaction limits** — per-transaction limits only в MVP (LMT-FR-02); per-day / per-month aggregate limits — later scope.
3. **Closed-loop in-house acquiring** — confirmed: cardholder = наш end user с issued card; merchant = separate entity с our settlement account в ledger (не end-user wallet); Stripe Connect используется только для outbound payouts merchant'у. Это и есть classic in-house acquiring scenario.
4. **Email uniqueness** — global across all user pools; same email не может быть зарегистрирован в multiple pools (избегаем role confusion в audit). Reflected в IDN-FR-02.
5. **PAN reveal** — rate-limited full PAN reveal с re-auth (CRD-FR-06 + UEW-FR-04) — это user-expected feature в современных fintech apps.
6. **Customer support / contact flow** — support ticket system как later scope. В MVP frozen user видит generic message со static contact email. Документировано в журнале 1.8 (02).
7. **Card replacement: new PAN** — на replacement генерируется new PAN, не reuse old. Reflected в CRD-FR-07.
8. **Chargeback reserve** — mechanism present in code, `chargeback_reserve_percent` configurable per merchant, default 0% в MVP. Allows learning pattern без overcomplicating default behavior. Reflected в SLM-FR-08.
9. **Authorization latency target** — P95 < 2s achievable благодаря sync HTTP fast path (Q1 resolution); async Kafka используется только для clearing/settlement. Reflected в NFR-FR-01.
