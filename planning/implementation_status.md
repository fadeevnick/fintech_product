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

Status: **PLANNING DRAFT — not approved, no product code started**.

Planning contract:
- `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — DRAFT v0.1.

Proposed scope:
- merchant employee registration/login;
- minimal merchant entity for real `merchant_id` scoping;
- first merchant employee as `merchant_admin`;
- merchant-scoped opaque session;
- cross-pool email uniqueness proof across end-user and merchant pools;
- merchant auth audit writes;
- minimal `MDB-UI-01` product implementation checkpoint based on an accepted standalone HTML prototype.

Created:
- `prototypes/ui/01_app_shell_cross_surface.html` — standalone cross-surface app shell HTML prototype from UI/UX prototype workflow.
- `prototypes/ui/02_merchant_auth.html` — standalone `MDB-UI-01` merchant auth HTML prototype from UI/UX prototype workflow.

Explicitly not started:
- product code for this slice;
- Stripe Connect onboarding;
- merchant API keys;
- public Payments API;
- backoffice OIDC/RBAC implementation.

Next planned step:
- Continue the UI prototype baseline before product code starts, likely with end-user auth or backoffice OIDC landing.
