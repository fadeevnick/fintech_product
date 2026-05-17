# Agent Task — Implement Phase 06 Slice 01 Outbound Merchant Webhooks

This file is temporary branch context for an implementation agent.
Do not merge this file back into the orchestration/main branch.

## Role

You are an Executor, not the orchestrator.

Implement one bounded backend/runtime slice:

```text
Phase 06 Slice 01 — Outbound Merchant Webhook Delivery Foundation
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
19. `planning/implementation-slices/phase_04_slice_03_merchant_dashboard_payments_planning.md`
20. `planning/implementation-slices/phase_06_slice_01_outbound_webhook_delivery_planning.md`

## Runtime Isolation

Use only this runtime slot:

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

Do not run `docker compose` without these variables.
Do not touch the owner's default `mini-fintech-platform` stack.

## Scope

Implement the approved planning contract:

- `planning/implementation-slices/phase_06_slice_01_outbound_webhook_delivery_planning.md`

Target behavior:

- Existing merchant webhook endpoint configuration becomes usable for real delivery.
- Webhook endpoint creation/rotation returns a signing secret once and stores only hash/prefix at rest.
- Creating a real payment-intent shell row creates exactly one `payment_intent.created` outbound event.
- Idempotent replay of payment-intent creation does not create duplicate outbound events.
- A real local HTTP receiver receives a signed webhook payload.
- The receiver validates the signature from raw body + timestamp.
- Delivery attempt state is persisted.

Recommended target:

- `WBH-01` — webhook signing/delivery.

`WBH-02` retry/DLQ may be included only if it remains narrow and real. Otherwise explicitly defer it.

## Ownership

Own this slice mainly in Platform:

- `product/apps/platform/src/main/kotlin/com/minifin/platform/merchant/webhooks/**`
- existing Platform public payment-intent creation code only for producing `payment_intent.created`
- Platform migration `V13__merchant_outbound_webhook_delivery.sql`
- `product/scripts/runtime/**` for retained Phase 06 webhook scripts and local test receiver
- factual docs/status/evidence files after verification

Do not move ownership to `acquirer` in this slice. The approved planning note explicitly keeps this first foundation in Platform because current payment-intent shell and endpoint config are Platform-hosted.

## Out Of Scope

- No Stripe Connect onboarding (`MRC-01`).
- No card authorization, capture, clearing, settlement, refunds, payouts or chargebacks.
- Do not fake `payment_intent.authorized`, `payment_intent.captured`, `payment_intent.refunded`, settlement or chargeback events.
- Do not claim `SET-*`, `PAY-*`, `MRC-01`, `CHB-*` or frontend checks.
- Do not change frontend SPA files or `prototypes/ui/**`.
- Do not implement Phase 05 card authorization in this branch.
- Do not store raw webhook signing secrets after one-time display.

## Runtime Checks

Add retained scripts:

- `product/scripts/runtime/lib_phase06_webhooks.sh`
- `product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh`

Optional only if retry/DLQ is implemented:

- `product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh`

Target checks:

- `WBH-01` — webhook signing/delivery.
- `WBH-02` only if real retry/DLQ is implemented and verified.

Run targeted regressions if touched:

- `MRC-05` — merchant webhook endpoint configuration remains scoped and role-protected.
- `PAY-01` — public API response shape remains valid.
- `PAY-02` — idempotency same-body replay still returns cached response.
- `PAY-03` — idempotency conflict still returns 409.
- `AUD-01` if secret lifecycle or delivery writes add audit rows.

## Evidence And Status

After runtime verification, update:

- `planning/runtime_evidence_log.md`
- `planning/implementation_status.md`
- `CURRENT.md`
- `product/README.md`
- `README.md` only if navigation/status materially changes

Do not overclaim. If `WBH-02` is not implemented, explicitly record it as deferred.

## Done

The slice is done only when:

- `WBH-01` retained script passes.
- Affected service builds and runs in the assigned runtime slot.
- Delivery is a real HTTP POST to a local receiver, not console logging.
- Runtime evidence is recorded.
- One logical implementation commit exists on your branch.
