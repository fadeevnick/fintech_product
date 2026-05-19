# Phase 08 Slice 02 — AML Structuring Alert — Planning Note

Status: **APPROVED v0.1 — planning-only; no product code implemented**.

This document fixes the next narrow AML slice after `Phase 08 Slice 01 — AML Velocity Alert Foundation`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 08 slice 02`:

```text
Implement AML-02 only: completed synthetic money-moving activity can trip a deterministic sub-threshold structuring rule and create an OPEN AML alert.
Do not implement dormancy-break, critical auto-freeze, alert review decisions, SoF, two-eyes or AML frontend UI.
```

This slice extends the existing AML rule engine and alert model without broadening into freeze/case workflow.

## 2. Why This Slice Is Next

This slice is next because:

- `AML-01` already created AML schema, rule-evaluation boundary, open-alert duplicate suppression and audit rows;
- approved MVP AML rule set includes velocity, structuring and dormancy-break;
- structuring is the next rule that can be verified with deterministic synthetic completed activity;
- critical auto-freeze should wait until multiple alert types and severities exist.

## 3. Exact Backend/Runtime Scope

The later implementation pass should:

1. Reuse the existing AML alert/evaluation persistence from `AML-01`.
2. Add one deterministic structuring rule:
   - count completed money-moving events for the same end user in a lookback window;
   - include only transactions in the range 90%-99% of a reporting threshold;
   - trip when qualifying count exceeds local threshold.
3. Add an internal/local runtime trigger, recommended:

```http
POST /internal/aml/evaluate-structuring
```

4. Create one `OPEN` AML alert with `rule_code = 'STRUCTURING'`.
5. Suppress duplicate open alerts for the same user/rule/window on rerun.
6. Write audit event(s) for alert creation using the same event family as `AML-01`.
7. Add retained runtime script `product/scripts/runtime/reg_phase08_aml_structuring_alert.sh`.

## 4. Rule Model

Use deterministic local defaults:

```text
reporting threshold = EUR 10,000
qualifying range = EUR 9,000.00 through EUR 9,900.00
lookback = 24 hours
trip threshold = more than 2 qualifying completed movements
severity = HIGH
```

The script may override these values through request body if the implementation exposes parameters, but defaults must be deterministic.

## 5. DB / Model Expectations

No new table is required if `aml.aml_alerts` and `aml.aml_rule_evaluations` from `AML-01` can store:

- `rule_code = 'STRUCTURING'`;
- severity;
- observed count;
- threshold count;
- metadata with reporting threshold and qualifying amount range.

If a small additive migration is needed, use:

```text
product/apps/platform/src/main/resources/db/migration/V25__aml_structuring_alert.sql
```

Do not rewrite the existing AML schema.

## 6. Runtime Verification

Target check:

- `AML-02` — synthetic activity trips structuring rule and opens alert.

The retained script should:

1. Seed or create an active end user.
2. Seed deterministic completed money-moving activity:
   - at least three qualifying movements in EUR 9,000.00-9,900.00;
   - optionally one non-qualifying movement outside the range to prove filtering.
3. Run AML structuring evaluation.
4. Assert exactly one `OPEN` alert exists with:
   - `rule_code = 'STRUCTURING'`;
   - severity `HIGH`;
   - observed qualifying count greater than threshold;
   - correct end user id.
5. Assert alert creation audit row exists.
6. Rerun evaluation and assert no duplicate open alert is created.

Targeted regressions:

- `AML-01`;
- `RUN-01`;
- `LDG-05` only if ledger tables are touched directly.

## 7. Explicitly Out Of Scope

Do not implement:

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

- `AML-03`;
- `AML-04`;
- `WLT-03`;
- `WLT-04`;
- frontend `UI-*`.

## 8. Agent Ownership Guidance

Implementation agent should own:

- AML structuring rule code;
- optional additive Platform migration if required;
- internal runtime trigger for structuring evaluation;
- retained Phase 08 AML structuring runtime script;
- status/evidence updates for this slice only.

Implementation agent should not edit settlement/refund/chargeback/sanctions code except for incidental build fixes.
