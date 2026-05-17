# Agent Task — Implement Phase 05 Slice 02 Card Authorization

This file is temporary branch context for an implementation agent.
Do not merge this file back into the orchestration/main branch.

## Role

You are an Executor, not the orchestrator.

Implement one bounded backend/runtime slice:

```text
Phase 05 Slice 02 — Card Authorization Path
```

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
19. `planning/implementation-slices/phase_05_slice_01_vault_card_issuance_planning.md`
20. `planning/implementation-slices/phase_05_slice_02_card_authorization_planning.md`

## Runtime Isolation

Use only this runtime slot:

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

Do not run `docker compose` without these variables.
Do not touch the owner's default `mini-fintech-platform` stack.

## Scope

Implement the approved planning contract:

- `planning/implementation-slices/phase_05_slice_02_card_authorization_planning.md`

Target behavior:

- Public merchant authorization reaches Acquirer -> Network -> Issuer synchronously.
- Approved authorization creates a balanced Platform Ledger hold.
- Approved authorization transitions the payment intent to `AUTHORIZED`.
- Structured declines are returned for insufficient funds, blocked/frozen actor, inactive/unknown card token and service failure cases.
- Declines and service failures must not create ledger holds.
- Existing Phase 04 public API response shape/idempotency behavior must not regress.

## Ownership

Own this slice across:

- `product/apps/acquirer/**`
- `product/apps/network/**`
- `product/apps/issuer/**` authorization/hold additions only
- `product/apps/platform/**` only for narrow internal wallet/ledger/actor-control APIs or temporary public API bridge if required
- `product/backend/libs/contracts-internal/**` if shared DTOs are needed
- `product/scripts/runtime/**` for retained Phase 05 authorization scripts
- factual docs/status/evidence files after verification

Expected migrations:

- `product/apps/acquirer/src/main/resources/db/migration/V2__payment_authorization_foundation.sql`
- `product/apps/network/src/main/resources/db/migration/V2__authorization_routing_foundation.sql`
- `product/apps/issuer/src/main/resources/db/migration/V3__card_authorization_and_holds.sql`
- Platform migration `V13__card_authorization_hold_support.sql` only if current ledger/wallet schema cannot support holds.

## Out Of Scope

- No capture, clearing, settlement, refunds, payouts, chargebacks or outbound merchant webhooks.
- Do not claim `SET-*`, `WBH-*`, `CHB-*`, `AML-*`, `KYC-*`, `SNX-*` or frontend checks.
- Do not implement Stripe Connect onboarding (`MRC-01`).
- Do not change frontend SPA files or `prototypes/ui/**`.
- Do not store PAN outside Vault.
- Do not fake authorization success without a real ledger hold.
- Do not implement Phase 06 webhook delivery in this branch.

## Runtime Checks

Add retained scripts:

- `product/scripts/runtime/lib_phase05_card_authorization.sh`
- `product/scripts/runtime/reg_phase05_authorization_approved_hold.sh`
- `product/scripts/runtime/reg_phase05_authorization_structured_declines.sh`

Target checks:

- `PAY-04` — approved authorization hold.
- `PAY-05` — structured authorization declines.

Run targeted regressions if touched:

- `VLT-01` / `VLT-02` spot check or card issuance prerequisite path.
- `PAY-01`, `PAY-02`, `PAY-03` if public payment API/idempotency changes.
- `LDG-05` or `LDG-99` after approved and declined authorization paths.
- `RUN-01` for affected services.

## Evidence And Status

After runtime verification, update:

- `planning/runtime_evidence_log.md`
- `planning/implementation_status.md`
- `CURRENT.md`
- `product/README.md`
- `README.md` only if navigation/status materially changes

Do not overclaim. If any runtime check does not pass, record it as partial/fail with the exact blocker.

## Done

The slice is done only when:

- `PAY-04` and `PAY-05` retained scripts pass.
- Affected services build and run in the assigned runtime slot.
- Runtime evidence is recorded.
- One logical implementation commit exists on your branch.
