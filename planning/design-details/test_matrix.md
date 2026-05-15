# Test Matrix — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

---

## 1. Purpose

This artifact maps functional areas to test and runtime verification expectations.

Runtime check IDs are finalized in `planning/runtime_checklists.md`.

## 2. Phase-Level Test Strategy

| Phase | Focus | Verification style |
|---:|---|---|
| 01 | Skeleton/runtime | Docker Compose health, migrations, metrics/logs/traces smoke |
| 02 | Identity/RBAC/audit | API + DB assertions + OIDC flow |
| 03 | Ledger/wallet | API + DB ledger invariant checks |
| 04 | Merchant/API/idempotency | Public API smoke + Stripe sandbox webhook |
| 05 | Vault/card/auth | Service path + log masking + ledger hold checks |
| 06 | Settlement/webhooks | Kafka/outbox + reconciliation + DLQ replay |
| 07 | KYC/sanctions/cases | Real sandbox/API integration + fail-closed checks |
| 08 | AML/freezes/SoF | Synthetic data rule trips + RBAC checks |
| 09 | Chargebacks | End-to-end dispute state + ledger movements |
| 10 | SPAs | Playwright for critical workflows |
| 11 | Hardening/demo | retained reconciliation scripts + dashboards |

## 3. Initial Check ID Prefixes

| Prefix | Area |
|---|---|
| `RUN` | Local runtime/skeleton |
| `AUTH` | Identity/session/RBAC |
| `AUD` | Audit/read-audit |
| `LDG` | Ledger invariants |
| `WLT` | Wallet/manual operations |
| `MRC` | Merchant onboarding/API keys |
| `PAY` | Payment intent/auth/capture |
| `VLT` | Vault/card data isolation |
| `SET` | Clearing/settlement/refunds |
| `WBH` | Webhook delivery/DLQ |
| `KYC` | KYC/Sumsub |
| `SNX` | Sanctions/OpenSanctions |
| `AML` | AML/freeze |
| `CHB` | Chargeback |
| `UI` | Browser/UI workflows |

## 4. Phase 01 Candidate Runtime Checks

| Check ID | Description |
|---|---|
| `RUN-01` | All backend service health endpoints return healthy. |
| `RUN-02` | All service databases run initial Flyway migrations. |
| `RUN-03` | Kafka is reachable from a backend worker process. |
| `RUN-04` | Keycloak reachable from Platform config. |
| `RUN-05` | SeaweedFS S3 endpoint reachable from object storage adapter config. |
| `RUN-06` | One service emits structured log visible in Loki. |
| `RUN-07` | One service emits Micrometer metric visible to Prometheus. |
| `RUN-08` | One trace reaches Tempo. |
| `UI-01` | Three SPA shells build and are served by nginx containers. |

## 5. Resolution Notes

1. **Phase 01 observability pass criteria** — require logs and metrics to pass. Traces may be recorded as `pass-indirect` in the first skeleton slice if Tempo wiring needs a follow-up slice; this must be explicit in runtime evidence.
