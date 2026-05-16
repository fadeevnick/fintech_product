# Agent Task — Phase 04 Slice 02 — Stripe Connect Onboarding and Webhooks

This file is temporary branch context for an implementation agent.

Do not merge this file back into `orchestration`. It can be deleted before merge after the real slice artifacts/code are complete.

Branch: `task/p04s02-stripe-connect-webhooks`

Base assumption: this branch starts after Phase 03 wallet primitives through internal transfer are available.

## Important Rules

- Do not use GitHub MCP.
- Do not push to remote.
- Do not create PRs.
- Do not edit approved baseline docs unless the task explicitly requires it and the reason is recorded.
- Do not touch API key / public API idempotency work; another branch owns that.
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
15. Product runtime notes in `product/README.md`.

## Goal

Prepare and implement a narrow Phase 04 backend/runtime slice for Stripe Connect sandbox onboarding and inbound Stripe webhook handling.

## Scope

Create a slice planning note first, then implement only this backend/runtime scope:

- merchant can start Stripe Connect sandbox onboarding;
- persist Stripe account/onboarding state against the merchant;
- receive Stripe webhook events through a local endpoint;
- verify webhook signature;
- process webhook events idempotently;
- update merchant KYB/onboarding state once for duplicate events;
- audit onboarding start and webhook-driven state changes.

## Runtime Checks To Target

- `MRC-01` — Stripe Connect onboarding start.
- `MRC-02` — Stripe webhook signature and idempotency.
- `AUD-01` — onboarding/webhook state changes are audit logged.

## Explicitly Out Of Scope

- Merchant API key lifecycle (`MRC-03`).
- Public API idempotency checks (`PAY-01`, `PAY-02`, `PAY-03`).
- Payment intent authorization/capture/settlement.
- Frontend implementation.
- Real production Stripe configuration beyond sandbox/local test mode.
- Changes to approved baseline docs.
- GitHub MCP, remote push, PR creation.

## Expected Deliverables

- `planning/implementation-slices/phase_04_slice_02_stripe_connect_webhooks_planning.md`
- migrations and backend code needed for the narrow slice;
- retained runtime scripts under `product/scripts/runtime/`;
- documented local env vars / `.env.example` updates if Stripe sandbox credentials are required;
- updates to `planning/runtime_evidence_log.md`, `planning/implementation_status.md`, `CURRENT.md`, `README.md`, `product/README.md`;
- one logical commit on this branch after verification.

## Notes

- If real Stripe sandbox credentials are unavailable, stop at a documented blocker instead of faking Stripe behavior.
- Webhook signature verification must be real, not a bypass.
- Duplicate webhook event IDs must not move state twice.
- Keep this slice independent from API key/idempotency work where possible.
