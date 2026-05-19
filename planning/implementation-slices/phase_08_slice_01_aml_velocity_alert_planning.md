# Phase 08 Slice 01 — AML Velocity Alert Foundation — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the first narrow AML slice after KYC/sanctions foundations.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 08 slice 01`:

```text
Implement AML-01 only: completed synthetic money-moving activity can trip a deterministic velocity rule and create an OPEN AML alert.
Do not implement structuring, dormancy-break, critical auto-freeze, alert review decisions, SoF, two-eyes or AML frontend UI.
```

This slice establishes the AML schema, rule-evaluation boundary and first retained runtime check without touching account freeze or case decision workflows.

## 2. Why This Slice Is Next

This slice is next because:

- approved requirements include three MVP AML rules, with velocity as the simplest first rule;
- wallet/payment flows already create enough factual activity for synthetic runtime checks;
- later AML slices (`AML-02`, `AML-03`, `AML-04`) need a durable alert model and rule engine boundary first;
- AML UI exists as prototype intake, but frontend implementation remains later Phase 10.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Add `aml` persistence for rule configuration/evaluation and alerts.
2. Add a narrow AML service/rule engine boundary in Platform.
3. Implement one deterministic velocity rule for local runtime:
   - count completed money-moving events for the same end user over a configurable lookback window;
   - trip when count exceeds local threshold.
4. Provide a deterministic internal runtime trigger or hook that evaluates the rule against seeded/completed activity.
5. Create one `OPEN` AML alert when the rule trips.
6. Avoid duplicate alerts for the same subject/rule/window during reruns.
7. Write audit event(s) for alert creation.
8. Add retained runtime script `product/scripts/runtime/reg_phase08_aml_velocity_alert.sh`.

## 4. Recommended API / Runtime Surface

Recommended internal route:

```http
POST /internal/aml/evaluate-velocity
```

The route may accept a narrow body with `endUserId` for deterministic local checks, or process due recent activity. It must be internal/local only, not a public API.

No backoffice AML alert list/detail/decision API is required in this slice.

## 5. DB / Model Expectations

Recommended migration:

```text
product/apps/platform/src/main/resources/db/migration/V24__aml_velocity_alert_foundation.sql
```

Recommended minimal tables:

```sql
create schema if not exists aml;

create table aml.aml_alerts (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    rule_code text not null,
    severity text not null check (severity in ('LOW','MEDIUM','HIGH','CRITICAL')),
    status text not null check (status in ('OPEN','IN_REVIEW','CLOSED_FALSE_POSITIVE','ESCALATED','MARKED_FOR_SAR','ACCOUNT_FROZEN_PERMANENT')),
    window_started_at timestamptz not null,
    window_ended_at timestamptz not null,
    observed_count integer not null,
    threshold_count integer not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
```

The implementation may add a narrow `aml_rule_evaluations` table if useful, but it is not required for `AML-01`.

## 6. Runtime Verification

Target check:

- `AML-01` — synthetic activity trips velocity rule and opens alert.

The retained script should:

1. Seed or create an active end user.
2. Seed deterministic completed money-moving activity for that user, using existing wallet/ledger records if practical.
3. Run AML velocity evaluation.
4. Assert exactly one `OPEN` alert exists with:
   - `rule_code = 'VELOCITY'`;
   - expected severity, recommended `MEDIUM`;
   - observed count greater than threshold;
   - correct end user id.
5. Assert alert creation audit row exists.
6. Rerun evaluation and assert no duplicate open alert is created for the same user/rule/window.

Targeted regressions:

- `RUN-01` platform health;
- `LDG-05` only if ledger tables are touched directly.

## 7. Explicitly Out Of Scope

Do not implement:

- structuring rule (`AML-02`);
- dormancy-break rule (`AML-03`);
- critical auto-freeze (`AML-04`);
- AML alert review decisions;
- account freeze/unfreeze;
- SoF threshold (`WLT-03`);
- two-eyes enforcement (`WLT-04`);
- AML frontend UI;
- SAR filing workflow;
- vendor/ML fraud integrations.

Do not claim:

- `AML-02`;
- `AML-03`;
- `AML-04`;
- `WLT-03`;
- `WLT-04`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- AML schema/migration foundation;
- Platform AML service/rule engine boundary;
- internal runtime trigger for velocity evaluation;
- retained Phase 08 AML velocity runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit settlement/refund/chargeback/sanctions code except for incidental build fixes.
