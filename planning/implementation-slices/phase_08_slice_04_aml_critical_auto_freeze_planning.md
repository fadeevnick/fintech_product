# Phase 08 Slice 04 — AML Critical Auto-Freeze — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow AML slice after `Phase 08 Slice 03 — AML Dormancy-Break Alert`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 08 slice 04`:

```text
Implement AML-04 only: an OPEN CRITICAL AML alert can trigger automatic END_USER actor-control FROZEN state, and existing wallet write guards block writes after the freeze.
Do not implement AML alert review decisions, unfreeze workflow, SoF, two-eyes or AML frontend UI.
```

This slice connects the AML alert model to the existing actor-control primitive without building case-management UI.

## 2. Why This Slice Is Next

This slice is next because:

- `AML-01`, `AML-02` and `AML-03` now provide the three MVP AML rules and shared alert persistence;
- Phase 02 Slice 04 already created `identity.actor_controls`;
- wallet write paths already enforce `FROZEN` / `BLOCKED`;
- the next compliance invariant is proving critical AML alerts can freeze an account and block subsequent writes.

## 3. Exact Backend/Runtime Scope

The implementation pass should:

1. Reuse `aml.aml_alerts` and `identity.actor_controls`.
2. Add an internal/local runtime processor:

```http
POST /internal/aml/process-critical-auto-freezes
```

3. Find `OPEN` AML alerts with `severity = 'CRITICAL'`.
4. For each critical alert, set the alert end user actor control to `FROZEN` with reason code `aml_critical_alert`.
5. Mark the alert status as `ACCOUNT_FROZEN_PERMANENT` after freeze is persisted.
6. Write audit rows for:
   - actor-control state change;
   - AML auto-freeze action.
7. Add retained runtime script `product/scripts/runtime/reg_phase08_aml_critical_auto_freeze.sh`.

## 4. Runtime Verification

Target check:

- `AML-04` — critical alert freezes account and blocks writes.

The retained script should:

1. Register and log in an active end user.
2. Seed one `OPEN` `CRITICAL` AML alert for that end user.
3. Run critical auto-freeze processing.
4. Assert `identity.actor_controls` has `END_USER` / `FROZEN` / `aml_critical_alert`.
5. Assert the alert status moved to `ACCOUNT_FROZEN_PERMANENT`.
6. Assert audit rows exist.
7. Try `POST /api/v1/deposits` for that end user and assert `403 actor_control_blocked`.
8. Rerun processing and assert no duplicate freeze work is performed.

Targeted regressions:

- `AML-03`;
- `AML-02`;
- `AML-01`;
- `WLT-02` deposit create branch;
- `RUN-01`;
- `LDG-05` only if ledger tables are touched directly.

## 5. Explicitly Out Of Scope

Do not implement:

- AML alert review decisions;
- account unfreeze workflow;
- senior-compliance approval workflow;
- SoF threshold (`WLT-03`);
- two-eyes enforcement (`WLT-04`);
- AML frontend UI;
- SAR filing workflow;
- vendor/ML fraud integrations.

Do not claim:

- `WLT-03`;
- `WLT-04`;
- frontend `UI-*`.
