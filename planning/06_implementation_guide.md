# 06 Implementation Guide — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

History:
- v0.1 — first implementation guide draft from approved baseline 01..05.
- v0.2 — open questions resolved by owner: keep payment lifecycle before compliance integrations; Phase 01 uses full-shape skeleton for all five services and core infra with no domain behavior; create early SPA deployment shells, with real UI workflows delayed to Phase 10.

Inputs:
- `planning/01_business_requirements.md` — APPROVED v0.3.
- `planning/02_user_journeys.md` — APPROVED v0.2.
- `planning/03_functional_requirements.md` — APPROVED v0.2.
- `planning/04_architecture.md` — APPROVED v0.2.
- `planning/05_tech_stack.md` — APPROVED v0.4.

---

## 1. Purpose

This guide is the phase-level implementation plan for Mini Fintech Platform.

It does **not** replace:
- `planning/design-details/*` — implementation-near contracts, schemas, state machines, tests, cuts, ADRs.
- `planning/runtime_checklists.md` — stable runtime check ID registry.
- `planning/implementation-slices/*` — exact per-slice coding scope.
- `planning/implementation_status.md` — factual implementation/runtime state.
- `planning/runtime_evidence_log.md` — append-only runtime evidence.

This guide answers:
- what phases exist;
- what each phase must prove;
- what should be built first;
- what is explicitly deferred;
- where runtime evidence must appear before a phase can be treated as complete.

## 2. Implementation Strategy

The project is too large to implement by bounded context in isolation. The correct path is **vertical runtime slices** that progressively connect real persistence, auth, ledger, async messaging, external adapters and UIs.

Ordering principles:
- Build infrastructure and cross-cutting foundations before domain breadth.
- Make ledger and audit trustworthy before adding complex payment flows.
- Prefer one narrow production-working path per phase over broad unverified surface area.
- Keep vendor integrations real sandbox integrations, not mocks.
- Treat manual operations as legitimate product behavior where approved by 01/02.
- Do not create fake provider paths or in-memory persistence.

Implementation stack baseline:
- Backend: Kotlin + Java 21 LTS + Spring Boot 3.5.x.
- Persistence: PostgreSQL 18, Flyway, jOOQ + Spring JDBC/JdbcClient + raw SQL.
- Async: Apache Kafka KRaft, Spring for Apache Kafka, JSON payloads with strict topic versioning.
- Frontend: React 19 + Vite 8, three separate SPAs.
- Object storage: SeaweedFS S3 API locally, AWS S3-compatible adapter boundary.
- OIDC: Keycloak 26.x for backoffice.
- Observability: OTel Java + Micrometer + Prometheus/Grafana/Tempo/Loki/Vector.

## 3. Required Pre-Code Artifacts

Before creating `product/` or any implementation code, create:

1. `planning/design-details/access_matrix.md`
2. `planning/design-details/api_contracts.md`
3. `planning/design-details/state_machines.md`
4. `planning/design-details/schema_drafts.md`
5. `planning/design-details/ui_prototypes.md`
6. `planning/design-details/test_matrix.md`
7. `planning/design-details/cut_register.md`
8. `planning/design-details/adr/`
9. `planning/runtime_checklists.md`
10. `planning/implementation-slices/`

Minimum ADRs expected before first coding slice:
- ADR-001 — JVM/Spring/Kotlin backend baseline.
- ADR-002 — SQL-first persistence: jOOQ/JdbcClient/raw SQL over ORM-first.
- ADR-003 — Selective services deployment topology.
- ADR-004 — Ledger as central source of truth with sync internal API.
- ADR-005 — SeaweedFS S3 local object storage, LocalStack optional.

After these exist, create the first slice planning note:

```text
planning/implementation-slices/phase_01_slice_01_product_skeleton_planning.md
```

Only then create `product/`.

## 4. Phase Overview

| Phase | Name | Primary outcome |
|---:|---|---|
| 00 | Implementation-near planning pack | Converts baseline into contracts/check IDs before code. |
| 01 | Product skeleton and local runtime | Monorepo, services, Compose infra, migrations, health and observability baseline. |
| 02 | Identity, RBAC, sessions and audit | Real login/session/RBAC for end-user, merchant, backoffice OIDC; append-only audit. |
| 03 | Ledger and wallet manual operations | Double-entry ledger, wallet, manual deposit/withdraw, transfer, two-eyes skeleton. |
| 04 | Merchant onboarding and public API foundation | Merchant model, API keys, idempotency, Stripe Connect sandbox, payment intent shell. |
| 05 | Vault, card issuance and authorization | SeaweedFS for docs, Vault isolation, card issue, sync acquirer-network-issuer auth path. |
| 06 | Clearing, settlement, refunds and webhooks | Kafka/outbox-driven capture, clearing, settlement, fee split, refund, webhook delivery. |
| 07 | Compliance integrations and case management | Sumsub, OpenSanctions, case queues, object attachments, read-audit. |
| 08 | AML, freezes, SoF and high-value controls | AML rules, account freezes, Source of Funds, high-value manual operation controls. |
| 09 | Chargeback lifecycle | End-user dispute, merchant evidence, backoffice arbitration, ledger movements. |
| 10 | Three SPAs completion pass | End-user, merchant and backoffice workflows cover MVP journeys. |
| 11 | Reconciliation, hardening and portfolio demo | Retained checks, dashboards, runbooks, demo data, cut register finalization. |

## 5. Phase 00 — Implementation-Near Planning Pack

### Goal

Create the artifacts that make the first coding slice exact and runtime-verifiable.

### Scope

Create:
- access matrix for all roles and service identities;
- API contracts for public API, SPA APIs, internal APIs and vendor webhooks;
- state-machine tables for payment, auth, KYC, sanctions, AML, withdraw/deposit, payout and chargeback;
- schema drafts per service/schema;
- UI/workflow prototypes for end-user web, merchant dashboard and backoffice workflow UI;
- test matrix mapping FR IDs to runtime checks;
- cut register with explicit `cut`, `manual`, `simple production` classification;
- ADRs for the high-cost decisions listed in §3;
- runtime checklist registry with stable check IDs.

### Exit Criteria

Phase 00 exits when:
- all files in §3 exist;
- `planning/runtime_checklists.md` has initial check IDs for phases 01-03 at minimum;
- first coding slice planning note exists;
- no `product/` code has been created yet.

## 6. Phase 01 — Product Skeleton and Local Runtime

### Goal

Build the smallest real local runtime that can host future slices.

### Scope

Implement:
- Gradle multi-project backend skeleton;
- pnpm workspace frontend skeleton;
- five Spring Boot application shells: `platform`, `acquirer`, `network`, `issuer`, `vault`;
- Docker Compose with PostgreSQL per service, Kafka KRaft, Keycloak, SeaweedFS, Traefik, observability stack and nginx SPA containers;
- Flyway migration wiring per service;
- health endpoints and build/test tasks;
- structured JSON logging baseline;
- Prometheus/Micrometer metrics baseline;
- OTel trace export baseline.

### Explicitly Out

Do not implement:
- business domains;
- real auth flows;
- ledger;
- payment lifecycle;
- vendor integrations beyond health/config checks.

### Exit Criteria

Runtime checks must prove:
- all backend services start and expose health endpoints;
- all service DBs accept migrations;
- Kafka is reachable from at least one backend worker path;
- Keycloak and SeaweedFS are reachable from application config;
- Grafana can see at least metrics/logs/traces from one service;
- SPAs build and are served by nginx containers.

## 7. Phase 02 — Identity, RBAC, Sessions and Audit

### Goal

Create trustworthy identity and audit foundations before money movement.

### Scope

Implement:
- end-user registration, email verification and login;
- merchant employee registration and login;
- server-side opaque sessions and cookies;
- backoffice OIDC login via Keycloak;
- role mapping and server-side RBAC middleware;
- global email uniqueness across user pools;
- audit log append-only table and write path;
- read-audit primitive for compliance-sensitive reads;
- basic account block/freeze enforcement hook.

### Exit Criteria

Runtime checks must prove:
- end user can register, verify email and log in;
- merchant employee can register and log in;
- backoffice user can log in through Keycloak;
- same email cannot register across pools;
- protected endpoints reject unauthenticated and wrong-role actors;
- audit entries are inserted for auth/session state changes;
- audit table cannot be updated/deleted through app path.

## 8. Phase 03 — Ledger and Wallet Manual Operations

### Goal

Make ledger trustworthy and connect it to wallet operations.

### Scope

Implement:
- ledger accounts, journal entries, postings, balance derivation;
- DB-level and application-level balanced-entry enforcement;
- append-only ledger protections;
- wallet account creation for end users;
- manual deposit request and operator confirmation;
- manual withdraw request, hold and operator completion;
- internal transfer end-user to end-user;
- high-value operation state placeholders for two-eyes;
- retained ledger reconciliation script.

### Exit Criteria

Runtime checks must prove:
- invalid unbalanced journal entry is rejected;
- valid deposit creates correct postings and derived balance;
- valid withdraw creates hold and final debit flow;
- internal transfer moves value atomically between wallets;
- frozen/blocked account cannot perform wallet writes;
- reconciliation script passes for seeded ledger state.

## 9. Phase 04 — Merchant Onboarding and Public API Foundation

### Goal

Create merchant-facing platform surface before full card-network simulation.

### Scope

Implement:
- merchant entity and employee association;
- Stripe Connect sandbox account link flow;
- Stripe webhook receiver with signature/idempotency;
- API key generation, rotation and revocation;
- idempotency middleware for public write endpoints;
- public Payments API host shape (`api.miniefin.local`);
- payment intent creation shell with lifecycle state but without card authorization settlement path;
- merchant dashboard read shell for onboarding/API keys/payment list.

### Exit Criteria

Runtime checks must prove:
- merchant can register and start Stripe Connect onboarding;
- Stripe sandbox webhook updates merchant KYB state;
- API key is shown once, hashed/fingerprinted at rest, and revoked keys fail;
- idempotency returns cached response for same key/body and 409 for same key/different body;
- public API responses follow `{data, errors}` contract.

## 10. Phase 05 — Vault, Card Issuance and Authorization

### Goal

Build the first card payment fast path: card issue and sync authorization.

### Scope

Implement:
- Vault service with isolated DB and tokenization;
- PAN encryption via `pgcrypto` or equivalent approved design;
- issuer card records and card state machine;
- end-user card issuance flow;
- restricted service-token auth for issuer to vault;
- sync Acquirer → Network → Issuer authorization path;
- issuer balance check and hold posting through Platform Ledger internal API;
- authorization decline reasons.

### Exit Criteria

Runtime checks must prove:
- card issuance stores PAN only in Vault and last4/token outside Vault;
- non-issuer service cannot detokenize;
- approved authorization places a hold through ledger;
- insufficient funds/card blocked/expired produce structured declines;
- PAN does not appear in application logs;
- authorization P95 target is measured on local path, even if early numbers are recorded as baseline not guarantee.

## 11. Phase 06 — Clearing, Settlement, Refunds and Webhooks

### Goal

Turn authorization into full payment lifecycle with async clearing, settlement, fee split and merchant webhooks.

### Scope

Implement:
- capture endpoint and payment state transition;
- outbox events for captures;
- Kafka clearing event consumption in Network;
- settlement position calculation;
- ledger postings for cardholder, merchant settlement, issuer interchange, network assessment, acquirer margin;
- acquirer merchant settlement projection;
- refund flow with partial refund support;
- outbound webhook signing, retry and DLQ;
- replay from DLQ.

### Exit Criteria

Runtime checks must prove:
- captured payment reaches settlement through Kafka/outbox path;
- fee split postings balance exactly;
- acquirer settlement projection reconciles with Platform Ledger;
- partial refund cannot exceed original payment amount;
- webhook delivery signs payloads and retries failures;
- DLQ replay can redeliver a retained failed event.

## 12. Phase 07 — Compliance Integrations and Case Management

### Goal

Add real KYC/sanctions integrations and unified case queues.

### Scope

Implement:
- Sumsub applicant creation, submission and webhook handling;
- KYC state machine and manual review queue;
- OpenSanctions sync screening with fail-closed behavior;
- sanctions hit cases and cleared exceptions;
- unified case management core;
- S3-compatible attachments via SeaweedFS for KYC/case evidence;
- read-audit for compliance-sensitive reads.

### Exit Criteria

Runtime checks must prove:
- email-verified user can start KYC through real Sumsub sandbox path;
- Sumsub webhook signature and idempotency are enforced;
- sanctions timeout/failure fails closed and opens a case;
- false-positive sanctions clearance creates exception and suppresses repeat block for same pair;
- case attachment upload creates object and case reference;
- compliance-sensitive read produces read-audit before data is returned.

## 13. Phase 08 — AML, Freezes, SoF and High-Value Controls

### Goal

Add transaction monitoring and manual-control realism.

### Scope

Implement:
- AML rule engine for velocity, structuring and dormancy-break;
- AML alert cases and review decisions;
- critical alert auto-freeze;
- account freeze/unfreeze flow;
- Source of Funds declaration for deposits > EUR 10k;
- banking statement upload threshold;
- two-eyes flow for high-value deposits/withdraws;
- senior compliance override with strong audit.

### Exit Criteria

Runtime checks must prove:
- each MVP AML rule can trip on synthetic data;
- critical rule auto-freezes account and blocks writes;
- false-positive review can close alert and unfreeze when appropriate;
- deposit > EUR 10k requires SoF before instructions;
- high-value manual operation cannot be completed by the same operator twice;
- senior override requires justification and writes audit.

## 14. Phase 09 — Chargeback Lifecycle

### Goal

Implement full dispute lifecycle across end user, issuer, network, acquirer, merchant and backoffice.

### Scope

Implement:
- end-user chargeback initiation within window;
- issuer provisional credit;
- chargeback message routing issuer → network → acquirer;
- merchant notification and webhook;
- merchant evidence submission with attachments;
- backoffice arbitration outcome;
- ledger movements for WON/LOST/merchant accepted/deadline expired;
- chargeback rate metric.

### Exit Criteria

Runtime checks must prove:
- cardholder can dispute eligible transaction;
- provisional credit posts correctly;
- merchant receives dispute and can submit evidence;
- arbitration WON reverses provisional credit and releases merchant funds correctly;
- arbitration LOST makes cardholder credit permanent and debits merchant correctly;
- chargeback rate metric appears in dashboard/metrics endpoint.

## 15. Phase 10 — Three SPAs Completion Pass

### Goal

Make the three user-facing apps cover MVP journeys end to end.

### Scope

Complete:
- end-user web: registration, KYC, wallet, deposit/withdraw, transfer, card, history, dispute initiation;
- merchant dashboard: onboarding, API keys, payment/refund list, webhook config/DLQ, settlements, disputes/evidence;
- backoffice workflow UI: queues for KYC, AML, sanctions, manual ops, SoF, chargebacks, frozen accounts, audit log;
- shared UI components and API clients;
- UX for access denials and frozen/blocked states.

### Exit Criteria

Runtime checks must prove:
- each role can log in through correct auth flow;
- each role cannot access another role's resources;
- core MVP journeys can be completed through UI, not only API scripts;
- UI does not expose PAN except approved reveal path;
- Playwright screenshots/evidence exist for critical screens.

## 16. Phase 11 — Reconciliation, Hardening and Portfolio Demo

### Goal

Turn working slices into a demonstrable, resumable portfolio system with honest cuts.

### Scope

Implement or finalize:
- retained reconciliation scripts for ledger, Stripe, Sumsub, acquirer projection;
- seeded demo scenarios;
- Grafana dashboards for payment success, KYC throughput, AML alerts, chargeback rate, webhook delivery, outbox lag, vault detokenize rate;
- runbooks for local setup, reset, seed, demo flows;
- cut register final pass;
- failure-mode checks for Kafka down, provider timeout, webhook failure, vault unreachable;
- README/product README separation.

### Exit Criteria

Runtime checks must prove:
- full local stack can be reset and seeded;
- demo path runs from merchant payment to settlement and optional chargeback;
- retained reconciliation scripts pass after demo data;
- dashboards show expected service/business metrics;
- cut register has no hidden fake implementations;
- implementation status and runtime evidence log accurately reflect final state.

## 17. First Coding Slice Recommendation

After Phase 00 artifacts are complete, first slice should be:

```text
phase_01_slice_01_product_skeleton
```

Decision:

```text
Create product skeleton with Gradle backend workspace, pnpm frontend workspace, empty service shells, health endpoints, and minimal Docker Compose dependencies.
```

Why first:
- every future slice depends on repo layout, build tooling and local runtime;
- it does not require domain schema depth beyond health/migration baseline;
- it gives early feedback on JVM/Spring/Kotlin/Gradle + Compose compatibility.

Explicitly out:
- identity, ledger, payments, vendor integrations, UIs beyond empty shells.

## 18. Runtime Evidence Policy

Every phase exit requires:
- check IDs in `planning/runtime_checklists.md`;
- runtime evidence entries in `planning/runtime_evidence_log.md`;
- factual state updates in `planning/implementation_status.md`;
- retained scripts under `product/scripts/runtime/reg_*` where checks are expected to be rerun;
- honest result tags: `pass`, `pass-indirect`, `partial`, `fail`.

No phase is complete because code exists. A phase is complete only when its exit checks have recorded evidence.

## 19. Cut / Manual / Simple Production Policy

Every scoped capability must be one of:

| Form | Meaning | Where recorded |
|---|---|---|
| `simple production` | Real working implementation, possibly minimal. | Design details + runtime evidence. |
| `manual` | Real workflow completed by operator/backoffice/provider dashboard. | Design details + runbook + runtime evidence. |
| `cut` | Not implemented in MVP. | `planning/design-details/cut_register.md`. |

Forbidden:
- fake provider adapters;
- in-memory persistent data;
- hardcoded success responses;
- console logging instead of actual event/email/webhook/provider behavior.

## 20. Resolution Notes

1. **Phase ordering** — owner accepted recommendation: payment lifecycle remains before compliance integrations. Rationale: ledger/card/acquirer/network learning path stays coherent; compliance then attaches to real money-moving events instead of blocking the first payment lifecycle baseline.
2. **First coding phase scope** — owner accepted recommendation: Phase 01 creates a full-shape skeleton for all five application services plus core infra, but proves only health/migrations/config/logging/observability. Domain behavior stays out of Phase 01.
3. **UI timing** — owner accepted recommendation: Phase 01 creates early SPA deployment shells for all three SPAs. These are build/serve/routing shells only, not real UI workflows. Real workflow screens remain Phase 10.
4. **Open questions** — all v0.1 Open Questions resolved; 06 is ready for explicit owner approval.
