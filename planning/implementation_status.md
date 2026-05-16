# Implementation Status

Last updated: 2026-05-17.

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
- `prototypes/ui/14_enduser_wallet_home.html` — standalone `UEW-UI-03` end-user wallet home HTML prototype from UI/UX prototype workflow; minor JSX style syntax fixed locally; headless Chrome render check passed on 2026-05-17.

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
