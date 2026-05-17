# Agent Task — Phase 06 Slice 01 Outbound Merchant Webhooks Planning

This file is temporary branch context for an implementation agent.
Do not merge this file back into the orchestration/main branch.

Branch: `task/p06s01-outbound-webhooks-planning`
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

- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2`
- `PLATFORM_BASE_URL=http://127.0.0.1:28181`
- `PLATFORM_HTTP_HOST_PORT=28181`
- `PLATFORM_DB_HOST_PORT=25433`
- `ACQUIRER_HTTP_HOST_PORT=28182`
- `ACQUIRER_DB_HOST_PORT=25434`
- `NETWORK_HTTP_HOST_PORT=28183`
- `NETWORK_DB_HOST_PORT=25435`
- `ISSUER_HTTP_HOST_PORT=28184`
- `ISSUER_DB_HOST_PORT=25436`
- `VAULT_HTTP_HOST_PORT=28185`
- `VAULT_DB_HOST_PORT=25437`
- `KAFKA_HOST_PORT=29092`
- `KEYCLOAK_HOST_PORT=38080`

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
19. `planning/implementation-slices/phase_04_slice_03_merchant_dashboard_payments_planning.md`
20. `planning/implementation-slices/phase_05_slice_01_vault_card_issuance_planning.md`

## Goal

Create a precise implementation planning note for the first Phase 06 webhook slice:

```text
Phase 06 Slice 01 — outbound merchant webhook delivery foundation using existing merchant webhook endpoint configuration.
```

## Scope

Create:

- `planning/implementation-slices/phase_06_slice_01_outbound_webhook_delivery_planning.md`

The note must define:

- exact backend/runtime scope;
- why this can proceed after Phase 04 Slice 03 even while Stripe Connect onboarding remains blocked;
- affected services/modules and ownership boundaries;
- expected DB migrations and reserved migration names/numbers;
- event/outbox or delivery-attempt model;
- webhook signing model and one-time/hashed secret handling if a secret is needed;
- test receiver strategy for local runtime verification;
- delivery, retry and DLQ boundaries for the first slice;
- relationship to future capture/settlement events if those producers do not exist yet;
- runtime scripts/check IDs to add;
- expected evidence/status updates;
- explicit out-of-scope items;
- risks/open questions if any.

Recommended target checks:

- `WBH-01` — webhook signing/delivery.
- `WBH-02` — webhook retry/DLQ only if it can be implemented narrowly without fake payment lifecycle events; otherwise explicitly defer retry/DLQ to a later Phase 06 slice.

## Explicitly Out Of Scope

- No Kotlin, SQL, runtime scripts or frontend implementation.
- No Stripe Connect onboarding (`MRC-01`).
- No card authorization, capture, clearing, settlement, refund, payout or chargeback implementation.
- Do not fake payment lifecycle events as if capture/settlement already exists.
- Do not claim `SET-*`, `PAY-*`, `MRC-01`, `CHB-*` or frontend checks.
- Do not merge this branch.

## Expected Deliverables

- New planning note for Phase 06 Slice 01.
- Update `planning/implementation_status.md` only if needed to record the approved/draft planning artifact, not runtime implementation.
- Update `CURRENT.md` only if handoff state materially changes.
- One logical commit.
- Leave `AGENT_TASK.md` in this branch only; it must not be merged later.
