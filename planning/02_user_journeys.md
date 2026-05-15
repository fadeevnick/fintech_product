# 02 User Journeys — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

History:
- v0.1 — first draft journeys для четырёх ролей (end user, merchant, backoffice operator, compliance officer) под scope, утверждённый в `01_business_requirements.md` APPROVED v0.3.
- v0.2 — open questions v0.1 разрешены: email + wallet ID для transfer, two-eyes (>€10k), Stripe Connect payout в MVP, hosted form default + s2s supported, simplified chargeback ENUM, SoF declaration для >€10k deposits, email verified hard gate, flat card limits в MVP, single webhook endpoint с event filter.

---

## Conventions

Каждый journey описан с полями:

- **Actor** — основная роль, инициирующая journey.
- **Preconditions** — что должно быть истинно до старта.
- **Touches** — какие UI / API / external integrations / internal subsystems задействованы.
- **Steps** — пронумерованные шаги happy path.
- **Alt paths** — ключевые ветвления (не все edge cases, только архитектурно значимые).
- **Postconditions** — наблюдаемое состояние после journey.
- **Key audit events** — события, которые обязаны попасть в audit log.

Маркеры в шагах:
- `[manual]` — операция выполняется руками backoffice-оператора (banking rails substitute).
- `[vendor: X]` — взаимодействие с реальным sandbox provider (Sumsub / Stripe / OpenSanctions / OIDC).
- `[sim: X]` — internal card network simulation subsystem (issuer / network / acquirer / vault).
- `[async]` — событие проходит через outbox / queue, не sync request-response.

---

## 1. End User Journeys

### 1.1 Registration & KYC

**Actor:** End user (новый).

**Preconditions:** none (открытая регистрация).

**Touches:** end-user web SPA, identity API, Sumsub sandbox, sanctions API (OpenSanctions), case management, audit log.

**Steps:**
1. User открывает end-user web, заполняет registration form (email, password, name, DOB, country).
2. Identity service создаёт user record в `kyc_status = NOT_STARTED`, session, шлёт email verification link.
3. User кликает link, email подтверждён.
4. User инициирует KYC: end-user web редиректит на Sumsub Web SDK с applicant token. `[vendor: Sumsub]`
5. User загружает документы (ID + selfie) внутри Sumsub flow. `[vendor: Sumsub]`
6. На submit Sumsub переходит applicant в pending, шлёт callback URL → нашему backend webhook. `[vendor: Sumsub, async]`
7. KYC service апдейтит `kyc_status = IN_REVIEW`, открывает KYC case.
8. Параллельно: sanctions screening против OpenSanctions по name + DOB + country. `[vendor: OpenSanctions]`
9. Sumsub возвращает финальный verdict через webhook (обычно через минуты-часы в sandbox). `[vendor: Sumsub, async]`
10. На `clear` verdict → `kyc_status = APPROVED`, case закрыт, end user может пользоваться wallet operations выше unverified threshold.

**Alt paths:**
- **A.** Sanctions hit на шаге 8 → KYC case → sanctions sub-case `OPEN`, user остаётся в `IN_REVIEW`, требуется compliance officer manual decision (см. journey 3.5 / 4.2).
- **B.** Sumsub возвращает `consider` (uncertain) → KYC case остаётся `IN_REVIEW`, попадает в backoffice queue для manual review (см. journey 3.3).
- **C.** Sumsub возвращает `rejected` → `kyc_status = REJECTED`, user видит причину (high-level reason code), может resubmit с другими документами.

**Postconditions (happy path):**
- `users.kyc_status = APPROVED`.
- KYC case `CLOSED_APPROVED`.
- Audit log: registration, email verification, KYC submission, Sumsub verdict, sanctions check result.
- End user может issue card, request deposit выше KYC threshold, transfer.

**Key audit events:** `user.registered`, `user.email_verified`, `kyc.submitted`, `kyc.verdict_received`, `sanctions.checked`.

---

### 1.2 First Deposit (manual operator path)

**Actor:** End user (KYC approved), backoffice operator (отдельно).

**Preconditions:** `users.kyc_status = APPROVED`, end user имеет wallet account в EUR.

**Touches:** end-user web, wallet API, backoffice UI, ledger, audit log.

**Steps:**
1. End user в end-user web нажимает «Deposit», вводит сумму (EUR). Если amount > €10k → system требует заполнить **Source of Funds declaration** (structured form: source category — salary / business income / savings / investment / other; employment / business details; supporting docs upload опционально) до показа payment instructions. SoF declaration linked к deposit request, stored через case management (отдельный case type). После SoF (если требовался) — end user получает payment instructions (bank reference number, beneficiary info — статически отображённые, поскольку banking rails — manual).
2. End user (вне системы) делает банковский перевод по этим реквизитам.
3. Через какое-то время backoffice operator видит, что физически пришли деньги (это и есть banking rails substitute — у нас нет реального коннекта к банку). Operator открывает backoffice UI, secshion `Pending deposits` либо `Manual operations`.
4. Operator создаёт deposit запись: user, amount, reference number, attached proof (банковская выписка как файл в MinIO опционально). `[manual]`
5. Wallet service делает ledger posting: `debit suspense_bank_inflow_account, credit user_wallet_account`. Posting проходит через outbox.
6. End user в end-user web видит обновлённый balance, новую transaction в истории.

**Alt paths:**
- **A.** AML rule срабатывает при deposit (например, structuring detection) → создаётся AML alert, deposit completed, но может быть frozen retrospectively (см. journey 3.4).
- **B.** Operator выявляет нестыковку (сумма не совпадает с переводом, нет user'а) → deposit не создаётся, operator связывается с end user'ом вне системы.

**Postconditions:**
- Ledger: balance user_wallet_account ↑ на сумму.
- Audit log: `deposit.requested` (end user side), `deposit.processed` (operator side) с operator actor + reference.
- Outbox event `WalletCredited` для downstream (AML monitoring).

**Key audit events:** `deposit.requested`, `deposit.processed`, ledger postings linked.

---

### 1.3 Card Issuance

**Actor:** End user (KYC approved).

**Preconditions:** `users.kyc_status = APPROVED`, wallet account exists.

**Touches:** end-user web, card service, vault service `[sim: vault]`, issuer subsystem `[sim: issuer]`, audit log.

**Steps:**
1. End user в end-user web нажимает «Issue card».
2. Card service запрашивает vault на generate card data (PAN + expiration + CVV, реалистичная BIN-структура — мы owner'им один BIN). `[sim: vault]`
3. Vault генерирует PAN, expiration, CVV; хранит их у себя; возвращает только `card_token` + last4 для display.
4. Card service создаёт card record в issuer subsystem: `card_id`, `token`, `last4`, `expiration_month/year`, `state = ACTIVE`, link to user wallet account. `[sim: issuer]`
5. End user видит карту в card list (last4, expiry visible; CVV — separate reveal flow с rate limit, доступ через vault detokenize только из issuer subsystem).

**Alt paths:**
- **A.** Пользователь хочет посмотреть full PAN — вызов rate-limited endpoint, который через issuer запрашивает vault detokenize, return через short-lived JWT с display data. `[sim: vault]`
- **B.** Block / replace card — state machine `ACTIVE → BLOCKED`, новая card issued тем же путём.

**Postconditions:**
- Issuer держит card record.
- Vault держит PAN/CVV под `card_token`.
- End user видит card в end-user web.

**Key audit events:** `card.issued`, `card.viewed_full_pan` (rate-limited, audit-tracked).

---

### 1.4 Card Payment at Merchant

**Actor:** End user (как cardholder), merchant (как initiator API call).

**Preconditions:** end user имеет active card с достаточным available balance; merchant onboarded с API key.

**Touches:** merchant Payments API, acquirer `[sim]`, network `[sim]`, issuer `[sim]`, vault `[sim]`, ledger, outbox, merchant webhook delivery, audit log.

**Steps:**
1. Merchant integration собирает card details от end user'а одним из двух путей: **(a) Hosted Payment Page (default)** — мы host'им payment form на нашем домене; card data идёт напрямую от browser в vault через client-side tokenization, merchant API получает только `card_token` (минимизирует PCI scope merchant'а); **(b) Server-to-server (advanced)** — merchant отправляет card data через API endpoint, который immediately tokenize в vault и не оседает в acquirer subsystem или logs. PAN ни в одном пути не доходит до acquirer subsystem. `[sim: vault]`
2. Merchant вызывает `POST /v1/payment_intents` с `amount`, `currency=EUR`, `card_token` (или raw card data → tokenize first), idempotency key.
3. Acquirer subsystem создаёт `payment_intent` (state `CREATED`), validate'ит merchant. `[sim: acquirer]`
4. Acquirer форвардит authorization request в network. `[sim: acquirer → network, async]`
5. Network looks up BIN → issuer routing, доставляет в issuer. `[sim: network → issuer, async]`
6. Issuer проверяет: card active, available balance ≥ amount, transaction within configured per-card limits (см. §5.6), AML rules don't block, sanctions don't block. На pass — кладёт hold на wallet (`debit available_balance suspense, credit user_wallet_hold`), отвечает `AUTH_APPROVED` с auth_code. `[sim: issuer]`
7. Response идёт обратно: issuer → network → acquirer. `[async]`
8. Acquirer обновляет payment_intent state `AUTHORIZED`, отвечает merchant'у с `payment_intent_id`, `status=authorized`, `auth_code`.
9. Merchant (в реальном flow — почти сразу) вызывает `POST /v1/payment_intents/{id}/capture`.
10. Acquirer переводит state `CAPTURED`, ставит запись в clearing batch.
11. **End-of-day clearing batch** [async, scheduled]: acquirer aggreget'ит все captures за день, отправляет clearing file в network. Network distribute'ит issuer'ам. Issuer финализирует hold → actual debit cardholder'а wallet. `[sim: clearing async cycle]`
12. **Next day settlement**: network считает net positions, fee splits (interchange to issuer, assessment to network, остаток acquirer'у). Ledger postings: cardholder wallet debit fully, merchant settlement account credit (amount − MDR), issuer interchange revenue credit, network assessment credit, acquirer margin credit. `[sim: settlement async]`
13. Merchant видит payment в `available` settlement balance на T+1.
14. Merchant получает webhook `payment_intent.succeeded` (на step 8) и `payment_intent.captured` (на step 10), `payment.settled` (на step 13).

**Alt paths:**
- **A.** Issuer declines на step 6 (insufficient funds / fraud / frozen card) → `AUTH_DECLINED`, payment_intent `FAILED`, merchant получает decline response с reason code.
- **B.** Network timeout (внутренний artificial timeout для testing) → retry policy в acquirer, eventually fail with `AUTH_TIMEOUT`.
- **C.** Auth approved но capture никогда не вызван (auth expires через 7 days) → hold release: issuer возвращает funds, ledger reversal.
- **D.** Refund flow (separate journey — см. merchant journey 2.4).

**Postconditions (happy path):**
- Cardholder wallet: debited (initially hold, eventually posted).
- Merchant settlement account: credited по T+1.
- Ledger: 5 postings (cardholder, merchant, issuer interchange, network assessment, acquirer margin) с invariant `Σ debits == Σ credits`.
- Webhooks delivered к merchant'у.

**Key audit events:** `payment.created`, `payment.authorized`, `payment.captured`, `payment.cleared`, `payment.settled`, `webhook.delivered` для каждого.

---

### 1.5 Internal Transfer (end user → end user)

**Actor:** End user A, end user B.

**Preconditions:** оба пользователя `kyc_status = APPROVED`; A имеет достаточный balance.

**Touches:** end-user web, wallet API, ledger, AML rule engine, audit log.

**Steps:**
1. A в end-user web ищет B по **email или wallet ID** (phone-based search — later scope, требует phone verification flow).
2. A заполняет amount, optional memo.
3. Wallet service validate'ит: A balance ≥ amount, A `kyc_status = APPROVED`, B exists, B not frozen.
4. AML pre-check (synchronous) на rules, которые блокируют transfer (sanctions on B; aggregate velocity rule).
5. Ledger posting (atomic): `debit A wallet, credit B wallet`. Outbox event `InternalTransferCompleted`.
6. A видит подтверждение, transaction в history; B видит incoming transaction.

**Alt paths:**
- **A.** AML rule trips post-completion (например, structuring) → completed, но создан alert (см. journey 3.4).
- **B.** Sanctions check on B fails (например, B был cleared earlier, но re-screened на каждый transaction) → transfer blocked synchronously, sanctions case opened.
- **C.** B `kyc_status != APPROVED` → block (transfer to non-KYC'd user — это compliance risk).

**Postconditions:**
- Ledger: A balance ↓, B balance ↑.
- Audit log: `transfer.initiated`, `transfer.completed` / `transfer.blocked`.

**Key audit events:** `transfer.initiated`, `transfer.completed` либо `transfer.blocked` с reason.

---

### 1.6 Chargeback Initiation

**Actor:** End user (cardholder).

**Preconditions:** существует прошлый card payment (по journey 1.4) внутри chargeback window (60 days default).

**Touches:** end-user web, issuer subsystem `[sim]`, network `[sim]`, acquirer `[sim]`, merchant dashboard, case management, ledger, audit log.

**Steps:**
1. End user в end-user web → transaction history → выбирает прошлый payment → нажимает «Dispute».
2. End user выбирает reason из dropdown (упрощённый набор: `fraud_no_authorization`, `goods_not_received`, `goods_not_as_described`, `duplicate_charge`).
3. Issuer subsystem создаёт chargeback record: state `INITIATED`, links to original payment. `[sim: issuer]`
4. Chargeback case opened (через unified case management abstraction).
5. Issuer credit'ит cardholder wallet provisionally (это reality — cardholder обычно получает money back немедленно, merchant платит позже если проиграет). Ledger: `debit acquirer dispute_reserve, credit cardholder wallet`. `[sim: ledger]`
6. Issuer шлёт chargeback message в network → acquirer → merchant. `[sim: async]`
7. State transitions to `MERCHANT_NOTIFIED`.
8. Acquirer создаёт chargeback notification на merchant'е, шлёт webhook `dispute.created` к merchant'у с reason code, deadline (10-30 days в нашей simulation).
9. Merchant видит chargeback в merchant dashboard (см. journey 2.5).

**Alt paths:** dispute может быть закрыт по разным путям — см. merchant journey 2.5 и backoffice journey 3.6.

**Postconditions:**
- Chargeback в state `MERCHANT_NOTIFIED`.
- Cardholder provisionally credited.
- Merchant'у delivered webhook.
- Case opened.

**Key audit events:** `chargeback.initiated`, `cardholder.provisionally_credited`, `webhook.delivered (dispute.created)`.

---

### 1.7 Withdraw (manual operator path)

**Actor:** End user (KYC approved), backoffice operator (separately).

**Preconditions:** `users.kyc_status = APPROVED`, wallet balance ≥ requested amount, нет active freeze.

**Touches:** end-user web, wallet API, backoffice UI, ledger, audit log.

**Steps:**
1. End user в end-user web → «Withdraw» → вводит amount + destination bank account details (свободный текст в MVP, structured fields в later scope).
2. Wallet service создаёт withdraw_request (state `PENDING`); freezes amount on wallet (hold), но не списывает.
3. AML pre-check (velocity / structuring on withdrawals).
4. Backoffice operator видит pending withdraw в queue, проверяет (нет alerts, AML clear, KYC active).
5. Operator approves withdraw. `[manual]` Ledger: hold → actual debit (`debit user_wallet, credit suspense_bank_outflow`). Withdraw_request → `APPROVED`.
6. Operator физически делает банковский перевод вне системы (banking rails substitute).
7. Operator marks withdraw_request as `COMPLETED` после физического перевода. `[manual]`

**Alt paths:**
- **A.** AML rule trips → withdraw_request → `HELD_FOR_REVIEW`, compliance officer escalation.
- **B.** Operator rejects (insufficient compliance documentation, suspicious pattern) → withdraw_request `REJECTED`, hold released на wallet.

**Postconditions:**
- Wallet balance ↓ на сумму.
- Audit log: `withdraw.requested`, `withdraw.approved`, `withdraw.completed` с operator actor.

**Key audit events:** `withdraw.requested`, `withdraw.approved`, `withdraw.completed`.

---

### 1.8 Account Freeze Experience

**Actor:** End user (passive — frozen by compliance), compliance officer (active — see journey 4.1).

**Preconditions:** AML rule trip / sanctions hit / manual review escalation.

**Touches:** end-user web, wallet API (read-only), audit log.

**Steps (from end user POV):**
1. End user пытается transfer / withdraw / card payment.
2. Wallet API возвращает `403 Forbidden` с reason code `ACCOUNT_FROZEN`. Generic message в UI: «Your account is under review. Please contact support.»
3. End user может только: view balance, view history. Все write operations blocked.
4. End user contact'ит support (вне системы; в MVP нет support inbox).
5. Compliance officer резолвит case (см. journey 4.1) — может unfreeze.
6. End user на следующий attempt — operations работают; в audit log событие `account.unfrozen`.

**Postconditions:** transient — freeze может быть permanent (на true sanctions match) или transient (на false-positive AML clearance).

**Key audit events:** `account.freeze_blocked_operation` (на каждую попытку write во время freeze).

---

## 2. Merchant Journeys

### 2.1 Merchant Onboarding (Stripe Connect KYB)

**Actor:** Merchant employee (тот, кто регистрирует компанию).

**Preconditions:** none.

**Touches:** merchant dashboard, identity API (отдельный merchant pool), Stripe Connect sandbox `[vendor: Stripe]`, audit log.

**Steps:**
1. Merchant employee регистрируется в merchant dashboard (email + password, отдельный user pool от end user).
2. Merchant создаёт merchant entity: company name, country, business type.
3. Merchant initiate'ит Stripe Connect Express onboarding flow: backend создаёт Stripe Account, генерирует Account Link, редиректит в Stripe-hosted onboarding form. `[vendor: Stripe]`
4. Merchant заполняет business details в Stripe form (legal entity name, tax ID, business address, beneficial owners, bank account для payouts). `[vendor: Stripe]`
5. Stripe возвращает merchant в наш dashboard (success redirect).
6. Stripe шлёт webhook `account.updated` с verification status. `[vendor: Stripe, async]`
7. На `charges_enabled = true && payouts_enabled = true` → merchant `kyb_status = APPROVED`.
8. Merchant видит status «Approved», может generate API key (см. journey 2.2).

**Alt paths:**
- **A.** Stripe возвращает `restricted` (verification incomplete) → merchant видит required fields, может resubmit.
- **B.** Stripe `rejected` → merchant `kyb_status = REJECTED`, не может generate API key.

**Postconditions:**
- Merchant entity created с `kyb_status = APPROVED`, linked Stripe Account ID.
- Merchant имеет settlement account в нашем ledger.

**Key audit events:** `merchant.registered`, `merchant.stripe_onboarding_started`, `merchant.kyb_verdict`.

---

### 2.2 API Integration Setup

**Actor:** Merchant employee.

**Preconditions:** `merchant.kyb_status = APPROVED`.

**Touches:** merchant dashboard, identity API (key management), audit log.

**Steps:**
1. Merchant в dashboard → `API Keys` → `Generate new key`.
2. System генерирует public/secret key pair (HMAC secret for signing webhooks; API key for inbound auth). Возвращает один раз; merchant копирует, повторно нельзя увидеть.
3. Merchant добавляет webhook endpoint URL в `Webhooks` section, выбирает события для подписки.
4. System отправляет test webhook для verification (signed payload). Merchant видит result (delivered / failed).

**Alt paths:**
- **A.** Key rotation: merchant generates new key, старый помечается `rotated_at`, grace period 30 days перед disable.
- **B.** Key revocation: immediate `revoked`, все incoming requests с этим key получают 401.

**Postconditions:**
- API key и webhook endpoint configured.
- Merchant готов к integration.

**Key audit events:** `api_key.generated`, `api_key.rotated`, `api_key.revoked`, `webhook_endpoint.configured`.

---

### 2.3 Payment Processing (Merchant API side of journey 1.4)

**Actor:** Merchant (через API).

**Preconditions:** API key valid, merchant `kyb_status = APPROVED`, target end user имеет card.

**Touches:** Payments API, acquirer `[sim]`, full card lifecycle subsystems.

**Steps:** см. journey 1.4 steps 2-14 с POV merchant'а как API caller.

Дополнительно: merchant видит payment в `payment list` в dashboard. Sort/filter by status, date, amount.

**Key audit events:** `merchant.api_call (payment_intent.create)` с request fingerprint.

---

### 2.4 Refund

**Actor:** Merchant.

**Preconditions:** existing captured payment в state `CAPTURED` или `SETTLED`; не уже refunded fully; в refund window (180 days default).

**Touches:** Payments API, acquirer `[sim]`, network `[sim]`, issuer `[sim]`, ledger, merchant webhook, end-user web.

**Steps:**
1. Merchant вызывает `POST /v1/payments/{id}/refunds` с `amount` (full or partial), `reason`, idempotency key. Либо через dashboard «Refund» button.
2. Acquirer создаёт refund record, validates (amount ≤ remaining refundable).
3. Acquirer шлёт refund instruction в network → issuer. `[sim: async]`
4. Issuer credit'ит cardholder wallet, ledger reversal entry.
5. Settlement adjustment: acquirer adjustment к merchant settlement balance в следующем batch (с rebate fee расщеплением — interchange typically НЕ возвращается).
6. End user видит refund в transaction history.
7. Merchant получает webhook `refund.succeeded`.

**Alt paths:**
- **A.** Refund после chargeback initiated — typically blocked (merchant должен ответить через chargeback evidence, не через refund); или merchant accept'ит chargeback вместо refund.
- **B.** Partial refund — multiple refunds возможны пока aggregate ≤ original.

**Postconditions:**
- Cardholder credited.
- Merchant settlement debited (amount − fee rebates).

**Key audit events:** `refund.created`, `refund.completed`, ledger postings linked.

---

### 2.5 Chargeback Evidence Submission

**Actor:** Merchant.

**Preconditions:** chargeback initiated по journey 1.6, state `MERCHANT_NOTIFIED`.

**Touches:** merchant dashboard, case management, S3/MinIO, ledger (hold mechanics), audit log.

**Steps:**
1. Merchant видит notification в dashboard (badge + email опционально).
2. Merchant открывает chargeback detail: original payment, dispute reason, customer-provided narrative, deadline.
3. Merchant decides: `accept` или `represent`.
4. Если `represent`:
   - Заполняет evidence narrative (text).
   - Загружает supporting documents (delivery proof, signed receipt, customer correspondence, IP logs) → S3/MinIO.
   - Submit'ит.
5. Chargeback state `EVIDENCE_SUBMITTED`. Merchant'у delivered webhook `dispute.evidence_received`.
6. Backoffice operator получает task на arbitration (см. journey 3.6).

Если `accept`:
- Merchant подтверждает.
- Chargeback state → `LOST`. Settlement adjustment: merchant settlement debited fully, dispute reserve released. Cardholder credit становится permanent.

**Alt paths:**
- **A.** Deadline expires без response → automatic `LOST` (default loss).

**Postconditions (на `represent`):**
- Evidence files в S3/MinIO.
- Case state `EVIDENCE_SUBMITTED`.

**Key audit events:** `dispute.evidence_submitted` либо `dispute.accepted_by_merchant`.

---

### 2.6 Settlement View

**Actor:** Merchant.

**Preconditions:** merchant имеет processed payments.

**Touches:** merchant dashboard, settlement reporting.

**Steps:**
1. Merchant в dashboard → `Settlements`.
2. Видит rolling balance: `pending` (captured но not yet settled), `available` (settled, ready for payout), `held` (под disputes / chargeback reserves).
3. Видит history settlement batches: batch ID, date, gross volume, fees breakdown (interchange, assessment, acquirer margin), refunds, chargebacks, net amount.
4. Merchant triggers payout: «Payout to bank account» button → initiates payout через Stripe Connect (real Stripe sandbox payout). Stripe handles outbound transfer к merchant's connected bank account; мы receive payout webhook на final state. Settlement balance state machine: `available` → `payout_pending` → `payout_succeeded` либо `payout_failed`. Ledger: payout — это `debit merchant_settlement_account, credit stripe_payout_clearing_account` на initiation, и `debit stripe_payout_clearing, credit external_settled` на webhook confirmation. `[vendor: Stripe, async]`

**Postconditions:** read-only journey.

**Key audit events:** `merchant.settlements_viewed` (для compliance auditability).

---

## 3. Backoffice Operator Journeys

### 3.1 Process Manual Deposit

См. journey 1.2 steps 3-6 с POV operator.

**Key responsibilities operator-side:**
- Verify proof (banking statement upload mandatory если sum > €1000).
- Cross-check user identity (no operations on frozen / non-KYC'd accounts).
- Verify SoF declaration present для deposits > €10k (см. journey 1.2 step 1).
- **Two-eyes principle для high-value deposits (>€10k):** second operator approval mandatory before release; первый operator marks `READY_FOR_SECOND_REVIEW`, второй reviews и approves либо rejects. Audit log записывает обоих actors.

---

### 3.2 Process Manual Withdraw

См. journey 1.7 steps 4-7 с POV operator.

**Key responsibilities operator-side:**
- AML pre-check verification (read alert queue для user).
- Bank account validation (manual cross-check destination).
- **Two-eyes principle для high-value withdraws (>€10k):** second operator approval mandatory before release.

---

### 3.3 KYC `consider` Verdict Review

**Actor:** Backoffice operator (или compliance officer).

**Preconditions:** Sumsub returned `consider` verdict для user; KYC case в `IN_REVIEW`.

**Touches:** backoffice UI, case management, Sumsub data (через adapter), audit log.

**Steps:**
1. Operator открывает KYC queue → выбирает case.
2. Видит Sumsub-returned data (document images через secure preview, OCR результаты, liveness score, similarity score), reason for `consider` (e.g., «document partially obscured», «face match below threshold»).
3. Operator может: approve (override Sumsub), reject (override Sumsub), request resubmit (user будет prompted в end-user web загрузить новые документы).
4. Decision recorded в case + audit log + Sumsub via API callback (если Sumsub поддерживает override).

**Alt paths:**
- **A.** Operator escalates to compliance officer (если signals fraud / sanctions concern).

**Postconditions:**
- KYC case closed либо state moved.
- Audit с operator actor + rationale text (required).

**Key audit events:** `kyc.manual_decision` с actor, decision, rationale.

---

### 3.4 AML Alert Review (False Positive Path)

**Actor:** Backoffice operator или compliance officer.

**Preconditions:** AML rule fired, alert в `OPEN`.

**Touches:** backoffice UI, case management, audit log, wallet (для read history).

**Steps:**
1. Operator opens AML alert queue, выбирает alert.
2. Видит: triggering rule, triggering event (transaction details), user profile, recent transaction history (last 30 days), prior alerts, KYC documents.
3. Operator analyzes, заключает что это false positive (например, velocity rule сработал на legitimate paycheck-distribution behavior).
4. Operator marks decision `CLOSED_FALSE_POSITIVE`, заполняет required rationale text.
5. Optional: add user в `whitelist` для конкретного rule (предотвращает повторные trips на этот pattern).

**Postconditions:**
- AML alert case `CLOSED_FALSE_POSITIVE`.
- User account не имеет active freeze (если freeze был — снят).

**Key audit events:** `aml.alert_reviewed`, `aml.alert_closed`, optional `aml.whitelist_added`.

---

### 3.5 Sanctions Hit Clearance (False Positive)

**Actor:** Compliance officer (предпочтительно — RBAC может ограничивать operator).

**Preconditions:** sanctions hit `OPEN`, user blocked.

**Touches:** backoffice UI, case management, OpenSanctions data, audit log.

**Steps:**
1. Officer opens sanctions hits queue, выбирает hit.
2. Видит: user data (name, DOB, country), matched watchlist entry (name, AKAs, DOB, country, sanctions program), similarity score.
3. Officer determines: false positive (e.g., common name match, completely different DOB и country).
4. Marks `CLEARED_FALSE_POSITIVE`, заполняет required rationale.
5. Creates `cleared_exception` для этого user — последующие matches на тот же watchlist entry не re-block'ают.

**Postconditions:**
- User unblocked (если был blocked solely из-за sanctions).
- Exception record создан.

**Key audit events:** `sanctions.hit_reviewed`, `sanctions.cleared`, `sanctions.exception_created`.

---

### 3.6 Chargeback Arbitration

**Actor:** Backoffice operator (играет роль card network arbitration в нашей simulation).

**Preconditions:** chargeback в state `EVIDENCE_SUBMITTED`.

**Touches:** backoffice UI, case management, MinIO (evidence files), ledger, audit log.

**Steps:**
1. Operator opens chargeback arbitration queue.
2. Видит: original payment, dispute reason, customer narrative, merchant evidence (text + attached files).
3. Operator evaluates evidence quality, makes decision: `WON` (merchant prevails) или `LOST` (cardholder prevails).
4. Decision recorded:
   - `WON` → cardholder provisional credit reversed (cardholder debited), dispute reserve released to merchant, ledger postings.
   - `LOST` → cardholder credit becomes permanent, merchant fully debited.
5. Both parties get webhooks (`dispute.won` / `dispute.lost`).

**Postconditions:**
- Chargeback в terminal state.
- Ledger reconciled.

**Key audit events:** `dispute.arbitration_decided` с actor, decision, rationale.

---

## 4. Compliance Officer Journeys (RBAC-subset of backoffice)

### 4.1 High-Severity AML Escalation + Account Freeze

**Actor:** Compliance officer.

**Preconditions:** AML alert escalated (либо auto-frozen на critical severity).

**Touches:** backoffice UI, case management, wallet (freeze capability), audit log.

**Steps:**
1. Officer opens escalated alert (либо initial AML rule trip пришла как `CRITICAL`).
2. Если account еще не frozen — officer initiate'ит freeze: state `account_freezes.ACTIVE`, freeze reason (text), case linked.
3. Frozen account: все write operations возвращают 403 (см. journey 1.8).
4. Officer проводит investigation (читает history, prior cases).
5. Decision:
   - `escalate_for_sar_filing` — пометить case as `MARKED_FOR_SAR_FILING`. (Само SAR filing — later scope, см. 01 §6.2.)
   - `permanent_freeze` — freeze остаётся indefinitely.
   - `unfreeze` — false positive after investigation, lift freeze.

**Postconditions:**
- Account either frozen permanently, unfrozen, либо marked for future SAR.

**Key audit events:** `account.frozen`, `account.unfrozen`, `case.marked_for_sar`.

---

### 4.2 Sanctions True Match + Permanent Block

**Actor:** Compliance officer.

**Preconditions:** sanctions hit `OPEN`, similarity high, manual review confirms genuine match.

**Touches:** backoffice UI, case management, wallet (permanent block), audit log.

**Steps:**
1. Officer reviews sanctions hit (см. journey 3.5 steps 1-2).
2. Confirms true match.
3. Marks `TRUE_MATCH`. User account permanently blocked (different from temporary freeze — irreversible через regular path).
4. Case marked for SAR filing (later scope).
5. Audit retained per regulatory retention (5+ years в реальности; в MVP — без retention enforcement, но immutability append-only audit).

**Postconditions:**
- User permanent_block status.
- Case marked for SAR.

**Key audit events:** `sanctions.true_match_confirmed`, `account.permanent_block`.

---

## 5. Cross-Cutting Concerns Observable Across Journeys

### 5.1 Audit log (write + read)

- **Write audit**: каждый state transition, каждое доменное событие, каждое manual operation — append-only с actor, timestamp, before/after snapshot (где relevant), correlation_id.
- **Read audit** (compliance entities): попытки чтения sanctions hits, AML alerts, SAR cases — записываются с actor + timestamp. Это критично для no-tipping-off discipline.

### 5.2 RBAC enforcement points

- End-user API endpoints: ownership check (можешь читать только свой wallet / cards / history).
- Merchant API: scoped по `merchant_id` через API key.
- Backoffice UI: role-based access (`operator` vs `compliance_officer` vs `senior_compliance`).
- Vault detokenize: запрещено для всех, кроме issuer subsystem service identity.

### 5.3 Idempotency

- All public write API endpoints (Payments API, Refunds, etc.) принимают `Idempotency-Key` header. Repeated request с тем же key → cached previous response.
- Webhook delivery handlers (Sumsub, Stripe) на нашей стороне идемпотентны по vendor event ID.

### 5.4 Error / retry behavior (user-observable)

- API user-facing errors: structured JSON `{error: {code, message, hint}}`, без internal stack traces.
- Async retries (webhook delivery, vendor callback handling): exponential backoff с jitter, DLQ после N retries (см. 04 для exact policy).
- Timeouts на vendor calls: configured per adapter, fail-soft где legitimate (e.g., sanctions check timeout → fail-closed / block).

### 5.5 Webhook delivery (out)

- Каждый merchant-facing event → signed webhook payload (HMAC).
- Retries: exponential backoff (1m, 5m, 30m, 2h, 12h, 24h, 48h, 72h).
- DLQ после max retries.
- Merchant dashboard имеет webhook delivery log, replay button для DLQ events.
- **Single endpoint URL per merchant** с event filter (merchant subscribes to specific event types через dashboard). Multiple endpoints с per-event routing — later scope.

### 5.6 Card payment limits (MVP)

- **Unverified user** (KYC not approved): hard cap €100 per transaction, €500 cumulative — этот path в основном для allowing card issuance до KYC clear для demo / testing purposes; в production fintech такое чаще disallowed целиком.
- **KYC-approved user**: flat cap €10 000 per transaction в MVP. Per-day / per-month aggregate limits и tiered limits по risk score — later scope.
- Limit enforcement в Issuer subsystem на authorization step (см. journey 1.4 step 6).

### 5.7 Chargeback reason codes

- MVP использует simplified ENUM: `fraud_no_authorization`, `goods_not_received`, `goods_not_as_described`, `duplicate_charge`.
- Mapping к real Visa/MC 4-digit codes (4837, 4855, 4853, etc.) задокументирован в design-details (когда тот будет), real codes в API — later scope.

---

## v0.2 Resolution Notes

Open questions из v0.1 разрешены (project owner confirmed 2026-05-15):

1. **Transfer identifier** — email + wallet ID; phone — later scope.
2. **Two-eyes principle** — mandatory для deposits/withdraws > €10k, второй operator approval обязателен.
3. **Merchant payout** — через Stripe Connect sandbox в MVP; полный payout lifecycle (см. journey 2.6 step 4).
4. **Card data ingestion** — оба пути supported, default hosted form (см. journey 1.4 step 1).
5. **Chargeback reason codes** — simplified ENUM в MVP, mapping к real codes в design-details (см. §5.7).
6. **Source of funds declaration** — required для deposits > €10k, structured form linked к case management (см. journey 1.2 step 1).
7. **Email verification** — hard gate перед KYC initiation (already reflected в journey 1.1 step 3-4).
8. **Card payment limits** — flat caps в MVP (см. §5.6); tiered limits — later scope.
9. **Webhook subscription** — single endpoint per merchant с event filter (см. §5.5).
