# Phase 05 Slice 02 — Card Authorization Path — Planning Note

Status: **DRAFT v0.1 — planning-only; no product code implemented**.

This document fixes the second implementation slice inside `Phase 05 — Vault, Card Issuance and Authorization`.

It is a pre-code scope contract. Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md`.

---

## 1. Decision

`Phase 05 slice 02`:

```text
Implement the synchronous card authorization path from public payment intent authorization through
Acquirer → Network → Issuer, including approved wallet holds and structured declines. Do not implement
capture, clearing, settlement, refunds, payouts, chargebacks or outbound merchant webhooks.
```

This means:

- merchant-facing payment authorization becomes a real sync request-response path rather than the existing shell-only payment intent state;
- Acquirer owns payment-intent authorization state and calls Network;
- Network routes the authorization to Issuer by BIN and records route/audit metadata;
- Issuer validates card token/state, actor controls and available wallet balance, then either places a ledger hold or returns a structured decline;
- Platform Ledger remains the source of truth for balances and hold postings;
- Vault remains the only PAN store; authorization uses the card token produced by Phase 05 Slice 01 and does not expose PAN to Acquirer or Network;
- `PAY-04` and `PAY-05` are the target checks for this slice.

## 2. Why This Slice Is Next

This slice follows Phase 05 Slice 01 because:

- Phase 05 Slice 01 establishes the Vault token boundary and issuer card records required for safe card authorization;
- `planning/06_implementation_guide.md` defines Phase 05 as card issue plus sync authorization, and `PAY-04`/`PAY-05` remain pending after the issuance slice;
- Phase 04 already created merchant API keys, public API authentication, idempotency and the minimal `payment_intent` shell needed for a merchant-triggered auth attempt;
- Phase 03 already created wallet, ledger and hold-capable ledger foundations that the Issuer can use to reserve cardholder funds;
- the approved architecture explicitly models the fast path as sync HTTP: Acquirer → Network → Issuer → Platform Ledger.

If capture/settlement were implemented before this slice, they would have no real authorization/hold source to capture against. This slice creates the narrow production-working auth baseline while intentionally leaving post-authorization money movement to Phase 06.

## 3. Exact Backend/Runtime Scope

In the later coding slice:

1. Move or extend the existing public `POST /v1/payment_intents` shell so it can create and authorize a payment intent in the Acquirer-owned flow while preserving the existing public API auth/idempotency contract.
2. Add Acquirer persistence for payment intents and authorization attempts.
3. Add Network BIN-routing persistence and authorization route/audit records.
4. Add Issuer authorization persistence and hold records if not already present after Phase 05 Slice 01.
5. Add the Acquirer → Network internal authorization API/client.
6. Add the Network → Issuer internal authorization API/client.
7. Add Issuer authorization decision logic:
   - card token exists and is active;
   - card is not expired;
   - owning end-user actor is not blocked/frozen;
   - available wallet balance is enough after existing holds;
   - Platform Ledger hold posting succeeds.
8. Add Platform internal ledger/wallet APIs needed by Issuer if existing internal/probe endpoints are not sufficient:
   - wallet/cardholder reference lookup by stable wallet/end-user reference;
   - balance/available-balance lookup;
   - hold posting.
9. Add structured decline responses for insufficient funds, blocked/frozen actor, inactive/unknown card token and downstream service failures.
10. Add retained runtime scripts for `PAY-04` and `PAY-05`.
11. Build/start only affected services with the assigned runtime slot, run target checks and regressions, then record runtime evidence/status in the coding branch.

## 4. Explicitly In Scope

### 4.1 Services And Modules

#### Acquirer

Acquirer owns merchant-facing payment intent state and the public authorization request entrypoint.

Expected responsibilities:

- validate merchant API key and idempotency using the existing public API contract or the narrow service-auth bridge from Platform if API key validation remains Platform-owned during the transition;
- persist Acquirer-owned `payment_intents` with authorization state;
- accept card token source for authorization;
- call Network synchronously;
- map approved authorization to payment intent `AUTHORIZED`;
- map structured declines to payment intent `FAILED`;
- return the public `{data, errors}` response shape.

#### Network

Network owns routing and network audit for the authorization hop.

Expected responsibilities:

- maintain MVP BIN registry for the project-owned BIN;
- accept Acquirer authorization request via signed service token;
- route to Issuer by BIN/card token metadata;
- persist `network.authorization_routes` and/or `network.network_audit_log`;
- return Issuer response unchanged except for network-level envelope/correlation metadata;
- convert route failures/timeouts into structured service-failure declines.

#### Issuer

Issuer owns the card authorization decision and hold lifecycle foundation.

Expected responsibilities:

- accept Network authorization request via signed service token;
- load `issuer.cards` by `card_token`;
- reject unknown, inactive, blocked, lost or expired cards with structured decline;
- call Platform for actor-control and available-balance data;
- call Platform Ledger to place an authorization hold for approved auth;
- persist `issuer.card_authorizations` and `issuer.holds`;
- return `AUTH_APPROVED` with `auth_code`, `authorization_id` and `expires_at`, or `AUTH_DECLINED` with a structured reason.

#### Platform

Platform remains owner of Ledger, Wallet and Actor Controls.

Expected responsibilities:

- expose the narrow internal API(s) needed by Issuer for cardholder/wallet lookup, actor-control check, available balance and hold posting;
- post balanced ledger hold entries using the existing ledger posting primitive;
- maintain existing ledger append-only and reconciliation invariants;
- not own payment intent authorization state.

#### Vault

Vault is not a decisioning service in this slice.

Expected responsibilities:

- no new required behavior if card token metadata from Issuer is sufficient;
- no PAN exposure to Acquirer or Network;
- no merchant/server-side card-data ingestion.

### 4.2 Behavioral Outcomes

Approved authorization:

- Merchant calls public authorization path with an existing active card token and amount/currency.
- Acquirer creates or updates a payment intent and calls Network.
- Network routes to Issuer.
- Issuer confirms card state and cardholder wallet availability.
- Issuer posts a Platform Ledger hold.
- Issuer persists authorization/hold records.
- Acquirer transitions payment intent to `AUTHORIZED`.
- Public response returns `AUTH_APPROVED`/`AUTHORIZED` data and no errors.

Structured declines:

- Insufficient funds returns `AUTH_DECLINED` with `declineCode = "insufficient_funds"` and payment intent `FAILED`.
- Frozen/blocked actor returns `declineCode = "actor_blocked"` or `declineCode = "actor_frozen"` and payment intent `FAILED`.
- Unknown or inactive card token returns `declineCode = "card_not_found"` or `declineCode = "card_inactive"` and payment intent `FAILED`.
- Expired card returns `declineCode = "card_expired"` and payment intent `FAILED`.
- Network/Issuer/Platform/Vault dependency failures return a structured service-failure response without creating a ledger hold.

### 4.3 Verification Outcomes

- `PAY-04` — pass when a public authorization request follows Acquirer → Network → Issuer and creates a balanced ledger hold visible in Platform Ledger.
- `PAY-05` — pass when insufficient funds, blocked/frozen actor, inactive/unknown card token and service failure cases return structured declines and do not create holds.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- capture endpoint behavior beyond preserving existing stubs, if any;
- clearing, settlement, fee splitting or merchant settlement projection;
- refunds, payouts, chargebacks or dispute flows;
- outbound merchant webhook delivery (`WBH-*`);
- Stripe Connect onboarding (`MRC-01`);
- hosted payment form tokenization;
- server-to-server PAN ingestion;
- CVV validation;
- hold expiration scheduler/release flow;
- partial authorization;
- card limits, velocity rules, fraud scoring or sanctions screening beyond the existing actor-control/freeze/block hook;
- AML rules or account freeze review UI;
- frontend SPA implementation or UI prototypes;
- changes to approved baseline docs under `planning/01..06`, `planning/design-details/**` or `planning/runtime_checklists.md`.

Specifically:

- do not claim `SET-*`, `WBH-*`, `CHB-*`, `AML-*`, `KYC-*`, `SNX-*` or frontend checks;
- do not fake authorization success without ledger hold posting;
- do not store PAN outside Vault;
- do not create in-memory authorization/hold stores;
- do not silently convert dependency failures into approved authorizations.

## 6. DB Migration Expectations And Reserved Names

Confirm exact current migration numbers before coding. Based on the branch state at planning time, reserve:

### 6.1 Acquirer DB

Create:

```text
product/apps/acquirer/src/main/resources/db/migration/V2__payment_authorization_foundation.sql
```

Expected tables:

- `acquirer.payment_intents`
- `acquirer.authorization_attempts`

Expected `acquirer.payment_intents` fields:

- `id uuid primary key`;
- `merchant_id uuid not null`;
- `amount numeric(20,4) not null`;
- `currency text not null check (currency = 'EUR')`;
- `state text not null check (state in ('CREATED','AUTHORIZED','FAILED'))`;
- `card_token text`;
- `authorization_id uuid`;
- `auth_code text`;
- `decline_code text`;
- `decline_message text`;
- `created_at timestamptz not null default now()`;
- `updated_at timestamptz not null default now()`;
- `request_id text`;
- `correlation_id text`.

Expected `acquirer.authorization_attempts` fields:

- `id uuid primary key`;
- `payment_intent_id uuid not null`;
- `merchant_id uuid not null`;
- `amount numeric(20,4) not null`;
- `currency text not null`;
- `card_token text not null`;
- `status text not null check (status in ('APPROVED','DECLINED','ERROR'))`;
- `authorization_id uuid`;
- `auth_code text`;
- `decline_code text`;
- `decline_message text`;
- `network_route_id uuid`;
- `created_at timestamptz not null default now()`;
- `request_id text`;
- `correlation_id text`.

### 6.2 Network DB

Create:

```text
product/apps/network/src/main/resources/db/migration/V2__authorization_routing_foundation.sql
```

Expected tables:

- `network.bin_registry`
- `network.authorization_routes`
- `network.network_audit_log` if the baseline migration does not already contain a usable table.

MVP BIN registry should contain one active project-owned BIN mapped to `issuer`.

### 6.3 Issuer DB

Create:

```text
product/apps/issuer/src/main/resources/db/migration/V3__card_authorization_and_holds.sql
```

Expected tables:

- `issuer.card_authorizations`
- `issuer.holds`

Expected `issuer.card_authorizations` fields:

- `id uuid primary key`;
- `card_id uuid`;
- `card_token text not null`;
- `end_user_id uuid`;
- `wallet_account_id uuid`;
- `amount numeric(20,4) not null`;
- `currency text not null check (currency = 'EUR')`;
- `state text not null check (state in ('REQUESTED','APPROVED','HELD','DECLINED'))`;
- `auth_code text`;
- `decline_code text`;
- `decline_message text`;
- `expires_at timestamptz`;
- `created_at timestamptz not null default now()`;
- `updated_at timestamptz not null default now()`;
- `request_id text`;
- `correlation_id text`.

Expected `issuer.holds` fields:

- `id uuid primary key`;
- `authorization_id uuid not null`;
- `card_token text not null`;
- `wallet_account_id uuid not null`;
- `ledger_journal_entry_id bigint not null`;
- `amount numeric(20,4) not null`;
- `currency text not null check (currency = 'EUR')`;
- `state text not null check (state in ('HELD','RELEASED','CAPTURED','EXPIRED'))`;
- `expires_at timestamptz not null`;
- `created_at timestamptz not null default now()`;
- `updated_at timestamptz not null default now()`.

### 6.4 Platform DB

Create only if existing ledger/wallet/actor-control internals are insufficient:

```text
product/apps/platform/src/main/resources/db/migration/V13__card_authorization_hold_support.sql
```

Expected use:

- add stable account codes or metadata needed for `USER_WALLET_HOLD` accounts;
- add stored-procedure support for wallet-to-hold postings if existing `ledger.post_journal(...)` and ledger account model cannot already express the hold;
- avoid changing ledger semantics or mutating previous postings.

No Vault migration is expected in this slice unless Phase 05 Slice 01 implementation left an explicit gap needed for card token lookup. Vault should not gain authorization decision tables.

## 7. API Endpoints And DTO Shapes

### 7.1 Public Payments API

Preferred public route:

```http
POST /v1/payment_intents/{id}/authorize
Authorization: Bearer mfp_live_...
Idempotency-Key: <client-supplied>
Content-Type: application/json

{
  "cardToken": "tok_...",
  "amount": "12.50",
  "currency": "EUR"
}
```

Success:

```json
{
  "data": {
    "id": "pi_...",
    "object": "payment_intent",
    "amount": "12.50",
    "currency": "EUR",
    "state": "AUTHORIZED",
    "authorization": {
      "id": "auth_...",
      "status": "AUTH_APPROVED",
      "authCode": "A1B2C3",
      "expiresAt": "2030-01-01T00:00:00Z"
    }
  },
  "errors": []
}
```

Structured decline:

```json
{
  "data": {
    "id": "pi_...",
    "object": "payment_intent",
    "amount": "12.50",
    "currency": "EUR",
    "state": "FAILED",
    "authorization": {
      "status": "AUTH_DECLINED",
      "declineCode": "insufficient_funds",
      "declineMessage": "Insufficient available wallet balance"
    }
  },
  "errors": []
}
```

Allowed tactical alternative:

- If the existing `POST /v1/payment_intents` shell is simpler to evolve in one step, the coding slice may accept optional `cardToken` and perform authorization during create. If so, it must still preserve Phase 04 `PAY-01..PAY-03` behavior for non-authorizing shell requests or explicitly update retained scripts without weakening the public API/idempotency contract.

### 7.2 Acquirer → Network Internal API

```http
POST /internal/network/authorize
Authorization: ServiceToken acquirer -> network
Content-Type: application/json
```

Request:

```json
{
  "paymentIntentId": "pi_...",
  "merchantId": "uuid",
  "amount": "12.50",
  "currency": "EUR",
  "cardToken": "tok_...",
  "requestId": "req_...",
  "correlationId": "corr_..."
}
```

Response:

```json
{
  "routeId": "uuid",
  "status": "AUTH_APPROVED",
  "authorizationId": "uuid",
  "authCode": "A1B2C3",
  "expiresAt": "2030-01-01T00:00:00Z",
  "declineCode": null,
  "declineMessage": null
}
```

### 7.3 Network → Issuer Internal API

```http
POST /internal/issuer/authorize
Authorization: ServiceToken network -> issuer
Content-Type: application/json
```

Request shape should match the Acquirer → Network request plus network route metadata.

Response fields:

- `status`: `AUTH_APPROVED` or `AUTH_DECLINED`;
- `authorizationId`;
- `authCode`;
- `expiresAt`;
- `declineCode`;
- `declineMessage`;
- `ledgerHoldJournalEntryId` for approved authorizations.

### 7.4 Issuer → Platform Internal APIs

Use existing internal routes where possible. If new routes are required, keep them narrow:

```http
GET /internal/wallet/accounts/{walletAccountId}/available-balance
POST /internal/ledger/holds
GET /internal/identity/actor-controls/end-users/{endUserId}
```

Expected hold posting request:

```json
{
  "authorizationId": "uuid",
  "walletAccountId": "uuid",
  "amount": "12.50",
  "currency": "EUR",
  "eventSource": "card.authorization",
  "requestId": "req_...",
  "correlationId": "corr_..."
}
```

Expected hold posting response:

```json
{
  "journalEntryId": 123,
  "walletAccountId": "uuid",
  "holdAccountId": "uuid",
  "amount": "12.50",
  "currency": "EUR"
}
```

## 8. Payment-Intent State Transitions For Authorization Only

This slice may use the existing Phase 04 shell state as an input, but the Acquirer-owned authorization state must end with one of:

```text
CREATED -> AUTHORIZED
CREATED -> FAILED
```

If the current shell state is still `REQUIRES_PAYMENT_METHOD`, the coding slice may bridge it as:

```text
REQUIRES_PAYMENT_METHOD -> AUTHORIZED
REQUIRES_PAYMENT_METHOD -> FAILED
```

Rules:

- `AUTHORIZED` requires an approved Issuer authorization and a successful Platform Ledger hold.
- `FAILED` for auth declines is terminal for this slice; retry creates a new payment intent or a new authorization attempt according to the public API/idempotency behavior chosen during implementation.
- No `CAPTURED`, `SETTLED`, `REFUNDED`, `DISPUTED` or `CHARGED_BACK` transitions are allowed in this slice.

## 9. Ledger Hold Behavior

Approved authorization must create a balanced ledger journal entry that reserves funds without final debit.

Expected posting shape:

```text
debit  user_wallet
credit user_wallet_hold
```

Where:

- `user_wallet` is the cardholder wallet ledger account;
- `user_wallet_hold` is a hold sub-account associated with the same wallet/end user;
- amount and currency match the authorization request;
- `event_source = "card.authorization"`;
- external reference includes the Issuer authorization id and/or Acquirer payment intent id.

Rules:

- Available balance calculation must subtract existing holds.
- A declined authorization must not create a hold.
- A service-failure outcome must not create a hold.
- Hold release/expiration/capture conversion is explicitly out of scope, but the persisted hold must include `expires_at` defaulting to 7 days so Phase 06 can build on it.
- Ledger reconciliation (`LDG-05`/`LDG-99`) must still pass after approved and declined authorization checks.

## 10. Structured Decline Behavior

Minimum decline code set:

| Code | Trigger | Hold created? | Public handling |
|---|---|---:|---|
| `insufficient_funds` | Available wallet balance < requested amount. | no | HTTP 200 with `AUTH_DECLINED`, payment intent `FAILED`. |
| `actor_blocked` | End-user actor control is `BLOCKED`. | no | HTTP 200 with `AUTH_DECLINED`, payment intent `FAILED`. |
| `actor_frozen` | End-user actor control is `FROZEN`. | no | HTTP 200 with `AUTH_DECLINED`, payment intent `FAILED`. |
| `card_not_found` | Card token is unknown to Issuer. | no | HTTP 200 with `AUTH_DECLINED`, payment intent `FAILED`. |
| `card_inactive` | Card state is `BLOCKED`, `LOST` or otherwise not active. | no | HTTP 200 with `AUTH_DECLINED`, payment intent `FAILED`. |
| `card_expired` | Expiration date is in the past. | no | HTTP 200 with `AUTH_DECLINED`, payment intent `FAILED`. |
| `issuer_unavailable` | Network cannot reach Issuer or Issuer times out. | no | HTTP 502 or public structured service error; no `AUTHORIZED`. |
| `ledger_unavailable` | Issuer cannot reach Platform Ledger or hold post fails before commit. | no | HTTP 502 or public structured service error; no `AUTHORIZED`. |
| `network_unavailable` | Acquirer cannot reach Network or Network times out. | no | HTTP 502 or public structured service error; no `AUTHORIZED`. |

Service failures may be represented as public API errors rather than `AUTH_DECLINED` business declines, but they must be structured and must not transition the payment intent to `AUTHORIZED`.

## 11. Service-Auth Expectations

Use signed service tokens already established for internal service calls.

Required caller/target pairs:

- `service:acquirer` → `service:network` for `/internal/network/authorize`;
- `service:network` → `service:issuer` for `/internal/issuer/authorize`;
- `service:issuer` → `service:platform` for internal wallet/ledger/actor-control calls.

Rules:

- wrong/missing service token returns `401/403 service_auth_denied`;
- service identity must be logged with `request_id`/`correlation_id`;
- service-token implementation may remain long-lived shared-secret MVP style, matching `04_architecture.md`;
- do not introduce fake trust-by-network-location checks.

## 12. Runtime Scripts And Check IDs To Add

Expected retained scripts:

- `product/scripts/runtime/lib_phase05_card_authorization.sh`
- `product/scripts/runtime/reg_phase05_authorization_approved_hold.sh`
- `product/scripts/runtime/reg_phase05_authorization_structured_declines.sh`

This slice exercises:

- `PAY-04` — approved authorization hold.
- `PAY-05` — structured authorization declines.

Recommended regressions:

- `VLT-01`/`VLT-02` spot check or Phase 05 issuance helper prerequisite, because authorization depends on a real card token;
- `PAY-01`/`PAY-02`/`PAY-03` public API response/idempotency regressions if the public payment route changes;
- `LDG-05` or `LDG-99` after approved and declined authorization paths;
- `RUN-01` for affected services (`platform`, `acquirer`, `network`, `issuer`, `vault`).

No new runtime check IDs are required.

## 13. Expected Evidence Updates

After implementation, append a new section to `planning/runtime_evidence_log.md` for:

```text
2026-..-.. — Phase 05 Slice 02 Card Authorization Runtime Verification
```

Evidence must include:

- actor used:
  - merchant API key for public authorization request;
  - service identities `acquirer`, `network`, `issuer` for internal calls;
  - end-user/cardholder fixture for card and wallet setup;
- preconditions:
  - an active card token exists from Phase 05 Slice 01 issuance path;
  - the cardholder wallet has enough balance for the approved case;
  - a second case has insufficient balance;
  - blocked/frozen actor and inactive/unknown card cases are prepared;
- action taken:
  - approved authorization;
  - insufficient-funds decline;
  - blocked/frozen actor decline;
  - inactive/unknown card decline;
  - at least one service-failure decline/error branch if scriptable without destabilizing the shared runtime;
- expected and actual results for `PAY-04` and `PAY-05`;
- evidence reference:
  - public API responses;
  - Acquirer payment-intent rows;
  - Network route/audit rows;
  - Issuer authorization/hold rows;
  - Platform Ledger postings and reconciliation output;
- verification script paths and commit hash containing retained scripts;
- result tags with explicit remaining gaps for any non-pass.

Update `planning/implementation_status.md` only after code/runtime verification, with factual state rather than a journal.

For this planning-only branch, `planning/implementation_status.md` may list this planning note as draft if the branch wants discoverability, but it must not claim implementation or runtime evidence.

## 14. Concrete Files To Touch In The Later Coding Slice

### 14.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `product/README.md`
- `product/.env.example` if new service-auth or base URL env vars are needed.
- Existing public API/idempotency code if payment authorization remains temporarily Platform-hosted.
- Existing service-auth configuration/classes if needed.

### 14.2 Create

- Acquirer migration `V2__payment_authorization_foundation.sql`.
- Network migration `V2__authorization_routing_foundation.sql`.
- Issuer migration `V3__card_authorization_and_holds.sql`.
- Platform migration `V13__card_authorization_hold_support.sql` only if needed.
- Acquirer payment authorization Kotlin classes/controllers/clients.
- Network authorization router Kotlin classes/controllers/clients.
- Issuer authorization and hold Kotlin classes/controllers/clients.
- Platform internal wallet/ledger/actor-control APIs only if missing.
- Internal contract DTOs under `product/backend/libs/contracts-internal` if current convention uses shared DTOs.
- Retained runtime scripts listed in §12.

### 14.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved baseline.
- `planning/runtime_checklists.md` — `PAY-04` and `PAY-05` already exist.
- Vault persistence/encryption code except to consume existing safe token metadata.
- Frontend SPA files and standalone UI prototypes.
- Kafka/outbox/settlement/webhook delivery code.

## 15. Recommended Change Order For The Later Coding Slice

1. Confirm Phase 05 Slice 01 implementation state and exact endpoint/DTO names for card issuance and card token metadata.
2. Confirm current migration numbers in `acquirer`, `network`, `issuer` and `platform`.
3. Add Acquirer migration for payment intents and authorization attempts.
4. Add Network migration for BIN registry and authorization routes/audit.
5. Add Issuer migration for authorizations and holds.
6. Add Platform hold-account support only if existing ledger can not already represent wallet holds.
7. Implement service-auth clients/controllers in the order Issuer internal API → Network router → Acquirer caller.
8. Implement Issuer decisioning and ledger hold posting.
9. Implement public authorization route and idempotency behavior.
10. Add retained runtime scripts.
11. Build/start affected services with the assigned runtime slot.
12. Run `PAY-04`, `PAY-05` and targeted regressions.
13. Update evidence/status/CURRENT/README/product README.
14. Commit the completed coding slice.

## 16. Linked Runtime Checks

This slice exercises:

- `PAY-04` — approved authorization hold.
- `PAY-05` — structured authorization declines.

This slice **does not** exercise:

- `SET-01`..`SET-04` — capture, clearing, settlement and refund are Phase 06;
- `WBH-01`..`WBH-03` — outbound webhook delivery is Phase 06;
- `CHB-01`..`CHB-05` — chargeback lifecycle is Phase 09;
- frontend `UI-*` checks.

## 17. Linked ADRs

- ADR-002 — SQL-first persistence for payment, route, authorization, hold and ledger tables.
- ADR-003 — selective services topology: Acquirer, Network, Issuer, Vault and Platform remain separate deployment units.
- ADR-004 — Ledger remains central source of truth; approved authorization hold must be a Platform Ledger posting, not an Issuer-only balance mutation.

No new ADR is required for this planning note. A future ADR may be needed if implementation changes the accepted sync HTTP authorization topology, moves Ledger ownership out of Platform, or replaces signed service tokens with a different service-auth model before hardening.

## 18. Verification Shape

Expected coding-slice verification:

```bash
cd product
docker compose -f deploy/docker-compose.yml build platform acquirer network issuer vault
docker compose -f deploy/docker-compose.yml up -d platform acquirer network issuer vault
scripts/runtime/reg_phase05_authorization_approved_hold.sh
scripts/runtime/reg_phase05_authorization_structured_declines.sh
scripts/runtime/reg_phase03_ledger_reconciliation.sh
scripts/runtime/reg_phase01_runtime_health.sh
```

In executor branches, run these only with the runtime isolation variables assigned by the orchestrator for that branch.

`reg_phase05_authorization_approved_hold.sh` must prove:

- a real active card token exists;
- wallet available balance is sufficient before auth;
- public auth response is approved/authorized;
- Acquirer, Network and Issuer records exist with the same correlation/request lineage;
- Platform Ledger contains a balanced wallet-to-hold journal entry;
- reconciliation still passes.

`reg_phase05_authorization_structured_declines.sh` must prove:

- insufficient funds decline;
- blocked or frozen actor decline;
- inactive or unknown card token decline;
- at least one service-failure structured error/decline if feasible in local runtime;
- no decline branch creates a ledger hold.

## 19. Pass Criteria

Slice considered implemented only when:

- public merchant authorization reaches Acquirer → Network → Issuer synchronously;
- approved authorization transitions the payment intent to `AUTHORIZED`;
- approved authorization persists Issuer authorization/hold records;
- approved authorization creates a balanced Platform Ledger hold posting;
- declined authorizations return structured decline data;
- insufficient funds, actor blocked/frozen, inactive/unknown card and service-failure branches do not create ledger holds;
- public API response shape and idempotency regressions still pass where affected;
- retained scripts for `PAY-04` and `PAY-05` pass;
- `SET-*`, `WBH-*`, `CHB-*` and frontend checks remain explicitly unclaimed;
- evidence/status files record the factual runtime result.

## 20. Risks And Open Questions

### 20.1 Risks

- **Public API ownership transition** — Phase 04 temporarily hosted public API routes in Platform. This slice may need to either move the relevant path to Acquirer or add a narrow Platform→Acquirer bridge. The coding slice must avoid duplicating payment-intent truth in both services without a clear owner.
- **Ledger hold semantics** — existing wallet withdraw hold code may not match card authorization hold needs exactly. Reuse the ledger primitive, but do not mutate old postings or bypass balanced-entry enforcement.
- **Service-auth breadth** — three internal hops are needed. If service-auth is still minimal, harden only the required routes rather than introducing broad internal trust.
- **Decline vs error mapping** — business declines (`insufficient_funds`, card/actor state) should be distinct from service failures (`issuer_unavailable`, `ledger_unavailable`) so runtime evidence does not overclaim authorization decisioning.
- **State naming mismatch** — Phase 04 shell used `REQUIRES_PAYMENT_METHOD`; design-details state machine uses `CREATED`. The coding slice must preserve backwards-compatible tests or explicitly migrate the shell state to the Acquirer state machine.

### 20.2 Open Questions

No owner-level open questions are required before implementation. The coding agent should resolve tactical details from the current codebase:

1. whether the public authorization route is implemented as `POST /v1/payment_intents/{id}/authorize` or as an authorize-on-create extension to `POST /v1/payment_intents`;
2. whether Phase 04 public API auth/idempotency is moved into Acquirer now or bridged through Platform for one more slice;
3. the exact project-owned BIN/card-token metadata available after Phase 05 Slice 01;
4. whether existing wallet hold/account schema can represent `user_wallet_hold` without a new Platform migration;
5. how to script one deterministic service-failure branch without damaging shared runtime state.
