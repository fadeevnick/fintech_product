# Implementation Status

Last updated: 2026-05-21.

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
- `prototypes/ui/04_backoffice_oidc_login.html` — standalone `BOF-UI-01` backoffice OIDC login HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/05_backoffice_work_queue_home.html` — standalone `BOF-UI-02` backoffice work queue home HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/06_backoffice_manual_deposits.html` — standalone `BOF-UI-03` backoffice manual deposits HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/07_backoffice_manual_withdrawals.html` — standalone `BOF-UI-04` backoffice manual withdrawals HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/08_backoffice_kyc_queue.html` — standalone `BOF-UI-05` backoffice KYC queue HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/09_backoffice_aml_alerts.html` — standalone `BOF-UI-06` backoffice AML alerts HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/10_backoffice_sanctions_hits.html` — standalone `BOF-UI-07` backoffice sanctions hits HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-16.
- `prototypes/ui/11_backoffice_chargeback_arbitration.html` — standalone `BOF-UI-08` backoffice chargeback arbitration HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-17.
- `prototypes/ui/12_backoffice_audit_log.html` — standalone `BOF-UI-09` backoffice audit log HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-17.
- `prototypes/ui/13_enduser_kyc_status.html` — standalone `UEW-UI-02` end-user KYC status HTML prototype from UI/UX prototype workflow; headless Chrome render check passed on 2026-05-17.
- `prototypes/ui/14_enduser_wallet_home.html` — standalone `UEW-UI-03` end-user wallet home HTML prototype from UI/UX prototype workflow; minor JSX style syntax, flex layout clipping and wide viewport root sizing fixed locally; headless Chrome render check passed on 2026-05-17.
- `prototypes/ui/15_enduser_deposit_request.html` — standalone `UEW-UI-04` end-user deposit request HTML prototype from UI/UX prototype workflow; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-17.
- `prototypes/ui/16_enduser_transfer.html` — standalone `UEW-UI-05` end-user transfer HTML prototype from UI/UX prototype workflow; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-17.
- `prototypes/ui/17_enduser_cards.html` — standalone `UEW-UI-06` end-user cards HTML prototype from UI/UX prototype workflow; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-17.
- `prototypes/ui/18_enduser_transaction_detail.html` — standalone `UEW-UI-07` end-user transaction detail HTML prototype from UI/UX prototype workflow; minor audit-token overflow fix applied locally; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-17.
- `prototypes/ui/19_merchant_onboarding_status.html` — standalone `MDB-UI-02` merchant onboarding status HTML prototype from UI/UX prototype workflow; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-17.
- `prototypes/ui/20_merchant_api_keys.html` — standalone `MDB-UI-03` merchant API keys HTML prototype from UI/UX prototype workflow; minor wide viewport root sizing fix applied locally; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-18.
- `prototypes/ui/21_merchant_webhooks.html` — standalone `MDB-UI-04` merchant webhooks HTML prototype from UI/UX prototype workflow; minor root sizing and endpoint table overflow fixes applied locally; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-18.
- `prototypes/ui/22_merchant_payments.html` — standalone `MDB-UI-05` merchant payments HTML prototype from UI/UX prototype workflow; minor root sizing and payment table overflow fixes applied locally; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-18.
- `prototypes/ui/23_merchant_settlements.html` — standalone `MDB-UI-06` merchant settlements HTML prototype from UI/UX prototype workflow; minor root sizing, initial drawer state and settlement table overflow fixes applied locally; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-18.
- `prototypes/ui/24_merchant_disputes.html` — standalone `MDB-UI-07` merchant disputes HTML prototype from UI/UX prototype workflow; minor root sizing and disputes table overflow fixes applied locally; headless Chrome render checks passed at 1440px and 2048px widths on 2026-05-19.

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
- Review/approve `planning/implementation-slices/phase_02_slice_03_backoffice_oidc_rbac_planning.md`, then implement its non-frontend backend/runtime scope if accepted.

---

## Phase 02 Slice 03 — Backoffice OIDC and RBAC Foundation

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend checkpoint not started**.

Planning contract:
- `planning/implementation-slices/phase_02_slice_03_backoffice_oidc_rbac_planning.md` — backend/runtime sub-scope executed v0.2; frontend checkpoint pending.

Implemented backend/runtime scope:
- local Keycloak OIDC token validation in Platform for `/api/v1/backoffice/**`;
- Keycloak realm/client/user/role upsert helper for reproducible local runtime checks;
- Keycloak role mapping into Platform roles:
  - `backoffice_operator`
  - `compliance_officer`
  - `senior_compliance`
- minimal `GET /api/v1/backoffice/me`;
- denial for missing bearer token, valid token without approved role, and end-user/merchant cookie sessions on backoffice endpoint;
- backoffice auth success/failure audit writes;
- retained runtime scripts:
  - `product/scripts/runtime/lib_phase02_backoffice_keycloak.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_auth_audit.sh`

Explicitly not started:
- backoffice SPA login UI;
- KYC/AML/sanctions/manual ops queues;
- sensitive read-audit workflows.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 02 Slice 03 Backoffice OIDC/RBAC Runtime Verification`.
- `AUTH-03` — pass for Keycloak OIDC token acquisition and Platform role mapping.
- `AUTH-05` — pass for unauthenticated and wrong-role denial across end-user, merchant and backoffice protected paths.
- `AUD-01` — pass for end-user, merchant and backoffice auth audit events.
- `AUD-02` — pass for audit append-only protection.
- `RUN-04` — pass for Keycloak connectivity.

Next planned step:
- Review/approve `planning/implementation-slices/phase_02_slice_04_read_audit_account_controls_planning.md`, then implement its non-frontend backend/runtime scope if accepted.

---

## Phase 02 Slice 04 — Read-Audit Primitive and Account Control Hook

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not started**.

Planning contract:
- `planning/implementation-slices/phase_02_slice_04_read_audit_account_controls_planning.md` — backend/runtime sub-scope executed v0.2; frontend not in scope.

Implemented backend/runtime scope:
- append-only `audit.read_audit_log`;
- `identity.actor_controls` for `END_USER` and `MERCHANT`;
- synchronous read-audit probe endpoint:
  - `GET /api/v1/backoffice/read-audit/probe/{resourceId}`;
- backoffice actor-control mutation endpoint:
  - `POST /api/v1/backoffice/actor-controls`;
- write-guard probe endpoints:
  - `POST /api/v1/enduser/write-guard/probe`;
  - `POST /api/v1/merchant/write-guard/probe`;
- audit rows for read-audit, control changes and denied probes;
- retained runtime scripts:
  - `product/scripts/runtime/reg_phase02_read_audit_probe.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_merchant.sh`

Explicitly not started:
- wallet writes or `WLT-02` full proof;
- KYC/AML/sanctions/manual ops queues;
- account freeze/unfreeze UI;
- audit viewer UI;
- sensitive read-audit workflows beyond the primitive/probe.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 02 Slice 04 Read-Audit and Actor Controls Runtime Verification`.
- `AUD-03` — partial/foundation for read-audit primitive.
- `AUD-99` — partial/foundation for read-audit append-only behavior.
- Actor-control precursor — pass for end-user and merchant write-guard probes.
- `AUTH-01`, `AUTH-02`, `AUTH-03`, `AUTH-05`, `AUD-01`, `AUD-02`, `RUN-01` regressions passed.
- `WLT-02` is not claimed; real wallet writes do not exist yet.

Next planned step:
- Review/approve `planning/implementation-slices/phase_03_slice_01_ledger_foundation_planning.md`, then implement its non-frontend backend/runtime scope if accepted.

---

## Phase 03 Slice 01 — Ledger Foundation

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not in scope**.

Planning contract:
- `planning/implementation-slices/phase_03_slice_01_ledger_foundation_planning.md` — backend/runtime sub-scope executed v0.2; frontend not in scope.

Implemented backend/runtime scope:
- Platform DB migration `V5__ledger_foundation.sql` for ledger accounts, journal entries, postings, append-only triggers, `ledger.post_journal(...)` and derived balance view;
- narrow internal runtime proof endpoints for account creation, balanced journal posting, account balance lookup and reconciliation;
- Kotlin ledger repository/service/controller in `platform`;
- retained runtime scripts:
  - `product/scripts/runtime/reg_phase03_ledger_unbalanced_rejection.sh`
  - `product/scripts/runtime/reg_phase03_ledger_balanced_posting.sh`
  - `product/scripts/runtime/reg_phase03_ledger_append_only.sh`
  - `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`

Explicitly not started:
- wallet account creation;
- manual deposit/withdraw workflows;
- internal transfers;
- card/payment authorization holds;
- merchant settlement;
- frontend implementation.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 03 Slice 01 Ledger Foundation Runtime Verification`.
- `LDG-01` — pass for unbalanced journal rejection and no persistence.
- Ledger foundation balanced posting — pass for atomic two-posting journal and derived balances; `LDG-02` is not claimed.
- `LDG-04` — pass for journal/posting append-only protection.
- `LDG-05` — pass for reconciliation over persisted ledger state.
- `RUN-01`, `AUTH-03`, `AUD-01`, `AUD-02`, `AUD-03`, `AUD-99` and actor-control regressions passed.

Next planned step:
- Completed by later Phase 03 slices; current next step is tracked in the latest section below.

---

## Phase 03 Slice 02 — Wallet Account and Manual Deposit

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not in scope**.

Planning contract:
- `planning/implementation-slices/phase_03_slice_02_wallet_manual_deposit_planning.md` — backend/runtime sub-scope executed v0.2; frontend not in scope.

Implemented backend/runtime scope:
- Platform DB migration `V6__wallet_manual_deposit.sql` for `wallet` schema: `wallet.wallet_accounts`, `wallet.deposit_requests` with state constraint, `amount > 0 and amount < 10000.0000` constraint, `updated_at` trigger; idempotent seed of `ledger.accounts(code='EXTERNAL_DEPOSIT_CLEARING', account_type='EXTERNAL_CLEARING', normal_side='DEBIT')`.
- End-user surface in `com.minifin.platform.wallet`:
  - `POST /api/v1/deposits` — create deposit request (amount < EUR 10k); persists `REQUESTED` and immediately transitions to `PENDING_OPERATOR_REVIEW` in the same transaction; lazily provisions wallet ledger account `WALLET_USER:<userId>` and `wallet.wallet_accounts` row when missing; calls `ActorControlService.requireWriteAllowed("END_USER", user.id)`.
  - `GET /api/v1/wallet` — lazy-provisions wallet, returns derived balance from `ledger.account_balances` and the user's deposit history.
- Backoffice manual-ops surface (existing OIDC + role `backoffice_operator` or higher):
  - `GET /api/v1/backoffice/manual-ops/deposits` — pending queue with synchronous read-audit per listed deposit.
  - `POST /api/v1/backoffice/manual-ops/deposits/{id}/decision` — `APPROVE` posts a balanced journal through `ledger.post_journal(...)` (DEBIT `EXTERNAL_DEPOSIT_CLEARING`, CREDIT `WALLET_USER:<userId>`) and transitions to `COMPLETED`; `REJECT` transitions to `REJECTED` with no ledger movement; both produce write-audit; second decision returns 409.
- Actor-control hook on both deposit create and approve, refusing for `actor_controls.state ∈ {FROZEN, BLOCKED}` with HTTP 403 `actor_control_blocked` and an `identity.actor_control_write_denied` audit row.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase03_wallet_deposit.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_happy_path.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_reject.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_actor_control_block.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_double_decision.sh`
  - `product/scripts/runtime/reg_phase03_wallet_deposit_amount_validation.sh`
  - `product/scripts/runtime/reg_phase03_wallet_provisioning_idempotency.sh`
- Hardening follow-up:
  - amount validation returns structured HTTP 400 errors for unsupported scale, non-numeric, zero and negative values instead of leaking internal exceptions;
  - lazy wallet provisioning is idempotent for repeated/parallel first-time `GET /api/v1/wallet` calls.

Explicitly not started:
- manual withdraw workflow (`LDG-03`);
- internal end-user → end-user transfer (`WLT-01`);
- Source of Funds and amount ≥ EUR 10k (`WLT-03`);
- two-eyes / second-actor approval (`WLT-04`);
- AML rule trip / AML auto-freeze;
- card / payment authorization holds; merchant settlement;
- backoffice manual deposits SPA frontend (HTML prototype `06_backoffice_manual_deposits.html` accepted, frontend implementation deferred);
- end-user wallet/deposit SPA frontend (no accepted HTML prototype yet).

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 03 Slice 02 Wallet Manual Deposit Runtime Verification`.
- `LDG-02` — pass for balanced two-posting journal and derived balance increase on approve.
- `WLT-02` — pass for all four sub-branches (create+`FROZEN`, create+`BLOCKED`, approve+`FROZEN`, approve+`BLOCKED`).
- `AUD-01` — pass for deposit create / approve / reject and actor-control denial audit rows.
- `AUD-03` — pass for backoffice manual deposits queue read producing synchronous read-audit row.
- `LDG-99` foundation — pass via `reg_phase03_ledger_reconciliation.sh` re-run after deposit cycle.
- Phase 01/02/03 regression subset passed: `RUN-01`, `AUTH-03`, `AUD-01`, `AUD-02`, `AUD-03` (partial/foundation), `AUD-99` (partial/foundation), actor-control precursor, `LDG-01`, `LDG-04`, `LDG-05`.
- Hardening verification passed: `reg_phase03_wallet_deposit_amount_validation.sh`, `reg_phase03_wallet_provisioning_idempotency.sh`, plus wallet happy-path, double-decision and ledger reconciliation regressions.

Result tag notes:
- `LDG-03`, `WLT-01`, `WLT-03`, `WLT-04` are not claimed; withdraw, transfer, SoF and two-eyes workflows do not exist yet.

Next planned step:
- Implement the drafted Phase 03 Slice 03 manual withdraw hold/final-debit backend/runtime sub-scope.

---

## Phase 03 Slice 03 — Wallet Manual Withdraw Hold and Final Debit

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not in scope**.

Planning contract:
- `planning/implementation-slices/phase_03_slice_03_wallet_manual_withdraw_planning.md` — backend/runtime sub-scope executed v0.2; frontend not in scope.

Implemented backend/runtime scope:
- Platform DB migration `V7__wallet_manual_withdraw.sql` for `wallet.withdraw_requests` and idempotent seed of `ledger.accounts(code='EXTERNAL_WITHDRAWAL_CLEARING', account_type='EXTERNAL_CLEARING', normal_side='CREDIT')`.
- End-user `POST /api/v1/withdrawals` for manual withdrawals under EUR 10k. It validates EUR amount, checks actor-control, verifies sufficient derived wallet balance, creates a withdrawal request, posts a hold journal and returns state `HELD`.
- `GET /api/v1/wallet` now includes own withdrawal history alongside balance and deposits.
- Per-user hold ledger account `WALLET_WITHDRAW_HOLD:<userId>` is lazily provisioned as a credit-normal hold account.
- Backoffice manual-ops surface:
  - `GET /api/v1/backoffice/manual-ops/withdrawals` — held queue with synchronous read-audit per listed withdrawal;
  - `POST /api/v1/backoffice/manual-ops/withdrawals/{id}/decision` — `COMPLETE` posts final debit from hold to `EXTERNAL_WITHDRAWAL_CLEARING`; `REJECT` releases hold back to wallet.
- Actor-control hook blocks withdrawal create and completion for `FROZEN` / `BLOCKED`; rejection remains allowed to release held funds.
- Retained runtime scripts:
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_hold_complete.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_reject_releases_hold.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_insufficient_funds.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_actor_control_block.sh`
  - `product/scripts/runtime/reg_phase03_wallet_withdraw_double_decision.sh`

Explicitly not started:
- Source of Funds (`WLT-03`);
- two-eyes (`WLT-04`);
- real payout rails;
- frontend implementation.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 03 Slice 03 Wallet Manual Withdraw Runtime Verification`.
- `LDG-03` — pass for hold/final-debit flow with balanced postings.
- `WLT-02` — pass for withdrawal create + completion actor-control blocks across `FROZEN` and `BLOCKED`.
- `AUD-01` — pass for withdrawal hold, complete, reject and actor-control denial audit rows.
- `AUD-03` — pass for backoffice manual withdrawals queue read producing synchronous read-audit row.
- `LDG-05` / `LDG-99` — pass via ledger reconciliation after withdrawal flows.
- Regression subset passed: wallet deposit happy path, wallet deposit double-decision and ledger reconciliation.

Result tag notes:
- `LDG-03` is passed.
- At this point in history, `WLT-01`, `WLT-03`, `WLT-04` were not claimed; transfer was implemented in the following slice.

Next planned step:
- Completed by Phase 03 Slice 04 below.

---

## Phase 03 Slice 04 — Wallet Internal Transfer

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not in scope**.

Planning contract:
- `planning/implementation-slices/phase_03_slice_04_wallet_internal_transfer_planning.md` — backend/runtime sub-scope executed v0.2; frontend not in scope.

Implemented backend/runtime scope:
- Platform DB migration `V8__wallet_internal_transfer.sql` for `wallet.internal_transfers`.
- End-user `POST /api/v1/transfers` for internal transfers under EUR 10k.
- Sender wallet debit and receiver wallet credit through one balanced `ledger.post_journal(...)` journal with `journal_type = 'WALLET_INTERNAL_TRANSFER'`.
- Sufficient-funds guard before posting.
- Self-transfer, missing receiver and inactive receiver refusal.
- Actor-control write block for sender and receiver across `FROZEN` and `BLOCKED`.
- Optional `Idempotency-Key` duplicate protection: same sender/key/body returns the original completed transfer; same sender/key with a different normalized request returns 409.
- `GET /api/v1/wallet` includes own sent/received transfer history.
- Retained runtime scripts:
  - `product/scripts/runtime/reg_phase03_wallet_transfer_happy_path.sh`
  - `product/scripts/runtime/reg_phase03_wallet_transfer_insufficient_funds.sh`
  - `product/scripts/runtime/reg_phase03_wallet_transfer_actor_control_block.sh`
  - `product/scripts/runtime/reg_phase03_wallet_transfer_idempotency.sh`

Explicitly not started:
- Source of Funds (`WLT-03`);
- two-eyes (`WLT-04`);
- AML velocity/structuring rules;
- transfer cancellation/reversal;
- frontend implementation.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 03 Slice 04 Wallet Internal Transfer Runtime Verification`.
- `WLT-01` — pass for atomic internal transfer debit/credit.
- `WLT-02` — pass for sender and receiver actor-control blocks.
- `AUD-01` — pass for transfer success and actor-control denial audit rows.
- `LDG-05` / `LDG-99` — pass via ledger reconciliation after transfer flows.

Result tag notes:
- `WLT-01` is passed.
- `WLT-03`, `WLT-04` are not claimed; SoF and two-eyes workflows do not exist yet.

Next planned step:
- Review the parallel high-value controls planning branch, then decide whether to continue Phase 03 placeholders or move to the next approved implementation slice.

---

## Phase 04 Slice 02 — Stripe Connect Webhooks

Status: **WEBHOOK BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — onboarding-start blocked on Stripe sandbox credentials**.

Planning contract:
- `planning/implementation-slices/phase_04_slice_02_stripe_connect_webhooks_planning.md` — webhook-only backend/runtime sub-scope executed v0.1; `MRC-01` blocker recorded.

Implemented backend/runtime scope:
- Platform DB migration `V10__merchant_stripe_webhooks.sql` for:
  - `merchant.stripe_account_links`;
  - `merchant.stripe_webhook_events` with unique Stripe event id.
- Local env/config wiring for `STRIPE_WEBHOOK_SIGNING_SECRET` and `STRIPE_WEBHOOK_TOLERANCE_SECONDS`.
- Public vendor endpoint `POST /webhooks/stripe/v1`.
- Stripe-format HMAC-SHA256 signature verification over exact raw body bytes using `Stripe-Signature: t=<unix_ts>,v1=<signature>`.
- Timestamp tolerance enforcement.
- Idempotent duplicate handling by Stripe event id: duplicate delivery returns outcome `DUPLICATE`, does not rerun state changes.
- `account.updated` handling for known `merchant.stripe_account_links.stripe_account_id`:
  - `charges_enabled=true && payouts_enabled=true` ⇒ merchant `kyb_status = VERIFIED`;
  - `details_submitted=true` ⇒ merchant `kyb_status = PENDING` unless already verified path applies.
- Audit rows for valid `account.updated`, invalid signature, old timestamp and duplicate event delivery.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase04_stripe_webhook.sh`
  - `product/scripts/runtime/reg_phase04_stripe_webhook_signature_valid.sh`
  - `product/scripts/runtime/reg_phase04_stripe_webhook_signature_invalid.sh`
  - `product/scripts/runtime/reg_phase04_stripe_webhook_timestamp_tolerance.sh`
  - `product/scripts/runtime/reg_phase04_stripe_webhook_idempotency.sh`
  - `product/scripts/runtime/reg_phase04_stripe_webhook_bad_then_valid_retry.sh`

Explicitly not started / blocked:
- `MRC-01` Stripe Connect onboarding start is blocked because real Stripe sandbox credentials are unavailable; no fake `Account.create` or `AccountLink.create` path exists.
- Merchant API key lifecycle (`MRC-03`) and public API response/idempotency checks (`PAY-01`, `PAY-02`, `PAY-03`) were not in this slice; they are covered by Phase 04 Slice 01.
- Payment intent authorization/capture/settlement.
- Frontend implementation.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 04 Slice 02 Stripe Webhook Runtime Verification`.
- `MRC-02` — pass for valid signature, invalid signature rejection without idempotency poisoning, timestamp tolerance rejection and duplicate event id idempotency.
- `AUD-01` — pass extension for webhook-driven KYB update and webhook failure/duplicate audit rows.
- `LDG-05` / `LDG-99` — ledger reconciliation regression still passes.

Result tag notes:
- `MRC-01` remains blocked and unclaimed until Stripe Connect sandbox credentials are available.
- This slice seeds `merchant.stripe_account_links` only inside runtime scripts to represent the account-link row that a future real onboarding-start implementation would create. No Stripe API behavior is faked.

Next planned step:
- Provide Stripe Connect sandbox credentials to implement `MRC-01`, or continue the separate merchant API key / public API idempotency workstream.

Follow-up hardening after review:
- Rejected webhook deliveries no longer insert into `merchant.stripe_webhook_events`, so invalid signature/timestamp attempts cannot poison later valid retries for the same Stripe event id.
- Added retained regression script `product/scripts/runtime/reg_phase04_stripe_webhook_bad_then_valid_retry.sh`.

---

## Phase 04 Slice 01 — Merchant API Keys and Public API Idempotency

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not in scope**.

Planning contract:
- `planning/implementation-slices/phase_04_slice_01_api_keys_idempotency_planning.md` — backend/runtime sub-scope executed v0.3; frontend not in scope.

Implemented backend/runtime scope:
- Platform DB migration `V11__merchant_api_keys_and_idempotency.sql` for `merchant.api_keys`, `merchant.payment_intents` shell (state `REQUIRES_PAYMENT_METHOD` only), `idempotency` schema with `idempotency.idempotency_keys`, primary key scoped by `(merchant_id, route, idempotency_key)`, reservation placeholders, finalize-only update guard and no-delete trigger.
- Merchant dashboard API key endpoints in `com.minifin.platform.merchant.apikeys`:
  - `POST /api/v1/merchant/api-keys` — `merchant_admin` only; generates an `mfp_live_*` key, returns it once with `apiKeyId`, `keyPrefix` (`mfp_live_` + 3 chars), 16-hex `fingerprint`, `status=ACTIVE`; stores only `key_hash = sha256(rawKey)` and `fingerprint`; optional `Idempotency-Key` returns the same metadata on replay but never replays the one-time raw secret.
  - `GET /api/v1/merchant/api-keys` — returns prefix/fingerprint/status only; raw key is never returned again.
  - `POST /api/v1/merchant/api-keys/{id}/revoke` — `merchant_admin` only; transitions key to `REVOKED`; idempotent on already-revoked key.
- Public API surface in `com.minifin.platform.publicapi`:
  - `PublicApiAuthFilter` runs only for `/v1/**`, parses `Authorization: Bearer mfp_live_*`, resolves an `ACTIVE` key by `sha256(rawKey)` lookup, rejects missing/malformed/unknown/revoked keys with HTTP 401 and a `{data, errors}` body, writes an audit row on failure and attaches a `PublicApiPrincipal(merchantId, apiKeyId)` to the request on success.
  - `SecurityConfig` was extended with an ordered public-API filter chain that matches `/v1/**` and bypasses the OIDC chain.
  - Idempotency repository/service backed by `idempotency.idempotency_keys`. Request fingerprint is `sha256(method + "|" + route + "|" + rawBody)`. Replay with same `(merchantId, route, key, method, fingerprint)` returns the cached HTTP status and body verbatim; same route/key with different fingerprint returns HTTP 409 `idempotency_conflict`; the same key string is independent across merchants and routes. The business write and cache finalization run in one transaction behind a placeholder row to serialize concurrent callers.
  - `POST /v1/payment_intents` — minimal shell; requires non-empty `Idempotency-Key`; persists a `merchant.payment_intents` row in state `REQUIRES_PAYMENT_METHOD`; returns `{"data":{"object":"payment_intent",...},"errors":[]}`.
  - `GET /v1/payment_intents/{id}` — returns own payment intent; cross-merchant lookup returns HTTP 404 `payment_intent_not_found`.
- Audit rows for `merchant.api_key_created`, `merchant.api_key_revoked`, and `publicapi.auth_failed`.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase04_public_api.sh`
  - `product/scripts/runtime/reg_phase04_api_key_lifecycle.sh`
  - `product/scripts/runtime/reg_phase04_public_api_response_shape.sh`
  - `product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh`
  - `product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh`
  - `product/scripts/runtime/reg_phase04_public_api_concurrent_idempotency.sh`
  - `product/scripts/runtime/reg_phase04_public_api_idempotency_per_route.sh`
  - `product/scripts/runtime/reg_phase04_idempotency_append_only.sh`
  - `product/scripts/runtime/reg_phase04_dashboard_api_key_idempotency.sh`

Deliberate scope-narrowing decisions:
- Public API endpoints (`/v1/**`) are mounted in `platform` rather than in `acquirer` for this slice. Reasons: API key material lives in the same DB as the merchant identity used to manage it, and no service-to-service auth library exists yet to let `acquirer` validate platform-owned API keys. Moving public routes into `acquirer` is a deliberately deferred later Phase 04 slice gated on a real service-auth primitive.
- API key rotation is not a separate route; "generate new key + revoke old key" satisfies rotation via the existing lifecycle endpoints in this slice.
- Idempotency TTL cleanup is not implemented in this slice; rows are stored with `created_at` and can be aged out by a later operational slice.

Explicitly not started:
- Stripe Connect onboarding (`MRC-01`);
- Stripe webhook receiver (`MRC-02`);
- payment intent state machine beyond `REQUIRES_PAYMENT_METHOD`;
- card authorization / capture / refund / settlement / outbound webhook delivery;
- merchant dashboard webhook endpoint, payments list, refunds, settlements, payouts;
- frontend implementation under `spa-merchant` or `spa-enduser`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-16 — Phase 04 Slice 01 Merchant API Keys and Public API Idempotency Runtime Verification`.
- `MRC-03` — pass for one-time visibility, hashed/fingerprinted at rest and revoked-key rejection.
- `PAY-01` — pass for `{data, errors}` shape on success and on errors.
- `PAY-02` — pass for same key/body cached replay, atomic concurrent replay and per-route scope.
- `PAY-03` — pass for same route/key with different body returning HTTP 409; same key reused on another route is independent.
- `AUD-01` — pass for API key create/revoke audit rows.
- `LDG-05` — pass regression after the slice.

Result tag notes:
- `MRC-01`, `MRC-02` are not claimed; Stripe Connect onboarding and webhook receiver are owned by a separate branch.

Next planned step:
- Provide real Stripe Connect sandbox credentials to implement `MRC-01`, or choose the next approved backend/runtime slice toward Phase 05 card path; the API key, public idempotency and inbound webhook primitives are now available as foundations.

---

## Phase 04 Slice 03 — Merchant Dashboard Payments Read Shell and Webhook Configuration

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — frontend not in scope**.

Planning contract:
- `planning/implementation-slices/phase_04_slice_03_merchant_dashboard_payments_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V12__merchant_dashboard_read_shell.sql` for `merchant.webhook_endpoints` and dashboard payment-intent read index.
- Merchant dashboard payment-intent read endpoints:
  - `GET /api/v1/merchant/payment-intents`;
  - `GET /api/v1/merchant/payment-intents/{id}`.
- Payment reads use the authenticated merchant session scope; cross-merchant detail lookup returns 404 and list excludes other merchants' rows.
- Merchant dashboard webhook endpoint configuration endpoints:
  - `GET /api/v1/merchant/webhook-endpoints`;
  - `POST /api/v1/merchant/webhook-endpoints`;
  - `PUT /api/v1/merchant/webhook-endpoints/{id}`;
  - `DELETE /api/v1/merchant/webhook-endpoints/{id}`.
- Webhook endpoint writes require `merchant_admin`; `merchant_member` can list/read but cannot create/update/delete.
- Audit rows for webhook endpoint create/update/delete.
- Retained runtime scripts:
  - `product/scripts/runtime/reg_phase04_merchant_payment_reads.sh`;
  - `product/scripts/runtime/reg_phase04_merchant_webhook_config.sh`.
- Phase 04 public API runtime helper supports compose-network curl execution via `PLATFORM_CURL_CONTAINER_NETWORK`, used for branch-isolated runtime verification where host-published platform port accepted TCP but did not return responses.

Explicitly not implemented:
- No Stripe Connect onboarding (`MRC-01`).
- No card authorization, capture, refund, settlement or outbound webhook delivery.
- No `WBH-01..WBH-03` claims.
- No frontend implementation.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-17 — Phase 04 Slice 03 Merchant Dashboard Payments/Webhook Config Runtime Verification`.
- `MRC-04` — pass for own payment list/detail and cross-merchant empty-list/404 behavior.
- `MRC-05` — pass for webhook endpoint create/update/list/delete, merchant-admin write enforcement, merchant-member read-only behavior and cross-merchant scoping.
- `MRC-03` — pass regression for API key lifecycle.
- `PAY-01`, `PAY-02`, `PAY-03` — pass targeted public API response/idempotency regressions.

Next planned step:
- Provide real Stripe Connect sandbox credentials to implement `MRC-01`, or move to the next approved backend/runtime slice.

---

## Phase 05 Slice 01 — Vault Tokenization and Card Issuance Foundation

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_05_slice_01_vault_card_issuance_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Vault DB migration `V2__vault_card_tokenization.sql` for `vault.key_versions`, `vault.card_tokens` and `vault.detokenize_audit_log`.
- Vault tokenization API:
  - `POST /internal/vault/tokenize` accepts only `service:issuer` and returns token/last4/expiration/BIN.
  - Full PAN is generated inside Vault and persisted encrypted via `pgcrypto`.
- Vault restricted detokenize API:
  - `POST /internal/vault/detokenize` returns PAN only to `service:issuer`.
  - Non-issuer service callers are denied and written to `vault.detokenize_audit_log`.
- Issuer DB migration `V2__issuer_cards.sql` for `issuer.cards`.
- Issuer card issuance API:
  - `POST /internal/issuer/cards` accepts only `service:platform`.
  - Issuer calls Vault tokenize and stores only token/last4/expiration/BIN/user/wallet metadata.
- Platform end-user API:
  - `POST /api/v1/cards` requires end-user session, applies actor-control write guard, provisions/resolves wallet, delegates to Issuer and returns safe card metadata only.
- Compose/env wiring for narrow service-auth secret, Platform→Issuer and Issuer→Vault URLs, Vault encryption key and test BIN.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase05_vault_card.sh`
  - `product/scripts/runtime/reg_phase05_card_issue_pan_isolation.sh`
  - `product/scripts/runtime/reg_phase05_vault_detokenize_restriction.sh`
  - `product/scripts/runtime/reg_phase05_pan_log_masking.sh`

Explicitly not implemented:
- Authorization, capture, settlement, refunds, chargebacks or outbound webhook delivery.
- `PAY-04` / `PAY-05`; they remain for the later authorization slice.
- Card block/unblock/lost/replacement flows.
- Full PAN reveal to end users.
- Frontend SPA implementation.

Runtime evidence:
- Isolated compile checks passed for affected services:
  - `:apps:vault:compileKotlin`
  - `:apps:issuer:compileKotlin`
  - `:apps:platform:compileKotlin`
- Isolated Compose project `mini-fintech-platform-a1` started `platform`, `issuer`, `vault` and required dependencies with fresh DB migrations.
- `VLT-01` — pass for PAN isolation: Platform returns safe card metadata, Issuer stores token/last4/state only, Vault stores encrypted PAN, and Platform/Issuer schemas have no PAN/CVV columns.
- `VLT-02` — pass for restricted detokenize: Issuer detokenize succeeds, Platform detokenize is denied with `service_auth_denied`, and both outcomes are audit logged.
- `VLT-03` — pass for PAN log masking: raw detokenized PAN was absent from Platform/Issuer/Vault general logs.

Next planned step:
- Choose the next approved implementation slice: Phase 05 Slice 02 card authorization, or Phase 06 Slice 01 outbound merchant webhook delivery.

---

## Phase 05 Slice 02 — Card Authorization Path

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_05_slice_02_card_authorization_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Public merchant authorization route in Platform:
  - `POST /v1/payment_intents/{id}/authorize` with existing API-key auth and idempotency primitive.
  - Payment intent responses include safe authorization status, auth code, expiry or structured decline details.
- Platform → Acquirer bridge for the current public API hosting topology.
- Acquirer authorization persistence and internal `POST /internal/acquirer/authorize` service endpoint.
- Network authorization route persistence, MVP BIN route registry for `400000`, network audit row and internal `POST /internal/network/authorize` service endpoint.
- Issuer `POST /internal/issuer/authorize` decision path that checks card state/expiry, Platform actor controls, Platform wallet available balance and posts approved ledger holds.
- Platform internal service endpoints used by Issuer for actor-control lookup, wallet available balance and ledger hold posting.
- Flyway migrations:
  - `product/apps/acquirer/src/main/resources/db/migration/V2__payment_authorization_foundation.sql`.
  - `product/apps/network/src/main/resources/db/migration/V2__authorization_routing_foundation.sql`.
  - `product/apps/issuer/src/main/resources/db/migration/V3__card_authorization_and_holds.sql`.
  - `product/apps/platform/src/main/resources/db/migration/V13__card_authorization_hold_support.sql`.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase05_card_authorization.sh`.
  - `product/scripts/runtime/reg_phase05_authorization_approved_hold.sh`.
  - `product/scripts/runtime/reg_phase05_authorization_structured_declines.sh`.

Explicitly not implemented:
- Capture, clearing, settlement, refunds, payouts, chargebacks or outbound merchant webhook delivery.
- Card lifecycle operations beyond authorization-time inactive-card declines.
- Frontend SPA implementation.
- `SET-*`, `WBH-*`, `CHB-*` or frontend checks.

Runtime evidence:
- Isolated compile checks passed for affected services:
  - `:apps:issuer:compileKotlin`.
  - `:apps:vault:compileKotlin`.
  - `:apps:platform:compileKotlin`, `:apps:network:compileKotlin`, `:apps:acquirer:compileKotlin`.
- Isolated Compose project `mini-fintech-platform-a1` built and started `platform`, `acquirer`, `network`, `issuer`, `vault` and required dependencies with fresh DB migrations.
- `PAY-04` — pass for approved authorization hold: public authorization returned `AUTHORIZED`/`AUTH_APPROVED`, Issuer hold count increased by one, Platform ledger recorded a `CARD_AUTHORIZATION_HOLD` journal and LDG reconciliation passed.
- `PAY-05` — pass for structured declines: insufficient funds, blocked actor, inactive card and unknown card token returned `FAILED`/`AUTH_DECLINED` with expected decline codes and no hold creation.

Result tag notes:
- Local host-to-container published HTTP ports accepted TCP connections but did not return response bytes in this environment; the verification therefore ran the retained scripts from a temporary container on `mini-fintech-platform-a1_default` with temporary DB helper rewiring to Postgres service DNS. The product scripts themselves remain retained in `product/scripts/runtime/`.

Next planned step:
- Choose the next approved implementation slice, likely Phase 06 Slice 01 outbound merchant webhook delivery, or provide real Stripe Connect sandbox credentials for blocked `MRC-01`.

---

## Phase 06 Slice 01 — Outbound Merchant Webhook Delivery Foundation

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_06_slice_01_outbound_webhook_delivery_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V14__merchant_outbound_webhook_delivery.sql`.
- Extended `merchant.webhook_endpoints` with encrypted signing-secret material, `signing_secret_hash`, `secret_prefix` and `secret_rotated_at`.
- Merchant webhook endpoint creation now generates a `mfp_whsec_*` signing secret, returns it once, stores encrypted delivery secret material, and keeps SHA-256 hash plus prefix metadata.
- Added merchant dashboard secret rotation endpoint:
  - `POST /api/v1/merchant/webhook-endpoints/{id}/rotate-secret`.
- Added persisted outbound webhook events and delivery attempts:
  - `merchant.webhook_events`;
  - `merchant.webhook_delivery_attempts`.
- Existing public `POST /v1/payment_intents` now emits one durable `payment_intent.created` webhook event for a newly persisted payment-intent shell.
- Delivery sends a real HTTP POST to active subscribed merchant endpoints with MiniFin HMAC headers:
  - `MiniFin-Webhook-Id`;
  - `MiniFin-Webhook-Timestamp`;
  - `MiniFin-Webhook-Signature`;
  - `MiniFin-Webhook-Event`;
  - `MiniFin-Webhook-Attempt`.
- Delivery attempts persist success/failure status, HTTP status, bounded response snippet and error metadata.
- Compose adds `host.docker.internal` host mapping for Platform; retained `WBH-01` script uses a containerized local receiver on the same compose network for deterministic local verification.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase06_webhooks.sh`;
  - `product/scripts/runtime/phase06_webhook_receiver.js`;
  - `product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh`.

Explicitly not implemented:
- No Stripe Connect onboarding (`MRC-01`).
- No card authorization, capture, clearing, settlement, refund, payout or chargeback implementation.
- No `SET-*`, `PAY-*`, `MRC-01`, `CHB-*` or frontend claims.
- No retry/DLQ (`WBH-02`) or DLQ replay (`WBH-03`); failed delivery is persisted as `FAILED` for this foundation slice.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-18 — Phase 06 Slice 01 Outbound Webhook Delivery Runtime Verification`.
- Platform image build passed after implementation.
- Isolated Compose project `mini-fintech-platform-a2` ran `platform` and dependencies on assigned ports.
- `WBH-01` — pass: local receiver validated signed `payment_intent.created` payloads, webhook event rows became `DELIVERED`, delivery attempts became `SUCCEEDED` with HTTP 200, and same-key payment-intent idempotency replay did not create duplicate webhook events.
- Targeted regressions passed:
  - `MRC-05`;
  - `PAY-01`;
  - `PAY-02`;
  - `PAY-03`.

Next planned step:
- Implement the next approved backend/runtime slice: Phase 06 Slice 02 outbound webhook retry/DLQ for `WBH-02`, or reprioritize Phase 07 Slice 01 KYC/Sumsub foundation if compliance work is preferred. `MRC-01` remains blocked on real Stripe Connect sandbox credentials.

---

## Phase 06 Slice 02 — Outbound Webhook Retry/DLQ

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_06_slice_02_outbound_webhook_retry_dlq_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform migration `V15__merchant_outbound_webhook_retry_dlq.sql` adds persisted event retry/DLQ state: retry count, max attempts, next retry time, latest error/status metadata and `dlq_at`.
- Outbound webhook delivery now distinguishes success (`DELIVERED`), retryable failed delivery (`FAILED` with persisted `next_retry_at`) and exhausted failed delivery (`DLQ`).
- Failed non-2xx receiver responses, including HTTP 500, persist the HTTP status and response/error metadata.
- A narrow internal dispatcher endpoint retries due failed events without implementing replay.
- Local retry policy is configurable with `WEBHOOK_MAX_ATTEMPTS` and `WEBHOOK_RETRY_DELAYS_SECONDS`, defaulting to 3 total attempts and short local delays.
- Retained runtime receiver helpers can return deterministic non-2xx responses for retry/DLQ verification.
- Retained runtime script `product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh` verifies `WBH-02` with a real failing HTTP receiver and DB assertions.

Runtime verification:
- `WBH-02` — pass.
- Targeted regressions passed:
  - `WBH-01`
  - `MRC-05`
  - `PAY-01`
  - `PAY-02`
  - `PAY-03`

Explicitly not implemented:
- `WBH-03` DLQ replay remains out of scope and unclaimed.
- No merchant DLQ UI, new webhook event producers, settlement/capture/refund work, Stripe Connect onboarding or frontend implementation was added.

Next planned step:
- Implement the now-approved Phase 06 Slice 03 DLQ replay scope for `WBH-03`.

---

## Phase 07 Slice 01 — KYC/Sumsub Foundation

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — `KYC-01` partial, `KYC-02` runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_07_slice_01_kyc_sumsub_foundation_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V16__kyc_sumsub_foundation.sql` creates `kyc` schema tables:
  - `kyc.kyc_profiles`;
  - `kyc.kyc_sessions`;
  - `kyc.sumsub_webhook_events`.
- New Platform package `com.minifin.platform.kyc`.
- End-user route `POST /api/v1/kyc/start`:
  - requires authenticated end-user session via `MFP_SESSION`;
  - rejects merchant sessions through existing identity actor-type checks;
  - requires active/email-verified user state;
  - reuses actor-control write guard for blocked/frozen end users;
  - persists KYC profile/start state;
  - is idempotent when an active local KYC session already exists;
  - returns `sumsub_not_configured` and records a failed session when Sumsub sandbox credentials are absent, without faking vendor success.
- Sumsub adapter boundary:
  - uses real Sumsub API calls only when app token and secret key are configured;
  - stores access-token hash only, not raw token material.
- Webhook route `POST /webhooks/sumsub/v1`:
  - verifies HMAC-SHA256 payload digest before state mutation;
  - persists vendor event ids with uniqueness;
  - treats duplicate vendor event ids as idempotent no-ops;
  - maps minimal review outcomes (`GREEN`, `RED` + reject type, pending/review events) to internal KYC statuses.
- Retained runtime scripts:
  - `product/scripts/runtime/lib_phase07_kyc_sumsub.sh`;
  - `product/scripts/runtime/reg_phase07_kyc_start.sh`;
  - `product/scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh`.

Runtime verification:
- `planning/runtime_evidence_log.md` — `2026-05-18 — Phase 07 Slice 01 KYC/Sumsub Foundation Runtime Verification`.
- `KYC-01` — partial because real Sumsub sandbox credentials were not configured; local auth/config/persistence behavior passed and no fake vendor success was returned.
- `KYC-02` — pass for invalid signature rejection, valid locally signed payload acceptance, duplicate vendor event id idempotency and `APPROVED` state mapping.
- Targeted wrong-role denial passed for merchant session calling end-user KYC start.

Explicitly not implemented/claimed:
- OpenSanctions;
- AML alerts/freezes/SoF;
- backoffice KYC queue or manual decisions;
- document preview/read-audit;
- case attachment upload or SeaweedFS document storage;
- wallet/card/payment gating changes beyond this slice's KYC state persistence;
- frontend code;
- real `KYC-01` full pass without Sumsub sandbox credentials.

Follow-up state:
- Phase 06 Slice 03 outbound webhook DLQ replay for `WBH-03` and Phase 07 Slice 02 backoffice KYC review queue/manual decisions for `KYC-03` were completed later.

---

## Phase 06 Slice 03 — Outbound Webhook DLQ Replay

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_06_slice_03_outbound_webhook_dlq_replay_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Merchant dashboard backend APIs under `/api/v1/merchant/webhook-events`:
  - `GET /api/v1/merchant/webhook-events?status=DLQ`;
  - `GET /api/v1/merchant/webhook-events/{id}`;
  - `POST /api/v1/merchant/webhook-events/{id}/replay`.
- DLQ list/detail is scoped to the authenticated merchant; cross-merchant access returns 404.
- `merchant_admin` can replay one retained DLQ event; `merchant_member` can read list/detail but receives `403 forbidden_role` on replay.
- Replay reuses the existing signed outbound delivery path and current endpoint signing secret behavior.
- Successful replay moves the event from `DLQ` to `DELIVERED`.
- Failed manual replay keeps the event in `DLQ`.
- Platform migration `V17__merchant_webhook_dlq_replay.sql` adds `merchant.webhook_delivery_attempts.trigger_type` with `AUTO` and `MANUAL_REPLAY`.
- Retained runtime script `product/scripts/runtime/reg_phase06_webhook_dlq_replay.sh` verifies `WBH-03`.

Runtime verification:
- `WBH-03` — pass.
- Targeted regressions passed:
  - `WBH-01`;
  - `WBH-02`;
  - `MRC-05`;
  - `PAY-01`;
  - `PAY-02`;
  - `PAY-03`.

Explicitly not implemented/claimed:
- Frontend DLQ UI;
- bulk replay;
- automatic replay of DLQ events;
- replay of non-DLQ delivered events;
- new webhook event producers;
- capture, settlement, refund, payout or chargeback lifecycle;
- Stripe Connect onboarding (`MRC-01`);
- KYC/backoffice compliance work.

---

## Phase 07 Slice 02 — Backoffice KYC Review Queue and Manual Decisions

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_07_slice_02_kyc_review_queue_manual_decisions_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V18__kyc_manual_review.sql` creates `kyc.kyc_manual_decisions`.
- Backoffice KYC routes:
  - `GET /api/v1/backoffice/kyc-cases`;
  - `GET /api/v1/backoffice/kyc-cases/{id}`;
  - `POST /api/v1/backoffice/kyc-cases/{id}/decision`.
- Queue/detail returns KYC case metadata for profiles in `IN_REVIEW` without document payloads or document preview.
- Manual decisions support `APPROVE`, `REJECT` and `REQUEST_RESUBMIT` with trimmed rationale length >= 20 characters.
- Valid transitions from `IN_REVIEW` to `APPROVED`, `REJECTED` or `NEEDS_RESUBMIT`; terminal/invalid transitions return `409 invalid_state`.
- Manual decision metadata is persisted with backoffice subject/role and timestamp.
- Business audit rows are written with event type `kyc.manual_decision_recorded`.
- End-user and merchant sessions cannot access backoffice KYC routes through the existing backoffice bearer-token security boundary.
- Retained runtime script `product/scripts/runtime/reg_phase07_kyc_manual_review.sh` verifies `KYC-03`.

Runtime verification:
- `planning/runtime_evidence_log.md` — `2026-05-18 — Phase 07 Slice 02 Backoffice KYC Manual Review Runtime Verification`.
- `KYC-03` — pass for queue listing, case detail, short-rationale validation, manual approval, persisted decision/audit rows, terminal repeat denial and end-user/merchant wrong-role denial.
- Targeted regressions passed:
  - `KYC-02`;
  - merchant-session wrong-role denial for `POST /api/v1/kyc/start`;
  - backoffice unauthenticated/wrong-role denial helper.

Explicitly not implemented/claimed:
- OpenSanctions (`SNX-*`);
- AML alerts/freezes/SoF;
- document preview or KYC document read-audit (`AUD-03`);
- SeaweedFS document storage;
- case attachment upload;
- wallet/card/payment gating changes beyond updating `kyc.kyc_profiles.status`;
- frontend UI;
- real `KYC-01` full pass without Sumsub sandbox credentials.

Next planned step:
- Choose and plan the next backend/runtime slice, likely settlement foundation (`SET-01`) or the next sanctions/compliance slice (`SNX-02`/`AUD-03`).

---

## Phase 05 Slice 03 — Payment Capture Foundation

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_05_slice_03_payment_capture_foundation_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform public API route `POST /v1/payment_intents/{id}/capture`.
- Public API key auth and required `Idempotency-Key` use the existing public API auth/idempotency primitives.
- Capture is scoped to the authenticated merchant; cross-merchant capture returns the same not-found behavior as payment-intent reads.
- Capture is allowed only from `AUTHORIZED`; non-`AUTHORIZED` second capture returns `409 invalid_state`.
- Optional `amount` and `currency` request fields are validated against the authorized payment intent for full-capture-only behavior.
- Platform migration `V19__payment_capture_foundation.sql` adds `CAPTURED` state plus `captured_at`, `captured_amount` and `capture_request_id` metadata.
- Merchant payment dashboard repository reads the new capture columns for captured payment-intent DTOs.
- Retained runtime script `product/scripts/runtime/reg_phase05_payment_capture.sh` verifies `PAY-06`.
- Retained Phase 05 helper and ledger reconciliation scripts support compose-network curl runners for isolated runtime slots where host-published HTTP hangs.

Runtime verification:
- `planning/runtime_evidence_log.md` — `2026-05-19 — Phase 05 Slice 03 Payment Capture Foundation Runtime Verification`.
- `PAY-06` — pass.
- Targeted regressions passed:
  - `PAY-04`;
  - `PAY-05`;
  - `PAY-01`;
  - `PAY-02`;
  - `PAY-03`.

Verification note:
- Gradle `bootJar` inside fresh Docker build containers stalled during dependency resolution in this environment. Runtime verification used a patched runtime jar built from the previous boot jar plus the freshly compiled classes/resources and a small `mfp_agent9_pay06-platform` image; Flyway applied `V19` and the runtime scripts exercised the actual updated Platform process in isolated compose network `mfp_agent9_pay06_default`.

Explicitly not implemented/claimed:
- clearing batch, settlement batch, fee split ledger postings, Acquirer settlement projection, refunds, payouts, chargebacks, frontend UI, Stripe Connect onboarding (`MRC-01`) or sanctions/AML/KYC changes.
- `SET-*`, `CHB-*` and frontend `UI-*` are not claimed.

---

## Phase 07 Slice 03 — OpenSanctions Fail-Closed Foundation

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_07_slice_03_opensanctions_fail_closed_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V20__opensanctions_fail_closed.sql` for `sanctions.sanctions_hits`.
- OpenSanctions adapter boundary with explicit local runtime modes: `disabled`, `unavailable`/`timeout`, `match` and `no_match`.
- KYC manual approval gate: `APPROVE` runs sanctions screening before `APPROVED` is returned.
- Screening unavailable/timeout creates an `OPEN` hit with `reason = 'SCREENING_UNAVAILABLE'`, keeps KYC `IN_REVIEW` and returns fail-closed error `sanctions_screening_unavailable`.
- High-confidence match creates an `OPEN` hit with `reason = 'POSSIBLE_MATCH'`, keeps KYC `IN_REVIEW` and returns blocked error `sanctions_possible_match`.
- No-match screening writes pass audit and allows manual approval to proceed.
- Audit rows for OpenSanctions pass/block/unavailable outcomes.
- Retained runtime script:
  - `product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh`

Explicitly not implemented:
- `SNX-02` false-positive exception lifecycle;
- sanctions queue/detail/decision UI;
- permanent account freeze/blocking;
- AML alerts;
- wallet/card/payment sanctions gating beyond KYC manual approval;
- document preview/read-audit;
- frontend UI;
- real production watchlist ingestion.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-19 — Phase 07 Slice 03 OpenSanctions Fail-Closed Runtime Verification`.
- `SNX-01` — pass.
- `KYC-03` — pass targeted regression.

Next planned step:
- Implement Phase 06 Slice 04 capture-to-settlement foundation for `SET-01`, or Phase 07 Slice 04 sanctions false-positive exception for `SNX-02`.

---

## Phase 06 Slice 04 — Capture to Settlement Foundation

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_06_slice_04_capture_to_settlement_foundation_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V21__capture_to_settlement_foundation.sql` creates `settlement.settlement_batches`, `settlement.settlement_items`, the `CARD_SETTLEMENT_CLEARING` ledger account and extends payment-intent state to `SETTLED`.
- Internal `POST /internal/settlement/process-captured` processor settles eligible `CAPTURED` payment intents, persists one settlement item per intent and marks the payment queryable as `SETTLED`.
- Settlement processing posts one balanced `CARD_PAYMENT_SETTLEMENT` ledger journal from `CARD_SETTLEMENT_CLEARING` to a per-merchant `MERCHANT_SETTLEMENT:<merchantId>` ledger account.
- Processor reruns are idempotent for already-settled intents through the unique settlement item per payment intent.
- Retained runtime script: `product/scripts/runtime/reg_phase06_capture_to_settlement.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-19 — Phase 06 Slice 04 Capture to Settlement Runtime Verification`.
- `SET-01` — pass.
- Targeted regressions passed: `PAY-06`, `PAY-04`, `PAY-05`, `PAY-01`, `LDG-05`.

Not implemented / not claimed:
- fee split correctness;
- refunds, payouts, chargebacks, clearing-file export or acquirer settlement projection;
- merchant dashboard settlement UI.

---

## Phase 07 Slice 04 — Sanctions False-Positive Exception

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_07_slice_04_sanctions_false_positive_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V22__sanctions_false_positive_exception.sql` for sanctions hit decisions and false-positive exceptions.
- Compliance-only backoffice sanctions hit list/detail/decision APIs: `GET /api/v1/backoffice/sanctions-hits`, `GET /api/v1/backoffice/sanctions-hits/{id}`, `POST /api/v1/backoffice/sanctions-hits/{id}/decision`.
- `CLEAR_FALSE_POSITIVE` decision requires compliance role (`compliance_officer` or `senior_compliance`) and rationale length >= 20 characters; `backoffice_operator` sanctions hit access/decision attempts are denied.
- Open hits transition from `OPEN` to `CLEARED_FALSE_POSITIVE`; decision and exception rows are persisted and audit row `sanctions.hit_false_positive_cleared` is written.
- Active false-positive exception suppresses the same OpenSanctions match for the same end user/vendor entity during later KYC manual approval; suppressed match writes audit row `sanctions.opensanctions_screening_suppressed` and does not create a new blocking hit.
- Retained runtime script:
  - `product/scripts/runtime/reg_phase07_sanctions_false_positive.sh`

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-19 — Phase 07 Slice 04 Sanctions False-Positive Exception Runtime Verification`.
- `SNX-02` — pass.
- `SNX-01` — pass targeted regression.

Explicitly not implemented/claimed:
- true-match permanent account block, freeze/unfreeze, AML, document preview/read-audit (`AUD-03`), sanctions frontend UI, wallet/card/payment sanctions gating beyond KYC approval gate, broad case-management abstraction, settlement/payment processing.

---

## Phase 06 Slice 05 — Settlement Fee Split

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_06_slice_05_settlement_fee_split_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Platform DB migration `V23__settlement_fee_split.sql` adds fee component columns to `settlement.settlement_items` and creates deterministic local fee destination ledger accounts.
- Settlement processing now computes and persists merchant net, issuer interchange, network assessment and acquirer margin for each settled card payment.
- `CARD_PAYMENT_SETTLEMENT` journals debit gross from `CARD_SETTLEMENT_CLEARING` and credit merchant net plus the three fee destination accounts, remaining exactly balanced.
- Existing settlement item uniqueness keeps reruns idempotent: no duplicate settlement item, fee component row or settlement ledger journal for the same payment intent.
- Retained runtime script: `product/scripts/runtime/reg_phase06_settlement_fee_split.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-19 — Phase 06 Slice 05 Settlement Fee Split Runtime Verification`.
- `SET-02` — pass.
- Targeted regressions passed: `SET-01`, `PAY-06`, `PAY-01`, `LDG-05`.

Explicitly not implemented/claimed:
- acquirer settlement projection/reconciliation (`SET-03`), refunds (`SET-04`), payouts, chargebacks, merchant settlement frontend, tenant pricing engine.

---

## Phase 08 Slice 01 — AML Velocity Alert Foundation

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_08_slice_01_aml_velocity_alert_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Added `aml` schema foundation with `aml.aml_alerts` and `aml.aml_rule_evaluations`.
- Added Platform AML service/rule engine boundary.
- Implemented deterministic local velocity rule evaluation for completed wallet deposit, withdrawal and internal-transfer activity.
- Added internal trigger `POST /internal/aml/evaluate-velocity`.
- Creates one `OPEN` `VELOCITY` AML alert with `MEDIUM` severity when observed activity exceeds threshold.
- Suppresses duplicate open alerts for the same end user/rule/window on rerun.
- Writes `aml.alert_created` audit event for alert creation.
- Added retained runtime script `product/scripts/runtime/reg_phase08_aml_velocity_alert.sh`.

Runtime verification:
- `planning/runtime_evidence_log.md` — `2026-05-19 — Phase 08 Slice 01 AML Velocity Alert Foundation Runtime Verification`.
- `AML-01` — pass.
- `RUN-01` — pass targeted regression.
- `LDG-05` not run because AML implementation does not write ledger tables directly.

Explicitly not implemented/claimed:
- structuring (`AML-02`), dormancy-break (`AML-03`), critical auto-freeze (`AML-04`), AML review decisions, SoF (`WLT-03`), two-eyes (`WLT-04`), frontend `UI-*`.

---

## Phase 06 Slice 06 — Acquirer Settlement Projection

Status: **COMPLETE — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_06_slice_06_acquirer_settlement_projection_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Acquirer DB migration `V3__merchant_settlement_projection.sql` creates `merchant_settlement.balance_projection`.
- Acquirer exposes service-auth-protected `POST /internal/settlement/projections` for Platform-originated settlement projection ingestion.
- Platform exposes `POST /internal/settlement/publish-projections?limit=100` to publish current settled Platform `settlement.settlement_items` into Acquirer.
- Projection rows include Platform settlement item id, batch id, merchant id, payment intent id, gross amount, merchant net, issuer interchange, network assessment, acquirer margin, currency and Platform settled timestamp.
- Acquirer ingestion is idempotent by `platform_settlement_item_id`.
- Added retained runtime script `product/scripts/runtime/reg_phase06_acquirer_settlement_projection.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 06 Slice 06 Acquirer Settlement Projection Runtime Verification`.
- `SET-03` — pass.
- Targeted regressions passed: `SET-02`, `SET-01`, `PAY-06`, `LDG-05`.

Target runtime checks:
- `SET-03` — Acquirer settlement projection matches Platform settlement state.
- Targeted regressions: `SET-02`, `SET-01`, `PAY-06`, `LDG-05`.

Explicitly not implemented/claimed:
- refunds (`SET-04`), payouts, chargebacks, merchant settlement frontend, bank file export, scheduled reconciliation jobs.

---

## Phase 08 Slice 02 — AML Structuring Alert

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_08_slice_02_aml_structuring_alert_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Reused AML alert/evaluation persistence from `AML-01`.
- Added deterministic local structuring rule for repeated completed deposit/withdraw/transfer movements in the EUR 9,000.00-9,900.00 band over a 24-hour lookback.
- Added internal `POST /internal/aml/evaluate-structuring`.
- Creates one `OPEN` alert with `rule_code = 'STRUCTURING'`, severity `HIGH`, duplicate suppression and `aml.alert_created` audit row.
- Added retained runtime script `product/scripts/runtime/reg_phase08_aml_structuring_alert.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 08 Slice 02 AML Structuring Alert Runtime Verification`.
- `AML-02` — pass.
- Targeted regressions passed: `AML-01`, `RUN-01`.
- `LDG-05` not run because AML implementation reads completed wallet activity but does not write ledger tables directly.

Explicitly not implemented/claimed:
- dormancy-break (`AML-03`), critical auto-freeze (`AML-04`), AML review decisions, account freeze/unfreeze, SoF (`WLT-03`), two-eyes (`WLT-04`), frontend `UI-*`.

---

## Phase 08 Slice 03 — AML Dormancy-Break Alert

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_08_slice_03_aml_dormancy_break_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Reused AML alert/evaluation persistence from `AML-01`.
- Added deterministic local dormancy-break rule for users with previous completed money-moving activity, no completed activity during a 30-day dormant gap and recent completed activity over EUR 1,000.00 in a 24-hour lookback.
- Added internal `POST /internal/aml/evaluate-dormancy-break`.
- Creates one `OPEN` alert with `rule_code = 'DORMANCY_BREAK'`, severity `HIGH`, duplicate suppression and `aml.alert_created` audit row.
- Added retained runtime script `product/scripts/runtime/reg_phase08_aml_dormancy_break_alert.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 08 Slice 03 AML Dormancy-Break Alert Runtime Verification`.
- `AML-03` — pass.
- Targeted regressions passed: `AML-02`, `AML-01`, `RUN-01`.
- `LDG-05` not run because AML implementation reads completed wallet activity but does not write ledger tables directly.

Explicitly not implemented/claimed:
- critical auto-freeze (`AML-04`), AML review decisions, account freeze/unfreeze, SoF (`WLT-03`), two-eyes (`WLT-04`), frontend `UI-*`, SAR.

---

## Phase 08 Slice 04 — AML Critical Auto-Freeze

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_08_slice_04_aml_critical_auto_freeze_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Added internal `POST /internal/aml/process-critical-auto-freezes`.
- Processor finds `OPEN` AML alerts with `severity = 'CRITICAL'`.
- For each critical alert, sets the end user `identity.actor_controls` state to `FROZEN` with reason code `aml_critical_alert`.
- Marks processed critical alerts as `ACCOUNT_FROZEN_PERMANENT`.
- Writes `identity.actor_control_changed` and `aml.critical_alert_auto_frozen` audit rows.
- Added retained runtime script `product/scripts/runtime/reg_phase08_aml_critical_auto_freeze.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 08 Slice 04 AML Critical Auto-Freeze Runtime Verification`.
- `AML-04` — pass.
- Targeted regressions passed: `AML-03`, `AML-02`, `AML-01`, `RUN-01`.
- `LDG-05` not run because AML auto-freeze updates actor-control state and alert status but does not write ledger tables directly.

Explicitly not implemented/claimed:
- AML alert review decisions, account unfreeze workflow, SoF (`WLT-03`), two-eyes (`WLT-04`), frontend `UI-*`, SAR.

---

## Phase 08 Slice 05 — Source-of-Funds Deposit Threshold

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_08_slice_05_sof_deposit_threshold_planning.md` — APPROVED v0.1.

Implemented backend/runtime scope:
- Added Platform migration `V25__source_of_funds_deposit_threshold.sql`.
- Deposit requests over EUR 10,000 are now accepted into `REQUESTED` state instead of being refused as unsupported high-value deposits.
- Low-value deposits continue to move directly to `PENDING_OPERATOR_REVIEW`.
- Added `wallet.source_of_funds_declarations`.
- Added end-user `POST /api/v1/deposits/{depositId}/source-of-funds`.
- SoF submission requires deposit ownership, high-value amount and `REQUESTED` state; accepted declaration moves the deposit to `PENDING_OPERATOR_REVIEW`.
- Added retained runtime script `product/scripts/runtime/reg_phase08_sof_deposit_threshold.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 08 Slice 05 Source-of-Funds Deposit Threshold Runtime Verification`.
- `WLT-03` — pass.
- Targeted regressions passed: low-value deposit happy path and deposit amount validation.
- Platform health endpoint passed.

Explicitly not implemented/claimed:
- two-eyes approval (`WLT-04`), SoF backoffice review queue, document upload/storage, high-value withdrawals/transfers, frontend `UI-*`.

---

## Phase 08 Slice 06 — Two-Eyes Enforcement

Status: **BACKEND/RUNTIME SUB-SCOPE IMPLEMENTED — runtime verified**.

Planning contract:
- `planning/implementation-slices/phase_08_slice_06_two_eyes_enforcement_planning.md` — backend/runtime sub-scope executed v0.1.

Implemented backend/runtime scope:
- Added Platform migration `V26__two_eyes_enforcement.sql`.
- Added `READY_FOR_SECOND_REVIEW` state support for wallet deposit and withdrawal requests.
- Added durable first-review actor metadata to `wallet.deposit_requests` and `wallet.withdraw_requests`.
- High-value deposits `>= EUR 10,000.00` now require first backoffice approval to mark the request for second review before any deposit ledger journal is posted.
- Same backoffice actor attempting the second high-value deposit approval is rejected with `403 two_eyes_same_actor_denied`.
- A second distinct backoffice actor completes the high-value deposit and posts exactly one existing `WALLET_DEPOSIT` ledger journal.
- High-value withdrawals `>= EUR 10,000.00` are now accepted up to the local wallet operation maximum, with the existing hold posted at request time.
- First backoffice completion decision on a high-value withdrawal marks it for second review without posting the final withdrawal completion journal.
- Same backoffice actor attempting the second high-value withdrawal completion is rejected with `403 two_eyes_same_actor_denied`.
- A second distinct backoffice actor completes the high-value withdrawal and posts exactly one existing `WALLET_WITHDRAW_COMPLETE` ledger journal.
- Manual-ops queues include `READY_FOR_SECOND_REVIEW` records so second-review items stay visible.
- Added audit rows `wallet.deposit_marked_for_second_review` and `wallet.withdrawal_marked_for_second_review` while retaining existing final approval/completion/rejection audit semantics.
- Added retained runtime script `product/scripts/runtime/reg_phase08_two_eyes_enforcement.sh`.
- Added Gradle Wrapper under `product/` to allow local `./gradlew` verification without a system Gradle install.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 08 Slice 06 Two-Eyes Enforcement Runtime Verification`.
- `WLT-04` — pass.
- `LDG-05` — pass targeted regression.
- `WLT-03` — pass targeted regression.
- Platform `compileKotlin` and `bootJar` passed via `product/gradlew`.

Verification notes:
- Docker Compose bridge network creation/host port publishing was blocked by a local Docker iptables chain issue, so runtime verification reused an existing Docker bridge network with service host ports reset and compose-network URLs.
- Platform image for the runtime pass was built from the locally verified `bootJar` output because the Dockerfile's in-container Gradle build path was blocked by dependency/plugin resolution behavior in this environment.

Explicitly not implemented/claimed:
- senior compliance override / bypass;
- dedicated SoF backoffice review queue;
- object-storage document upload;
- frontend UI;
- wider unified case-management refactor;
- banking statement upload threshold;
- high-value internal transfers;
- chargebacks (`CHB-*`);
- refunds (`SET-04`).

---

## 2026-05-21 — Phase 09 Slice 01 Chargeback Initiation (`CHB-01`)

Planning note:
- `planning/implementation-slices/phase_09_slice_01_chargeback_initiation_planning.md` — backend/runtime sub-scope executed v0.1.

Implemented:
- Added `cards.issued_cards` Platform-side cardholder mapping persisted at card issuance so later card-payment disputes can resolve `card_token` to the end user.
- Added `chargeback.disputes` persistence with unique one-dispute-per-payment-intent constraint, MVP reason-code validation, merchant deadline and `MERCHANT_NOTIFIED` state.
- Extended `merchant.payment_intents` state constraint to include `DISPUTED`.
- Added authenticated end-user endpoint `POST /api/v1/card-payments/{paymentIntentId}/disputes`.
- Enforced CHB-01 eligibility:
  - caller must be the original cardholder;
  - cardholder KYC must be `APPROVED`;
  - original payment must be `SETTLED`;
  - payment must be within the configured 60-day default dispute window;
  - currency must be EUR;
  - duplicate initiation returns the existing same-cardholder dispute rather than inserting a second row.
- Successful initiation inserts a `MERCHANT_NOTIFIED` dispute, marks the payment intent `DISPUTED`, writes `chargeback.initiated` audit, and persists `dispute.created` through the existing outbound webhook outbox path.
- Added retained runtime script `product/scripts/runtime/reg_phase09_chargeback_initiation.sh`.

Verification:
- `bash -n product/scripts/runtime/reg_phase09_chargeback_initiation.sh` — pass.
- `git diff --check` — pass.
- `product/gradlew --no-daemon -p product :apps:platform:compileKotlin` — pass.
- `product/gradlew --no-daemon -p product :apps:platform:bootJar` — pass.
- `product/gradlew --no-daemon -p product :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar` — pass for local runtime images.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 09 Slice 01 Chargeback Initiation Runtime Verification`.
- `CHB-01` — pass.
- `LDG-05` — pass targeted regression.
- `SET-01` — pass targeted regression.

Verification notes:
- Docker Compose bridge network creation/host port publishing remained blocked by a local Docker iptables chain issue, so runtime verification reused an existing Docker bridge network with service host ports reset via `!reset []` and compose-network URLs.
- Service images for runtime verification were built from locally verified `bootJar` outputs because the Dockerfile's in-container Gradle build path was unstable in this environment.

Explicitly not implemented/claimed:
- provisional cardholder credit (`CHB-02`);
- merchant evidence submission (`CHB-03`);
- arbitration `WON`/`LOST` (`CHB-04`, `CHB-05`);
- merchant reserve or settlement balance debit/hold;
- attachment storage;
- backoffice/merchant/end-user frontend UI;
- chargeback rate dashboard/metric;
- chargeback ledger movement correctness.

---

## 2026-05-21 — Phase 09 Slice 02 Provisional Cardholder Credit (`CHB-02`)

Planning note:
- `planning/implementation-slices/phase_09_slice_02_provisional_credit_planning.md` — backend/runtime sub-scope executed v0.1.

Implemented:
- Added platform-level `ACQUIRER_DISPUTE_RESERVE` ledger account.
- Added `chargeback.disputes.provisional_credit_journal_id`.
- Successful cardholder dispute initiation now posts exactly one `CARDHOLDER_PROVISIONAL_CREDIT` journal in the same transaction:
  - reference type `CHARGEBACK`;
  - reference id = dispute id;
  - debit `ACQUIRER_DISPUTE_RESERVE`;
  - credit cardholder wallet ledger account;
  - amount = dispute amount.
- Dispute responses now include `provisionalCreditJournalId`.
- Duplicate same-cardholder initiation still returns the existing dispute and does not post a second provisional-credit journal.
- Denied initiation paths remain before value movement and do not post provisional-credit journals.
- Retained runtime script `product/scripts/runtime/reg_phase09_chargeback_initiation.sh` now verifies both `CHB-01` and `CHB-02`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 09 Slice 02 Provisional Cardholder Credit Runtime Verification`.
- `CHB-02` — pass.
- `CHB-01` — pass regression within the same retained script.
- `LDG-05` — pass targeted regression.
- `SET-01` — pass targeted regression.

Verification notes:
- Docker Compose bridge network creation/host port publishing remained blocked by a local Docker iptables chain issue, so runtime verification reused an existing Docker bridge network with service host ports reset via `!reset []` and compose-network URLs.
- Platform runtime image was built from the locally verified `bootJar` output.

Explicitly not implemented/claimed:
- merchant evidence submission (`CHB-03`);
- arbitration `WON`/`LOST` (`CHB-04`, `CHB-05`);
- reversal of provisional credit on `WON`;
- permanent merchant debit / reserve release on `LOST`;
- attachment storage;
- frontend UI;
- chargeback rate dashboard/metric.

---

## 2026-05-21 — Phase 09 Slice 03 Merchant Evidence Submission (`CHB-03`)

Planning note:
- `planning/implementation-slices/phase_09_slice_03_merchant_evidence_planning.md` — backend/runtime sub-scope executed v0.1.

Implemented:
- Added `chargeback.evidence_submissions` and `chargeback.evidence_attachments` metadata persistence.
- Added merchant dashboard endpoint `POST /api/v1/merchant/disputes/{disputeId}/evidence`.
- Merchant evidence submission enforces:
  - active merchant employee session;
  - dispute belongs to the employee's merchant;
  - dispute is in `MERCHANT_NOTIFIED`;
  - merchant response deadline has not expired;
  - required narrative and bounded attachment metadata.
- Successful submission transitions dispute state to `EVIDENCE_SUBMITTED`, persists evidence/attachment metadata, writes `chargeback.evidence_submitted` audit, and persists `dispute.evidence_received` webhook outbox event.
- Added retained runtime script `product/scripts/runtime/reg_phase09_merchant_evidence.sh`.

Runtime evidence:
- `planning/runtime_evidence_log.md` — `2026-05-21 — Phase 09 Slice 03 Merchant Evidence Submission Runtime Verification`.
- `CHB-03` — pass.
- `CHB-01`/`CHB-02` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Verification notes:
- Docker Compose bridge network creation/host port publishing remained blocked by a local Docker iptables chain issue, so runtime verification reused an existing Docker bridge network with service host ports reset via `!reset []` and compose-network URLs.
- Platform runtime image was built from the locally verified `bootJar` output.

Explicitly not implemented/claimed:
- actual S3/MinIO multipart upload/download;
- merchant accept / terminal `LOST`;
- arbitration (`CHB-04`, `CHB-05`);
- provisional credit reversal or merchant debit;
- frontend UI;
- chargeback rate metrics.
