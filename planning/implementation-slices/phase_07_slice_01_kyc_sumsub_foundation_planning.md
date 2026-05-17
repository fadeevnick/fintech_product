# Phase 07 Slice 01 — KYC/Sumsub Foundation — Planning Note

Status: **DRAFT v0.1 — planning-only; no product code implemented**.

This document fixes the first implementation slice inside `Phase 07 — Compliance Integrations and Case Management`.

It is a pre-code scope contract. Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md`.

---

## 1. Decision

`Phase 07 slice 01`:

```text
Implement the first KYC/Sumsub foundation in Platform: end-user KYC start, persisted KYC applicant/session/request state,
Sumsub webhook signature verification and vendor-event idempotency, with minimal KYC state transitions for applicant review
outcomes. Do not implement sanctions, AML, document preview UI, case attachment upload or manual backoffice decisions yet.
```

Recommended boundary:

- current implementation owner: `platform`;
- new module/package: `com.minifin.platform.kyc`;
- new schema: `kyc`;
- real Sumsub adapter boundary with sandbox credentials when available;
- deterministic local verification only for behaviors that do not pretend to be Sumsub:
  - request validation and email-verification gate;
  - local persistence;
  - Sumsub-format webhook signature verification against locally signed payloads;
  - vendor event id idempotency;
  - state transition logic from verified webhook payloads.

This slice targets:

- `KYC-01` — Sumsub KYC start;
- `KYC-02` — Sumsub webhook verification/idempotency.

`KYC-01` may be only `partial` until real Sumsub sandbox credentials are provided and a retained script proves applicant/access-token creation against Sumsub. `KYC-02` can be fully proven locally if the implementation uses Sumsub's real HMAC signature algorithm and idempotency model with deterministic signed webhook fixtures.

## 2. Why This Slice Is Next

Phase 07 can start now because:

- identity/session foundations already exist for end users;
- email verification is already represented in the identity flow and is a hard gate for KYC (`IDN-FR-03`);
- wallet/card/payment foundations already need a real `kyc_status` path before broader compliance gates become meaningful;
- `planning/06_implementation_guide.md` defines Phase 07 as Sumsub, OpenSanctions, cases, attachments and read-audit, but `KYC-01`/`KYC-02` are the narrowest integration-first subset;
- UI prototypes continue independently; this slice exposes backend/runtime contracts only and does not implement end-user or backoffice frontend workflows.

This slice deliberately starts with KYC because sanctions, AML and case management depend on a stable compliance subject model and vendor-event ingestion pattern.

## 3. Exact Backend/Runtime Scope

In the later implementation pass, this slice should:

1. Add Platform KYC persistence for end-user applicant/request/session state.
2. Add end-user API route `POST /api/v1/kyc/start`.
3. Enforce that only an authenticated, email-verified, active end user can start KYC.
4. Make `POST /api/v1/kyc/start` idempotent for an already active KYC applicant/session for the same user.
5. Add a Sumsub adapter boundary that can call real Sumsub sandbox APIs when credentials are configured.
6. Persist vendor request metadata without storing raw secrets or full sensitive document payloads.
7. Add `POST /webhooks/sumsub/v1` with real Sumsub-style signature verification.
8. Persist webhook events and reject invalid signatures.
9. Enforce vendor event id idempotency so duplicate deliveries do not move state twice.
10. Map a minimal subset of Sumsub review events to the KYC state machine:
    - started/submitted path;
    - happy-path approval;
    - review/consider path that leaves the user in `IN_REVIEW` and opens or marks a future-review placeholder if the cases module is not implemented yet.
11. Add retained runtime scripts for `KYC-01` and `KYC-02`.
12. Update factual status/evidence only after runtime checks actually run.

The implementation must not fake successful Sumsub applicant creation. If real credentials are absent, `KYC-01` evidence must say what was proven locally and what remains blocked.

## 4. Explicitly In Scope

### 4.1 Product Behavior

- End-user starts KYC through `POST /api/v1/kyc/start`.
- Platform persists:
  - one KYC profile/applicant record per end user;
  - start request/session state;
  - vendor applicant id when real Sumsub returns one;
  - vendor access token metadata when returned, without logging secrets;
  - KYC status and transition timestamps.
- Starting KYC requires:
  - valid end-user session;
  - email verified;
  - end user not blocked/frozen by existing actor-control hook if the implementation can reuse it cleanly.
- Repeated start for the same user should return the current applicant/session state rather than creating duplicate applicant records.
- Sumsub webhook endpoint verifies signature before parsing or applying state changes.
- Sumsub webhook endpoint stores the vendor event id and treats duplicate delivery as idempotent success/no-op.
- Invalid signature attempts are rejected and audit logged if existing audit conventions make this low-friction.

### 4.2 Minimal KYC Statuses

Use the existing approved KYC state machine:

```text
NOT_STARTED -> SUBMITTED -> IN_REVIEW -> APPROVED
                                      -> REJECTED
                                      -> NEEDS_RESUBMIT
```

For this first slice:

- start flow may move `NOT_STARTED -> SUBMITTED` once a Sumsub applicant/access-token session is created;
- webhook indicating review started/pending maps to `SUBMITTED -> IN_REVIEW`;
- webhook indicating `GREEN`/approved maps to `IN_REVIEW -> APPROVED`;
- webhook indicating final reject maps to `IN_REVIEW -> REJECTED`;
- webhook indicating resubmission required maps to `IN_REVIEW -> NEEDS_RESUBMIT`;
- webhook indicating `RED`/`consider` or ambiguous review result keeps the case in `IN_REVIEW` for later backoffice manual review.

The exact Sumsub payload fields should be confirmed against Sumsub sandbox docs during implementation, but the slice must preserve these internal state semantics.

### 4.3 Operational Boundaries

- Use bounded HTTP timeouts for Sumsub API calls.
- Treat missing Sumsub credentials as a configuration blocker for real applicant/access-token creation.
- Do not store raw webhook signing secrets in logs.
- Do not log full webhook payloads if they may contain personal data; log vendor event id, applicant id, review status, user id and transition result.
- Store bounded request/response snippets only if they are scrubbed of tokens and document content.
- Maintain a clear distinction between:
  - local signature/idempotency verification;
  - real Sumsub sandbox start-flow verification.

## 5. Explicitly Out Of Scope

This planning task does **not** implement Kotlin, SQL, runtime scripts or frontend code.

The later implementation of this slice must also not implement:

- OpenSanctions (`SNX-01`, `SNX-02`);
- AML alerts, freezes or SoF controls;
- backoffice KYC queue UI;
- manual KYC decisions (`KYC-FR-07`) beyond a minimal review placeholder if required by state modeling;
- KYC document preview/read-audit (`AUD-03`) except documenting where it will attach later;
- case attachment upload or document storage in SeaweedFS;
- sanctions-triggered account block;
- wallet/card/payment gating changes beyond reading/writing `kyc_status` for this slice's own user state;
- frontend SPA implementation;
- fake Sumsub success path without credentials.

Not claimed:

- `SNX-*`;
- `AML-*`;
- `AUD-03`;
- `UI-*`;
- `KYC-01` full pass unless real Sumsub sandbox credentials are used;
- `KYC-02` unless signature verification and duplicate event id behavior are both runtime-verified.

## 6. Service And Module Boundaries

### 6.1 Current Boundary

Implement in `platform` because:

- end-user identity/session state is Platform-owned;
- KYC status gates user-facing wallet/card flows currently in Platform;
- approved architecture places compliance/case management in Platform for MVP;
- no cross-service compliance projection exists yet.

Recommended package layout:

```text
product/apps/platform/src/main/kotlin/com/minifin/platform/kyc/
```

Recommended internal components:

- `KycController` for end-user start route;
- `SumsubWebhookController` for vendor webhook route;
- `KycService` for state machine and user gating;
- `SumsubClient` or `SumsubAdapter` for real sandbox API calls;
- `SumsubSignatureVerifier`;
- `KycRepository`.

### 6.2 Future Boundaries

Later Phase 07 slices should add:

- KYC review queue/backoffice APIs;
- case management integration;
- document preview and synchronous read-audit;
- sanctions dependency after KYC submission;
- SeaweedFS-backed evidence/document object storage.

This slice should avoid creating broad case-management abstractions unless a minimal placeholder is necessary for `consider`/manual-review state.

## 7. DB Migration Expectations

### 7.1 Reserved Migration Name

Current observed Platform latest migration after Phase 06 Slice 01:

```text
product/apps/platform/src/main/resources/db/migration/V13__merchant_outbound_webhook_delivery.sql
```

Recommended reservation:

```text
product/apps/platform/src/main/resources/db/migration/V14__kyc_sumsub_foundation.sql
```

### 7.2 Expected Platform Schema / Tables

Create schema if not already present:

```sql
create schema if not exists kyc;
```

Recommended table for current KYC state:

```sql
create table kyc.kyc_profiles (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    status text not null check (status in ('NOT_STARTED','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','NEEDS_RESUBMIT')),
    vendor text not null check (vendor in ('SUMSUB')),
    vendor_applicant_id text,
    level_name text,
    external_user_id text,
    review_answer text,
    review_reject_type text,
    review_moderation_comment text,
    submitted_at timestamptz,
    in_review_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    needs_resubmit_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    request_id text,
    correlation_id text,
    unique (end_user_id),
    unique (vendor, vendor_applicant_id)
);
```

Recommended table for start sessions/access-token metadata:

```sql
create table kyc.kyc_sessions (
    id uuid primary key,
    profile_id uuid not null references kyc.kyc_profiles(id),
    vendor text not null check (vendor in ('SUMSUB')),
    vendor_applicant_id text,
    access_token_hash text,
    external_user_id text not null,
    status text not null check (status in ('CREATED','ACTIVE','EXPIRED','FAILED')),
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);
```

Recommended table for vendor events:

```sql
create table kyc.sumsub_webhook_events (
    id uuid primary key,
    vendor_event_id text not null,
    vendor_applicant_id text,
    event_type text not null,
    review_status text,
    review_answer text,
    payload jsonb not null,
    processing_status text not null check (processing_status in ('PROCESSED','DUPLICATE','IGNORED','FAILED')),
    signature_valid boolean not null,
    processed_at timestamptz not null default now(),
    request_id text,
    correlation_id text,
    unique (vendor_event_id)
);
```

Recommended indexes:

- `kyc.kyc_profiles(end_user_id)`;
- `kyc.kyc_profiles(status, updated_at desc)`;
- `kyc.kyc_sessions(profile_id, created_at desc)`;
- `kyc.sumsub_webhook_events(vendor_applicant_id, processed_at desc)`.

If implementation finds that `identity.end_users` already has no `kyc_status`, it may add a denormalized `kyc_status` mirror only if it is kept transactionally consistent with `kyc.kyc_profiles`. Preferred first slice source of truth is `kyc.kyc_profiles`.

## 8. End-User API Contract

### 8.1 Start KYC

Route:

```text
POST /api/v1/kyc/start
```

Auth:

- end-user session cookie;
- email-verified user required.

Idempotency:

- dashboard/end-user write idempotency primitive may be reused if available for session routes;
- otherwise this route must be idempotent by natural key `end_user_id`, returning the current active profile/session.

Request:

```json
{
  "levelName": "basic-kyc"
}
```

`levelName` may be optional and default to configured `SUMSUB_LEVEL_NAME`.

Success response:

```json
{
  "data": {
    "kycProfileId": "uuid",
    "status": "SUBMITTED",
    "provider": "SUMSUB",
    "applicantId": "sumsub-applicant-id",
    "externalUserId": "uuid-or-stable-public-id",
    "accessToken": "one-time-or-short-lived-token",
    "expiresAt": "iso-8601",
    "levelName": "basic-kyc"
  },
  "errors": []
}
```

If credentials are absent:

- implementation should return a structured service-unavailable/configuration error for real start attempts;
- retained local script may assert email-verification gate and persistence behavior only if it does not claim `KYC-01` full pass.

Recommended error codes:

- `unauthenticated`;
- `email_not_verified`;
- `kyc_already_approved`;
- `sumsub_not_configured`;
- `sumsub_unavailable`;
- `invalid_kyc_level`.

## 9. Sumsub Webhook Contract

Route:

```text
POST /webhooks/sumsub/v1
```

Auth:

- no session;
- Sumsub signature required.

Verification:

- use Sumsub's documented HMAC signature header(s);
- verify over exact raw request body bytes and timestamp if Sumsub's current scheme includes one;
- use constant-time comparison;
- reject invalid signatures before state mutation;
- bounded timestamp tolerance if timestamp is present, recommended 5 minutes unless Sumsub docs require otherwise.

Idempotency:

- persist `vendor_event_id`;
- first valid event processes state transition;
- duplicate valid event with same `vendor_event_id` returns success/no-op and does not change state or write duplicate transition audit;
- invalid signature with same event id must not poison idempotency for a later valid delivery.

Minimal event mapping:

| Vendor signal | Internal effect |
|---|---|
| applicant pending/submitted | ensure `SUBMITTED` or `IN_REVIEW` |
| review started / pending | `SUBMITTED -> IN_REVIEW` |
| approved / GREEN | `IN_REVIEW -> APPROVED` |
| final rejected | `IN_REVIEW -> REJECTED` |
| resubmission requested | `IN_REVIEW -> NEEDS_RESUBMIT` |
| consider / manual review needed | remain `IN_REVIEW`; later backoffice queue slice owns decision |

The implementation should document the exact Sumsub fields used after checking the current Sumsub sandbox docs.

## 10. Runtime Verification Plan

Expected retained scripts:

```text
product/scripts/runtime/lib_phase07_kyc_sumsub.sh
product/scripts/runtime/reg_phase07_kyc_start.sh
product/scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh
```

### 10.1 `KYC-01` — Sumsub KYC start

Full pass requires:

- real Sumsub sandbox credentials configured;
- email-verified end user session;
- `POST /api/v1/kyc/start` calls Sumsub sandbox to create/resolve applicant and access token;
- Platform persists KYC profile/session with vendor applicant id;
- response returns `{data, errors}` shape with KYC session data;
- repeated start does not create duplicate applicant/profile rows.

Blocked without credentials:

- real Sumsub applicant/access-token creation;
- full `KYC-01` pass claim.

Still locally provable without credentials:

- unauthenticated request denied;
- unverified email denied;
- missing Sumsub config returns structured error and does not create fake approved KYC;
- persistence/idempotency can be covered using a deterministic adapter mode only if explicitly labeled local-adapter and not claimed as real Sumsub sandbox.

Recommended evidence tag without credentials:

```text
KYC-01 — partial
```

### 10.2 `KYC-02` — Sumsub webhook verification/idempotency

Pass requires:

- retained script signs payload with configured Sumsub webhook secret using real Sumsub HMAC algorithm;
- valid signature returns 2xx and applies exactly one state transition;
- invalid signature returns 401/403 and writes no state transition;
- duplicate valid event id returns 2xx/no-op and does not move state twice;
- DB shows one processed event row for the vendor event id and no duplicate transition.

`KYC-02` can be proven locally without Sumsub credentials if the webhook secret and signing algorithm are deterministic and match Sumsub's documented signature scheme.

## 11. Targeted Regression Checks

Run after implementation:

- `AUTH-01` or retained end-user auth scripts if the end-user session path is touched;
- `AUTH-05` wrong-role denial for KYC start route:
  - merchant session must not start end-user KYC;
  - backoffice token must not start end-user KYC;
- `AUD-01` if KYC state transitions write audit rows through existing audit path;
- `VLT-01` / card issuance smoke if KYC status gates are added to card issuance in this slice;
- `WLT-*` smoke only if wallet write gates are changed;
- `PAY-*` and `WBH-*` do not need to run unless public payment/webhook code is touched.

Do not broaden implementation just to run these checks. Regression scope follows touched code.

## 12. Documentation And Evidence Updates For Later Implementation

After runtime verification, update:

- `planning/implementation_status.md`;
- `planning/runtime_evidence_log.md`;
- `CURRENT.md`;
- `product/README.md`;
- `product/.env.example` if Sumsub env vars are added.

Expected env vars to document if used:

```text
SUMSUB_BASE_URL
SUMSUB_APP_TOKEN
SUMSUB_SECRET_KEY
SUMSUB_WEBHOOK_SECRET
SUMSUB_LEVEL_NAME
SUMSUB_TIMEOUT_MS
SUMSUB_WEBHOOK_TOLERANCE_SECONDS
```

Evidence must explicitly state:

- whether real Sumsub sandbox credentials were used;
- which actor started KYC;
- which applicant/session ids were returned/persisted;
- which webhook payload was signed and delivered;
- how duplicate vendor event id idempotency was proven;
- whether `KYC-01` is `pass` or `partial`.

## 13. Next Planned Step

Review this planning note with the project owner.

If accepted, mark it `APPROVED v0.1`, then implement only the backend/runtime scope above. After this slice, the likely next Phase 07 planning slice is one of:

- KYC backoffice review queue and manual decision foundation;
- OpenSanctions fail-closed foundation (`SNX-01`);
- compliance read-audit/document-preview slice if KYC document access becomes the next dependency.
