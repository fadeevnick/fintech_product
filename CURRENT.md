# CURRENT — Mini Fintech Platform handoff state

Last updated: 2026-05-16 (Phase 03 Slice 03 planning drafted).

---

## Focus

UI prototype baseline is in progress in parallel with eligible non-frontend Phase 03 work.

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
- `planning/implementation-slices/phase_02_slice_02_merchant_identity_planning.md` — backend/runtime sub-scope executed v0.2.
- `planning/implementation-slices/phase_02_slice_03_backoffice_oidc_rbac_planning.md` — backend/runtime sub-scope executed v0.2.
- `planning/implementation-slices/phase_02_slice_04_read_audit_account_controls_planning.md` — backend/runtime sub-scope executed v0.2.
- `planning/implementation-slices/phase_03_slice_01_ledger_foundation_planning.md` — backend/runtime sub-scope executed v0.2.
- `planning/implementation-slices/phase_03_slice_02_wallet_manual_deposit_planning.md` — backend/runtime sub-scope executed v0.2.

Completed workstream — latest:
- Phase 03 Slice 02 wallet account + manual deposit implemented in `platform`;
- `LDG-02`, `WLT-02`, `AUD-01`, `AUD-03` passed for the manual deposit path;
- hardening follow-up passed for structured amount validation and idempotent lazy wallet provisioning;
- `LDG-99` foundation regression passed after deposit cycle;
- regression subset passed: `RUN-01`, `AUTH-03`, `AUD-01`, `AUD-02`, `AUD-03` (partial/foundation), `AUD-99` (partial/foundation), actor-control precursor, `LDG-01`, `LDG-04`, `LDG-05`;
- factual state recorded in `planning/implementation_status.md` and `planning/runtime_evidence_log.md`;
- local compose stack is currently up.

Planning workstream — latest:
- `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md` — DRAFT v0.1.
- Scope: manual withdrawal under EUR 10k with ledger hold, backoffice completion/final debit, rejection release, actor-control write block and retained runtime scripts.
- Target checks: `LDG-03`, `WLT-02`, `AUD-01`, `AUD-03`, `LDG-05`, `LDG-99`.

## Next

**Continue standalone UI prototypes in parallel. Product work should stop only at frontend implementation points that need a missing accepted HTML prototype. Current received artifacts: `prototypes/ui/01_app_shell_cross_surface.html`, `prototypes/ui/02_merchant_auth.html`, `prototypes/ui/03_enduser_auth.html`, `prototypes/ui/04_backoffice_oidc_login.html`, `prototypes/ui/05_backoffice_work_queue_home.html`, `prototypes/ui/06_backoffice_manual_deposits.html`, `prototypes/ui/07_backoffice_manual_withdrawals.html`, `prototypes/ui/08_backoffice_kyc_queue.html`, `prototypes/ui/09_backoffice_aml_alerts.html`.**

Next planned product step:
- Implement the Phase 03 Slice 03 backend/runtime sub-scope from `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md`, then run and record the linked runtime checks.

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

Latest executed slice planning note:
- `planning/implementation-slices/phase_03_slice_02_wallet_manual_deposit_planning.md` — backend/runtime sub-scope executed v0.2.
- Scope: end-user wallet account + manual deposit < EUR 10k via balanced ledger postings; backoffice operator approve/reject with read-audit; actor-control write block (`FROZEN` ∪ `BLOCKED`) on both create and approve.
- Linked checks: `LDG-02`, `WLT-02`, `AUD-01`, `AUD-03`.

Latest drafted slice planning note:
- `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md` — DRAFT v0.1.
- Scope: manual withdrawal < EUR 10k with hold/final debit, rejection release, actor-control write block and runtime checks for `LDG-03`.

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
11. `planning/implementation_status.md` and `planning/runtime_evidence_log.md` — factual implementation/evidence state.
12. Latest executed slice note:
    `planning/implementation-slices/phase_03_slice_02_wallet_manual_deposit_planning.md`.
13. Latest drafted slice note:
    `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md`.

После прочтения — implement the Phase 03 Slice 03 backend/runtime sub-scope, then update runtime evidence and status files.

## Do-not-do-yet rules

Запрещено:
- редактировать approved baseline docs без отдельной причины и owner approval;
- добавлять wallet/payment/card/vendor workflows без отдельного approved slice;
- реализовывать frontend workflow без accepted standalone HTML prototype for that screen/workflow.

Разрешено сейчас:
- draft/review the next Phase 03 slice;
- continue backend/domain/runtime work that is not blocked by missing UI prototypes;
- continue standalone UI prototype intake in parallel.

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

Current Phase 03 Slice 02:
- `planning/implementation-slices/phase_03_slice_02_wallet_manual_deposit_planning.md` — backend/runtime sub-scope executed v0.2.
- Implemented: `wallet` schema, `wallet_accounts`, `deposit_requests`, idempotent `EXTERNAL_DEPOSIT_CLEARING` ledger account seed, end-user `POST /api/v1/deposits` and `GET /api/v1/wallet`, backoffice `GET/POST /api/v1/backoffice/manual-ops/deposits[/{id}/decision]`, balanced approve through `ledger.post_journal(...)`, actor-control hook on create and approve.
- Runtime evidence recorded in `planning/runtime_evidence_log.md`.
- `LDG-02`, `WLT-02`, `AUD-01`, `AUD-03` are passed; `LDG-99` foundation regression passed.
- `LDG-03`, `WLT-01`, `WLT-03`, `WLT-04` are not claimed because withdraw, transfer, SoF and two-eyes workflows do not exist yet.
- No frontend work in this slice.

Next planned step: implement the Phase 03 Slice 03 backend/runtime sub-scope from `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md`. Frontend implementation for backoffice manual deposits, manual withdrawals and end-user wallet remains gated by their respective accepted standalone HTML prototypes.
