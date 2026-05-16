# Agent Task — Phase 04 Slice 03 Merchant Dashboard Payments Planning

This file is temporary branch context for an implementation agent.
Do not merge this file back into the orchestration/main branch.

Branch: `task/p04s03-merchant-dashboard-api-planning`
Base branch: `orchestration`

## Important Rules

- You are an Executor, not the orchestrator.
- Read this file first, then read the files listed in "Read First".
- Do not use GitHub MCP, do not push, do not merge branches.
- Do not implement product code in this task.
- Do not edit unrelated approved baseline planning files.
- Do not touch the owner's default `mini-fintech-platform` Docker stack.
- Make one logical commit when done.

## Runtime Isolation

This is planning-only. You should not need Docker.

If you run Docker anyway, use only:

- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a2`
- `PLATFORM_BASE_URL=http://127.0.0.1:28181`
- `PLATFORM_HTTP_HOST_PORT=28181`
- `PLATFORM_DB_HOST_PORT=25433`
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
9. `planning/06_implementation_guide.md`
10. `planning/design-details/api_contracts.md`
11. `planning/design-details/ui_prototypes.md`
12. `planning/runtime_checklists.md`
13. `planning/implementation_status.md`
14. `planning/runtime_evidence_log.md`
15. `planning/implementation-slices/phase_04_slice_01_api_keys_idempotency_planning.md`
16. `planning/implementation-slices/phase_04_slice_02_stripe_connect_webhooks_planning.md`
17. Existing UI prototypes under `prototypes/ui/`

## Goal

Create a precise planning note for the next merchant-dashboard backend/API slice that can proceed without Stripe sandbox credentials and without frontend implementation.

Recommended scope:

```text
Merchant dashboard read APIs for API keys, payment intent list/detail shell, and webhook endpoint configuration model.
```

This should prepare backend support for future merchant UI screens while keeping frontend implementation gated by accepted prototypes.

## Scope

Create:

- `planning/implementation-slices/phase_04_slice_03_merchant_dashboard_payments_planning.md`

The note must define:

- exact backend/runtime scope;
- why this is useful before Stripe `MRC-01` is unblocked;
- dashboard routes to implement later;
- DB migration expectations, if any;
- relationship to existing `merchant.api_keys`, `merchant.payment_intents`, Stripe webhook tables and public API;
- runtime scripts/check IDs to add or extend;
- expected evidence updates;
- explicit out-of-scope items;
- risks/open questions if any.

Recommended boundaries:

- Merchant authenticated dashboard endpoints only under `/api/v1/merchant/**`.
- List/detail shell for existing `merchant.payment_intents`.
- Webhook endpoint configuration CRUD model can be planned, but not outbound delivery yet.
- API key management already exists; only plan read/list refinements if needed.
- No frontend implementation.
- No real Stripe Connect onboarding.
- No authorization/capture/refund/settlement behavior.

Target related checks:

- `MRC-03` remains covered by Slice 01.
- `WBH-01..WBH-03` are future Phase 06 delivery checks; this slice may prepare config only.
- Any new check IDs should be proposed conservatively.

## Explicitly Out Of Scope

- No Kotlin, SQL, runtime scripts or frontend implementation.
- No compose changes.
- No Stripe sandbox API calls.
- No payment authorization/capture/refund/settlement.
- No outbound merchant webhook delivery/retry/DLQ.
- No UI prototypes.
- Do not merge this branch.

## Expected Deliverables

- New planning note for Phase 04 Slice 03.
- Update `planning/implementation_status.md` only if needed to record a draft planning artifact, not runtime implementation.
- Update `CURRENT.md` only if handoff state materially changes.
- One logical commit.
- Leave `AGENT_TASK.md` in this branch only; it must not be merged later.
