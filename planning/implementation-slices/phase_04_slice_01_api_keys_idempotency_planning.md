# Phase 04 Slice 01 — Merchant API Keys and Public API Idempotency — Planning Note

Status: **BACKEND/RUNTIME SUB-SCOPE EXECUTED v0.2; frontend not in scope**.

This document fixes the first implementation slice inside `Phase 04 — Merchant Onboarding and Public API Foundation`.

It is a pre-code scope contract. Runtime evidence is recorded separately in `planning/runtime_evidence_log.md`.

---

## 1. Decision

`Phase 04 slice 01`:

```text
Implement merchant API key lifecycle on the merchant dashboard surface, public API authentication using
merchant API keys, public write idempotency primitive, and a minimal public payment-intent creation shell
sufficient to prove the public response shape, idempotency replay and idempotency conflict checks.
```

This means:

- merchant employees with role `merchant_admin` can generate, list and revoke API keys through the merchant dashboard;
- a generated key is shown exactly once at creation time; thereafter only a key prefix, a SHA-256 fingerprint and the hashed key live at rest;
- public API routes under `/v1/**` require a valid active API key supplied via `Authorization: Bearer <key>`;
- public write routes require a non-empty `Idempotency-Key` header; replaying the same key with the same merchant and the same request fingerprint returns the cached response verbatim; the same key with a different request fingerprint returns HTTP 409 with the public error shape;
- a minimal `POST /v1/payment_intents` exists only to prove the public response shape `{data, errors}` and the idempotency primitive; it persists a `payment_intents` row with state `REQUIRES_PAYMENT_METHOD` and performs no card authorization/capture/refund/settlement behavior.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` lists merchant onboarding and public API foundation as Phase 04, and explicitly calls out API key lifecycle and idempotency middleware as required before any card-network simulation;
- the merchant identity foundation (Phase 02 Slice 02) and merchant dashboard auth already exist, so dashboard-side API key endpoints can reuse the merchant session/auth contract without new auth scaffolding;
- the public API surface needs an authentication primitive and an idempotency primitive before any payment-intent state machine work is meaningful;
- `MRC-03`, `PAY-01`, `PAY-02`, `PAY-03` remain pending until exactly this slice is implemented;
- Stripe Connect onboarding (`MRC-01`) and Stripe webhook receiver (`MRC-02`) are owned by a separate branch and are out of scope here.

If card authorization/capture or Stripe Connect were implemented before this slice, both would require an API key surface and idempotency primitive that does not yet exist; landing this slice first is the smaller move.

## 3. Exact Scope

In this slice:

1. Add Platform Flyway migration `V9__merchant_api_keys_and_idempotency.sql` for `merchant.api_keys`, `merchant.payment_intents` shell and `idempotency.idempotency_keys`.
2. Add backend code in `com.minifin.platform.merchant.apikeys` for API key generation, listing and revocation on the merchant dashboard.
3. Add backend code in `com.minifin.platform.publicapi` for:
   - API key authentication filter / resolver;
   - public API response shape `{data, errors}` and structured error mapping;
   - idempotency repository and service;
   - minimal `PaymentIntent` create endpoint shell.
4. Wire the API key auth filter through Spring Security so `/v1/**` routes are authenticated against `merchant.api_keys` and rejected with `401`/`403` when missing/invalid/revoked.
5. Wire idempotency around the `POST /v1/payment_intents` route.
6. Add retained runtime scripts for API key lifecycle, public response shape, idempotency replay and idempotency conflict.
7. Build and start `platform`; run the new scripts; record evidence; update status, evidence log and CURRENT/README.

## 4. Explicitly In Scope

### Code Changes

- Platform migration `V9__merchant_api_keys_and_idempotency.sql`.
- New Kotlin package `com.minifin.platform.merchant.apikeys` containing models, repository, service and merchant dashboard controller for API keys.
- New Kotlin package `com.minifin.platform.publicapi` containing:
  - public API response/error models and exception type;
  - API key authentication filter (Spring Security `OncePerRequestFilter` style) that resolves and stores the merchant principal on the request;
  - idempotency repository and service for write routes;
  - minimal payment intent repository / service / public controller for `POST /v1/payment_intents` and `GET /v1/payment_intents/{id}`.
- `SecurityConfig` update so `/v1/**` runs through the API key filter and bypasses the existing OIDC chain.
- Update to `product/README.md` runtime scripts list.

### Behavioral Outcomes

- `POST /api/v1/merchant/api-keys` (merchant_admin only) generates a key, returns it once in the response body together with the prefix, fingerprint and id; the raw key is never persisted; `key_hash` and `fingerprint` are stored alongside `status='ACTIVE'`.
- `GET /api/v1/merchant/api-keys` returns the merchant's keys with prefix/fingerprint/status only.
- `POST /api/v1/merchant/api-keys/{id}/revoke` (merchant_admin only) transitions the key to `REVOKED` and is idempotent on the same key.
- Public requests missing `Authorization`, with malformed key, with non-existent key, or with revoked key are rejected with `{data:null, errors:[{code:"unauthenticated"|"invalid_api_key"|"revoked_api_key", message:...}]}` and an appropriate HTTP status.
- `POST /v1/payment_intents` without `Idempotency-Key` returns HTTP 400 `idempotency_key_required`.
- `POST /v1/payment_intents` with a fresh `Idempotency-Key` creates a row in `merchant.payment_intents` and returns a `payment_intent` object inside `{data:..., errors:[]}` with state `REQUIRES_PAYMENT_METHOD`.
- Replaying the same `Idempotency-Key` with the same merchant and the same normalized request body returns the cached HTTP status and body verbatim.
- Reusing the same `Idempotency-Key` with the same merchant and a different normalized request body returns HTTP 409 `idempotency_conflict` and does not create a second `payment_intents` row.
- Cross-merchant key reuse is treated as separate scopes: `(merchant_id, idempotency_key)` is the unique scope; the same string used by two merchants is independently valid.
- Audit events are written for key creation, revocation and rejected public auth.

### Verification Outcomes

- `MRC-03` — pass for API key one-time visibility, hashed/fingerprinted storage and revoked-key rejection.
- `PAY-01` — pass for public API response shape `{data, errors}` on success and on errors.
- `PAY-02` — pass for same key/body replay returning cached response.
- `PAY-03` — pass for same key/different body returning 409.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- Stripe Connect onboarding (`MRC-01`) and Stripe webhook receiver (`MRC-02`) — owned by separate branch;
- card authorization / capture / refund / settlement / webhook delivery behavior;
- payment intent state machine beyond the `REQUIRES_PAYMENT_METHOD` shell;
- merchant dashboard webhook endpoint configuration (`PUT /api/v1/merchant/webhook-endpoint`);
- merchant payments list / refund / settlement / payout dashboard routes;
- API key rotation as a separate route — rotation in this slice is "generate a new key, revoke the old one", which the lifecycle endpoints already support;
- moving public API endpoints into the `acquirer` service — see §6.4;
- frontend implementation under `spa-merchant` or `spa-enduser`;
- changes to approved baseline docs `planning/01..06`, `planning/design-details/**`, `planning/runtime_checklists.md`;
- GitHub MCP, remote push or PR creation.

Specifically:

- do not edit approved baseline docs;
- do not touch prototype artifacts;
- do not introduce service-to-service HMAC auth — there is no inter-service call in this slice;
- do not change `ledger.post_journal(...)`;
- do not change wallet code paths;
- do not add Kafka topics or outbox events for payment intents.

## 6. Design Notes

### 6.1 API Key Material

- Key string format: `mfp_live_<32 random url-safe characters>`. The `mfp_live_` prefix is stable; the random suffix is generated with `SecureRandom` and base64url-encoded without padding.
- `key_prefix` stored: the first 12 characters of the full key (`mfp_live_` + 3 chars). This is enough for dashboards to identify a key without exposing the suffix.
- `key_hash` stored: SHA-256 hex of the entire raw key.
- `fingerprint` stored: SHA-256 hex of the entire raw key truncated to the first 16 hex characters. This gives merchants a stable identifier that is safe to display in support/audit channels.
- The full raw key is returned in the API response only on the create call and is never persisted in plaintext.

### 6.2 Public API Auth Surface

- `Authorization: Bearer <key>` is parsed by a Spring `OncePerRequestFilter` that runs for `/v1/**` only.
- The filter resolves the key via `SHA-256(key) == key_hash` lookup with `status='ACTIVE'`.
- A `PublicApiPrincipal(merchantId, apiKeyId)` is stored on the request and used by the public controller.
- For `/v1/**` Spring Security policy is `permitAll` because the custom filter enforces auth and the chain treats public errors as JSON-bodied responses with `{data, errors}`.

### 6.3 Idempotency Primitive

- Required for `POST /v1/payment_intents`. Future Phase 04 / Phase 05 routes will reuse `IdempotencyService.runWriteOnce(...)`.
- Storage: `idempotency.idempotency_keys` with `(merchant_id, idempotency_key)` as primary key. Stored columns: `request_fingerprint`, `route`, `method`, `response_status`, `response_body`, `created_at`.
- TTL is enforced by `created_at` plus `IDEMPOTENCY_TTL` (24 hours, matching `06_implementation_guide` reference). TTL cleanup is out of scope here; this slice only stores rows.
- Request fingerprint is `SHA-256( method + "|" + route + "|" + normalized_body )`. Normalization is "the raw request body bytes that the controller received"; clients are responsible for canonical JSON. This is intentionally simple for this slice and can be replaced with a deeper canonicalization later without changing the public contract.
- On replay with the same fingerprint: respond with the cached `response_status` and the cached `response_body`.
- On replay with a different fingerprint: respond with HTTP 409 and `errors:[{code:"idempotency_conflict", ...}]`.
- Missing `Idempotency-Key` on a required-idempotency route: respond with HTTP 400 and `errors:[{code:"idempotency_key_required", ...}]`.

### 6.4 Public API Hosting In This Slice

The approved architecture places public API in the `acquirer` service on host `api.miniefin.local`. In this slice the public API is hosted in `platform` for the following narrow reasons:

- API key material lives in the same DB as the merchant identity used to manage it;
- there is no service-to-service auth library implemented yet to let `acquirer` validate platform-owned API keys;
- the slice's runtime checks (`MRC-03`, `PAY-01`, `PAY-02`, `PAY-03`) do not depend on which service hosts the public routes.

The move into `acquirer` is intentionally a later phase 04 slice gated on a real service-to-service auth primitive. This decision is recorded in `implementation_status.md` as a deliberate scope-narrowing.

## 7. API Shape

### 7.1 Merchant Dashboard

```http
POST /api/v1/merchant/api-keys
Cookie: MFP_SESSION=...
Content-Type: application/json

{ "label": "default" }

201 Created
{
  "data": {
    "apiKeyId": "uuid",
    "label": "default",
    "key": "mfp_live_<one-time>",
    "keyPrefix": "mfp_live_xyz",
    "fingerprint": "ab12...cd34",
    "status": "ACTIVE",
    "createdAt": "iso-8601"
  },
  "errors": []
}
```

```http
GET /api/v1/merchant/api-keys
Cookie: MFP_SESSION=...

200 OK
{
  "data": [
    { "apiKeyId": "...", "label": "default", "keyPrefix": "mfp_live_xyz",
      "fingerprint": "ab12...cd34", "status": "ACTIVE",
      "createdAt": "...", "revokedAt": null }
  ],
  "errors": []
}
```

```http
POST /api/v1/merchant/api-keys/{id}/revoke
Cookie: MFP_SESSION=...

200 OK
{
  "data": { "apiKeyId": "...", "status": "REVOKED", "revokedAt": "..." },
  "errors": []
}
```

### 7.2 Public Payments API

```http
POST /v1/payment_intents
Authorization: Bearer mfp_live_...
Idempotency-Key: <client-supplied>
Content-Type: application/json

{ "amount": "12.50", "currency": "EUR", "description": "test" }

201 Created
{
  "data": {
    "id": "uuid",
    "object": "payment_intent",
    "amount": "12.50",
    "currency": "EUR",
    "state": "REQUIRES_PAYMENT_METHOD",
    "description": "test",
    "createdAt": "..."
  },
  "errors": []
}
```

```http
GET /v1/payment_intents/{id}
Authorization: Bearer mfp_live_...

200 OK
{
  "data": { "id": "...", "object": "payment_intent", "amount": "12.50",
            "currency": "EUR", "state": "REQUIRES_PAYMENT_METHOD",
            "description": "test", "createdAt": "..." },
  "errors": []
}
```

## 8. Concrete Files To Touch

### 8.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `product/README.md`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/SecurityConfig.kt`

### 8.2 Create

- `product/apps/platform/src/main/resources/db/migration/V9__merchant_api_keys_and_idempotency.sql`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/apikeys/ApiKeyModels.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/apikeys/ApiKeyRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/apikeys/ApiKeyService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/apikeys/MerchantApiKeyController.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/PublicApiSupport.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/PublicApiAuthFilter.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/IdempotencyRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/IdempotencyService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/PaymentIntentRepository.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/PaymentIntentService.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/publicapi/PublicApiController.kt`
- `product/scripts/runtime/lib_phase04_public_api.sh`
- `product/scripts/runtime/reg_phase04_api_key_lifecycle.sh`
- `product/scripts/runtime/reg_phase04_public_api_response_shape.sh`
- `product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh`
- `product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh`

### 8.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved baseline.
- `planning/runtime_checklists.md` — `MRC-03`, `PAY-01`, `PAY-02`, `PAY-03` already exist.
- Wallet, ledger, identity domain code beyond `SecurityConfig` chain ordering — wallet/ledger/identity behavior must not regress.
- `prototypes/ui/**` — owned by UI/UX prototype workflow.
- SPA implementation files — frontend implementation is gated by accepted standalone HTML prototypes.
- `acquirer`, `network`, `issuer`, `vault` service code — public API stays in `platform` for this slice per §6.4.

## 9. Recommended Change Order

1. Add migration `V9__merchant_api_keys_and_idempotency.sql`.
2. Add API key models / repository / service / merchant controller.
3. Add public API exception/response support and key auth filter.
4. Wire the filter into `SecurityConfig` for `/v1/**`.
5. Add idempotency repository/service.
6. Add payment intent repository/service/public controller.
7. Add retained runtime scripts.
8. Build and start `platform`.
9. Run the four new scripts plus a wallet regression spot check.
10. Record runtime evidence and update status files.

## 10. Linked Runtime Checks

This slice exercises:

- `MRC-03` — API key lifecycle (one-time visibility, hashed/fingerprinted at rest, revoked key rejection).
- `PAY-01` — public API `{data, errors}` response shape on success and on errors.
- `PAY-02` — idempotency replay returns cached response.
- `PAY-03` — idempotency conflict returns HTTP 409.

Adjacent regression spot check:

- `LDG-05` — ledger reconciliation remains clean (no ledger changes in this slice).

## 11. Verification Shape

After implementation in this slice, run:

- `docker compose -f deploy/docker-compose.yml build platform`
- `docker compose -f deploy/docker-compose.yml up -d platform`
- `product/scripts/runtime/reg_phase04_api_key_lifecycle.sh`
- `product/scripts/runtime/reg_phase04_public_api_response_shape.sh`
- `product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh`
- `product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh`
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` (regression)

Evidence is appended to `planning/runtime_evidence_log.md`.

## 12. Pass Criteria

Slice considered implemented when:

- the new endpoints exist and pass the retained runtime scripts;
- API keys are persisted hashed and fingerprinted; raw keys are never stored;
- idempotency replay returns identical cached response and conflict returns 409;
- linked check IDs have `pass` evidence entries;
- `planning/implementation_status.md`, `planning/runtime_evidence_log.md`, `CURRENT.md`, `README.md` and `product/README.md` reflect the factual state;
- one logical Git commit exists on `task/p04s01-api-keys-idempotency` after verification.
