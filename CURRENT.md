# CURRENT — Mini Fintech Platform handoff state

Last updated: 2026-05-15.

---

## Focus

UI prototype baseline is in progress in parallel with eligible non-frontend Phase 02 work.

## Status

Baseline approved до 06 включительно:
- `planning/01_business_requirements.md` — **APPROVED v0.3**.
- `planning/02_user_journeys.md` — **APPROVED v0.2**.
- `planning/03_functional_requirements.md` — **APPROVED v0.2**.
- `planning/04_architecture.md` — **APPROVED v0.2**.
- `planning/05_tech_stack.md` — **APPROVED v0.4**.
- `planning/06_implementation_guide.md` — **APPROVED v0.2**.
- `planning/design-details/` — **APPROVED v0.2**.
- `planning/runtime_checklists.md` — **APPROVED v0.1**.
- `planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md` — **APPROVED v0.1**.
- `planning/implementation-slices/phase_02_slice_01_identity_foundation_planning.md` — **APPROVED v0.1**.
- `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — **DRAFT v0.1**.

Completed workstream — Phase 01 Slice 01:
- `product/` skeleton created;
- backend/frontend/runtime skeleton implemented only;
- Docker build/runtime verification completed;
- `RUN-01`..`RUN-07` passed;
- `RUN-08` recorded as `pass-indirect`;
- `UI-01` passed;
- factual state recorded in `planning/implementation_status.md` and `planning/runtime_evidence_log.md`;
- local compose stack is currently up.

## Next

**Continue standalone UI prototypes in parallel. Product work should stop only at frontend implementation points that need a missing accepted HTML prototype. Current received artifacts: `prototypes/ui/01_app_shell_cross_surface.html`, `prototypes/ui/02_merchant_auth.html`, `prototypes/ui/03_enduser_auth.html`, `prototypes/ui/04_backoffice_oidc_login.html`.**

05 v0.4 resolved stack:
- Backend: Kotlin + Java 21 LTS + Spring Boot 3.5.x.
- DB access: jOOQ + Spring JDBC/JdbcClient + raw SQL for ledger/audit/outbox (accepted by owner).
- Migrations: Flyway SQL migrations per service.
- Frontend: React 19 + Vite 8, three separate SPAs.
- Monorepo: Gradle multi-project for backend + pnpm workspace for frontend.
- Kafka client: Spring for Apache Kafka.
- OIDC: self-hosted Keycloak 26.x.
- Observability: OTel Java + Micrometer + Prometheus/Grafana/Tempo/Loki + Vector.
- Object storage: SeaweedFS S3 API as default; LocalStack optional for future AWS-emulation tests, not default runtime object storage.

05 has no remaining Open Questions.

06 v0.2 resolved decisions:
1. Payment lifecycle before compliance integrations.
2. Phase 01 creates full-shape skeleton for all five application services + core infra, with no domain behavior.
3. Phase 01 creates early SPA deployment shells only; real UI workflows remain Phase 10.
4. Open Questions: none.

Design-details v0.2 approved:
- `planning/design-details/access_matrix.md` — APPROVED v0.2.
- `planning/design-details/api_contracts.md` — APPROVED v0.2.
- `planning/design-details/state_machines.md` — APPROVED v0.2.
- `planning/design-details/schema_drafts.md` — APPROVED v0.2.
- `planning/design-details/ui_prototypes.md` — APPROVED v0.2.
- `planning/design-details/test_matrix.md` — APPROVED v0.2.
- `planning/design-details/cut_register.md` — APPROVED v0.2.
- `planning/design-details/adr/adr-001-jvm-spring-kotlin-backend-baseline.md`
- `planning/design-details/adr/adr-002-sql-first-persistence.md`
- `planning/design-details/adr/adr-003-selective-services-topology.md`
- `planning/design-details/adr/adr-004-ledger-central-source-of-truth.md`
- `planning/design-details/adr/adr-005-local-object-storage-seaweedfs.md`

Design-details v0.2 resolved decisions:
1. Refunds are `merchant_admin` only in MVP.
2. Backoffice operators may preview KYC documents only inside KYC review queue, with rate limits and synchronous read-audit.
3. Merchant dashboard refund uses separate dashboard API route calling the same application service as public API refund.
4. Public refund route remains `POST /v1/payment_intents/{id}/refund` for MVP.
5. Deposit initial state is `REQUESTED`.
6. Chargeback `MERCHANT_ACCEPTED` remains separate internal terminal state, exposed as `lost`.
7. Ledger writes use stored-procedure-only insert as primary balanced-entry enforcement; defensive deferred trigger if feasible.
8. External IDs use UUID/public IDs; BIGSERIAL/internal numeric IDs are allowed inside one service DB only.
9. Backoffice case review uses detail pages, not drawers.
10. Hosted payment form is a separate hosted payment surface, not a full fourth dashboard SPA.
11. Phase 01 observability requires logs + metrics pass; traces may be `pass-indirect` if Tempo needs follow-up.
12. Open Questions: none.

Runtime checklists v0.1 created:
- check aliases: `RUN`, `AUTH`, `AUD`, `LDG`, `WLT`, `MRC`, `PAY`, `VLT`, `SET`, `WBH`, `KYC`, `SNX`, `AML`, `CHB`, `UI`, `REC`.
- Phase 01 check IDs: `RUN-01`..`RUN-08`, `UI-01`.
- Cross-phase checks: `AUD-99`, `LDG-99`, `UI-99`.

First slice planning note approved:
- `planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md` — APPROVED v0.1.
- Scope: create `product/` skeleton, five Spring Boot service shells, three Vite SPA shells, Docker Compose core infra, baseline migrations, health endpoints, retained Phase 01 runtime scripts.
- Linked checks: `RUN-01`..`RUN-08`, `UI-01`.

## Read-first order (новая AI-сессия)

1. `CURRENT.md` (этот файл).
2. `README.md` — навигация.
3. `planning/01_business_requirements.md` — scope, цель, quality bar.
4. `planning/02_user_journeys.md` — журналы и cross-cutting concerns.
5. `planning/03_functional_requirements.md` — FR по всем модулям.
6. `planning/04_architecture.md` — deployment topology, bounded contexts, communication, security.
7. `planning/05_tech_stack.md` — approved stack baseline.
8. `planning/06_implementation_guide.md` — approved implementation guide.
9. `planning/design-details/` — approved implementation-near planning pack.
10. `planning/runtime_checklists.md` — approved runtime check registry.
11. `planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md` — current first slice planning note.

После прочтения — review Phase 02 Slice 02 draft. Не писать следующий identity/RBAC code до approval нового slice planning note.

## Do-not-do-yet rules

Запрещено:
- редактировать approved baseline docs без отдельной причины и owner approval;
- добавлять доменные endpoint'ы или hardcoded business success paths в Phase 01 Slice 01;
- реализовывать auth/RBAC/ledger/wallet/cards/payments/vendor workflows в этом slice.

Разрешено сейчас:
- уточнять и approve/revise Phase 02 Slice 02 planning note;
- обсуждать/уточнять merchant identity/backoffice OIDC/RBAC split;
- не писать следующий product code до approval нового slice planning note.

Current Phase 02 draft:
- `planning/implementation-slices/phase_02_slice_01_identity_foundation_planning.md` — APPROVED v0.1.
- Scope: end-user register/email verify/login, opaque session cookie, `GET /api/v1/enduser/me`, auth audit writes, global email uniqueness foundation, minimal `UEW-UI-01` prototype checkpoint.
- Explicitly out: merchant login, backoffice OIDC, full RBAC, wallet/ledger/payment/KYC behavior.

Completed Phase 02 Slice 01:
- Platform V2 identity/audit migration added.
- End-user registration, email verification, login, logout and `me` endpoint implemented.
- BCrypt password hashing and opaque `MFP_SESSION` cookie implemented.
- Audit append-only trigger implemented.
- `UEW-UI-01` minimal prototype checkpoint added to `spa-enduser`.
- Runtime scripts passed:
  - `AUTH-01` pass.
  - `AUTH-04` partial.
  - `AUTH-05` partial.
  - `AUD-01` pass.
  - `AUD-02` pass.

## Current Phase 01 Slice 01 Files

Created:
- `product/settings.gradle.kts`, `product/build.gradle.kts`, `product/gradle/wrapper/gradle-wrapper.properties`.
- Five Spring Boot app shells under `product/apps/{platform,acquirer,network,issuer,vault}`.
- Shared backend libs under `product/backend/libs/*`.
- Three React/Vite SPA shells under `product/apps/spa-*`.
- Local runtime config under `product/deploy/*`.
- Retained smoke scripts under `product/scripts/runtime/reg_phase01_*.sh`.
- `planning/implementation_status.md`.
- `planning/runtime_evidence_log.md`.

Verified:
- `docker compose -f deploy/docker-compose.yml config --quiet` passed.
- backend Docker images build.
- SPA Docker images build.
- `docker compose -f deploy/docker-compose.yml up -d` starts the full local stack.
- `RUN-01`..`RUN-07` pass.
- `RUN-08` passes as `pass-indirect`.
- `UI-01` passes.

## Open discussion thread

**Workflow pattern, который установился через 01..04:**
1. AI пишет v0.1 draft с Open Questions в конце.
2. User reviews, либо просит «развернуть»/«объяснить» какой-то Q before answering, либо отвечает по каждому Q.
3. AI применяет резолюции, bump'ит до v0.2/v0.3, заменяет Open Questions на Resolution Notes.
4. User говорит «меняй» — AI меняет status на `APPROVED`, обновляет README, переходит к следующему artifact.

**Question pattern preferences (важно):**
- Когда decisions архитектурные / неоднозначные — user prefers что AI задаёт 2-4 questions с **обязательным включением AI's recommendation как одной из options** (с markup `(Recommended)`).
- User catches contradictions и просит reframing через Q&A (см. 01 v0.1 → v0.2 transition: «production-grade» vs «никаких реальных провайдеров» резолвилось через explicit Q&A round).
- User confirms terse: «меняй», «го», «начинай», «согласен с рекомендацией».

**Critical project conventions established:**
- Three-way framing: **build (наш core) / integrate real sandbox (где industry buys) / internally simulate (где требует banking license)** — это организующий принцип всего проекта. Build/Buy decision matrix должен быть в 05 как первоклассный artifact.
- Quality bar (no fakes): любая capability — либо `cut`, либо `manual` (banking rails substitute), либо `simple production` (реально работает). Это применяется к каждому FR в 03.
- Russian для body текста / English для technical terms (state machine names, FR IDs, module names).
- Timeline expectation: 8-12 месяцев single-dev evenings/weekends. Не speed-to-launch.

## Snapshot of approved decisions (high-impact)

Чтобы новый AI не перечитывал 04 целиком ради этих фактов:

- **Deployment**: selective services — 5 application services (`platform` monolith + `vault` + `issuer` + `network` + `acquirer`) + Keycloak + Traefik + observability stack + 3 nginx static SPA containers. ~25 Docker containers total.
- **DB**: schema-per-context, 5 Postgres instances (one per service) + Postgres для Keycloak.
- **Sync comm**: in-process function calls внутри monolith; sync HTTP between services for fast path; signed service tokens (HMAC) для auth.
- **Async**: Kafka (KRaft mode) с JSON payloads + strict topic versioning.
- **Saga**: choreography + state machines, no central orchestrator.
- **Identity**: hybrid — built end-user/merchant identity в monolith; Keycloak self-hosted для backoffice OIDC SSO.
- **Public API**: в Acquirer service, host `api.miniefin.local` (Stripe-style).
- **Ledger**: в monolith, central source of truth, callers reach via `/internal/ledger/*`.
- **Compliance scope**: KYC (Sumsub) + Sanctions (OpenSanctions) + AML (3 hand-coded rules) + Case Management; SAR — later scope.
- **Card model**: three-party simulation (issuer + network + acquirer + vault), full chargeback lifecycle.
- **Three UIs**: end-user web + merchant dashboard + backoffice workflow, deployed as 3 separate SPAs.
- **Currency**: EUR test mode.
- **Quality controls**: SoF для deposits > €10k, two-eyes principle ≥ €10k, idempotency TTL 24h.
- **External integrations**: real Stripe Connect sandbox + Sumsub sandbox + OpenSanctions + Keycloak + S3-compatible local object storage. 05 APPROVED v0.4 selects SeaweedFS S3 API by default; LocalStack is optional for future AWS-emulation tests.

## Next Planned Step

Current stack control:

```bash
cd /home/nickf/Documents/sre_projects/mini-fintech-platform/product
docker compose -f deploy/docker-compose.yml ps
docker compose -f deploy/docker-compose.yml down
```

Current Phase 02 Slice 02 draft:
- `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — DRAFT v0.1.
- Proposed scope: merchant registration/login, first merchant employee as `merchant_admin`, merchant-scoped session, cross-pool email uniqueness, merchant auth audit writes, minimal `MDB-UI-01` product implementation checkpoint based on accepted standalone HTML prototype.
- Open questions: merchant entity depth, first employee role, merchant email verification gate, `AUTH-05` result tag.
- UI/UX prompt is temporary chat handoff and must not be committed.
- Standalone prototype artifacts received:
  - `prototypes/ui/01_app_shell_cross_surface.html`
  - `prototypes/ui/02_merchant_auth.html`
  - `prototypes/ui/03_enduser_auth.html`
  - `prototypes/ui/04_backoffice_oidc_login.html`
- Phase 02 Slice 02 backend/runtime sub-scope is implemented and verified:
  - Platform migration `V3__merchant_identity_foundation.sql`.
  - Merchant register/email verify/login/logout/me.
  - `merchant_admin` first employee and `kyb_status = NOT_STARTED`.
  - Cross-pool end-user vs merchant employee email uniqueness.
  - End-user/merchant wrong-role denial.
  - Merchant auth audit rows.
  - Runtime evidence recorded in `planning/runtime_evidence_log.md`.
- `spa-merchant` frontend implementation checkpoint for `MDB-UI-01` is not started.
- Backend/domain/runtime work may proceed even if unrelated UI prototypes are still being prepared.
- Frontend implementation for a screen/workflow requires its accepted standalone HTML prototype first.

Current Phase 02 Slice 03:
- `planning/implementation-slices/phase_02_slice_03_backoffice_oidc_rbac_planning.md` — backend/runtime sub-scope executed v0.2; frontend checkpoint pending.
- Implemented: backoffice OIDC token validation against local Keycloak, Keycloak role mapping to Platform RBAC roles, `GET /api/v1/backoffice/me`, all-pool wrong-role denial, and backoffice auth audit evidence.
- No backoffice SPA/frontend work in this slice.
- Runtime evidence recorded in `planning/runtime_evidence_log.md`.

Next planned step: draft the next non-frontend Phase 02 slice for the remaining identity/audit foundation work while frontend implementation remains gated by accepted standalone HTML prototypes.
