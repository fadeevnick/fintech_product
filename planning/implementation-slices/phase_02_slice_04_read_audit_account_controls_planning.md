# Phase 02 Slice 04 — Read-Audit Primitive and Account Control Hook — Planning Note

Status: **BACKEND/RUNTIME SUB-SCOPE EXECUTED v0.2; frontend not in scope**.

This document fixes the fourth implementation slice inside `Phase 02 — Identity, RBAC, Sessions and Audit`.

It is a pre-code scope contract. It is not implementation status and not runtime evidence.

Execution note:
- The backend/domain/runtime portion was approved with `го`, implemented and verified on 2026-05-16.
- No frontend implementation was in scope.

---

## 1. Decision

`Phase 02 slice 04`:

```text
Implement the Platform read-audit primitive for compliance-sensitive reads and a generic actor/account control hook that can block future wallet/payment writes when an end user or merchant is blocked/frozen.
```

This means:

- Phase 02 finishes the non-money identity/audit foundation before Phase 03 ledger/wallet work.
- This slice does not implement KYC, sanctions, AML, manual ops queues, account freeze UI or real wallet writes.
- It creates reusable backend primitives and runtime proof points:
  - synchronous read audit is written before returning sensitive data;
  - blocked/frozen actor state can be checked and denies a protected write-like action.
- Future phases plug real sensitive reads and wallet/payment writes into these primitives instead of inventing new behavior.

## 2. Why This Slice Is Next

This slice is next because:

- `planning/06_implementation_guide.md` includes Phase 02 scope items:
  - read-audit primitive for compliance-sensitive reads;
  - basic account block/freeze enforcement hook.
- `planning/design-details/access_matrix.md` defines synchronous read-audit requirements for sensitive reads.
- `planning/runtime_checklists.md` carries cross-phase `AUD-99` for read-audit persistence, and later `WLT-02`, `PAY-05`, `AML-04` depend on a real block/freeze hook.
- Slices 01-03 already implemented end-user, merchant and backoffice identity/RBAC/audit foundations.
- Ledger/wallet work should not start until this authorization/control layer has a concrete backend shape.

## 3. Exact Scope

In this slice:

1. Add Platform DB migration for:
   - read-audit records, or a typed read-audit event convention in `audit.audit_log`;
   - actor/account control records for end users and merchants.
2. Implement a read-audit application service that:
   - writes audit data synchronously;
   - records actor, subject, resource type, resource id, purpose/case context and decision;
   - is callable before returning sensitive data.
3. Add a minimal backoffice-sensitive read endpoint for runtime proof:
   - likely `GET /api/v1/backoffice/audit-log/probe/{id}` or `GET /api/v1/backoffice/users/{id}/sensitive-probe`;
   - requires approved backoffice bearer token;
   - writes read-audit before returning the probe payload;
   - returns only minimal synthetic/probe data, not fake compliance queues.
4. Implement generic actor control storage and service:
   - actor type: `END_USER` or `MERCHANT`;
   - actor id;
   - control state: `ACTIVE`, `BLOCKED`, `FROZEN`;
   - reason code and audit metadata;
   - block/freeze/unblock operations for runtime proof.
5. Add minimal internal/runtime endpoints or backoffice endpoints to set and inspect controls:
   - must be clearly scoped as local/runtime control hooks;
   - must not pretend to be full AML/sanctions case management.
6. Add a protected write-probe endpoint that uses the control hook:
   - for end user session, a blocked/frozen end user is denied;
   - for merchant session, a blocked/frozen merchant is denied;
   - active actors pass the probe.
7. Add audit writes for:
   - control state changes;
   - blocked/frozen write-probe denial;
   - read-audit probe events.
8. Add retained runtime scripts proving:
   - read-audit is written before sensitive probe response is accepted;
   - blocked/frozen end-user write probe is denied;
   - blocked/frozen merchant write probe is denied;
   - active actors still pass;
   - audit append-only protection still holds.

## 4. Explicitly In Scope

### Code Changes

- Platform service migrations and Kotlin code.
- Read-audit service/repository.
- Account/actor control service/repository.
- Minimal runtime-proof endpoints in Platform.
- Retained runtime scripts under `product/scripts/runtime/reg_phase02_*`.
- Status/evidence docs after verification.

### Behavioral Outcomes

- Backoffice OIDC user can perform a sensitive-read probe.
- Platform writes synchronous read-audit before the sensitive-read probe returns.
- End-user and merchant actor control states can be set locally for runtime proof.
- A blocked/frozen end user cannot pass a write-like protected probe.
- A blocked/frozen merchant cannot pass a write-like protected probe.
- Active actors continue to pass the probe.
- Control changes and denied write probes write audit rows.

### Verification Outcomes

This slice exercises:

- `AUD-03` as an early backend primitive proof, not full compliance coverage.
- `AUD-99` as a foundation check for future sensitive reads.
- `AUTH-05` regression for protected endpoint denial.
- `AUD-01` for control/auth-adjacent audit rows if applicable.
- `AUD-02` regression for audit append-only protection.
- A local precursor for future `WLT-02`, without claiming wallet behavior.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- real wallet balances, deposits, withdrawals or transfers;
- `WLT-02` as a full wallet check;
- payment authorization declines;
- KYC document preview;
- sanctions/AML cases;
- AML critical auto-freeze rules;
- Source of Funds workflows;
- account freeze/unfreeze UI;
- audit-log viewer UI;
- production policy engine;
- backoffice workflow SPA implementation.

Do not add fake compliance queues, fake wallet ledger rows or fake AML rules. If a capability is not in scope, it must remain absent or explicitly return a real unsupported response.

## 6. Proposed API Shape

The exact route names may be adjusted during implementation to fit code structure, but the behavioral surface should stay narrow and clearly marked as local/runtime proof.

### 6.1 Sensitive Read Probe

```http
GET /api/v1/backoffice/read-audit/probe/{resourceId}
Authorization: Bearer <keycloak-access-token>
```

Response:

```json
{
  "data": {
    "resourceId": "uuid",
    "resourceType": "READ_AUDIT_PROBE",
    "readAudited": true
  },
  "errors": []
}
```

Expected behavior:

- Requires a valid backoffice role.
- Writes a read-audit row before response.

### 6.2 Actor Control Mutation For Runtime Proof

```http
POST /api/v1/backoffice/actor-controls
Authorization: Bearer <keycloak-access-token>
```

Request:

```json
{
  "actorType": "END_USER",
  "actorId": "uuid",
  "state": "FROZEN",
  "reasonCode": "runtime_probe"
}
```

Response:

```json
{
  "data": {
    "actorType": "END_USER",
    "actorId": "uuid",
    "state": "FROZEN"
  },
  "errors": []
}
```

### 6.3 Write Guard Probe

```http
POST /api/v1/enduser/write-guard/probe
Cookie: MFP_SESSION=<end-user-session>
```

```http
POST /api/v1/merchant/write-guard/probe
Cookie: MFP_SESSION=<merchant-session>
```

Blocked/frozen response:

```json
{
  "data": null,
  "errors": [
    {
      "code": "actor_control_blocked",
      "message": "Actor is blocked or frozen."
    }
  ]
}
```

## 7. Concrete Files To Touch

### 7.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md` after verification
- `product/apps/platform/src/main/kotlin/com/minifin/platform/backoffice/**`
- `product/apps/platform/src/main/kotlin/com/minifin/platform/identity/**` if session actor helpers need reuse

### 7.2 Create

- Platform migration, likely:
  - `product/apps/platform/src/main/resources/db/migration/V4__read_audit_actor_controls.sql`
- Platform control/audit code, likely:
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/controls/ActorControlModels.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/controls/ActorControlRepository.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/controls/ActorControlService.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/controls/ActorControlController.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/audit/ReadAuditRepository.kt`
  - `product/apps/platform/src/main/kotlin/com/minifin/platform/audit/ReadAuditService.kt`
- Retained scripts, likely:
  - `product/scripts/runtime/reg_phase02_read_audit_probe.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_merchant.sh`

### 7.3 Do NOT Touch

- `planning/01_business_requirements.md` through `planning/06_implementation_guide.md` — approved baseline.
- `planning/design-details/**` — approved inputs, unless implementation discovers a real mismatch and owner approves an update.
- `planning/runtime_checklists.md` — approved check IDs; do not churn IDs during this slice.
- `product/apps/spa-*` — frontend implementation is out of scope.
- Ledger, wallet, payment, KYC, AML, sanctions, card, chargeback or settlement domain behavior.

## 8. Recommended Change Order

1. Add DB migration for read-audit/control tables.
2. Add read-audit repository/service.
3. Add actor control repository/service.
4. Add backoffice read-audit probe endpoint.
5. Add backoffice actor-control mutation endpoint for runtime proof.
6. Add end-user and merchant write-guard probe endpoints.
7. Add audit writes for read-audit, control changes and denied probes.
8. Add retained runtime scripts.
9. Rebuild Platform image and recreate `platform`.
10. Run Phase 01/02 regression subset:
    - runtime health;
    - backoffice OIDC;
    - end-user auth;
    - merchant auth;
    - protected endpoint denial;
    - audit append-only.
11. Run new read-audit/control scripts.
12. Record evidence in `planning/runtime_evidence_log.md`.
13. Update `planning/implementation_status.md`, `README.md` and `CURRENT.md`.
14. Commit completed backend/runtime slice separately from any future frontend work.

## 9. Linked Runtime Checks

This slice targets:

- `AUD-03` — compliance read-audit primitive.
- `AUD-99` — sensitive read-audit foundation.
- `AUTH-05` — protected endpoint denial regression.
- `AUD-01` — audit writes for control state changes and denial events.
- `AUD-02` — audit append-only protection regression.

This slice intentionally does not mark `WLT-02` as passed. It may record a precursor note only:

- actor control hook exists and denies probe writes;
- real wallet write blocking is proven later when wallet writes exist.

Expected result tags:

- `AUD-03`: `partial` if only the primitive/probe is implemented; full pass waits for real compliance-sensitive reads.
- `AUD-99`: `foundation` or `partial` until all sensitive read paths exist.
- `AUTH-05`: `pass` regression if existing protected endpoint denial remains intact.
- `AUD-01`: `pass` if control changes and denied probes create audit rows.
- `AUD-02`: `pass` if DB-level no-update/no-delete trigger still rejects mutation.

## 10. Linked UI Prototype

No product frontend implementation is in scope.

Potential future UI prototypes:

- account freeze/unfreeze review;
- audit log viewer;
- sensitive read permission/denial state.

Terminology note:

- `prototypes/ui/*.html` are standalone visual prototype artifacts.
- `product/apps/**` is product implementation code, not a prototype itself.
- This slice must not implement SPA screens.

## 11. Verification Shape

After implementation, run:

- Platform image rebuild:
  - `docker compose -f deploy/docker-compose.yml build platform`
- Platform recreate:
  - `docker compose -f deploy/docker-compose.yml up -d platform`
- Regression scripts:
  - `product/scripts/runtime/reg_phase01_runtime_health.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_oidc.sh`
  - `product/scripts/runtime/reg_phase02_enduser_auth.sh`
  - `product/scripts/runtime/reg_phase02_merchant_auth.sh`
  - `product/scripts/runtime/reg_phase02_backoffice_role_denial.sh`
  - `product/scripts/runtime/reg_phase02_auth_audit.sh`
- New scripts:
  - `product/scripts/runtime/reg_phase02_read_audit_probe.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_enduser.sh`
  - `product/scripts/runtime/reg_phase02_actor_control_merchant.sh`

Evidence must be appended to:

```text
planning/runtime_evidence_log.md
```

## 12. Open Questions

1. **Read-audit storage**

   Decision: create a dedicated `audit.read_audit_log` table with append-only protection, instead of overloading `audit.audit_log`.

   Reason: read-audit has different query and retention patterns than auth/domain audit events, and future `AUD-99` can target it explicitly.

2. **Control subject model**

   Decision: store controls by `(actor_type, actor_id)` with actor types `END_USER` and `MERCHANT`, not wallet/account ids yet.

   Reason: wallet accounts do not exist yet. Actor-level controls give future wallet/payment slices a real enforcement hook without inventing placeholder wallet rows.

3. **Runtime proof endpoints**

   Decision: add narrow probe endpoints with explicit names `/write-guard/probe` and `/read-audit/probe`, then keep them until real endpoints supersede them.

   Reason: this is honest runtime evidence for a primitive. It avoids fake wallet/compliance workflows while still proving behavior.

4. **Result tags**

   Decision: record `AUD-03` as `partial/foundation`, not full pass.

   Reason: the primitive is real, but real sensitive reads such as KYC preview, frozen account review and audit viewer arrive in later phases.

## 13. Next Planned Step

Backend/runtime implementation is verified and recorded in `planning/runtime_evidence_log.md`.

Next planned step: draft the next non-frontend implementation slice.
