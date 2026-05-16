# Phase 02 Slice 03 — Backoffice OIDC and RBAC Foundation — Planning Note

Status: **BACKEND/RUNTIME SUB-SCOPE EXECUTED v0.2; frontend checkpoint pending**.

This document fixes the third implementation slice inside `Phase 02 — Identity, RBAC, Sessions and Audit`.

It is a pre-code scope contract. It is not implementation status and not runtime evidence.

Execution note:
- The backend/domain/runtime portion was approved with `го`, implemented and verified on 2026-05-16.
- The backoffice SPA/frontend login implementation remains pending.

---

## 1. Decision

`Phase 02 slice 03`:

```text
Implement backoffice OIDC token authentication against local Keycloak, map Keycloak roles into Platform backoffice RBAC roles, expose a minimal protected backoffice identity endpoint, prove wrong-role denial against end-user/merchant sessions, and write auth/audit evidence for the backoffice auth path.
```

This means:

- Backoffice identity uses Keycloak OIDC, not Platform password registration.
- Platform validates bearer JWTs through Keycloak issuer/JWKS.
- Backoffice roles map to the approved role names:
  - `backoffice_operator`
  - `compliance_officer`
  - `senior_compliance`
- This slice completes the missing identity pool needed before Phase 03 money/ledger work starts.
- Backoffice workflow UI remains out of scope until the relevant standalone HTML prototype exists and a frontend slice is approved.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` says Phase 02 must establish end-user, merchant and backoffice identity/RBAC before money movement.
- `planning/runtime_checklists.md` defines `AUTH-03` as backoffice OIDC login with role mapping into Platform RBAC.
- `planning/design-details/access_matrix.md` defines backoffice roles and Keycloak OIDC as their auth source.
- `AUTH-05` remains partial until backoffice wrong-role/unauthenticated denial is proven.
- Slice 01 and Slice 02 already implemented password/session identity for end-user and merchant pools; the remaining identity pool is backoffice.

## 3. Exact Scope

In this slice:

1. Add Platform configuration for local Keycloak issuer/JWKS:
   - local issuer: `http://keycloak:8080/realms/minifin-backoffice` inside Docker;
   - host issuer for runtime scripts: `http://localhost:18080/realms/minifin-backoffice`;
   - support local dev without committing secrets.
2. Add Spring Security OAuth2 resource-server support to Platform.
3. Add a backoffice auth/RBAC component that:
   - validates bearer JWTs;
   - extracts subject, email and role claims;
   - maps Keycloak roles to approved Platform roles;
   - rejects tokens without one approved role.
4. Add minimal protected endpoint:
   - `GET /api/v1/backoffice/me`
   - returns OIDC subject, email and mapped Platform roles.
5. Add minimal RBAC-protected smoke endpoints if needed for role checks:
   - for example `GET /api/v1/backoffice/rbac/operator-check`;
   - for example `GET /api/v1/backoffice/rbac/compliance-check`;
   - keep these clearly local/runtime smoke endpoints or use real `me` plus role variants if cleaner.
6. Add auth audit writes for:
   - successful backoffice token authentication on `me`;
   - rejected authenticated token with no approved backoffice role if observable in app path.
7. Add retained runtime scripts that:
   - seed or upsert local Keycloak realm/client/users/roles through Keycloak admin API;
   - acquire access tokens for test users;
   - call Platform backoffice protected endpoint with bearer token;
   - prove end-user/merchant cookie sessions cannot access backoffice endpoint;
   - prove missing/invalid bearer token gets 401;
   - prove role-limited endpoint returns expected 403 where applicable.

## 4. Explicitly In Scope

### Code Changes

- Platform service Kotlin code and config.
- Platform build dependency changes for OAuth2 resource-server/JWT validation.
- Backoffice auth/RBAC support package, likely under:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/backoffice/**`
  - or `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/**` if the local pattern stays consolidated.
- Security configuration that keeps existing end-user/merchant cookie endpoints working.
- Retained runtime scripts under `product/scripts/runtime/reg_phase02_*`.
- Status/evidence docs after verification.

### Behavioral Outcomes

- A local Keycloak user with `backoffice_operator` can authenticate to Platform with a bearer token.
- Platform maps Keycloak roles into Platform RBAC roles.
- `GET /api/v1/backoffice/me` returns the current backoffice identity and roles.
- Requests without bearer token are rejected.
- End-user and merchant cookie sessions are rejected by backoffice endpoint.
- Token with no approved backoffice role is rejected.
- Backoffice auth/read event writes audit rows.

### Verification Outcomes

This slice exercises:

- `AUTH-03` for backoffice OIDC login and role mapping.
- `AUTH-05` for unauthenticated and wrong-role denial across all three user pools.
- `AUD-01` for backoffice auth audit events.
- `AUD-02` regression for audit append-only protection.
- `RUN-04` regression for Keycloak connectivity.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- Backoffice SPA login flow or UI screens.
- KYC, AML, sanctions, manual ops or chargeback queues.
- Sensitive read-audit workflows such as KYC document preview or audit viewer access.
- Full admin user management in the product.
- Production Keycloak realm export/secrets management.
- MFA, device posture, step-up auth or session revocation through Keycloak admin events.
- Fine-grained policy engine beyond the approved role mapping required for Phase 02.

Do not add fake queue data or fake compliance workflows. If an endpoint is not in scope, it must remain absent or return a real unsupported response.

## 6. Proposed API Shape

### 6.1 Current Backoffice Identity

```http
GET /api/v1/backoffice/me
Authorization: Bearer <keycloak-access-token>
```

Response:

```json
{
  "data": {
    "subject": "keycloak-user-id",
    "email": "operator@minifin.local",
    "roles": ["backoffice_operator"],
    "issuer": "http://keycloak:8080/realms/minifin-backoffice"
  },
  "errors": []
}
```

### 6.2 Error Shape

Missing/invalid token:

```json
{
  "data": null,
  "errors": [
    {
      "code": "unauthenticated",
      "message": "Authentication is required."
    }
  ]
}
```

Valid token without approved backoffice role:

```json
{
  "data": null,
  "errors": [
    {
      "code": "forbidden_role",
      "message": "Backoffice role is required."
    }
  ]
}
```

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` after verification
- `product/apps/platform/build.gradle.kts`
- `product/apps/platform/src/main/resources/application.yml`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/SecurityConfig.kt`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/AuditRepository.kt` only if audit helper needs extension
- `product/deploy/docker-compose.yml` only if issuer/JWKS env vars are required

### 7.2 Create

- Platform backoffice auth/RBAC Kotlin code, likely:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/backoffice/BackofficeModels.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/backoffice/BackofficeSecurity.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/backoffice/BackofficeController.kt`
- Retained scripts, likely:
  - `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_auth_audit.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved inputs, unless implementation discovers a real mismatch and owner approves an update.
- `planning/runtime_checklists.md` — approved check IDs; do not churn IDs during this slice.
- `product/apps/spa-backoffice/**` — frontend implementation is out of scope.
- Ledger, wallet, payment, KYC, AML, sanctions, card, chargeback or settlement domain behavior.

## 8. Recommended Change Order

1. Add OAuth2 resource-server dependency and local Keycloak config.
2. Add Keycloak realm/client/user/role upsert logic in a retained runtime helper script.
3. Configure Platform security so:
   - existing end-user/merchant cookie endpoints keep current behavior;
   - `/api/v1/backoffice/**` requires bearer JWT;
   - backoffice auth errors return the standard `ApiResponse` error shape.
4. Add role extraction/mapping to Platform role names.
5. Add `GET /api/v1/backoffice/me`.
6. Add role-denial behavior for valid token without approved backoffice role.
7. Add audit writes for successful backoffice identity access and observable auth denial.
8. Add retained runtime scripts.
9. Rebuild Platform image and recreate `platform`.
10. Run Phase 01/02 regression subset:
    - Keycloak connectivity;
    - end-user auth;
    - merchant auth;
    - protected endpoint denial;
    - audit append-only.
11. Run new backoffice OIDC/RBAC scripts.
12. Record evidence in `planning/runtime_evidence_log.md`.
13. Update `planning/implementation_status.md`, `README.md` and `CURRENT.md`.
14. Commit completed backend/runtime slice separately from any future frontend work.

## 9. Linked Runtime Checks

This slice targets:

- `AUTH-03` — backoffice OIDC login.
- `AUTH-05` — protected endpoint denial for unauthenticated and wrong-role actors across end-user, merchant and backoffice paths.
- `AUD-01` — auth audit events.
- `AUD-02` — audit append-only protection regression.
- `RUN-04` — Keycloak connectivity regression.

Expected result tags:

- `AUTH-03`: `pass` if a Keycloak backoffice user obtains a token and Platform maps approved roles.
- `AUTH-05`: `pass` if unauthenticated, end-user, merchant and wrong-role backoffice actors are denied from protected endpoints as expected.
- `AUD-01`: `pass` if end-user, merchant and backoffice auth/session audit rows are inserted.
- `AUD-02`: `pass` if existing DB-level no-update/no-delete trigger still rejects mutation.
- `RUN-04`: `pass` if Keycloak discovery/JWKS and token acquisition work in local compose.

## 10. Linked UI Prototype

No product frontend implementation is in scope.

Available UI prototype artifact:

- `prototypes/ui/04_backoffice_oidc_login.html` — standalone `BOF-UI-01` backoffice OIDC login/session state screen.

Terminology note:

- `prototypes/ui/*.html` are standalone visual prototype artifacts.
- `product/apps/spa-backoffice/**` is product implementation code, not a prototype itself.
- This slice must not implement backoffice SPA login UX.

## 11. Verification Shape

After implementation, run:

- Platform image rebuild:
  - `docker compose -f deploy/docker-compose.yml build platform`
- Platform recreate:
  - `docker compose -f deploy/docker-compose.yml up -d platform`
- Regression scripts:
  - `product/scripts/runtime/reg_phase01_keycloak.sh`
  - `product/scripts/runtime/reg_phase01_runtime_health.sh`
  - `product/scripts/runtime/reg_phase02_enduser_auth.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth.sh`
  - `product/scripts/runtime/reg_phase02_cross_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_auth_audit.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth_audit.sh`
- New scripts:
  - `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_auth_audit.sh`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

## 12. Open Questions

1. **Keycloak realm setup**

   Decision: runtime script upserts the local `minifin-backoffice` realm, public test client and smoke users through Keycloak admin API.

   Reason: Phase 01 only proves Keycloak is reachable. A retained script makes local OIDC verification reproducible without committing generated realm state or secrets.

2. **Backoffice endpoint scope**

   Decision: implement only `GET /api/v1/backoffice/me`; no extra RBAC smoke endpoints were needed.

   Reason: this proves auth/RBAC without starting compliance queues before their phases.

3. **Role source**

   Decision: read roles from realm/client access roles and map only exact approved role strings.

   Reason: local Keycloak setup stays simple, while Platform keeps a strict allow-list.

4. **Audit event semantics**

   Decision: audit successful `backoffice.me` as `identity.backoffice_authenticated`, audit role-denial as `identity.backoffice_auth_failed`, and leave sensitive read-audit (`AUD-03`) for the later compliance/read-audit slice.

   Reason: this keeps `AUD-01` focused on auth/session events and avoids pretending that compliance read-audit is complete.

## 13. Next Planned Step

Backend/runtime implementation is verified and recorded in `planning/runtime_evidence_log.md`.

Next planned step: draft the next non-frontend Phase 02 slice for the remaining identity/audit foundation work while frontend implementation remains gated by accepted standalone HTML prototypes.
