# Agent Task — Phase 05 Slice 02 Card Authorization Planning

This file is temporary branch context for an implementation agent.
Do not merge this file back into the orchestration/main branch.

Branch: `task/p05s02-card-authorization-planning`
Base branch: `orchestration`

## Important Rules

- You are an Executor, not the orchestrator.
- Read this file first, then read the files listed in "Read First".
- Do not use GitHub MCP, do not merge branches, do not launch other agents.
- Do not implement product code in this task.
- Do not edit unrelated approved baseline planning files.
- Make one logical commit when done.

## Runtime Isolation

This is planning-only. You should not need Docker.

If you run Docker anyway, use only:

- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1`
- `PLATFORM_BASE_URL=http://127.0.0.1:18181`
- `PLATFORM_HTTP_HOST_PORT=18181`
- `PLATFORM_DB_HOST_PORT=15433`
- `ACQUIRER_HTTP_HOST_PORT=18182`
- `ACQUIRER_DB_HOST_PORT=15434`
- `NETWORK_HTTP_HOST_PORT=18183`
- `NETWORK_DB_HOST_PORT=15435`
- `ISSUER_HTTP_HOST_PORT=18184`
- `ISSUER_DB_HOST_PORT=15436`
- `VAULT_HTTP_HOST_PORT=18185`
- `VAULT_DB_HOST_PORT=15437`
- `KAFKA_HOST_PORT=19092`
- `KEYCLOAK_HOST_PORT=28080`

Do not run compose without assigned variables.

## Read First

1. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/README.md`
2. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/guides/artifact-driven-project-guide.md`
3. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/guides/coding_slice_discipline_guide.md`
4. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/guides/parallel_agent_orchestration_guide.md`
5. `CURRENT.md`
6. `README.md`
7. `planning/03_functional_requirements.md`
8. `planning/04_architecture.md`
9. `planning/05_tech_stack.md`
10. `planning/06_implementation_guide.md`
11. `planning/design-details/access_matrix.md`
12. `planning/design-details/api_contracts.md`
13. `planning/design-details/schema_drafts.md`
14. `planning/design-details/state_machines.md`
15. `planning/design-details/test_matrix.md`
16. `planning/runtime_checklists.md`
17. `planning/implementation_status.md`
18. `planning/runtime_evidence_log.md`
19. `planning/implementation-slices/phase_04_slice_01_api_keys_idempotency_planning.md`
20. `planning/implementation-slices/phase_05_slice_01_vault_card_issuance_planning.md`

## Goal

Create a precise implementation planning note for the next Phase 05 slice:

```text
Phase 05 Slice 02 — card authorization path with Acquirer -> Network -> Issuer sync authorization, approved holds and structured declines.
```

## Scope

Create:

- `planning/implementation-slices/phase_05_slice_02_card_authorization_planning.md`

The note must define:

- exact backend/runtime scope;
- why this follows Phase 05 Slice 01;
- affected services/modules and ownership boundaries across `platform`, `acquirer`, `network`, `issuer`, and `vault`;
- expected DB migrations and reserved migration names/numbers per service;
- public/internal API endpoints and DTO/response shapes;
- payment-intent state transitions required for authorization only;
- ledger hold behavior for approved authorization;
- structured decline behavior for insufficient funds, blocked/frozen actor, inactive/unknown card token and service failures;
- service-auth expectations between services;
- runtime scripts/check IDs to add;
- expected evidence/status updates;
- explicit out-of-scope items;
- risks/open questions if any.

Recommended target checks:

- `PAY-04` — approved authorization hold.
- `PAY-05` — structured authorization declines.

## Explicitly Out Of Scope

- No Kotlin, SQL, runtime scripts or frontend implementation.
- No capture, clearing, settlement, refund, payout, chargeback or outbound merchant webhook delivery.
- No Stripe Connect onboarding (`MRC-01`).
- No UI prototypes or SPA implementation.
- Do not claim `SET-*`, `WBH-*`, `CHB-*` or frontend checks.
- Do not merge this branch.

## Expected Deliverables

- New planning note for Phase 05 Slice 02.
- Update `planning/implementation_status.md` only if needed to record the approved/draft planning artifact, not runtime implementation.
- Update `CURRENT.md` only if handoff state materially changes.
- One logical commit.
- Leave `AGENT_TASK.md` in this branch only; it must not be merged later.
