# Phase 07 Slice 05 — Compliance Read Audit — Planning Note

Status: **EXECUTED v0.1 — backend/runtime sub-scope implemented and runtime verified**.

This document fixes the next narrow backend/runtime slice after `Phase 06 Slice 07 — Refund Bounds`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 07 slice 05`:

```text
Implement AUD-03 for the existing compliance-sensitive sanctions hit detail read surface: reading a sanctions hit detail must synchronously write audit.read_audit_log before returning data.
Do not implement new AML backoffice UI/API, frozen-account viewer, audit-log viewer, full PAN reveal, KYC document preview or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Keep `GET /api/v1/backoffice/sanctions-hits` list behavior unchanged.
2. Update `GET /api/v1/backoffice/sanctions-hits/{id}` so the compliance principal is passed into the service.
3. Before returning the sanctions hit detail response, write one `audit.read_audit_log` row with:
   - `actor_type = BACKOFFICE`;
   - actor subject UUID/reference from the backoffice principal;
   - `subject_type = SANCTIONS_HIT`;
   - `subject_id = sanctions hit id`;
   - `resource_type = SANCTIONS_HIT`;
   - `resource_id = sanctions hit id`;
   - `purpose = compliance_sanctions_hit_detail`;
   - `decision = ALLOW`;
   - metadata with roles and end user id.
4. Keep the compliance role guard before read-audit write; forbidden reads must not create ALLOW read-audit rows.
5. Add retained runtime script `product/scripts/runtime/reg_phase07_compliance_read_audit.sh`.

## 3. Runtime Verification

Target check:

- `AUD-03` — sanctions hit detail read writes sync read-audit before data is returned.

The retained script should:

1. Create an end user/KYC profile in review.
2. Configure OpenSanctions local match mode.
3. Attempt manual KYC approval as compliance to create an `OPEN` sanctions hit.
4. Read `GET /api/v1/backoffice/sanctions-hits/{hitId}` as compliance.
5. Assert HTTP 200 and response contains the hit id.
6. Assert exactly one `audit.read_audit_log` row exists for that `SANCTIONS_HIT` detail read with `decision = ALLOW`.
7. Assert list endpoint remains accessible without creating detail read-audit for that hit.

Targeted regressions:

- `SNX-01`;
- `SNX-02`;
- `KYC-03`.

## 4. Explicitly Out Of Scope

Do not implement:

- AML alert backoffice list/detail APIs;
- frozen-account viewer;
- audit-log viewer;
- full PAN reveal;
- KYC document preview;
- frontend UI.
