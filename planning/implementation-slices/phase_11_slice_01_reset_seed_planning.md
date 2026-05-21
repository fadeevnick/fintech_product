# Phase 11 Slice 01 — Reset and Seed — Planning Note

Status: **EXECUTED v0.1 — backend/runtime sub-scope implemented and runtime verified**.

This document fixes the next narrow backend/runtime slice after `Phase 07 Slice 05 — Compliance Read Audit`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 11 slice 01`:

```text
Implement REC-01 retained reset/seed scripts for local demo data.
Do not implement full demo journey, dashboards, vendor reconciliation, final cut-register audit or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should add:

1. `product/scripts/runtime/reg_phase11_reset_dev.sh`
   - destructive local reset script;
   - requires explicit confirmation env var before running;
   - uses the active `COMPOSE_FILE` / `COMPOSE_PROJECT_NAME`;
   - removes compose volumes and starts the backend/runtime services with existing images so migrations recreate schemas.
2. `product/scripts/runtime/reg_phase11_seed_dev.sh`
   - idempotent deterministic seed script;
   - creates three demo end users:
     - approved KYC;
     - in-review KYC;
     - rejected KYC;
   - creates two demo merchants:
     - onboarded/verified;
     - pending KYB;
   - creates a wallet ledger account and issued-card records for the approved user;
   - creates one sample merchant payment intent;
   - asserts expected rows exist after repeat execution.
3. Update runtime checklist `REC-01` to point at the retained scripts.

## 3. Runtime Verification

Target check:

- `REC-01` — local stack can be reset and seeded with deterministic demo data.

The retained verification should prove:

1. Reset script is syntactically valid and guarded by explicit confirmation.
2. Seed script is syntactically valid.
3. On a running local stack, seed script can run twice without duplicate-key failure.
4. Seeded row counts match expected demo actors and sample data.

Targeted regressions:

- `LDG-05` ledger reconciliation after seeded data.

## 4. Explicitly Out Of Scope

Do not implement:

- `REC-02` full demo path;
- `REC-03` vendor reconciliation;
- `REC-04` dashboards;
- `REC-05` final cut-register audit;
- frontend `UI-*`;
- real Stripe/Sumsub vendor calls.
