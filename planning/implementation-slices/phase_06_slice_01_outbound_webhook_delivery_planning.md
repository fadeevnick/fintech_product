# Phase 06 Slice 01 — Outbound Merchant Webhook Delivery Foundation — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the first implementation slice inside `Phase 06 — Clearing, Settlement, Refunds and Webhooks`.

It is a pre-code scope contract. Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md`.

---

## 1. Decision

`Phase 06 slice 01`:

```text
Implement the outbound merchant webhook delivery foundation using the existing merchant webhook endpoint
configuration, with real signed HTTP delivery to a local test receiver and persisted delivery attempts.
Do not fake future payment lifecycle events, settlement, refunds, chargebacks or DLQ replay.
```

This means:

- the already implemented `merchant.webhook_endpoints` configuration from Phase 04 Slice 03 becomes usable by a real delivery worker;
- a narrow first event source is allowed only if it reflects a real already-existing event, such as `payment_intent.created` from the current payment-intent shell;
- the delivery path signs payloads with a merchant endpoint secret and performs actual HTTP POST delivery to a local receiver;
- delivery attempts are persisted with status, response metadata and timestamps;
- retry/DLQ is either implemented as a narrow failure boundary for this delivery foundation or explicitly left for a later Phase 06 slice;
- no capture, clearing, settlement, refund, payout, card authorization or chargeback behavior is introduced.

## 2. Why This Slice Is Next

This slice can proceed now even though Stripe Connect onboarding (`MRC-01`) remains blocked because:

- outbound merchant webhooks are a platform-owned capability, not a Stripe-owned capability;
- Phase 04 Slice 03 already implemented merchant webhook endpoint configuration (`MRC-05`) and dashboard payment reads (`MRC-04`);
- Phase 04 Slice 01 already implemented public API key authentication, public write idempotency and a persisted payment-intent shell;
- `MRC-01` requires real Stripe Connect sandbox credentials and must not be faked, but webhook delivery to merchants does not require Stripe credentials;
- `planning/06_implementation_guide.md` includes outbound webhook signing/retry/DLQ in Phase 06, and `planning/runtime_checklists.md` already reserves `WBH-01..WBH-03`;
- a delivery foundation is useful before full capture/settlement producers exist, as long as the slice only emits events that are backed by real current state changes.

The slice therefore advances a Phase 06 foundation without pretending that the broader payment lifecycle exists.

## 3. Exact Backend/Runtime Scope

In the later implementation pass, this slice should:

1. Extend Platform's existing merchant webhook endpoint model with one-time signing secret generation and hashed-at-rest storage if the current Phase 04 table does not already provide it.
2. Add an outbound webhook event/delivery model for real persisted events and attempts.
3. Produce a narrow `payment_intent.created` webhook event from the existing public `POST /v1/payment_intents` shell only after the payment intent is durably persisted.
4. Deliver pending webhook events to active merchant webhook endpoints whose `enabledEvents` contains the event type.
5. Sign every delivery with HMAC-SHA256 over a fixed timestamp + raw body canonical string.
6. Persist every attempt, including success, terminal failure and transient failure metadata.
7. Provide a local test receiver strategy for runtime verification that validates signature and records received payloads.
8. Add retained runtime scripts for `WBH-01` and targeted regressions.
9. Update factual status/evidence only after runtime checks actually run.

The implementation may run delivery synchronously from a worker/script-triggered path or through a minimal outbox/worker loop, but it must persist event and attempt state before claiming `WBH-01`.

## 4. Explicitly In Scope

### 4.1 Product Behavior

- Create or rotate a webhook signing secret for each merchant webhook endpoint.
- Show the raw signing secret only once on endpoint creation or explicit rotation.
- Store only a secret hash and non-secret prefix/fingerprint at rest.
- Deliver real JSON payloads by HTTP POST to configured endpoint URLs.
- Include signature headers on each delivery:
  - `MiniFin-Webhook-Id`;
  - `MiniFin-Webhook-Timestamp`;
  - `MiniFin-Webhook-Signature`;
  - `MiniFin-Webhook-Event`;
  - `MiniFin-Webhook-Attempt`.
- Persist outbound event records and delivery attempt records.
- Treat any `2xx` receiver response as delivered.
- Treat non-`2xx`, timeout and connection errors as failed attempts.
- Record response status code, bounded response body snippet, error class/message and attempt timestamp.

### 4.2 Event Types

The only event type expected in this first slice is:

```text
payment_intent.created
```

Allowed source:

- existing public `POST /v1/payment_intents` shell from Phase 04.

Payload shape:

```json
{
  "id": "evt_...",
  "type": "payment_intent.created",
  "createdAt": "iso-8601",
  "merchantId": "uuid",
  "data": {
    "object": {
      "id": "uuid",
      "object": "payment_intent",
      "amount": "12.50",
      "currency": "EUR",
      "state": "REQUIRES_PAYMENT_METHOD",
      "createdAt": "iso-8601"
    }
  }
}
```

Future event names may remain configurable in webhook endpoint `enabledEvents`, but this slice must not produce them until real producers exist.

### 4.3 Operational Boundaries

- Use bounded HTTP timeouts for delivery attempts.
- Use a deterministic event ID and delivery ID format suitable for idempotent receiver behavior.
- Avoid logging raw signing secrets.
- Avoid logging full payload bodies if they may later contain sensitive data; log event ID/type/merchant/endpoint/attempt/status.
- Add audit rows for secret creation/rotation and webhook endpoint delivery-status actions if existing audit conventions make this low-friction.

## 5. Explicitly Out Of Scope

This planning task does **not** implement Kotlin, SQL, runtime scripts or frontend code.

The later implementation of this slice must also not implement:

- Stripe Connect onboarding (`MRC-01`);
- Stripe sandbox API calls;
- card authorization or `PAY-04` / `PAY-05`;
- capture endpoint and `PAY-*` lifecycle progression beyond the existing create shell;
- clearing, settlement, fee splitting, merchant settlement projection or `SET-*` checks;
- refunds, payouts or chargebacks;
- `payment_intent.authorized`, `payment_intent.captured`, `payment_intent.refunded`, `chargeback.created` producers;
- hosted payment form;
- merchant dashboard DLQ UI;
- frontend SPA implementation;
- moving public API/payment-intent ownership to `acquirer`;
- Kafka-based full clearing/settlement pipeline unless a minimal outbox dispatcher already exists and can be reused without broadening scope.

Specifically:

- do not fake capture/settlement/refund/chargeback events;
- do not claim `SET-*`, `PAY-*`, `MRC-01`, `CHB-*` or frontend checks;
- do not mark `WBH-02` or `WBH-03` passed unless retry/DLQ behavior is actually implemented and verified;
- do not store raw webhook signing secrets after one-time display;
- do not use `console.log` as webhook delivery.

## 6. Service And Module Boundaries

### 6.1 Approved Architecture Target

Approved architecture places outbound webhook delivery in the `acquirer` service:

```text
acquirer/webhook_delivery
```

and the `webhook` schema in the acquirer database.

### 6.2 Current Implementation Boundary For This Slice

Current Phase 04 payment-intent shell and merchant webhook endpoint configuration are implemented in `platform`.

For this first delivery foundation, the recommended implementation boundary is:

- keep delivery in `platform` next to the existing payment-intent shell and `merchant.webhook_endpoints`;
- explicitly record that final ownership migration to `acquirer` is later scope;
- do not introduce cross-service calls solely to satisfy the future topology before service-to-service payment ownership migration is planned.

This is consistent with Phase 04 Slice 03's deliberate platform-hosted shell narrowing.

### 6.3 Future Acquirer Ownership

When capture/settlement/payment lifecycle moves into `acquirer`, webhook delivery ownership should move with it. That later slice should plan:

- data migration or event bridge from `platform.merchant.webhook_endpoints` to `acquirer.webhook.endpoints`;
- service-authenticated merchant endpoint lookup or replicated endpoint projection;
- Kafka topics for lifecycle events;
- reconciliation between outbox events and webhook delivery status.

This slice must not perform that ownership migration.

## 7. DB Migration Expectations

### 7.1 Reserved Migration Names

Current observed migration state:

- Platform latest known migration: `V12__merchant_dashboard_read_shell.sql`.
- Acquirer latest known migration: `V1__baseline.sql`.
- Issuer latest known migration: `V2__issuer_cards.sql`.
- Vault latest known migration: `V2__vault_card_tokenization.sql`.

Recommended reservation for the later implementation pass:

```text
product/apps/platform/src/main/resources/db/migration/V13__merchant_outbound_webhook_delivery.sql
```

Do not add acquirer migrations in this slice unless the implementation owner deliberately moves the whole slice into `acquirer` and first plans the cross-service ownership boundary. The default is Platform-only.

### 7.2 Expected Platform Tables / Columns

If not already present, extend `merchant.webhook_endpoints` with:

```sql
signing_secret_hash text not null,
secret_prefix text not null,
secret_rotated_at timestamptz not null default now()
```

Create a durable outbound event table:

```sql
create table merchant.webhook_events (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants(id),
    event_type text not null,
    aggregate_type text not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    status text not null check (status in ('PENDING','DELIVERED','FAILED','DLQ')),
    created_at timestamptz not null default now(),
    delivered_at timestamptz,
    correlation_id text,
    request_id text
);
```

Create a durable delivery attempt table:

```sql
create table merchant.webhook_delivery_attempts (
    id uuid primary key,
    event_id uuid not null references merchant.webhook_events(id),
    endpoint_id uuid not null references merchant.webhook_endpoints(id),
    attempt_number integer not null,
    status text not null check (status in ('SUCCEEDED','FAILED')),
    http_status integer,
    response_body_snippet text,
    error_type text,
    error_message text,
    attempted_at timestamptz not null default now(),
    next_retry_at timestamptz,
    request_id text,
    correlation_id text,
    unique (event_id, endpoint_id, attempt_number)
);
```

Recommended indexes:

- `merchant.webhook_events(status, created_at)`;
- `merchant.webhook_events(merchant_id, event_type, created_at desc)`;
- `merchant.webhook_delivery_attempts(event_id, endpoint_id, attempt_number)`;
- `merchant.webhook_delivery_attempts(endpoint_id, attempted_at desc)`.

### 7.3 DLQ Table Boundary

Do not create a separate DLQ table in this first slice unless `WBH-02` is explicitly included and verified.

If retry/DLQ is deferred, model terminal failed events as `webhook_events.status = 'FAILED'` and leave:

```text
merchant.webhook_dlq
```

or equivalent for a later Phase 06 retry/DLQ slice.

## 8. Webhook Signing Model

### 8.1 Secret Lifecycle

Each webhook endpoint must have a signing secret.

Rules:

- generated server-side with cryptographically secure random bytes;
- displayed only once on endpoint creation or explicit rotation;
- stored only as a hash plus prefix/fingerprint;
- never returned by list/detail routes after creation;
- never written to logs;
- rotation creates a new one-time secret and invalidates the previous secret for future deliveries.

Recommended display format:

```text
mfp_whsec_<random>
```

Recommended persisted fields:

- `signing_secret_hash` — SHA-256 or stronger hash over the full secret;
- `secret_prefix` — non-secret display prefix, for example first 10-12 characters;
- `secret_rotated_at`.

If future support for overlapping old/new secrets is needed, defer it to a rotation-hardening slice.

### 8.2 Signature Header

Recommended signature algorithm:

```text
signed_payload = "<timestamp>.<raw_body>"
signature = hex(HMAC-SHA256(signing_secret, signed_payload))
MiniFin-Webhook-Signature = "t=<timestamp>,v1=<signature>"
```

Receiver validation expectation:

- reject if timestamp outside tolerance, recommended 5 minutes;
- compute HMAC over exact raw body bytes;
- constant-time compare for signature;
- deduplicate by `MiniFin-Webhook-Id` if receiver chooses to do so.

This mirrors the existing Stripe-style HMAC discipline already used for inbound Stripe webhook verification, but uses MiniFin-owned header names.

## 9. Event / Outbox / Delivery Attempt Model

### 9.1 Minimal Acceptable Model

For this slice, the minimal acceptable production-working path is:

```text
payment intent persisted
→ webhook event row persisted in same transaction or a safe post-commit path
→ delivery worker leases pending event rows
→ worker finds active matching endpoints
→ worker posts signed payload to receiver
→ worker records attempts
→ event status becomes DELIVERED or FAILED
```

If a generic outbox module already exists and is low-friction to reuse, the implementation may instead use:

```text
domain row + outbox row
→ outbox dispatcher
→ webhook pending event
→ delivery worker
```

But this slice must not introduce a broad Kafka topology unless it is necessary for real delivery and can be verified narrowly.

### 9.2 Idempotency

The public `POST /v1/payment_intents` idempotency behavior must not create duplicate webhook events for the same idempotent creation result.

Expected rule:

- first successful creation creates one `payment_intent.created` event;
- idempotent replay with same key/body returns cached response and does not create another event;
- idempotency conflict returns 409 and does not create a webhook event.

This should be covered as a regression alongside `PAY-02` / `PAY-03` if the public create path is touched.

### 9.3 Delivery Idempotency

Each delivery attempt should include:

- stable webhook event ID;
- endpoint ID;
- monotonically increasing attempt number.

The test receiver should record received IDs and prove exactly one successful delivery for the happy path unless retry testing is explicitly in scope.

## 10. Delivery, Retry And DLQ Boundaries

### 10.1 Required For `WBH-01`

Required behavior for this first slice:

- pending event is delivered to a real local HTTP receiver;
- request includes signed payload and headers;
- receiver validates signature successfully;
- delivery attempt is persisted as `SUCCEEDED`;
- event is marked `DELIVERED`.

### 10.2 Optional Narrow `WBH-02`

`WBH-02` may be implemented in this slice only if it remains narrow:

- configure receiver to return `500` or timeout;
- worker records failed attempts;
- worker retries according to a short local test schedule;
- after a configured low max attempt count, event moves to `DLQ` or a documented terminal failed state;
- retained runtime script proves this with real HTTP failures.

Recommended default for this planning note:

```text
Defer WBH-02 retry/DLQ to Phase 06 Slice 02 unless the implementation pass finds the retry worker trivial
to implement without broad Kafka/scheduler scope.
```

### 10.3 Deferred `WBH-03`

`WBH-03` DLQ replay is out of scope for this slice.

DLQ replay requires an operator/API surface or retained replay command and should be planned separately after the first failure/DLQ model is implemented.

## 11. Test Receiver Strategy

The later implementation pass should add a retained local receiver script under `product/scripts/runtime/`.

Recommended shape:

- small Node.js or shell-compatible HTTP receiver;
- listens on `127.0.0.1` with an env-configurable port;
- receives `POST /webhooks/minifin`;
- validates `MiniFin-Webhook-Signature` using the one-time secret captured by the runtime script;
- persists received payload metadata to a temporary file under `/tmp` or script-local temp directory;
- can be configured to:
  - return `200` for happy path;
  - return `500` for retry/DLQ tests if `WBH-02` is implemented;
  - sleep past timeout for timeout tests if needed.

The receiver is test tooling only, not product code. It must not be described as a merchant simulator with business behavior.

## 12. Runtime Scripts And Check IDs To Add

Expected retained scripts:

```text
product/scripts/runtime/lib_phase06_webhooks.sh
product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh
```

Optional only if retry/DLQ is included:

```text
product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh
```

This slice exercises:

- `WBH-01` — Webhook signing/delivery.

Optional:

- `WBH-02` — Webhook retry/DLQ, only if real retry/DLQ is implemented and verified.

Regressions to run if affected code paths are touched:

- `MRC-05` — merchant webhook endpoint configuration remains scoped and role-protected;
- `PAY-01` — public API response shape remains valid;
- `PAY-02` — idempotency same-body replay still returns cached response;
- `PAY-03` — idempotency conflict still returns 409;
- `AUD-01` — audit rows remain valid if secret creation/rotation or webhook delivery audit is added.

Not claimed:

- `WBH-03` — DLQ replay;
- `SET-01..SET-04`;
- `PAY-04..PAY-05`;
- `MRC-01`;
- `CHB-*`;
- `UI-*`.

## 13. Expected Evidence Updates

After implementation, append a new section to `planning/runtime_evidence_log.md` for:

```text
2026-..-.. — Phase 06 Slice 01 Outbound Webhook Delivery Runtime Verification
```

Evidence must include:

- actor used:
  - authenticated `merchant_admin` for webhook endpoint configuration/secret creation;
  - merchant API key caller for `POST /v1/payment_intents`;
  - local test receiver as external merchant endpoint;
- preconditions:
  - Platform service running;
  - merchant exists with active API key;
  - active webhook endpoint subscribed to `payment_intent.created`;
  - receiver has the one-time signing secret for verification;
- action taken:
  - configure endpoint;
  - create payment intent;
  - dispatch/deliver pending webhook;
  - receiver validates signature and records event;
  - inspect DB delivery attempt state;
- expected and actual results for `WBH-01`;
- verification script paths and the commit hash that contains retained scripts;
- result tags (`pass`, `partial` or `fail`) with explicit remaining gaps for any non-pass.

If `WBH-02` is implemented, evidence must separately prove retries and DLQ/terminal failure behavior through a failing receiver.

Update `planning/implementation_status.md` only after code/runtime verification, with factual state rather than a journal.

For this planning-only branch, `planning/implementation_status.md` does not need a runtime progress update unless the owner wants draft planning artifacts listed there.

## 14. Concrete Files To Touch In The Later Coding Slice

### 14.1 Modify

- `README.md` and `CURRENT.md` only if handoff/project status changes.
- `planning/implementation_status.md` after runtime verification.
- `planning/runtime_evidence_log.md` after runtime verification.
- `product/README.md` if new retained scripts or env vars are added.
- `product/.env.example` if receiver/worker timeout or webhook settings need env defaults.
- Existing Platform merchant/public API packages as needed.

Likely product files:

- `product/apps/platform/src/main/resources/db/migration/V13__merchant_outbound_webhook_delivery.sql`
- existing merchant payment-intent creation code from Phase 04 Slice 01.
- existing merchant webhook endpoint configuration code from Phase 04 Slice 03.

### 14.2 Create

Likely product files:

- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookEventRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookDeliveryService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookSigner.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookWorker.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/OutboundWebhookSecretService.kt`
- `product/scripts/runtime/lib_phase06_webhooks.sh`
- `product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh`
- optionally `product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh`

### 14.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md`;
- `planning/design-details/**`;
- frontend SPA implementation files;
- `prototypes/ui/**`;
- `issuer`, `vault`, `network` product code;
- `acquirer` product code unless a separate ownership-migration planning note is approved first;
- ledger/wallet/card authorization behavior.

## 15. Recommended Change Order For The Later Coding Slice

1. Confirm current Platform migration numbers and existing `merchant.webhook_endpoints` columns.
2. Add `V13__merchant_outbound_webhook_delivery.sql` for secrets/events/attempts.
3. Add one-time signing secret creation/rotation support to webhook endpoint configuration.
4. Add outbound event persistence for `payment_intent.created`, preserving public idempotency semantics.
5. Add signer and delivery service with bounded HTTP timeouts.
6. Add worker/dispatch trigger for pending events.
7. Add retained local receiver and `WBH-01` runtime script.
8. Optionally add narrow retry/DLQ script only if implemented.
9. Build/restart only affected services using the assigned runtime slot.
10. Run `WBH-01` and targeted `MRC-05` / `PAY-01..PAY-03` regressions.
11. Update evidence/status/handoff files.
12. Commit the completed implementation slice.

## 16. Linked Runtime Checks

This planning note targets:

- `WBH-01` — Webhook signing/delivery.

Conditional only:

- `WBH-02` — Webhook retry/DLQ, if the implementation pass includes real retry/DLQ behavior.

Regressions:

- `MRC-05` — Merchant webhook endpoint configuration CRUD/scoping.
- `PAY-01` — Public API response shape.
- `PAY-02` — Idempotency same body.
- `PAY-03` — Idempotency conflict.
- `AUD-01` — Audit events if secret lifecycle or delivery state writes add audit rows.

Not claimed:

- `WBH-03`;
- `SET-01..SET-04`;
- `PAY-04..PAY-05`;
- `MRC-01`;
- `CHB-*`;
- `UI-*`.

## 17. Risks And Open Questions

### 17.1 Platform-now vs Acquirer-later ownership

Risk: the approved architecture places webhook delivery in `acquirer`, while current merchant/payment shell implementation is in `platform`.

Recommendation: implement this foundation in `platform` for now and explicitly defer ownership migration. This avoids premature cross-service replication while keeping the delivery behavior real.

### 17.2 Retry/DLQ scope

Open question for the later implementation pass:

```text
Should Phase 06 Slice 01 include a narrow retry/DLQ proof, or should it stop at WBH-01 and leave WBH-02 to Slice 02?
```

Recommended: stop at `WBH-01` unless retry/DLQ is trivial to implement with the same worker and without fake events.

### 17.3 Secret rotation overlap

Risk: merchants may need old and new webhook secrets to overlap during rotation.

Recommendation: not in this slice. Implement immediate rotation only, with future overlapping validation support if a later merchant-ops slice needs it.

### 17.4 Event taxonomy before lifecycle producers

Risk: endpoint config may list future event names before producers exist.

Recommendation: allow future event names in configuration only if already accepted by Phase 04 Slice 03, but this slice produces only `payment_intent.created`.

## 18. Pass Criteria For Later Implementation

A later implementation of this slice is complete only when:

- webhook endpoint creation/rotation returns a signing secret once and stores only hash/prefix at rest;
- creating a real payment-intent shell row creates exactly one `payment_intent.created` outbound event;
- idempotent replay of payment-intent creation does not create duplicate outbound events;
- a real local HTTP receiver receives a signed webhook payload;
- the receiver validates the signature from raw body + timestamp;
- delivery attempt state is persisted;
- `WBH-01` has runtime evidence;
- `WBH-02` is either verified with evidence or explicitly deferred;
- no fake capture/settlement/refund/chargeback events are produced;
- no `SET-*`, `PAY-*`, `MRC-01`, `CHB-*` or frontend checks are claimed;
- `planning/implementation_status.md` and `planning/runtime_evidence_log.md` reflect only factual implemented state after checks;
- one logical commit exists for the implementation pass.
