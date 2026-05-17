# Phase 06 Slice 02 — Outbound Webhook Retry and DLQ — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow backend/runtime slice after `Phase 06 Slice 01 — Outbound Merchant Webhook Delivery Foundation`.

It is a pre-code scope contract. Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after a later implementation agent actually builds and verifies the slice.

---

## 1. Decision

`Phase 06 slice 02`:

```text
Implement WBH-02 only: failed outbound webhook deliveries are retried on a persisted schedule and move to terminal DLQ after retry exhaustion.
Do not implement DLQ replay, new webhook event producers, settlement/capture/refund lifecycle, Stripe Connect onboarding or frontend UI.
```

This means:

- build on the existing Platform-owned outbound webhook foundation from Phase 06 Slice 01;
- keep the current `payment_intent.created` producer as the only event source needed for retry/DLQ verification;
- convert a failed delivery from a one-shot `FAILED` terminal state into a retryable persisted state with `next_retry_at` and attempt counters;
- move exhausted events to a clear terminal `DLQ` state;
- prove `WBH-02` with a real failing HTTP receiver and database assertions;
- leave replay from DLQ (`WBH-03`) for Phase 06 Slice 03.

## 2. Why This Slice Is Next

This slice is next because:

- Phase 06 Slice 01 already implemented one-time endpoint signing secrets, real signed HTTP delivery, durable `merchant.webhook_events` and `merchant.webhook_delivery_attempts` in `platform`;
- `WBH-01` passed, so the remaining delivery reliability gap is failure handling rather than happy-path signing;
- the current implementation records failed attempts and marks events `FAILED`, but does not yet schedule retries or define a durable DLQ transition;
- `planning/runtime_checklists.md` already defines `WBH-02` as “Webhook retry/DLQ”; 
- `WBH-03` replay needs a separate operator/API/command surface and should be planned after the terminal DLQ state exists.

If the project instead starts with replay (`WBH-03`) or broader payment lifecycle events, it would need to design around a failure state that is not yet production-working and would broaden the slice beyond the next reliability primitive.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Extend the existing Platform webhook event/attempt persistence model with retry scheduling fields and exhausted-failure semantics.
2. Add a bounded retry policy configuration suitable for local MVP runtime.
3. Teach the existing delivery service to distinguish:
   - successful delivery;
   - retryable failure with future `next_retry_at`;
   - exhausted failure that transitions to `DLQ`.
4. Add a narrow dispatcher path that can pick due retry events and attempt delivery again.
5. Keep the current service/module boundary in `platform`, where outbound webhook delivery currently lives.
6. Add a retained runtime script for `WBH-02` using a real receiver that returns failures until exhaustion.
7. Run targeted regressions to prove `WBH-01`, `MRC-05` and public API idempotency remain green.
8. Update evidence/status docs only after real runtime verification.

The implementation may use a simple synchronous dispatcher endpoint, service method invoked by a retained script, or lightweight scheduled task, as long as retry state is persisted and the runtime proof exercises real HTTP delivery attempts.

## 4. Explicitly In Scope

### 4.1 Product Behavior

- Retry failed outbound webhook deliveries for existing `merchant.webhook_events`.
- Persist retry schedule and attempt state so retries survive process restart.
- Increment attempt numbers monotonically per `(event_id, endpoint_id)`.
- Treat `2xx` receiver response as success and mark the event `DELIVERED`.
- Treat non-`2xx`, timeout and connection errors as failed attempts.
- For retryable failed attempts:
  - persist `FAILED` delivery attempt row;
  - update event status to retryable state;
  - persist next due time.
- After configured retry exhaustion:
  - persist the final failed attempt;
  - transition event status to `DLQ`;
  - preserve final error/status metadata for operator diagnosis.
- Keep signature headers and payload signing behavior from `WBH-01` unchanged on every retry attempt.

### 4.2 Event Types

The runtime proof should use only the existing event type:

```text
payment_intent.created
```

No new lifecycle producers are needed for this slice.

### 4.3 Retry Policy Recommendation

Recommended local MVP retry policy defaults:

```text
max_attempts = 3 total attempts per event/endpoint, including the initial attempt
backoff schedule = 1s, 2s for local runtime checks
connect_timeout = existing 2s unless already configurable
read_timeout = existing 3s unless already configurable
```

Recommended implementation detail:

- Store and compare `attempt_number` against `max_attempts`.
- Compute `next_retry_at` from the attempt that just failed.
- Keep test/runtime values env-configurable so retained scripts do not sleep for production-length intervals.
- Avoid adding broad cron/Kafka infrastructure just for this slice.

### 4.4 DLQ State Semantics

For this slice, DLQ means:

```text
merchant.webhook_events.status = 'DLQ'
```

Semantics:

- terminal for automatic retry;
- event remains queryable in DB for diagnosis;
- no more automatic attempts are made while status is `DLQ`;
- final failure metadata is available from the latest `merchant.webhook_delivery_attempts` row;
- replay is not available yet.

A separate `merchant.webhook_dlq` table is not required for this slice unless implementation discovers that event-level status cannot hold enough terminal metadata without contorting queries. Recommended default: use the existing `DLQ` status already allowed by `V14__merchant_outbound_webhook_delivery.sql`.

## 5. Explicitly Out Of Scope

This planning task does **not** implement Kotlin, SQL migrations, runtime scripts or frontend code.

The later implementation of this slice must also not implement:

- `WBH-03` DLQ replay;
- merchant dashboard DLQ UI;
- backoffice webhook operations UI;
- manual replay command/API;
- new event producers such as `payment_intent.authorized`, `payment_intent.captured`, `payment_intent.refunded`, `settlement.created` or chargeback events;
- capture, clearing, settlement, fee splitting, refunds, payouts or chargebacks;
- Stripe Connect onboarding (`MRC-01`) or Stripe sandbox API calls;
- card authorization changes (`PAY-04`/`PAY-05` are already complete and should only be regression-protected if touched);
- moving webhook ownership to `acquirer`;
- Kafka-based delivery topology unless it already exists and can be reused without expanding the slice;
- frontend SPA implementation;
- fake receiver success/failure logic inside product code.

Specifically:

- do not mark `WBH-02` complete until a real failing receiver has been retried and moved to DLQ;
- do not claim `WBH-03`, `SET-*`, `MRC-01`, `CHB-*` or frontend checks;
- do not include temporary orchestration task files in the later implementation commit.

## 6. Service And Module Boundary

### 6.1 Current Boundary

Outbound webhook delivery currently lives in `platform`:

```text
product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/
```

Current durable tables also live in the Platform DB under `merchant` schema:

```text
merchant.webhook_events
merchant.webhook_delivery_attempts
merchant.webhook_endpoints
```

For this slice, keep retry/DLQ in `platform` next to the existing `WBH-01` implementation.

### 6.2 Future Boundary

The approved architecture still has a future acquirer-owned webhook/delivery shape, but moving ownership is out of scope. A later acquirer ownership slice should be planned only when payment lifecycle ownership moves out of Platform or when a cross-service event bridge is introduced.

## 7. Expected DB / Model Changes

### 7.1 Migration Version Recommendation

Existing Platform migrations include:

- `V13__card_authorization_hold_support.sql`;
- `V14__merchant_outbound_webhook_delivery.sql`.

Recommended next Platform migration:

```text
product/apps/platform/src/main/resources/db/migration/V15__merchant_outbound_webhook_retry_dlq.sql
```

No Acquirer, Network, Issuer or Vault migrations are expected for this slice.

### 7.2 Recommended Event Table Extensions

Extend `merchant.webhook_events` with fields similar to:

```sql
alter table merchant.webhook_events
    add column retry_count integer not null default 0,
    add column max_attempts integer not null default 3,
    add column next_retry_at timestamptz,
    add column last_attempt_at timestamptz,
    add column last_error_type text,
    add column last_error_message text,
    add column last_http_status integer,
    add column dlq_at timestamptz;
```

Recommended status handling:

```text
PENDING    — event has never been delivered or is due for first dispatch
DELIVERED  — at least one matching endpoint delivery succeeded
FAILED     — latest attempt failed, but retry may still be scheduled
DLQ        — retry exhaustion reached; no automatic retry
```

If implementation prefers a distinct retryable status such as `RETRY_SCHEDULED`, it must update the check constraint and document the transition. Recommended default: keep the existing status enum and use `FAILED + next_retry_at is not null` as the retryable state.

### 7.3 Recommended Attempt Table Extensions

`merchant.webhook_delivery_attempts` already has:

- `attempt_number`;
- `status`;
- `http_status`;
- `response_body_snippet`;
- `error_type`;
- `error_message`;
- `attempted_at`;
- `next_retry_at`.

Recommended additions only if needed:

```sql
alter table merchant.webhook_delivery_attempts
    add column exhausted boolean not null default false;
```

This is optional because exhaustion can also be derived from `webhook_events.status = 'DLQ'` plus latest attempt number.

### 7.4 Recommended Indexes

Add or verify indexes for due retry selection:

```sql
create index if not exists idx_merchant_webhook_events_due_retry
    on merchant.webhook_events (status, next_retry_at)
    where status = 'FAILED' and next_retry_at is not null;

create index if not exists idx_merchant_webhook_events_dlq_created
    on merchant.webhook_events (merchant_id, created_at desc)
    where status = 'DLQ';
```

## 8. Recommended Change Order For Later Implementation

1. Add Platform migration `V15__merchant_outbound_webhook_retry_dlq.sql` for retry/DLQ state.
2. Extend outbound webhook models and repository methods:
   - select due retries;
   - update retry state;
   - mark DLQ;
   - expose latest attempt metadata for runtime assertions.
3. Refactor `OutboundWebhookService.deliver(...)` so failure handling computes retry vs DLQ based on configured max attempts.
4. Add a narrow dispatcher method for due retries.
5. Add configuration properties for max attempts and short local retry delays.
6. Extend the local webhook receiver to support deterministic failing responses if the existing receiver does not already support that mode.
7. Add retained `WBH-02` runtime script.
8. Run `WBH-02` and targeted regressions.
9. Update factual evidence/status docs only after verification.

## 9. Concrete Files To Touch In Later Coding Slice

### 9.1 Modify

Likely product files:

- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookModels.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/WebhookDeliveryProperties.kt`
- `product/apps/platform/src/main/resources/application.yml`
- `product/deploy/docker-compose.yml` only if new env defaults must be exposed
- `product/scripts/runtime/lib_phase06_webhooks.sh`
- `product/scripts/runtime/phase06_webhook_receiver.js`
- `planning/implementation_status.md` after verification
- `planning/runtime_evidence_log.md` after verification
- `CURRENT.md` only if handoff state changes

### 9.2 Create

Likely product files:

- `product/apps/platform/src/main/resources/db/migration/V15__merchant_outbound_webhook_retry_dlq.sql`
- `product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh`

### 9.3 Do Not Touch

- temporary orchestration task files.
- Acquirer/Network/Issuer/Vault application code — no service-boundary move in this slice.
- Frontend SPA files — no UI scope.
- Stripe integration files — `MRC-01` remains blocked on real sandbox credentials.
- Planning notes for previous slices — do not rewrite prior scope contracts after implementation.

## 10. Linked Runtime Checks

This slice exercises:

- `WBH-02` — failed outbound webhook delivery retries and moves to DLQ.

Targeted regressions that should remain green:

- `WBH-01` — successful signed delivery still works and records `DELIVERED`/`SUCCEEDED`.
- `MRC-05` — merchant webhook endpoint configuration remains role-protected and merchant-scoped.
- `PAY-01` — public API response shape remains valid.
- `PAY-02` — public API idempotency same-body replay still returns cached response.
- `PAY-03` — public API idempotency conflict still returns 409 and does not create duplicate webhook events.

Not claimed:

- `WBH-03` — DLQ replay;
- `SET-01..SET-04`;
- `MRC-01`;
- `CHB-*`;
- `UI-*`.

## 11. Runtime Verification Plan For `WBH-02`

The later implementation should add retained script:

```text
product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh
```

Recommended runtime proof:

1. Start or reuse isolated compose stack with Platform and Platform DB.
2. Register/login merchant admin.
3. Create merchant webhook endpoint subscribed to `payment_intent.created`, using a local receiver URL.
4. Create merchant API key.
5. Start a real local receiver that deterministically returns HTTP 500 for every delivery request.
6. Create a payment intent through `POST /v1/payment_intents`.
7. Assert initial event and first failed delivery attempt are persisted.
8. Trigger or wait for due retry dispatch using the short local retry schedule.
9. Assert at least three delivery attempts exist for the same event/endpoint with monotonically increasing attempt numbers.
10. Assert final event state is `DLQ` after configured exhaustion.
11. Assert the receiver actually received the same MiniFin webhook event ID multiple times and that each attempt carried valid MiniFin signature headers.
12. Assert no successful delivery is recorded for the failing receiver.
13. Run `WBH-01` happy-path regression to ensure a healthy receiver still gets one signed delivery and event status `DELIVERED`.

Expected `WBH-02` pass output should be similar to:

```text
WBH-02 webhook retry dlq pass
```

## 12. Targeted Regression Checks

After implementing this slice, run:

```text
product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh
product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh
product/scripts/runtime/reg_phase04_merchant_webhook_config.sh
product/scripts/runtime/reg_phase04_public_api_response_shape.sh
product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh
product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh
```

If local host port publishing is unreliable, use the established compose-network runner pattern from prior evidence, but record that fact explicitly in `planning/runtime_evidence_log.md`.

## 13. Documentation And Evidence Updates For Later Implementation Agent

After implementation and runtime verification, update:

- `planning/runtime_evidence_log.md` with a new factual section:

```text
2026-..-.. — Phase 06 Slice 02 Outbound Webhook Retry/DLQ Runtime Verification
```

- `planning/implementation_status.md` Phase 06 Slice 02 status and current gaps.
- `CURRENT.md` if the session ends with unfinished work or if current next-step state changes.
- `product/README.md` only if new operator commands or env vars are added.
- `.env.example` / compose documentation only if new retry-related env vars are exposed.

Evidence must include:

- actor used: merchant admin, merchant API key caller, failing local receiver;
- preconditions: active endpoint with signing secret and subscription to `payment_intent.created`;
- action: create payment intent, observe failed deliveries, run retries until DLQ;
- actual DB assertions: event ID, endpoint ID, attempt count, statuses, final `DLQ`, `next_retry_at` or `dlq_at` values;
- receiver assertions: repeated real HTTP requests, matching event ID, valid signature headers;
- result tag for `WBH-02` and each targeted regression.

## 14. Pass Criteria

The later implementation slice is complete only when:

- migration `V15__merchant_outbound_webhook_retry_dlq.sql` or equivalent persisted retry/DLQ model exists;
- failed deliveries are retried according to persisted schedule;
- retry exhaustion transitions the event to terminal `DLQ`;
- retained runtime script proves `WBH-02` with a real failing receiver;
- `WBH-01`, `MRC-05`, `PAY-01`, `PAY-02` and `PAY-03` targeted regressions remain green;
- factual evidence is appended to `planning/runtime_evidence_log.md`;
- `planning/implementation_status.md` records implementation state and remaining gaps;
- `WBH-03` remains explicitly unclaimed.

## 15. Next Planned Step

After this planning note is approved, implement the narrow Platform retry/DLQ slice for `WBH-02` only. The next planning artifact after implementation should be `Phase 06 Slice 03 — Outbound Webhook DLQ Replay` for `WBH-03`, unless the owner reprioritizes Stripe Connect sandbox onboarding (`MRC-01`) or settlement/capture work.
