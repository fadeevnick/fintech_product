# Runtime Checklists — Mini Fintech Platform

Status: **APPROVED v0.1** (approved by project owner 2026-05-15).

Inputs:
- `planning/06_implementation_guide.md` — APPROVED v0.2.
- `planning/design-details/` — APPROVED v0.2.

---

## 1. ID Schema

Runtime check IDs use:

```text
{MODULE}-{NN}
```

IDs are stable. Retired IDs are not reused.

This file is the check contract. Actual execution evidence belongs in `planning/runtime_evidence_log.md`.

## 2. Module Aliases

| Alias | Module |
|---|---|
| `RUN` | Local runtime / infrastructure / service health |
| `AUTH` | Identity, sessions, RBAC |
| `AUD` | Audit and read-audit |
| `LDG` | Ledger invariants |
| `WLT` | Wallet and manual operations |
| `MRC` | Merchant onboarding and API keys |
| `PAY` | Payment intent, authorization and capture |
| `VLT` | Vault and card data isolation |
| `SET` | Clearing, settlement and refunds |
| `WBH` | Outbound webhook delivery and DLQ |
| `KYC` | KYC / Sumsub |
| `SNX` | Sanctions / OpenSanctions |
| `AML` | AML rules and freezes |
| `CHB` | Chargeback lifecycle |
| `UI` | Browser/UI workflow checks |
| `REC` | Reconciliation and hardening |

## 3. Evidence Rules

Every runtime evidence entry must include:
- date/time;
- runtime phase;
- check ID;
- actor used;
- preconditions;
- action taken;
- expected result;
- actual result;
- result tag;
- evidence reference;
- verification script or manual steps.

Result tags:
- `pass`
- `pass-indirect`
- `partial`
- `fail`

`pass-indirect` and `partial` require explicit remaining gap or follow-up.

## 4. Phase 01 — Product Skeleton and Local Runtime

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `RUN-01` | All five backend service health endpoints. | `platform`, `acquirer`, `network`, `issuer`, `vault` expose healthy readiness/liveness endpoints. | `product/scripts/runtime/reg_phase01_runtime_health.*` |
| `RUN-02` | Service database migrations. | All five service DBs run initial Flyway migrations successfully. | `product/scripts/runtime/reg_phase01_migrations.*` |
| `RUN-03` | Kafka worker connectivity. | At least one backend worker can connect to Kafka and perform a smoke produce/consume or metadata check. | `product/scripts/runtime/reg_phase01_kafka.*` |
| `RUN-04` | Keycloak connectivity. | Platform config can reach Keycloak discovery/JWKS endpoint. | `product/scripts/runtime/reg_phase01_keycloak.*` |
| `RUN-05` | SeaweedFS S3 connectivity. | Object storage adapter can create/list or write/read a smoke object in local S3-compatible endpoint. | `product/scripts/runtime/reg_phase01_object_storage.*` |
| `RUN-06` | Logs pipeline. | At least one backend service structured JSON log is visible in Loki. | manual or `product/scripts/runtime/reg_phase01_logs.*` |
| `RUN-07` | Metrics pipeline. | At least one Micrometer metric from a backend service is scraped by Prometheus. | manual or `product/scripts/runtime/reg_phase01_metrics.*` |
| `RUN-08` | Traces pipeline. | At least one trace reaches Tempo, or `pass-indirect` is recorded with explicit follow-up if Tempo wiring needs a later slice. | manual or `product/scripts/runtime/reg_phase01_traces.*` |
| `UI-01` | SPA shell build/serve. | End-user, merchant and backoffice SPA shells build and are served by nginx containers through Traefik routes. | `product/scripts/runtime/reg_phase01_spa_shells.*` |

## 5. Phase 02 — Identity, RBAC, Sessions and Audit

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `AUTH-01` | End-user registration and login. | End user can register, verify email and log in with server-side session cookie. | pending |
| `AUTH-02` | Merchant registration and login. | Merchant employee can register/log in and is scoped to own merchant. | pending |
| `AUTH-03` | Backoffice OIDC login. | Backoffice user authenticates through Keycloak and maps roles into platform RBAC. | pending |
| `AUTH-04` | Global email uniqueness. | Same email cannot register in multiple pools. | pending |
| `AUTH-05` | Protected endpoint denial. | Unauthenticated and wrong-role actors receive expected 401/403. | pending |
| `AUD-01` | Auth audit events. | Registration/login/session state changes create audit entries. | pending |
| `AUD-02` | Audit append-only protection. | App path cannot update/delete audit entries. | pending |

## 6. Phase 03 — Ledger and Wallet Manual Operations

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `LDG-01` | Unbalanced journal rejection. | Unbalanced entry is rejected and not persisted. | pending |
| `LDG-02` | Valid deposit postings. | Manual deposit creates balanced postings and derived wallet balance increases. | pending |
| `LDG-03` | Withdraw hold/final debit. | Withdraw creates hold and final debit flow with balanced postings. | pending |
| `LDG-04` | Ledger append-only protection. | App path cannot update/delete ledger entries/postings. | pending |
| `LDG-05` | Ledger reconciliation script. | Retained reconciliation script passes for seeded state. | pending |
| `WLT-01` | Internal transfer. | Atomic transfer debits sender and credits receiver. | pending |
| `WLT-02` | Frozen account write block. | Frozen/blocked account cannot perform wallet writes. | pending |

## 7. Phase 04 — Merchant Onboarding and Public API Foundation

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `MRC-01` | Stripe Connect onboarding start. | Merchant can start real Stripe Connect sandbox onboarding. | pending |
| `MRC-02` | Stripe webhook idempotency/signature. | Valid webhook updates state once; invalid signature rejected. | pending |
| `MRC-03` | API key lifecycle. | Key shown once, stored hashed/fingerprinted, revoked key fails. | pending |
| `MRC-04` | Merchant dashboard payment-intent read scoping. | Merchant can list/detail own shell payment intents; another merchant receives 404/empty result. | `product/scripts/runtime/reg_phase04_merchant_payment_reads.sh` |
| `MRC-05` | Merchant webhook endpoint configuration CRUD. | Merchant admin can create/update/list/delete webhook endpoint config; non-admin writes are rejected; config is scoped to merchant. | `product/scripts/runtime/reg_phase04_merchant_webhook_config.sh` |
| `PAY-01` | Public API response shape. | Public API returns `{data, errors}` shape. | pending |
| `PAY-02` | Idempotency same body. | Same key/body returns cached response. | pending |
| `PAY-03` | Idempotency conflict. | Same key/different body returns 409. | pending |

## 8. Phase 05 — Vault, Card Issuance and Authorization

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `VLT-01` | PAN stored only in Vault. | Card issuance stores PAN only in Vault; other services store token/last4 only. | pending |
| `VLT-02` | Detokenize restriction. | Non-issuer services cannot detokenize. | pending |
| `VLT-03` | PAN log masking. | PAN does not appear in service logs. | pending |
| `PAY-04` | Approved authorization hold. | Acquirer→Network→Issuer authorization places ledger hold. | pending |
| `PAY-05` | Structured auth declines. | Insufficient funds/blocked/expired produce structured decline. | pending |
| `PAY-06` | Payment capture foundation. | Authorized payment intent can be captured exactly once with idempotent public API behavior. | `product/scripts/runtime/reg_phase05_payment_capture.sh` |

## 9. Phase 06 — Clearing, Settlement, Refunds and Webhooks

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `SET-01` | Capture to settlement path. | Captured payment reaches settlement through outbox/Kafka path. | `product/scripts/runtime/reg_phase06_capture_to_settlement.sh` |
| `SET-02` | Fee split balance. | Settlement fee postings balance exactly. | `product/scripts/runtime/reg_phase06_settlement_fee_split.sh` |
| `SET-03` | Acquirer projection reconciliation. | Acquirer settlement projection matches Platform Ledger. | `product/scripts/runtime/reg_phase06_acquirer_settlement_projection.sh` |
| `SET-04` | Partial refund bounds. | Aggregate refunds cannot exceed original payment amount. | pending |
| `WBH-01` | Webhook signing/delivery. | Merchant webhook payload is signed and delivered to test receiver. | `product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh` |
| `WBH-02` | Webhook retry/DLQ. | Failed delivery retries and moves to DLQ. | `product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh` |
| `WBH-03` | DLQ replay. | Retained failed event can be replayed. | `product/scripts/runtime/reg_phase06_webhook_dlq_replay.sh` |

## 10. Phase 07 — Compliance Integrations and Case Management

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `KYC-01` | Sumsub KYC start. | Email-verified user can start real Sumsub sandbox flow. | pending |
| `KYC-02` | Sumsub webhook verification/idempotency. | Invalid signature rejected; duplicate event does not move state twice. | `product/scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh` |
| `KYC-03` | Backoffice manual KYC review. | Operator can list an in-review KYC case and approve/reject/resubmit with rationale and audit. | `product/scripts/runtime/reg_phase07_kyc_manual_review.sh` |
| `SNX-01` | OpenSanctions fail-closed. | Timeout/failure blocks dependent action and opens case. | `product/scripts/runtime/reg_phase07_opensanctions_fail_closed.sh` |
| `SNX-02` | Sanctions false-positive exception. | Cleared exception suppresses same future match. | `product/scripts/runtime/reg_phase07_sanctions_false_positive.sh` |
| `AUD-03` | Compliance read-audit. | Sensitive read writes sync read-audit before returning data. | pending |

## 11. Phase 08 — AML, Freezes, SoF and High-Value Controls

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `AML-01` | Velocity rule trip. | Synthetic activity trips velocity rule and opens alert. | `product/scripts/runtime/reg_phase08_aml_velocity_alert.sh` |
| `AML-02` | Structuring rule trip. | Synthetic activity trips structuring rule and opens alert. | `product/scripts/runtime/reg_phase08_aml_structuring_alert.sh` |
| `AML-03` | Dormancy-break rule trip. | Synthetic activity trips dormancy-break rule and opens alert. | `product/scripts/runtime/reg_phase08_aml_dormancy_break_alert.sh` |
| `AML-04` | Critical auto-freeze. | Critical alert freezes account and blocks writes. | `product/scripts/runtime/reg_phase08_aml_critical_auto_freeze.sh` |
| `WLT-03` | SoF deposit threshold. | Deposit > EUR 10k requires SoF before instructions. | pending |
| `WLT-04` | Two-eyes enforcement. | Same operator cannot complete both approvals. | pending |

## 12. Phase 09 — Chargeback Lifecycle

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `CHB-01` | Cardholder dispute initiation. | Eligible payment can be disputed within window. | pending |
| `CHB-02` | Provisional credit. | Initiation posts provisional cardholder credit. | pending |
| `CHB-03` | Merchant evidence. | Merchant can submit evidence with attachment. | pending |
| `CHB-04` | Arbitration WON. | WON reverses provisional credit and releases merchant funds correctly. | pending |
| `CHB-05` | Arbitration LOST. | LOST makes cardholder credit permanent and debits merchant correctly. | pending |

## 13. Phase 10 — Three SPAs Completion Pass

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `UI-02` | End-user MVP journey. | End-user can complete core wallet/card/dispute UI paths. | pending |
| `UI-03` | Merchant MVP journey. | Merchant can complete onboarding/API key/payment/refund/dispute UI paths. | pending |
| `UI-04` | Backoffice MVP journey. | Operator/compliance queues and decisions work through UI. | pending |
| `UI-05` | Cross-role access denial. | Roles cannot access each other's resources through UI/API. | pending |

## 14. Phase 11 — Reconciliation, Hardening and Portfolio Demo

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `REC-01` | Reset and seed. | Full stack can reset and seed deterministic demo data. | pending |
| `REC-02` | Full demo path. | Demo runs merchant payment to settlement and optional chargeback. | pending |
| `REC-03` | Vendor reconciliation. | Sumsub/Stripe retained reconciliation scripts pass or report honest gaps. | pending |
| `REC-04` | Dashboards. | Grafana dashboards show expected service/business metrics. | pending |
| `REC-05` | Cut register final audit. | No hidden fake implementations remain. | pending |

## 15. Cross-Phase Checks

| Check ID | What to verify | Expected result | Relevant phases |
|---|---|---|---|
| `AUD-99` | Sensitive read-audit remains enforced after all features. | All sensitive read paths write read-audit before data return. | 02, 07, 08, 10 |
| `LDG-99` | Ledger invariants after full demo. | Global debit/credit invariants and account derivations reconcile. | 03, 06, 09, 11 |
| `UI-99` | Role/resource isolation in browser. | End-user, merchant and backoffice actors cannot cross resource boundaries. | 02, 10 |

## 16. Retired Checks

None.

## 17. Linked ADRs

- `RUN-01`..`RUN-08` → ADR-003 selective services topology.
- `LDG-01`..`LDG-05`, `LDG-99` → ADR-002 SQL-first persistence; ADR-004 ledger central source of truth.
- `VLT-01`..`VLT-03`, `RUN-05` → ADR-005 SeaweedFS local object storage and vault isolation.
