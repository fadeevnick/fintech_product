# Phase 02 Slice 02 — Merchant Identity Foundation — Planning Note

Status: **DRAFT v0.1**.

This document fixes the second implementation slice inside `Phase 02 — Identity, RBAC, Sessions and Audit`.

It is a pre-code scope contract. It is not implementation status and not runtime evidence.

---

## 1. Decision

`Phase 02 slice 02`:

```text
Implement merchant employee registration, merchant-scoped login/session, first-employee merchant_admin role, global email uniqueness across end-user and merchant pools, auth audit writes, and product UI behavior aligned with the accepted `MDB-UI-01` standalone HTML prototype.
```

This means:

- Merchant identity is implemented before backoffice OIDC because it reuses the platform-built password/session identity foundation from Slice 01.
- `AUTH-02` becomes provable with a real merchant employee login.
- `AUTH-04` moves from partial to cross-pool proof for end-user vs merchant employee.
- Merchant resource scoping starts now with `merchant_id`, but Stripe Connect onboarding, API keys, payments and webhooks remain later phases.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` says Phase 02 must establish identity/RBAC/sessions/audit before money movement.
- `planning/design-details/api_contracts.md` defines `POST /api/v1/merchant/register`.
- `planning/design-details/access_matrix.md` says `merchant_admin` and `merchant_member` authenticate with Platform session cookie and may access only own merchant resources.
- `planning/design-details/schema_drafts.md` assigns `identity.merchant_employees` and `identity.sessions` to the Platform database.
- `planning/design-details/ui_prototypes.md` defines `MDB-UI-01` as the merchant login/register entry point.
- `prototypes/ui/prompts/01_merchant_auth_prompt.md` defines the handoff prompt for the separate UI/UX prototype agent. Product UI implementation should wait for the returned `prototypes/ui/01_merchant_auth.html` artifact.
- Slice 01 already created `identity.email_reservations`, `identity.sessions` and `audit.audit_log`, so this slice can extend the same primitives instead of introducing a second auth stack.

## 3. Exact Scope

In this slice:

1. Add Platform DB migration for:
   - `merchant.merchants` minimal identity-time merchant record;
   - `identity.merchant_employees`;
   - session `actor_type = 'MERCHANT_EMPLOYEE'`;
   - merchant email verification records or a generalized verification subject model.
2. Implement merchant registration:
   - normalized unique email;
   - global email reservation in pool `MERCHANT_EMPLOYEE`;
   - password hash storage, never plaintext;
   - merchant entity creation with `kyb_status = 'NOT_STARTED'`;
   - first employee linked as `merchant_admin`;
   - local/dev verification token issued through the same real verification path as end users.
3. Implement merchant email verification:
   - token lookup and expiry;
   - state transition to active/verified;
   - idempotent repeated verify response.
4. Implement merchant login:
   - password verification;
   - verified-email requirement;
   - opaque server-side session creation with actor type `MERCHANT_EMPLOYEE`;
   - session cookie compatible with the existing Platform session mechanism.
5. Implement `GET /api/v1/merchant/me`:
   - returns current employee, role and merchant id when authenticated as merchant;
   - rejects unauthenticated requests;
   - rejects end-user sessions with 403.
6. Implement merchant logout if needed for a complete session lifecycle.
7. Implement auth audit writes for:
   - merchant registered;
   - merchant email verified;
   - merchant login success;
   - merchant login failure;
   - merchant logout/session revoked if implemented.
8. Add retained runtime scripts for the covered checks.
9. After the standalone HTML prototype is provided and accepted, add a minimal `spa-merchant` implementation checkpoint for `MDB-UI-01`:
   - register form state;
   - verification pending/success/error state;
   - login form state;
   - authenticated merchant session state;
   - unauthenticated/wrong-role error state.

## 4. Explicitly In Scope

### Code Changes

- Platform service migrations and Kotlin code.
- Platform merchant identity/session/audit application services.
- Platform API controllers for merchant identity routes.
- Shared or generalized session lookup that can distinguish end-user vs merchant sessions.
- Role/resource context for merchant employees:
  - `merchant_admin` for first employee;
  - `merchant_member` model may exist as enum/data support, but invite/member-management workflow is out of scope.
- Minimal merchant entity table required to make `merchant_id` scoping real.
- Retained runtime scripts under `product/scripts/runtime/reg_phase02_*`.
- Minimal `spa-merchant` implementation checkpoint for login/register, based on the accepted standalone HTML prototype.

### Behavioral Outcomes

- New merchant can register with company basics and first employee credentials.
- Merchant employee can verify email.
- Verified merchant employee can log in and receive an opaque session cookie.
- Merchant session authenticates `GET /api/v1/merchant/me`.
- End-user session cannot authenticate `GET /api/v1/merchant/me`.
- Same email cannot register as both end user and merchant employee.
- Merchant auth/session events write audit rows.

### Verification Outcomes

This slice exercises:

- `AUTH-02` for merchant registration/login.
- `AUTH-04` fully for end-user vs merchant employee global email uniqueness.
- `AUTH-05` partially or fully for unauthenticated and wrong-role denial across end-user/merchant endpoints.
- `AUD-01` for merchant auth audit events.
- `AUD-02` regression for audit append-only protection.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- Stripe Connect account creation or hosted onboarding link;
- Stripe webhook receiver;
- merchant API key lifecycle;
- public Payments API;
- payment list/refund/dispute/settlement dashboard workflows;
- merchant invite flow or multi-employee administration UI;
- backoffice OIDC login via Keycloak;
- full RBAC matrix across all roles;
- wallet, ledger, KYC, card, payment or compliance behavior;
- production email provider integration;
- password reset / MFA / device management;
- CSRF hardening beyond the current local session smoke level unless it is low-risk and contained.

Do not add fake versions of these. If a capability is not in scope, it must remain absent or explicitly return a real unsupported response.

## 6. Proposed API Shape

### 6.1 Merchant Registration

```http
POST /api/v1/merchant/register
```

Request:

```json
{
  "email": "owner@example.com",
  "password": "long-enough-password",
  "companyName": "Example GmbH",
  "country": "DE",
  "businessType": "company"
}
```

Response:

```json
{
  "data": {
    "merchantId": "uuid",
    "employeeId": "uuid",
    "email": "owner@example.com",
    "role": "merchant_admin",
    "employeeStatus": "EMAIL_UNVERIFIED",
    "merchantStatus": "NOT_STARTED",
    "verificationToken": "local-only"
  },
  "errors": []
}
```

### 6.2 Merchant Email Verification

```http
POST /api/v1/merchant/email/verify
```

### 6.3 Merchant Login

```http
POST /api/v1/merchant/login
```

### 6.4 Current Merchant Session

```http
GET /api/v1/merchant/me
```

Response:

```json
{
  "data": {
    "merchantId": "uuid",
    "employeeId": "uuid",
    "email": "owner@example.com",
    "role": "merchant_admin",
    "employeeStatus": "ACTIVE",
    "merchantStatus": "NOT_STARTED"
  },
  "errors": []
}
```

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` after verification
- `product/apps/platform/**`
- `product/apps/spa-merchant/**`
- `product/scripts/runtime/**`

### 7.2 Create

- Platform migration, likely:
  - `product/apps/platform/src/main/resources/db/migration/V3__merchant_identity_foundation.sql`
- Platform merchant identity Kotlin code under:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/**`
  - optional `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/**`
- Retained scripts, likely:
  - `product/scripts/runtime/reg_phase02_merchant_auth.sh`
  - `product/scripts/runtime/reg_phase02_cross_pool_email_uniqueness.sh`
  - `product/scripts/runtime/reg_phase02_cross_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth_audit.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved inputs, unless implementation discovers a real mismatch and owner approves an update.
- `planning/runtime_checklists.md` — approved check IDs; do not churn IDs during this slice.
- Acquirer/Network/Issuer/Vault domain behavior.

## 8. Recommended Change Order

1. Add Platform DB migration for minimal merchant + merchant employee tables and session actor extension.
2. Generalize session lookup so endpoint handlers can require `END_USER` or `MERCHANT_EMPLOYEE`.
3. Add merchant registration and local verification path.
4. Add merchant login/session/logout and `GET /api/v1/merchant/me`.
5. Add cross-role denial checks for end-user vs merchant endpoints.
6. Add merchant auth audit writes.
7. Add retained runtime scripts.
8. Confirm `prototypes/ui/01_merchant_auth.html` exists and is accepted as the visual prototype input.
9. Add minimal `MDB-UI-01` implementation checkpoint in `spa-merchant`.
10. Rebuild Platform and merchant SPA images.
11. Run Phase 01 regression subset plus Phase 02 identity scripts.
12. Record evidence in `planning/runtime_evidence_log.md`.
13. Update `planning/implementation_status.md`, `README.md` and `CURRENT.md`.

## 9. Linked Runtime Checks

This slice targets:

- `AUTH-02` — merchant registration and login.
- `AUTH-04` — global email uniqueness across end-user and merchant pools.
- `AUTH-05` — protected endpoint denial for unauthenticated and wrong-role actors.
- `AUD-01` — auth audit events.
- `AUD-02` — audit append-only protection regression.

Expected result tags:

- `AUTH-02`: `pass` if register + verify + login + `me` all pass.
- `AUTH-04`: `pass` if an already registered end-user email cannot register as merchant and an already registered merchant email cannot register as end-user.
- `AUTH-05`: `partial` if wrong-role denial is proven only for end-user/merchant sessions; `pass` waits for backoffice OIDC unless the check is explicitly scoped in evidence.
- `AUD-01`: `pass` if end-user and merchant auth/session audit rows are inserted.
- `AUD-02`: `pass` if existing DB-level no-update/no-delete trigger still rejects mutation.

## 10. Linked UI Prototype

- `MDB-UI-01` — Login/register.

This slice should implement only the minimal product UI checkpoint for:

- merchant registration;
- verification pending/success/error;
- login;
- current merchant session;
- unauthenticated/protected endpoint error;
- wrong-role denial state when an end-user session hits merchant `me`.

Do not build final merchant dashboard navigation in this slice.

Terminology note:

- `prototypes/ui/01_merchant_auth.html` is the standalone visual prototype artifact.
- `product/apps/spa-merchant/**` is product implementation code, not the prototype itself.
- The SPA checkpoint should implement only the slice-relevant states from the accepted prototype.

## 11. Verification Shape

After implementation, run:

- Platform image rebuild.
- Merchant SPA image rebuild.
- Phase 01 regression subset:
  - `product/scripts/runtime/reg_phase01_runtime_health.sh`
  - `product/scripts/runtime/reg_phase01_migrations.sh`
  - `product/scripts/runtime/reg_phase01_logs.sh`
  - `product/scripts/runtime/reg_phase01_metrics.sh`
  - `product/scripts/runtime/reg_phase01_spa_shells.sh`
- Phase 02 regression and slice scripts:
  - `product/scripts/runtime/reg_phase02_enduser_auth.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth.sh`
  - `product/scripts/runtime/reg_phase02_cross_pool_email_uniqueness.sh`
  - `product/scripts/runtime/reg_phase02_cross_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth_audit.sh`
  - `product/scripts/runtime/reg_phase02_auth_audit.sh`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

## 12. Open Questions

1. **Merchant entity depth**

   Recommendation: create a minimal local `merchant.merchants` row with `kyb_status = 'NOT_STARTED'`, but do not create Stripe accounts in this slice.

   Reason: `merchant_id` scoping must be real for RBAC, while Stripe Connect belongs to Phase 04.

2. **First employee role**

   Recommendation: first registered merchant employee is always `merchant_admin`; `merchant_member` invite/member management stays out of scope.

   Reason: it proves merchant admin access without expanding into organization administration.

3. **Merchant email verification**

   Recommendation: require email verification before merchant login, matching the end-user hard gate.

   Reason: one auth policy across password-based pools is simpler and avoids unverified merchant dashboard sessions.

4. **AUTH-05 result tag**

   Recommendation: record `AUTH-05` as `partial` until backoffice OIDC exists, even if end-user/merchant wrong-role denial passes.

   Reason: the check definition says wrong-role actors broadly; full proof needs all user pools.

## 13. Next Planned Step

Owner sends `prototypes/ui/prompts/01_merchant_auth_prompt.md` to the UI/UX prototype agent and provides the returned `01_merchant_auth.html` file. After that, owner reviews this draft and approves or changes the open questions. Only after prototype acceptance and slice approval should product code for Phase 02 Slice 02 start.
