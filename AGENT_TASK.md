# Agent Task — Phase 04 Slice 01 — API Keys and Public API Idempotency

This file is temporary branch context for an implementation agent.

Do not merge this file back into `orchestration`. It can be deleted before merge after the real slice artifacts/code are complete.

Branch: `task/p04s01-api-keys-idempotency`

Base assumption: this branch starts after Phase 03 wallet primitives through internal transfer are available.

## Important Rules

- Do not use GitHub MCP.
- Do not push to remote.
- Do not create PRs.
- Do not edit approved baseline docs unless the task explicitly requires it and the reason is recorded.
- Do not touch Stripe Connect onboarding / Stripe webhook work; another branch owns that.
- Do not implement frontend code.
- Use the local project method in `project-method/`.

## Read First

Start with these files, in this order:

1. `project-method/README.md`
2. `project-method/guides/coding_slice_discipline_guide.md`
3. `project-method/templates/coding_slice_planning_note_template.md`
4. `CURRENT.md`
5. `README.md`
6. `planning/06_implementation_guide.md`
7. `planning/runtime_checklists.md`
8. `planning/implementation_status.md`
9. `planning/runtime_evidence_log.md`
10. `planning/design-details/api_contracts.md`
11. `planning/design-details/schema_drafts.md`
12. `planning/design-details/access_matrix.md`
13. Existing Phase 04 related implementation notes if present.
14. Existing Phase 02 merchant identity implementation under `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/`.
15. Existing wallet idempotency reference around `POST /api/v1/transfers`.
16. Product runtime notes in `product/README.md`.

## Goal

Prepare and implement a narrow Phase 04 backend/runtime slice for merchant API keys and public write idempotency.

## Scope

Create a slice planning note first, then implement only this backend/runtime scope:

- merchant dashboard API for API key lifecycle;
- API key generation, shown once;
- key hash/fingerprint stored at rest;
- key rotation and revocation;
- public API authentication using merchant API key;
- public write idempotency primitive for public API routes;
- a minimal public payment-intent creation shell if needed to prove idempotency and response shape, without card authorization/capture/settlement behavior.

## Runtime Checks To Target

- `MRC-03` — API key lifecycle.
- `PAY-01` — public API response shape `{data, errors}`.
- `PAY-02` — same idempotency key/body returns cached response.
- `PAY-03` — same idempotency key/different body returns 409.

## Explicitly Out Of Scope

- Stripe Connect onboarding and webhooks (`MRC-01`, `MRC-02`).
- Card authorization, capture, refunds, settlement or webhook delivery.
- Frontend implementation.
- Changes to approved baseline docs.
- GitHub MCP, remote push, PR creation.

## Expected Deliverables

- `planning/implementation-slices/phase_04_slice_01_api_keys_idempotency_planning.md`
- migrations and backend code needed for the narrow slice;
- retained runtime scripts under `product/scripts/runtime/`;
- updates to `planning/runtime_evidence_log.md`, `planning/implementation_status.md`, `CURRENT.md`, `README.md`, `product/README.md`;
- one logical commit on this branch after verification.

## Notes

- Prefer existing Kotlin/Spring/JdbcTemplate style.
- Keep API keys one-time-visible only.
- Do not store raw API keys.
- Use deterministic idempotency request fingerprinting and clear 409 conflict behavior.
- Keep this slice independent from Stripe Connect/webhook work where possible.
