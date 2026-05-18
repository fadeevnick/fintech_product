# Agent Task — Implement Phase 07 Slice 01 KYC/Sumsub

You are working in `mini-fintech-platform` as an executor implementation agent.

## Context To Read First

Read these files before changing code:

- `/home/nickf/Documents/sre_projects/project-kit-short/project-method/`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`
- `planning/design-details/access_matrix.md`
- `planning/design-details/api_contracts.md`
- `planning/design-details/schema_drafts.md`
- `planning/design-details/state_machines.md`
- `planning/implementation-slices/phase_07_slice_01_kyc_sumsub_foundation_planning.md`

## Task

Implement the approved planning scope:

```text
Phase 07 Slice 01 — KYC/Sumsub Foundation
```

Target checks:

```text
KYC-01 — Sumsub KYC start.
KYC-02 — Sumsub webhook verification/idempotency.
```

## Implementation Scope

Implement only the approved backend/runtime scope:

- Add Platform migration `V16__kyc_sumsub_foundation.sql`.
- Add Platform KYC module/package under `com.minifin.platform.kyc`.
- Add KYC persistence for end-user applicant/profile/session/vendor-event state.
- Add end-user route:

```text
POST /api/v1/kyc/start
```

- Enforce authenticated, email-verified, active end-user access.
- Make KYC start idempotent for an already active applicant/session for the same user.
- Add a Sumsub adapter boundary:
  - call real Sumsub sandbox only when credentials are configured;
  - do not fake successful Sumsub applicant creation;
  - without credentials, return structured configuration/service error and record `KYC-01` as partial only.
- Add Sumsub webhook route:

```text
POST /webhooks/sumsub/v1
```

- Verify Sumsub-style HMAC signature before state mutation.
- Enforce vendor event id idempotency.
- Map minimal review events to internal KYC states per the approved planning note.
- Add retained runtime scripts:

```text
product/scripts/runtime/lib_phase07_kyc_sumsub.sh
product/scripts/runtime/reg_phase07_kyc_start.sh
product/scripts/runtime/reg_phase07_sumsub_webhook_signature_idempotency.sh
```

## Required Verification

Run and record evidence for:

- `KYC-01` as full pass only if real Sumsub sandbox credentials are configured and actually used;
- otherwise `KYC-01` partial, proving local gates/config behavior without faking vendor success;
- `KYC-02` using deterministic locally signed Sumsub-format webhook payloads, invalid signature rejection, and duplicate vendor event id no-op.

Run targeted regressions as needed by touched code:

- end-user auth/session smoke for KYC start route;
- wrong-role denial for merchant/backoffice callers if existing helpers make that practical;
- no wallet/card/payment/webhook regressions unless those code paths are touched.

If host port publishing is unreliable, use the established compose-network runner pattern and state that clearly in evidence.

If you use Docker Compose locally, use an isolated project/ports so you do not collide with other agents or the main stack. Suggested slot:

```bash
COMPOSE_PROJECT_NAME=mini-fintech-platform-a2
PLATFORM_HTTP_HOST_PORT=28181
PLATFORM_DB_HOST_PORT=25433
ACQUIRER_HTTP_HOST_PORT=28182
ACQUIRER_DB_HOST_PORT=25434
NETWORK_HTTP_HOST_PORT=28183
NETWORK_DB_HOST_PORT=25435
ISSUER_HTTP_HOST_PORT=28184
ISSUER_DB_HOST_PORT=25436
VAULT_HTTP_HOST_PORT=28185
VAULT_DB_HOST_PORT=25437
KAFKA_HOST_PORT=29092
KEYCLOAK_HOST_PORT=38080
```

Stop your isolated review/runtime stack after verification unless you are explicitly asked to leave it running.

## Documentation Updates

After successful verification, update:

- `planning/runtime_evidence_log.md`
- `planning/implementation_status.md`
- `CURRENT.md` if the next-step/handoff state changes
- `product/README.md` if new env vars or retained scripts need documentation

Evidence must include:

- whether real Sumsub sandbox credentials were used;
- actor used for KYC start;
- KYC profile/session/applicant ids persisted;
- webhook payload/signature/idempotency assertions;
- exact `KYC-01` result: `pass` or `partial`;
- exact `KYC-02` result;
- note that OpenSanctions, AML, document preview/read-audit, case attachments and frontend UI remain unclaimed.

## Boundaries

Do not implement:

- OpenSanctions;
- AML alerts/freezes/SoF;
- backoffice KYC queue or manual decisions;
- document preview/read-audit;
- case attachment upload or SeaweedFS document storage;
- wallet/card/payment gating changes beyond this slice's own KYC state;
- frontend code;
- fake Sumsub success path without credentials.

Do not create or change slice planning artifacts except to record factual implementation state/evidence after verification. Do not include temporary orchestration task files in your final commit.

## Commit

Make one logical implementation commit on your branch after verification.

Use a clear message, for example:

```text
Implement kyc sumsub foundation
```
