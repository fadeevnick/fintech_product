# Phase 04 Slice 02 — Stripe Connect Onboarding and Webhooks — Planning Note

Status: **WEBHOOK BACKEND/RUNTIME SUB-SCOPE EXECUTED v0.1; onboarding-start blocked on missing Stripe sandbox credentials**.

This document fixes the second implementation slice inside `Phase 04 — Merchant Onboarding and Public API Foundation`.

It is a pre-code scope contract. Runtime evidence lives in `planning/runtime_evidence_log.md`; factual state lives in `planning/implementation_status.md`.

---

## 1. Decision

`Phase 04 slice 02`:

```text
Implement real Stripe webhook receiver with HMAC-SHA256 signature verification and event-id idempotency, plus a persisted Stripe-account/onboarding-state schema sufficient to drive merchant kyb_status from account.updated webhooks. Document Stripe Connect onboarding-start as a blocker because real Stripe sandbox credentials are not available; do not fake any Stripe API call.
```

This means:

- New schema slot `merchant.stripe_account_links` is added but is **populated only**:
  - in production by a real Stripe Connect onboarding-start that calls Stripe API (not implemented in this slice — blocker);
  - in this slice, by an explicit DB seed step inside runtime scripts that documents what onboarding-start would have inserted.
- New schema slot `merchant.stripe_webhook_events` enforces idempotency by Stripe event id at the unique-index level and records every verified event for traceability. Signature/timestamp/payload rejections are audit-only so they cannot poison later valid retries for the same Stripe event id.
- The new endpoint `POST /webhooks/stripe/v1` reads raw body bytes, verifies the `Stripe-Signature` header against `STRIPE_WEBHOOK_SIGNING_SECRET` using Stripe's standard `t=<unix_ts>,v1=<hex_hmac_sha256>` scheme, applies a configurable timestamp tolerance window, and rejects any payload whose signature or timestamp does not validate.
- Valid `account.updated` events update the linked merchant's `kyb_status` based on `charges_enabled`/`payouts_enabled`/`details_submitted`. Unknown account ids and unhandled event types are persisted with explicit ignore outcomes; they do not error and do not move state.
- A duplicate Stripe event id is processed exactly once: the second delivery returns a successful idempotent response, does not re-execute side effects, and writes a `stripe.webhook_duplicate` audit row.
- Audit rows are written for every accepted state change and for every signature/timestamp rejection.
- `MRC-01` (onboarding start) is **explicitly not claimed** in this slice and is recorded as a blocker.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` (Phase 04) lists Stripe Connect sandbox onboarding and webhook signature/idempotency as the merchant-onboarding foundation that other phases depend on.
- `planning/runtime_checklists.md` keeps `MRC-01`, `MRC-02` and the webhook-driven path of `AUD-01` pending until a real Stripe webhook receiver exists.
- Phase 02 Slice 02 already created the merchant entity, employees, sessions and `merchant.merchants.kyb_status` field that this slice updates.
- Webhook signature verification and event-id idempotency are independent from public-API key lifecycle (`MRC-03`) and public-API idempotency (`PAY-01..PAY-03`), which are owned by another branch per `AGENT_TASK.md`.

If onboarding-start were attempted first against the real Stripe API, the slice would be blocked end-to-end. By scoping this slice to the webhook path and explicitly documenting the onboarding-start blocker, real production-working signature/idempotency proofs ship now and unblock later phases (KYB transitions, merchant-payment lifecycle gating, settlement payout webhooks).

## 3. Stripe Sandbox Credentials Blocker

`AGENT_TASK.md` §Notes:

> If real Stripe sandbox credentials are unavailable, stop at a documented blocker instead of faking Stripe behavior.

Current state at slice start:

- `STRIPE_SECRET_KEY` is not set in the local environment, `product/.env` or `product/.env.example`.
- `STRIPE_WEBHOOK_SIGNING_SECRET` is not set in the local environment.
- No Stripe Java/Kotlin SDK is on the classpath.

Decision recorded in this slice (confirmed by project owner before implementation):

- Onboarding-start (`MRC-01`) is **blocked** until Stripe Connect sandbox credentials are provided. No code path will call Stripe `Account.create` or `AccountLink.create` in this slice. No fake Stripe response object is constructed.
- The webhook receiver (`MRC-02`) is implementable now because Stripe webhook signature verification is purely a local HMAC-SHA256 computation against a per-environment signing secret. A locally-configured `STRIPE_WEBHOOK_SIGNING_SECRET` placeholder is real signature material; signing the test payload with the same secret produces a real Stripe-format signature that the server verifies through the same algorithm Stripe servers use.
- The webhook-driven branch of `AUD-01` is implementable now.

## 4. Exact Scope

In this slice:

1. Add `merchant.stripe_account_links` and `merchant.stripe_webhook_events` to the platform DB.
2. Add new package `com.minifin.platform.merchant` with: webhook controller, webhook service, signature verifier, repository and config binding.
3. Bind `STRIPE_WEBHOOK_SIGNING_SECRET` (and an optional tolerance-seconds value) through Spring `@ConfigurationProperties`.
4. Implement `POST /webhooks/stripe/v1` reading raw bytes, verifying signature, deduplicating by Stripe event id, processing `account.updated`, persisting outcomes, writing audit rows.
5. Wire the new endpoint into Spring Security as a public path (no platform session, vendor signature is the auth).
6. Add `STRIPE_WEBHOOK_SIGNING_SECRET` (placeholder/local-test value) into `product/.env.example` and inject it into the `platform` service via `product/deploy/docker-compose.yml`.
7. Add retained runtime scripts proving valid signature, invalid signature rejection, timestamp-tolerance rejection, idempotent duplicate-event behavior and audit rows.
8. Update `planning/runtime_evidence_log.md`, `planning/implementation_status.md`, `CURRENT.md`, `README.md` and `product/README.md`.
9. Record one logical commit on `task/p04s02-stripe-connect-webhooks`.

## 5. Explicitly In Scope

### Code Changes

- Platform Flyway migration `V10__merchant_stripe_webhooks.sql`. (V9 is reserved by the parallel merchant API keys / idempotency branch; this slice picks V10 to remain merge-independent.)
- New Kotlin package `com.minifin.platform.merchant`:
  - `MerchantStripeProperties.kt` (`@ConfigurationProperties` for signing secret + tolerance).
  - `StripeWebhookSignatureVerifier.kt` (HMAC-SHA256, Stripe `t=,v1=` parser, constant-time compare, tolerance window).
  - `MerchantStripeRepository.kt` (insert webhook event row, find/update Stripe account link, recompute merchant kyb_status).
  - `StripeWebhookService.kt` (verify, dedupe, dispatch, audit).
  - `StripeWebhookController.kt` (`POST /webhooks/stripe/v1`, raw body, error handler).
- Update `SecurityConfig.kt` to allow `/webhooks/**` without authentication (vendor signature is the auth).
- Update `product/deploy/docker-compose.yml` `platform` service env block to inject `STRIPE_WEBHOOK_SIGNING_SECRET`.
- Update `product/.env.example` with `STRIPE_WEBHOOK_SIGNING_SECRET` placeholder and a note.
- Update `product/apps/platform/src/main/resources/application.yml` with the new config binding.

### Behavioral Outcomes

- A correctly signed `account.updated` event for a known Stripe account id transitions the linked merchant's `kyb_status`:
  - `charges_enabled = true && payouts_enabled = true` ⇒ `VERIFIED`;
  - else if `details_submitted = true` ⇒ `PENDING`;
  - else current value preserved.
- The same event id delivered twice is processed exactly once. Subsequent deliveries return HTTP 200, do not re-run state changes, and produce a `stripe.webhook_duplicate` audit row.
- A payload signed with a different secret returns HTTP 400 with code `stripe_webhook_signature_invalid`, persists no `stripe_webhook_events` row, performs no state change and writes a `stripe.webhook_signature_invalid` audit row with outcome `FAILURE`.
- A payload whose `t=` is outside the tolerance window is rejected with HTTP 400 code `stripe_webhook_timestamp_outside_tolerance`, persists no `stripe_webhook_events` row, performs no state change and writes a failure audit row.
- An event for an unknown Stripe account id is persisted as `IGNORED_UNKNOWN_ACCOUNT` and does not move any merchant state.
- An event whose type is not handled is persisted as `IGNORED_UNHANDLED_TYPE`.

### Verification Outcomes

- `MRC-02` — pass for Stripe webhook signature verification and Stripe-event-id idempotency.
- `AUD-01` — pass extension for webhook-driven KYB state changes and webhook signature failures.
- `MRC-01` — **not claimed**; recorded as a blocker requiring Stripe Connect sandbox credentials.

## 6. Explicitly Out Of Scope

This slice does **not** implement:

- real Stripe Connect onboarding-start (`MRC-01`): no `Account.create` or `AccountLink.create` calls;
- merchant API key lifecycle (`MRC-03`) — owned by another branch;
- public Payments API surface or idempotency middleware (`PAY-01`, `PAY-02`, `PAY-03`) — owned by another branch;
- payment intent creation/authorization/capture/settlement/refund flows;
- merchant payout webhook handling (`payout.succeeded`, `payout.failed`);
- Stripe outbound payout initiation;
- Kafka publication of webhook events to downstream consumers (the Kafka outbox path will be added when downstream consumers exist);
- frontend implementation;
- changes to approved baseline docs under `planning/01..06`, `planning/design-details/**` or `planning/runtime_checklists.md`.

Specifically:

- do not edit any approved baseline doc; the existing `MRC-01`, `MRC-02`, `AUD-01` IDs already cover this slice;
- do not add a fake Stripe API client or fake responses;
- do not bypass signature verification under any flag; the only knob is the secret value supplied via env;
- do not call `Stripe-Signature` parsing into business logic before HMAC verification succeeds.

## 7. Webhook Shape

### 7.1 Endpoint

```http
POST /webhooks/stripe/v1
Stripe-Signature: t=1747407600,v1=<hex sha256>
Content-Type: application/json

<exact bytes that were signed>
```

Successful response:

```json
{ "data": { "received": true, "eventId": "evt_...", "outcome": "PROCESSED" }, "errors": [] }
```

Outcome enum:

- `PROCESSED` — first delivery, side effects applied;
- `DUPLICATE` — second+ delivery of the same `stripe_event_id`;
- `IGNORED_UNKNOWN_ACCOUNT` — known event type but no `merchant.stripe_account_links` row matched;
- `IGNORED_UNHANDLED_TYPE` — recognized but not yet acted upon (everything except `account.updated` for now).

Error responses:

- `400 stripe_webhook_signature_missing` — header missing;
- `400 stripe_webhook_signature_invalid` — header present but no `v1=` matches the local HMAC of `t.payload`;
- `400 stripe_webhook_timestamp_outside_tolerance` — `t=` not within tolerance;
- `400 stripe_webhook_payload_invalid` — body not parseable JSON / missing `id`/`type`.

### 7.2 KYB Mapping (account.updated)

```text
charges_enabled && payouts_enabled  -> kyb_status = 'VERIFIED'
details_submitted                   -> kyb_status = 'PENDING'  (unless already VERIFIED)
otherwise                           -> kyb_status unchanged
```

`REJECTED` is not driven from `account.updated` alone; that state will be added when explicit Stripe rejection signals are wired.

### 7.3 Schema

```sql
create table merchant.stripe_account_links (
    id uuid primary key,
    merchant_id uuid not null unique references merchant.merchants(id),
    stripe_account_id text not null unique,
    charges_enabled boolean not null default false,
    payouts_enabled boolean not null default false,
    details_submitted boolean not null default false,
    last_event_id text,
    last_event_received_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table merchant.stripe_webhook_events (
    id uuid primary key,
    stripe_event_id text not null unique,
    event_type text not null,
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    outcome text not null check (outcome in (
        'PROCESSED','DUPLICATE','IGNORED_UNKNOWN_ACCOUNT','IGNORED_UNHANDLED_TYPE',
        'REJECTED_SIGNATURE','REJECTED_TIMESTAMP','REJECTED_PAYLOAD'
    )),
    payload_jsonb jsonb not null,
    signature_header text
);
```

`merchant.stripe_webhook_events` is append-only by app convention (no UPDATE/DELETE inside service code) but does not require a DB-level append-only trigger because each row is a single immutable record of one verified delivery and `processed_at`/`outcome` are set once during the same insert transaction. Re-deliveries do not modify earlier rows; the unique index on `stripe_event_id` blocks duplicates instead. Signature/timestamp/payload rejections are audit-only and do not insert into this table, so a rejected delivery cannot poison a later valid retry for the same Stripe event id.

## 8. Concrete Files To Touch

### 8.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `product/README.md`
- `product/.env.example`
- `product/deploy/docker-compose.yml`
- `product/apps/platform/src/main/resources/application.yml`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/SecurityConfig.kt`

### 8.2 Create

- `product/apps/platform/src/main/resources/db/migration/V10__merchant_stripe_webhooks.sql`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/MerchantStripeProperties.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/StripeWebhookSignatureVerifier.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/MerchantStripeRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/StripeWebhookService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/StripeWebhookController.kt`
- `product/scripts/runtime/lib_phase04_stripe_webhook.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_signature_valid.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_signature_invalid.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_timestamp_tolerance.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_idempotency.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_bad_then_valid_retry.sh`

### 8.3 Do NOT Touch

- `planning/01_business_requirements.md` … `planning/06_implementation_guide.md`, `planning/design-details/**`, `planning/runtime_checklists.md` — approved baseline; check IDs already cover this slice.
- `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/IdentityRepository.kt` and merchant identity — Phase 02 contracts.
- Any `wallet`, `ledger`, `controls` package — out of scope.
- Public Payments API or merchant API key paths — owned by another branch.

## 9. Recommended Change Order

1. Add migration `V10__merchant_stripe_webhooks.sql`.
2. Add Kotlin classes (`merchant` package) and config properties.
3. Update `SecurityConfig.kt` to permit `/webhooks/**`.
4. Add env wiring (`.env.example`, `application.yml`, `docker-compose.yml`).
5. Build platform image and restart container.
6. Add runtime scripts and library helper.
7. Run runtime scripts and capture evidence.
8. Update status/evidence/CURRENT/README.
9. Commit.

## 10. Linked Runtime Checks

This slice exercises:

- `MRC-02` — Stripe webhook signature verification and event-id idempotency.
- `AUD-01` — webhook-driven KYB state change and signature-failure audit rows.

This slice **does not** exercise:

- `MRC-01` — recorded as a blocker on missing Stripe sandbox credentials.
- `MRC-03`, `PAY-01`, `PAY-02`, `PAY-03` — owned by another branch.

## 11. Linked ADRs

- ADR-003 — selective services topology (webhook lives on `platform`, consistent with merchant onboarding ownership).
- ADR-002 — SQL-first persistence (raw SQL for the Stripe-link/event tables).

No new ADR is required for this slice. If the future onboarding-start slice changes the Stripe-account ownership (e.g., moves to acquirer or to its own service), it should add an ADR at that time.

## 12. Verification Shape

After implementation:

- `cd product && docker compose -f deploy/docker-compose.yml build platform`
- `docker compose -f deploy/docker-compose.yml up -d platform`
- `product/scripts/runtime/reg_phase04_stripe_webhook_signature_valid.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_signature_invalid.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_timestamp_tolerance.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_idempotency.sh`
- `product/scripts/runtime/reg_phase04_stripe_webhook_bad_then_valid_retry.sh`
- regression: `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` (foundation `LDG-99`).

Evidence is appended to `planning/runtime_evidence_log.md`.

## 13. Pass Criteria

Slice considered implemented when:

- `POST /webhooks/stripe/v1` is reachable on `platform`;
- migration `V10__merchant_stripe_webhooks.sql` is applied;
- runtime scripts in §12 all pass;
- `planning/implementation_status.md`, `planning/runtime_evidence_log.md`, `CURRENT.md`, `README.md`, `product/README.md` reflect:
  - `MRC-02` pass with a reference to the runtime evidence section;
  - `AUD-01` pass extension for the webhook-driven branch;
  - `MRC-01` documented blocker on Stripe sandbox credentials;
- a single logical commit exists on `task/p04s02-stripe-connect-webhooks`.
