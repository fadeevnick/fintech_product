# Phase 02 Slice 01 — Identity Foundation — Planning Note

Status: **APPROVED v0.1** (approved 2026-05-15).

This document fixes the first implementation slice inside `Phase 02 — Identity, RBAC, Sessions and Audit`.

It is a pre-code scope contract. It is not implementation status and not runtime evidence.

---

## 1. Decision

`Phase 02 slice 01`:

```text
Implement the Platform identity foundation for end-user registration, email verification, login, opaque server-side sessions, basic protected `me` endpoint, auth audit writes, and the first identity UI prototype checkpoint.
```

This means:

- The first real user-facing capability is end-user identity, not merchant/backoffice identity.
- Session cookies become the foundation for later end-user wallet/KYC/card workflows.
- Audit write path starts with auth/session events before money movement exists.
- The slice proves the auth plumbing with retained runtime scripts, not with manual inspection.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` says Phase 02 must establish identity/RBAC/sessions/audit before money movement.
- `planning/design-details/api_contracts.md` defines end-user routes:
  - `POST /api/v1/enduser/register`
  - `POST /api/v1/enduser/login`
  - `POST /api/v1/enduser/email/verify`
  - `GET /api/v1/enduser/me`
- `planning/design-details/schema_drafts.md` assigns identity/session/audit tables to the Platform database.
- `planning/design-details/ui_prototypes.md` defines `UEW-UI-01` as the end-user login/register entry point.
- End-user identity is the smallest useful session-bearing actor model. Merchant identity and backoffice OIDC have different scope and should not be mixed into the first slice.

## 3. Exact Scope

In this slice:

1. Add Platform DB schema/migrations for:
   - `identity.end_users`
   - `identity.email_verifications`
   - `identity.sessions`
   - `identity.email_reservations` or equivalent global email uniqueness mechanism
   - `audit.audit_log`
2. Implement end-user registration:
   - normalized unique email;
   - password hash storage, never plaintext;
   - initial `email_unverified` state;
   - email verification token issuance in local/dev mode.
3. Implement email verification:
   - token lookup and expiry;
   - state transition to verified;
   - idempotent repeated verify response.
4. Implement end-user login:
   - password verification;
   - verified-email requirement unless explicitly recorded otherwise during implementation;
   - opaque server-side session creation;
   - secure-ish local cookie attributes suitable for local dev.
5. Implement `GET /api/v1/enduser/me`:
   - returns current user/session when authenticated;
   - rejects unauthenticated requests.
6. Implement logout if needed to make session lifecycle testable.
7. Implement auth audit writes for:
   - registration created;
   - email verified;
   - login success;
   - login failure without leaking password details;
   - logout/session revoked if implemented.
8. Add retained runtime scripts for the covered checks.
9. Add a minimal implementation-near UI prototype checkpoint for `UEW-UI-01`:
   - can be a static/prototype route or very thin shell state inside `spa-enduser`;
   - must show the expected login/register/error/session states;
   - does not need polished final UX.

## 4. Explicitly In Scope

### Code Changes

- Platform service migrations and Kotlin code.
- Platform service auth/session/audit application services.
- Platform API controllers for end-user identity routes.
- Cookie/session middleware or interceptor for protected routes.
- Password hashing dependency/config.
- Minimal local email verification delivery strategy:
  - acceptable: return/dev-expose verification token in local-only response or log;
  - not acceptable: fake verified users without exercising verification path.
- Retained runtime scripts under `product/scripts/runtime/reg_phase02_*`.
- Minimal `spa-enduser` prototype route/state for login/register.

### Behavioral Outcomes

- New end user can register.
- Email verification transitions user to verified.
- Verified user can log in and receive an opaque session cookie.
- Session cookie authenticates `GET /api/v1/enduser/me`.
- Unauthenticated request to `GET /api/v1/enduser/me` is rejected.
- Auth/session events write audit rows.
- Reusing the same email across identity pools is prepared by a global reservation mechanism, even if merchant registration is implemented in a later slice.

### Verification Outcomes

This slice exercises:

- `AUTH-01` partially or fully for end-user registration/login.
- `AUTH-04` partially for global email uniqueness foundation.
- `AUTH-05` partially for protected endpoint denial.
- `AUD-01` for auth audit events.
- `AUD-02` partially if append-only protection is enforced in this slice.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- merchant employee registration/login (`AUTH-02`);
- backoffice OIDC login via Keycloak (`AUTH-03`);
- full RBAC matrix across all roles;
- merchant dashboard auth UI;
- backoffice UI/OIDC flow;
- wallet, ledger, KYC, card, payment or compliance behavior;
- production email provider integration;
- password reset / MFA / device management;
- API key auth;
- service-to-service signed tokens beyond existing runtime needs.

Do not add fake versions of these. If a capability is not in scope, it must remain absent or explicitly return a real unsupported response.

## 6. Concrete Files To Touch

### 6.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` after verification
- `product/apps/platform/**`
- `product/apps/spa-enduser/**`
- `product/scripts/runtime/**`

### 6.2 Create

- Platform migration files under `product/apps/platform/src/main/resources/db/migration/**`
- Platform identity/session/audit packages under `product/apps/platform/src/main/kotlin/com/minifin/platform/**`
- Retained scripts, likely:
  - `product/scripts/runtime/reg_phase02_enduser_auth.sh`
  - `product/scripts/runtime/reg_phase02_protected_endpoint.sh`
  - `product/scripts/runtime/reg_phase02_auth_audit.sh`
  - `product/scripts/runtime/reg_phase02_email_uniqueness.sh`
- Optional local-only fixture/config files if needed for deterministic smoke checks.

### 6.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved inputs, unless implementation discovers a real mismatch and owner approves an update.
- `planning/runtime_checklists.md` — approved check IDs; do not churn IDs during this slice.
- Non-Platform service domain behavior.

## 7. Recommended Change Order

1. Add Platform DB migrations for identity/session/audit foundation.
2. Add repository/data access layer with SQL-first style.
3. Add password hashing and token/session primitives.
4. Add end-user registration and verification endpoints.
5. Add login/session middleware and `GET /api/v1/enduser/me`.
6. Add audit write path and auth event writes.
7. Add retained runtime scripts.
8. Add minimal `UEW-UI-01` prototype checkpoint.
9. Rebuild Platform and end-user SPA images.
10. Run Phase 01 regression subset plus Phase 02 slice scripts.
11. Record evidence in `planning/runtime_evidence_log.md`.
12. Update `planning/implementation_status.md`.

## 8. Linked Runtime Checks

This slice targets:

- `AUTH-01` — end-user registration and login.
- `AUTH-04` — global email uniqueness, at least foundation-level proof.
- `AUTH-05` — protected endpoint denial, scoped to `GET /api/v1/enduser/me`.
- `AUD-01` — auth audit events.
- `AUD-02` — audit append-only protection, if feasible in this slice.

Expected result tags:

- `AUTH-01`: `pass` if register + verify + login + `me` all pass.
- `AUTH-04`: `partial` if only end-user reservation exists but merchant pool is not implemented yet.
- `AUTH-05`: `partial` because full wrong-role coverage needs merchant/backoffice roles.
- `AUD-01`: `pass` if auth/session audit rows are inserted.
- `AUD-02`: `partial` or `pass` depending on DB-level protection implemented in this slice.

## 9. Linked UI Prototype

- `UEW-UI-01` — Login/register.

This slice should create only a minimal implementation-near prototype checkpoint for:

- register form state;
- verification pending/success/error state;
- login form state;
- authenticated `me` state;
- unauthenticated/protected endpoint error state.

Do not build final end-user app navigation in this slice.

## 10. Verification Shape

After implementation, run:

- Platform image rebuild.
- End-user SPA image rebuild if UI prototype changes.
- Phase 01 regression subset:
  - `product/scripts/runtime/reg_phase01_runtime_health.sh`
  - `product/scripts/runtime/reg_phase01_migrations.sh`
  - `product/scripts/runtime/reg_phase01_logs.sh`
  - `product/scripts/runtime/reg_phase01_metrics.sh`
- Phase 02 slice scripts:
  - `product/scripts/runtime/reg_phase02_enduser_auth.sh`
  - `product/scripts/runtime/reg_phase02_protected_endpoint.sh`
  - `product/scripts/runtime/reg_phase02_auth_audit.sh`
  - `product/scripts/runtime/reg_phase02_email_uniqueness.sh`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

## 11. Resolution Notes

1. **Email verification local mode**

   Accepted: return verification token in local-only smoke response, guarded by config.

   Reason: this keeps the verification path real without introducing an email provider before the email worker slice.

2. **Password hashing**

   Accepted: use Spring Security Crypto BCrypt now, without enabling full Spring Security web stack in this slice.

   Reason: password storage must be real immediately, but a full security filter chain can expand scope if introduced too early.

3. **AUD-02 depth in this slice**

   Accepted: implement audit writes now and add DB-level no-update/no-delete trigger if low-risk.

   Reason: auth audit writes should exist now, but hard DB privilege separation may need a focused hardening slice.

4. **UI prototype depth**

   Accepted: add a minimal `spa-enduser` login/register prototype state, not a polished workflow.

   Reason: it creates a visible contract checkpoint without turning Phase 02 into a full UI phase.
