# Phase 04 Slice 03 — Merchant Dashboard Payments Read Shell and Webhook Configuration — Planning Note

Status: **DRAFT v0.1; planning-only artifact**.

This document fixes the third implementation slice inside `Phase 04 — Merchant Onboarding and Public API Foundation`.

It is a pre-code scope contract. Runtime evidence must be recorded separately in `planning/runtime_evidence_log.md` only after a later implementation pass actually runs checks.

---

## 1. Decision

`Phase 04 slice 03`:

```text
Implement merchant-authenticated dashboard read APIs for API keys, payment-intent list/detail shell,
and persisted webhook endpoint configuration CRUD, without frontend implementation, Stripe onboarding,
card authorization, capture, refund, settlement or outbound webhook delivery.
```

This means:

- the merchant dashboard receives read-oriented backend support for future `MDB-UI-03`, `MDB-UI-04` and `MDB-UI-05` screens;
- existing `merchant.api_keys` remains the key-management source of truth from Slice 01, with only small list/detail response refinements if needed;
- existing `merchant.payment_intents` rows from the public API shell become visible through dashboard-scoped list/detail endpoints;
- a new persisted `merchant.webhook_endpoints` configuration model can be created/updated/listed/deleted by `merchant_admin`, but no outbound webhook signing, dispatch, retry, DLQ or replay is implemented yet;
- all dashboard routes stay under `/api/v1/merchant/**` and use existing merchant session auth / merchant scoping.

## 2. Why This Slice Is Next

This slice is useful before Stripe `MRC-01` is unblocked because:

- `MRC-01` remains blocked on real Stripe Connect sandbox credentials, and the project rule is to stop at a documented blocker instead of faking provider calls;
- Slice 01 already created merchant API keys, public API auth/idempotency and a persisted `merchant.payment_intents` shell;
- Slice 02 already created Stripe webhook receipt/idempotency foundations but not real onboarding-start;
- Phase 04 in `planning/06_implementation_guide.md` explicitly includes a merchant dashboard read shell for onboarding/API keys/payment list;
- the merchant dashboard needs backend query/configuration surfaces before future frontend implementation, but frontend work remains gated by accepted standalone HTML prototypes;
- webhook endpoint configuration is a safe predecessor to Phase 06 outbound webhook delivery checks (`WBH-01..WBH-03`) because it stores real merchant configuration without pretending delivery exists.

This slice is therefore a low-risk backend/API planning step that uses already-implemented merchant/session/public-payment foundations and avoids the Stripe credential blocker.

## 3. Exact Backend/Runtime Scope

In the later implementation pass, this slice should:

1. Add or extend Platform DB schema for merchant webhook endpoint configuration.
2. Add merchant dashboard payment read endpoints scoped to the authenticated merchant:
   - payment intent list over existing `merchant.payment_intents`;
   - payment intent detail by id;
   - optional filters that can be implemented without adding payment behavior.
3. Add merchant dashboard webhook endpoint configuration CRUD under `/api/v1/merchant/**`.
4. Keep existing API key lifecycle behavior from Slice 01 intact; only refine list response fields if future `MDB-UI-03` needs non-secret metadata.
5. Add retained runtime scripts that prove:
   - dashboard payment list/detail cannot cross merchant boundaries;
   - webhook endpoint configuration persists and is scoped to the merchant;
   - merchant member/admin authorization rules are enforced;
   - existing `MRC-03`, `PAY-01..PAY-03` behavior does not regress.
6. Update factual implementation status and runtime evidence after checks actually run.

## 4. Dashboard Routes To Implement Later

All routes are merchant dashboard routes on `merchants.miniefin.local` and must use the existing merchant session cookie.

### 4.1 API Key Read Refinement

Existing Slice 01 routes remain canonical:

```http
GET /api/v1/merchant/api-keys
POST /api/v1/merchant/api-keys
POST /api/v1/merchant/api-keys/{id}/revoke
```

This slice may refine only the `GET` response metadata, if needed, for dashboard rendering:

- `apiKeyId`
- `label`
- `keyPrefix`
- `fingerprint`
- `status`
- `createdAt`
- `lastUsedAt` if already derivable or cheap to add
- `revokedAt`

The raw secret key must still be returned only on create and never by any read route.

### 4.2 Payment Intent List / Detail Shell

Planned routes:

```http
GET /api/v1/merchant/payment-intents
GET /api/v1/merchant/payment-intents/{id}
```

List query parameters:

- `limit` — default 25, max 100;
- `startingAfter` or `cursor` — optional stable pagination cursor;
- `state` — optional filter over existing shell states;
- `createdFrom` / `createdTo` — optional timestamp filters;
- `apiKeyId` — optional filter if the existing payment-intent record stores API key provenance.

Response shape:

```json
{
  "data": {
    "items": [
      {
        "id": "uuid",
        "object": "payment_intent",
        "amount": "12.50",
        "currency": "EUR",
        "state": "REQUIRES_PAYMENT_METHOD",
        "description": "test",
        "createdAt": "iso-8601",
        "updatedAt": "iso-8601"
      }
    ],
    "nextCursor": null
  },
  "errors": []
}
```

Detail response:

```json
{
  "data": {
    "id": "uuid",
    "object": "payment_intent",
    "amount": "12.50",
    "currency": "EUR",
    "state": "REQUIRES_PAYMENT_METHOD",
    "description": "test",
    "createdAt": "iso-8601",
    "updatedAt": "iso-8601"
  },
  "errors": []
}
```

If a payment intent belongs to another merchant, return `404 not_found` rather than leaking cross-merchant existence.

### 4.3 Webhook Endpoint Configuration CRUD

Recommended routes:

```http
GET    /api/v1/merchant/webhook-endpoints
POST   /api/v1/merchant/webhook-endpoints
PUT    /api/v1/merchant/webhook-endpoints/{id}
DELETE /api/v1/merchant/webhook-endpoints/{id}
```

`merchant_admin` only for create/update/delete. Read can be available to `merchant_admin` and `merchant_member`.

Create/update request:

```json
{
  "url": "https://merchant.example.test/webhooks/minifin",
  "enabledEvents": [
    "payment_intent.created",
    "payment_intent.authorized",
    "payment_intent.captured",
    "payment_intent.refunded",
    "chargeback.created"
  ],
  "status": "ACTIVE",
  "description": "primary endpoint"
}
```

Response:

```json
{
  "data": {
    "id": "uuid",
    "url": "https://merchant.example.test/webhooks/minifin",
    "enabledEvents": ["payment_intent.created"],
    "status": "ACTIVE",
    "description": "primary endpoint",
    "createdAt": "iso-8601",
    "updatedAt": "iso-8601",
    "deletedAt": null
  },
  "errors": []
}
```

No signing secret is returned or generated in this slice unless implementation chooses to create a persisted secret placeholder for future Phase 06 delivery. If a secret is generated, it must follow API-key one-time visibility rules and be clearly marked as unused until outbound delivery exists.

## 5. DB Migration Expectations

Expected new migration in a later implementation pass:

```text
product/apps/platform/src/main/resources/db/migration/V12__merchant_dashboard_read_shell.sql
```

Migration should add `merchant.webhook_endpoints` if it does not already exist from prior migrations.

Recommended shape:

```sql
create table merchant.webhook_endpoints (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants(id),
    url text not null,
    enabled_events jsonb not null,
    status text not null check (status in ('ACTIVE', 'DISABLED', 'DELETED')),
    description text,
    signing_secret_hash text,
    secret_prefix text,
    created_by_employee_id uuid references merchant.merchant_employees(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint merchant_webhook_endpoint_url_not_blank check (length(trim(url)) > 0)
);
```

Recommended indexes:

- `(merchant_id, status, created_at desc)` for dashboard list;
- partial unique index for one active endpoint per `(merchant_id, url)` if multiple endpoints are supported;
- no global uniqueness on `url` because multiple merchants may use the same receiver during local testing.

Payment read endpoints should reuse existing `merchant.payment_intents`. Add columns only if the current table cannot support safe dashboard reads, for example:

- `updated_at`;
- `api_key_id`;
- `metadata jsonb`;
- stable cursor-friendly index on `(merchant_id, created_at desc, id desc)`.

Do not move `payment_intents` to the `acquirer` service in this slice. The approved architecture still targets Acquirer ownership later; the current platform-hosted shell remains a deliberate Phase 04 scope narrowing until service-to-service auth and ownership migration are planned.

## 6. Relationship To Existing Tables And Public API

### 6.1 `merchant.api_keys`

- Remains owned by Slice 01.
- This slice must not change one-time secret visibility, hash/fingerprint semantics or revocation behavior.
- Dashboard list refinements are allowed only for non-secret metadata.

### 6.2 `merchant.payment_intents`

- Existing rows are created by `POST /v1/payment_intents`.
- Dashboard payment list/detail must be read-only views over those rows.
- The merchant scope must come from the authenticated dashboard session, not from request parameters.
- Public API response shape and dashboard response shape can differ in route naming, but both use `{data, errors}`.

### 6.3 Stripe Webhook Tables

- `merchant.stripe_account_links` and `merchant.stripe_webhook_events` remain owned by Slice 02.
- This slice does not change inbound Stripe webhook processing.
- Dashboard webhook endpoint config is for outbound merchant webhooks and is unrelated to Stripe's inbound webhook signing secret.

### 6.4 Public API

- Public API routes under `/v1/**` remain as implemented in Slice 01.
- This slice adds dashboard read routes; it does not add or change public capture/refund/settlement behavior.
- Dashboard refund route `POST /api/v1/payments/{id}/refunds` remains out of scope until actual refund lifecycle exists.

## 7. Explicitly In Scope

Later implementation should include:

- merchant dashboard payment list endpoint over persisted payment-intent shell rows;
- merchant dashboard payment detail endpoint with cross-merchant 404 behavior;
- merchant webhook endpoint config persistence and CRUD;
- merchant-role authorization:
  - `merchant_admin`: create/update/delete webhook config and API keys;
  - `merchant_member`: read payment list/detail and read webhook config if accepted by existing RBAC conventions;
- audit rows for webhook endpoint create/update/delete and denied cross-merchant or wrong-role attempts where existing audit conventions apply;
- retained runtime scripts for the new dashboard read/config checks;
- documentation/status/evidence updates only after implementation and runtime verification.

## 8. Explicitly Out Of Scope

This slice does **not** implement:

- Kotlin, SQL, runtime scripts or frontend code in this planning-only task;
- real Stripe Connect onboarding-start (`MRC-01`);
- Stripe sandbox API calls;
- payment authorization, capture, refund, settlement, payout or card-network behavior;
- outbound merchant webhook delivery, signing, retry, DLQ or replay;
- public API route expansion beyond existing payment-intent shell reads/writes;
- moving public API/payment-intent ownership to `acquirer`;
- UI prototypes or SPA implementation;
- changes to approved baseline docs under `planning/01..06`, `planning/design-details/**` or `planning/runtime_checklists.md`.

Specifically:

- do not fake webhook delivery by logging payloads;
- do not mark `WBH-01`, `WBH-02` or `WBH-03` as passed from configuration-only work;
- do not add payment states that imply authorization/capture has happened;
- do not expose raw API keys or webhook signing secrets through read routes.

## 9. Runtime Checks To Add Or Extend

Recommended conservative additions to `planning/runtime_checklists.md` during the later implementation pass:

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `MRC-04` | Merchant dashboard payment-intent read scoping. | Merchant can list/detail own shell payment intents; another merchant receives 404/empty result. | `product/scripts/runtime/reg_phase04_merchant_payment_reads.sh` |
| `MRC-05` | Merchant webhook endpoint configuration CRUD. | Merchant admin can create/update/list/delete webhook endpoint config; non-admin writes are rejected; config is scoped to merchant. | `product/scripts/runtime/reg_phase04_merchant_webhook_config.sh` |

Existing checks to keep covered/regressed:

- `MRC-03` — API key lifecycle remains covered by Slice 01; run a regression script if API key read refinements touch this area.
- `PAY-01` — public API response shape should not regress.
- `PAY-02` — public idempotency same-body replay should not regress.
- `PAY-03` — public idempotency conflict should not regress.

Future checks not claimed:

- `WBH-01` — outbound webhook signing/delivery waits for Phase 06 delivery implementation.
- `WBH-02` — retry/DLQ waits for Phase 06 delivery implementation.
- `WBH-03` — DLQ replay waits for Phase 06 delivery implementation.

## 10. Expected Evidence Updates After Implementation

When a later implementation pass executes this slice, update:

- `planning/runtime_evidence_log.md` with entries for:
  - `MRC-04` payment read scoping;
  - `MRC-05` webhook config CRUD/scoping;
  - `MRC-03` / `PAY-01..PAY-03` regressions if scripts are rerun;
- `planning/implementation_status.md` with factual state only:
  - routes added;
  - migration added;
  - explicit not-started items;
  - runtime checks passed/partial/not claimed;
- `CURRENT.md` only if handoff state or next planned step materially changes;
- `product/README.md` only if new retained runtime scripts are added.

No evidence update is expected from this planning-only task.

## 11. Concrete Files To Touch In Later Implementation

### 11.1 Modify

- `planning/runtime_checklists.md` — add `MRC-04` and `MRC-05` only if the implementation pass proceeds.
- `planning/implementation_status.md` — factual implementation state after runtime checks.
- `planning/runtime_evidence_log.md` — runtime evidence after checks.
- `CURRENT.md` / `README.md` if handoff/project status changes.
- `product/README.md` if new retained scripts are added.

Likely product files:

- `product/apps/platform/src/main/resources/db/migration/V12__merchant_dashboard_read_shell.sql`
- existing merchant/public API packages as needed.

### 11.2 Create

Likely product files:

- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/dashboard/MerchantPaymentDashboardController.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/dashboard/MerchantPaymentDashboardRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/MerchantWebhookEndpointController.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/MerchantWebhookEndpointRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/MerchantWebhookEndpointService.kt`
- `product/scripts/runtime/reg_phase04_merchant_payment_reads.sh`
- `product/scripts/runtime/reg_phase04_merchant_webhook_config.sh`

### 11.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md`;
- `planning/design-details/**`;
- frontend SPA implementation files;
- `prototypes/ui/**`;
- `acquirer`, `network`, `issuer`, `vault` service code;
- ledger/wallet behavior.

## 12. Recommended Change Order For Later Implementation

1. Add `MRC-04` and `MRC-05` to `planning/runtime_checklists.md`.
2. Add DB migration for webhook endpoint configuration and any safe payment-intent read indexes/columns.
3. Add repository/service/controller for payment-intent dashboard reads.
4. Add repository/service/controller for webhook endpoint configuration.
5. Add merchant role/scoping checks and structured `{data, errors}` responses.
6. Add retained runtime scripts.
7. Build/restart `platform` in the assigned runtime slot.
8. Run new scripts and relevant `MRC-03` / `PAY-01..PAY-03` regressions.
9. Update evidence/status/handoff files.
10. Commit the completed implementation slice.

## 13. Linked Runtime Checks

This planning note proposes:

- `MRC-04` — Merchant dashboard payment-intent read scoping.
- `MRC-05` — Merchant webhook endpoint configuration CRUD/scoping.

Regression checks:

- `MRC-03` — API key lifecycle remains valid if list refinements touch API-key code.
- `PAY-01` — public API response shape remains valid.
- `PAY-02` — public idempotency replay remains valid.
- `PAY-03` — public idempotency conflict remains valid.

Not claimed:

- `MRC-01` — still blocked on real Stripe Connect sandbox credentials.
- `WBH-01..WBH-03` — future Phase 06 outbound webhook delivery/retry/DLQ behavior.

## 14. Risks And Open Questions

### 14.1 Payment-intent ownership migration

Risk: the approved architecture places public Payments API and payment lifecycle in `acquirer`, while current Phase 04 shell is in `platform`.

Mitigation: keep this slice read-only over the current shell and record the ownership migration as later scope. Do not introduce cross-service behavior until service-to-service auth and acquirer ownership are planned explicitly.

### 14.2 Webhook endpoint multiplicity

Open question for later implementation:

```text
Should MVP allow one active webhook endpoint per merchant, or multiple endpoints with per-event subscriptions?
```

Recommended for first implementation: allow multiple endpoints but enforce uniqueness on active `(merchant_id, url)`. This matches common webhook platforms while keeping config CRUD simple.

### 14.3 Webhook signing secret lifecycle

Open question for later implementation:

```text
Should this config slice generate webhook signing secrets now, or wait until outbound delivery exists?
```

Recommended: wait until outbound delivery, unless Phase 06 planning explicitly wants configuration pre-provisioning. A secret that is never used can confuse evidence and support. If generated now, use one-time visibility and store only a hash/prefix.

### 14.4 Event taxonomy

Risk: choosing final `enabledEvents` names too early could conflict with future payment lifecycle events.

Mitigation: store event names as strings/jsonb with validation limited to a conservative allowlist:

- `payment_intent.created`
- `payment_intent.authorized`
- `payment_intent.captured`
- `payment_intent.refunded`
- `chargeback.created`

Do not dispatch any of them until Phase 06 creates real event producers.

## 15. Pass Criteria For Later Implementation

A later implementation of this slice is complete only when:

- dashboard payment list/detail endpoints exist and are merchant-scoped;
- webhook endpoint configuration CRUD exists and is merchant-scoped;
- wrong-role and cross-merchant access are rejected without data leaks;
- no outbound webhook delivery is claimed;
- `MRC-04` and `MRC-05` have runtime evidence;
- relevant `MRC-03` / `PAY-01..PAY-03` regressions pass if touched;
- `planning/implementation_status.md`, `planning/runtime_evidence_log.md`, `CURRENT.md` and `product/README.md` reflect the factual state;
- one logical commit exists for the implementation pass.
