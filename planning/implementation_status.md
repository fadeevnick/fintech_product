# Implementation Status

Last updated: 2026-05-15.

---

## Phase 01 Slice 01 — Product Skeleton

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md` — APPROVED v0.1.

Created:
- `product/` root.
- Gradle multi-project backend skeleton.
- Five Spring Boot service shells: `platform`, `acquirer`, `network`, `issuer`, `vault`.
- Shared backend lib placeholders: contracts public/internal/events, db, observability, service-auth.
- Minimal `/internal/health` and `/internal/ready` endpoints per backend service.
- One Flyway baseline migration per service DB.
- pnpm workspace skeleton.
- Three React/Vite SPA shells: `spa-enduser`, `spa-merchant`, `spa-backoffice`.
- Docker Compose core runtime: app services, service DBs, Keycloak, Kafka, SeaweedFS, Traefik, nginx SPA serving, Prometheus, Grafana, Tempo, Loki, OTel Collector, Vector.
- `.env.example`.
- `product/README.md`.
- Phase 01 runtime smoke scripts under `product/scripts/runtime/`.

Explicitly not implemented:
- auth/RBAC/session flows;
- ledger, wallet, card, payment, merchant, KYC, sanctions, AML, chargeback domains;
- vendor integrations;
- real UI workflows;
- outbox behavior;
- CI/CD.

Runtime verification:
- backend Docker images build successfully;
- SPA Docker images build successfully;
- full compose stack starts successfully;
- `RUN-01` — pass;
- `RUN-02` — pass;
- `RUN-03` — pass;
- `RUN-04` — pass;
- `RUN-05` — pass;
- `RUN-06` — pass;
- `RUN-07` — pass;
- `RUN-08` — pass-indirect;
- `UI-01` — pass.

Runtime-driven fixes applied:
- Kafka image changed from unavailable `bitnami/kafka:3.9` to available `apache/kafka:3.9.1`.
- `RUN-03` strengthened to check Kafka metadata through the `platform` backend.
- `RUN-05` strengthened to perform SeaweedFS bucket/object write-read.
- `RUN-06` strengthened to query Loki for `platform` container logs.

Next planned step:
- Draft the first Phase 02 implementation slice planning note before writing identity/auth code.

---

## Phase 02 Slice 01 — Identity Foundation

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_02_slice_01_identity_foundation_planning.md` — APPROVED v0.1.

Created/changed:
- Platform identity migrations:
  - `identity.end_users`
  - `identity.email_reservations`
  - `identity.email_verifications`
  - `identity.sessions`
  - `audit.audit_log`
- Audit append-only DB triggers for `audit.audit_log`.
- End-user identity routes:
  - `POST /api/v1/enduser/register`
  - `POST /api/v1/enduser/email/verify`
  - `POST /api/v1/enduser/login`
  - `GET /api/v1/enduser/me`
  - `POST /api/v1/enduser/logout`
- BCrypt password hashing.
- Opaque server-side session cookie `MFP_SESSION`.
- Local-only verification token exposure for smoke automation.
- Auth audit writes for registration, verification, login success/failure and logout.
- Minimal `UEW-UI-01` login/register/session state checkpoint in `spa-enduser`.
- Phase 02 retained runtime scripts under `product/scripts/runtime/`.

Explicitly not implemented:
- merchant employee registration/login;
- backoffice OIDC login;
- full RBAC matrix;
- wallet, ledger, payment, KYC, card or compliance behavior;
- production email provider integration.

Runtime verification:
- Phase 01 regression subset passed:
  - `RUN-01`
  - `RUN-02`
  - `RUN-06`
  - `RUN-07`
  - `UI-01`
- `AUTH-01` — pass.
- `AUTH-04` — partial.
- `AUTH-05` — partial.
- `AUD-01` — pass.
- `AUD-02` — pass.

Result tag notes:
- `AUTH-04` is partial because merchant/backoffice pools are not implemented yet; the global email reservation table exists and duplicate end-user reservation is rejected.
- `AUTH-05` is partial because full wrong-role coverage requires merchant/backoffice roles; unauthenticated denial for `GET /api/v1/enduser/me` is proven.

Next planned step:
- Review and approve or revise `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` before writing more product code.

---

## Phase 02 Slice 02 — Merchant Identity Foundation

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend checkpoint not started**.

Planning contract:
- `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — backend/runtime sub-scope executed v0.2; frontend checkpoint pending.

Implemented backend/runtime scope:
- Platform DB migration `V3__merchant_identity_foundation.sql` for minimal merchants, merchant employees, merchant email verification records and `MERCHANT_EMPLOYEE` sessions.
- Merchant registration, email verification, login, logout and `GET /api/v1/merchant/me`.
- First merchant employee is `merchant_admin`; merchant starts with `kyb_status = NOT_STARTED`.
- Cross-pool email uniqueness across end-user and merchant employee pools.
- Wrong-role denial between end-user and merchant sessions.
- Merchant auth audit writes, including login failure audit after fixing expected auth-error transaction rollback.
- Retained runtime scripts:
  - `product/scripts/runtime/reg_phase02_merchant_auth.sh`
  - `product/scripts/runtime/reg_phase02_cross_pool_email_uniqueness.sh`
  - `product/scripts/runtime/reg_phase02_cross_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth_audit.sh`

Created:
- `prototypes/ui/01_app_shell_cross_surface.html` — standalone cross-surface app shell HTML prototype from UI/UX prototype workflow.
- `prototypes/ui/02_merchant_auth.html` — standalone `MDB-UI-01` merchant auth HTML prototype from UI/UX prototype workflow.
- `prototypes/ui/03_enduser_auth.html` — standalone end-user auth HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.

Explicitly not started:
- `spa-merchant` frontend implementation checkpoint for `MDB-UI-01`;
- Stripe Connect onboarding;
- merchant API keys;
- public Payments API;
- backoffice OIDC/RBAC implementation.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 02 Slice 02 Backend/Runtime Verification`.
- `AUTH-02` — pass for merchant register/verify/login/me.
- `AUTH-04` — pass for end-user vs merchant employee cross-pool email uniqueness.
- `AUTH-05` — partial for unauthenticated and end-user/merchant wrong-role denial; full pass waits for backoffice OIDC/RBAC.
- `AUD-01` — pass for end-user and merchant auth audit events.
- `AUD-02` — pass for audit append-only protection.

Next planned step:
- Draft the next non-frontend Phase 02 slice while frontend implementation remains gated by accepted standalone HTML prototypes.
