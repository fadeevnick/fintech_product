# Agent Task — Implement Phase 06 Slice 02 WBH-02

You are working in `mini-fintech-platform` as an executor implementation agent.

## Context To Read First

Read these files before changing code:

- `/home/nickf/Documents/sre_projects/project-kit-short/project-method/`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`
- `planning/implementation-slices/phase_06_slice_02_outbound_webhook_retry_dlq_planning.md`
- `planning/implementation-slices/phase_06_slice_01_outbound_webhook_delivery_planning.md`

## Task

Implement the approved planning scope:

```text
Phase 06 Slice 02 — Outbound Webhook Retry/DLQ
```

Target check:

```text
WBH-02 — failed outbound webhook delivery retries and moves to DLQ.
```

## Implementation Scope

Implement only the approved `WBH-02` backend/runtime scope:

- Add Platform migration `V15__merchant_outbound_webhook_retry_dlq.sql`.
- Extend outbound webhook event/attempt persistence with retry scheduling and terminal DLQ state.
- Add configurable retry policy suitable for local runtime checks:
  - max attempts default: 3 total attempts per event/endpoint, including initial attempt;
  - short local retry delays suitable for retained scripts.
- Teach outbound webhook delivery to distinguish:
  - success -> `DELIVERED`;
  - retryable failure -> `FAILED` with persisted `next_retry_at`;
  - exhausted failure -> `DLQ`.
- Add a narrow dispatcher path for due retries.
- Extend the local webhook receiver/test helpers as needed for deterministic failing receiver behavior.
- Add retained runtime script:

```text
product/scripts/runtime/reg_phase06_webhook_retry_dlq.sh
```

## Required Verification

Run and record evidence for:

- `WBH-02` using a real failing HTTP receiver and DB assertions.

Run targeted regressions:

- `product/scripts/runtime/reg_phase06_webhook_signing_delivery.sh`
- `product/scripts/runtime/reg_phase04_merchant_webhook_config.sh`
- `product/scripts/runtime/reg_phase04_public_api_response_shape.sh`
- `product/scripts/runtime/reg_phase04_public_api_idempotency_replay.sh`
- `product/scripts/runtime/reg_phase04_public_api_idempotency_conflict.sh`

If host port publishing is unreliable, use the established compose-network runner pattern and state that clearly in evidence.

If you use Docker Compose locally, use an isolated project/ports so you do not collide with other agents or the main stack. Suggested slot:

```bash
COMPOSE_PROJECT_NAME=mini-fintech-platform-a1
PLATFORM_HTTP_HOST_PORT=18181
PLATFORM_DB_HOST_PORT=15433
ACQUIRER_HTTP_HOST_PORT=18182
ACQUIRER_DB_HOST_PORT=15434
NETWORK_HTTP_HOST_PORT=18183
NETWORK_DB_HOST_PORT=15435
ISSUER_HTTP_HOST_PORT=18184
ISSUER_DB_HOST_PORT=15436
VAULT_HTTP_HOST_PORT=18185
VAULT_DB_HOST_PORT=15437
KAFKA_HOST_PORT=19092
KEYCLOAK_HOST_PORT=28080
```

Stop your isolated review/runtime stack after verification unless you are explicitly asked to leave it running.

## Documentation Updates

After successful verification, update:

- `planning/runtime_evidence_log.md`
- `planning/implementation_status.md`
- `CURRENT.md` if the next-step/handoff state changes
- `product/README.md` if new env vars or retained scripts need documentation

Evidence must include:

- actor/preconditions;
- command(s) run;
- event ID / endpoint ID / attempt count / final `DLQ` assertion;
- receiver assertion showing repeated real HTTP attempts with valid signatures;
- targeted regression results;
- note that `WBH-03` remains unclaimed.

## Boundaries

Do not implement:

- `WBH-03` DLQ replay;
- merchant dashboard DLQ UI;
- new webhook event producers;
- capture, clearing, settlement, refunds, payouts or chargebacks;
- Stripe Connect onboarding;
- frontend code;
- service-boundary move to `acquirer`.

Do not create or change slice planning artifacts except to record factual implementation state/evidence after verification. Do not include temporary orchestration task files in your final commit.

## Commit

Make one logical implementation commit on your branch after verification.

Use a clear message, for example:

```text
Implement outbound webhook retry dlq
```
