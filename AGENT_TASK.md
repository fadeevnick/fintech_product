# Agent Task — Phase 05 Slice 01 Vault and Card Issuance Planning

This file is temporary branch context for an implementation agent.
Do not merge this file back into the orchestration/main branch.

Branch: `task/p05s01-vault-card-planning`
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

- `COMPOSE_PROJECT_NAME=mini-fintech-platform-a1`
- `PLATFORM_BASE_URL=http://127.0.0.1:18181`
- `PLATFORM_HTTP_HOST_PORT=18181`
- `PLATFORM_DB_HOST_PORT=15433`
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
19. Existing slice notes in `planning/implementation-slices/`

## Goal

Create the next precise implementation planning note for Phase 05 Slice 01:

```text
Vault tokenization foundation + minimal issuer card issuance backend/runtime slice.
```

## Scope

Create:

- `planning/implementation-slices/phase_05_slice_01_vault_card_issuance_planning.md`

The note must define:

- exact backend/runtime scope;
- why this is next after Phase 04;
- affected services/modules;
- DB migration expectations for `vault` and `issuer`;
- service-auth expectations and deliberate narrow cuts;
- API endpoints for the later coding slice;
- runtime scripts/check IDs to add;
- expected evidence updates;
- explicit out-of-scope items;
- risks/open questions if any.

Recommended boundaries:

- Vault stores PAN only in `vault` DB and returns opaque card token + last4 + expiration metadata.
- Issuer stores card record linked to end-user/wallet identity by token/last4 only, not PAN.
- End-user can issue a test card through Platform-facing API or an agreed service path.
- Non-issuer/non-authorized detokenize is denied.
- PAN must not appear in general app logs.
- No authorization/capture/settlement yet.

Target checks:

- `VLT-01`
- `VLT-02`
- `VLT-03`

Mention that `PAY-04` and `PAY-05` belong to the next authorization slice.

## Explicitly Out Of Scope

- No Kotlin, SQL, runtime scripts or frontend implementation.
- No compose changes.
- No authorization, capture, settlement, refunds, chargebacks or webhook delivery.
- No UI prototypes.
- Do not merge this branch.

## Expected Deliverables

- New planning note for Phase 05 Slice 01.
- Update `planning/implementation_status.md` only if needed to record a draft planning artifact, not runtime implementation.
- Update `CURRENT.md` only if handoff state materially changes.
- One logical commit.
- Leave `AGENT_TASK.md` in this branch only; it must not be merged later.
