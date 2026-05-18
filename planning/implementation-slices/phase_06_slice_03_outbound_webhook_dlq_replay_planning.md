# Phase 06 Slice 03 — Outbound Webhook DLQ Replay — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow backend/runtime slice after `Phase 06 Slice 02 — Outbound Webhook Retry and DLQ`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 06 slice 03`:

```text
Implement WBH-03 only: a merchant-admin can inspect DLQ webhook events and replay one retained DLQ event through the existing signed delivery path.
Do not implement frontend UI, new webhook event producers, payment lifecycle expansion, settlement, refunds, chargebacks or Stripe Connect onboarding.
```

This means:

- reuse the existing Platform-owned outbound webhook persistence from Phase 06 Slice 01 and retry/DLQ state from Phase 06 Slice 02;
- expose a merchant dashboard backend API for DLQ list/detail and single-event replay;
- ensure replay uses the same endpoint signing secret and headers as normal delivery;
- keep replay idempotent enough for operator retry: one replay request creates at most one replay attempt per call and does not mutate delivered events incorrectly;
- prove `WBH-03` with a real local receiver and database assertions.

## 2. Why This Slice Is Next

This slice is next because:

- `WBH-01` signed delivery is implemented and runtime-verified;
- `WBH-02` retry and terminal `DLQ` state are implemented and runtime-verified;
- `WBH-03` is the remaining webhook reliability checklist item in Phase 06;
- merchant dashboard webhook configuration exists, so a backend DLQ/replay API is useful before frontend implementation;
- the work can be implemented independently from compliance/KYC case work.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add merchant-authenticated dashboard routes under `/api/v1/merchant/webhook-events`.
2. Add DLQ list/detail behavior scoped to the authenticated merchant.
3. Add a replay command for a single DLQ event owned by the merchant.
4. Reuse the existing outbound delivery service so replay signs the same payload with the endpoint's current signing secret.
5. Persist replay attempts in `merchant.webhook_delivery_attempts`.
6. Update event state consistently:
   - successful replay moves event to `DELIVERED`;
   - failed replay keeps event in `DLQ` with latest failure metadata updated;
   - non-DLQ delivered events are not replayed by this slice.
7. Add retained runtime script `product/scripts/runtime/reg_phase06_webhook_dlq_replay.sh`.
8. Run targeted regressions for `WBH-01`, `WBH-02`, `MRC-05` and `PAY-01..PAY-03`.

## 4. Planned API

Merchant session routes:

```http
GET  /api/v1/merchant/webhook-events?status=DLQ
GET  /api/v1/merchant/webhook-events/{id}
POST /api/v1/merchant/webhook-events/{id}/replay
```

Access rules:

- list/detail: `merchant_admin` and `merchant_member`;
- replay: `merchant_admin` only;
- cross-merchant event access returns `404 not_found`;
- replay of non-`DLQ` events returns `409 invalid_state`.

Recommended replay response:

```json
{
  "data": {
    "eventId": "uuid",
    "status": "DELIVERED",
    "replayAttemptNumber": 4,
    "httpStatus": 200
  },
  "errors": []
}
```

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V17__merchant_webhook_dlq_replay.sql
```

Add only minimal metadata if the implementation needs to distinguish automatic retry attempts from manual replay attempts. Recommended default:

```sql
alter table merchant.webhook_delivery_attempts
    add column trigger_type text not null default 'AUTO'
        check (trigger_type in ('AUTO','MANUAL_REPLAY'));
```

No new service DBs or non-Platform migrations are expected.

## 6. Runtime Verification

Target check:

- `WBH-03` — retained failed event can be replayed.

The retained script should:

1. Create merchant, API key and webhook endpoint.
2. Create a payment intent that emits `payment_intent.created`.
3. Use a local receiver configured to fail until the event reaches `DLQ`.
4. Switch receiver to success.
5. Call the merchant-admin replay API.
6. Assert:
   - replay request is authorized for `merchant_admin`;
   - `merchant_member` cannot replay;
   - event transitions from `DLQ` to `DELIVERED` after successful replay;
   - latest delivery attempt has valid signature headers and `trigger_type = MANUAL_REPLAY` if that column is added;
   - cross-merchant replay is denied/hidden.

Targeted regressions:

- `WBH-01`;
- `WBH-02`;
- `MRC-05`;
- `PAY-01`;
- `PAY-02`;
- `PAY-03`.

## 7. Explicitly Out Of Scope

Do not implement:

- merchant frontend DLQ UI;
- backoffice webhook operations UI;
- bulk replay;
- automatic replay of DLQ events;
- replay of non-DLQ delivered events;
- new event producers beyond `payment_intent.created`;
- capture, settlement, refund, payout or chargeback lifecycle;
- Stripe Connect onboarding (`MRC-01`);
- moving webhook ownership to `acquirer`;
- Kafka delivery refactor.

Do not claim:

- `SET-*`;
- `CHB-*`;
- `MRC-01`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- Platform merchant/outbound-webhook backend code;
- Platform migration `V17__merchant_webhook_dlq_replay.sql`;
- retained Phase 06 replay runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit KYC/backoffice compliance code except for incidental imports/build fixes.
