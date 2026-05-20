# Phase 08 Slice 03 — AML Dormancy-Break Alert — Planning Note

Status: **backend/runtime sub-scope executed v0.1**.

This document fixes the next narrow AML slice after `Phase 08 Slice 02 — AML Structuring Alert`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 08 slice 03`:

```text
Implement AML-03 only: a previously active end user with no completed money-moving activity for a dormant period can trip a deterministic dormancy-break rule when recent completed activity exceeds a local aggregate threshold.
Do not implement critical auto-freeze, alert review decisions, SoF, two-eyes or AML frontend UI.
```

This slice extends the existing AML rule engine and alert model without broadening into freeze/case workflow.

## 2. Why This Slice Is Next

This slice is next because:

- `AML-01` and `AML-02` already created the AML persistence and duplicate suppression pattern;
- approved MVP AML rule set includes velocity, structuring and dormancy-break;
- dormancy-break is the remaining deterministic MVP AML rule;
- critical auto-freeze should wait until all three alert rules exist.

## 3. Exact Backend/Runtime Scope

The implementation pass should:

1. Reuse the existing AML alert/evaluation persistence.
2. Add one deterministic dormancy-break rule:
   - confirm the end user had completed money-moving activity before the dormant period;
   - confirm no completed money-moving activity exists during the dormant gap;
   - sum completed money-moving activity in the recent lookback window;
   - trip when recent aggregate amount exceeds local threshold.
3. Add internal/local runtime trigger:

```http
POST /internal/aml/evaluate-dormancy-break
```

4. Create one `OPEN` AML alert with `rule_code = 'DORMANCY_BREAK'`.
5. Suppress duplicate open alerts for the same user/rule/window on rerun.
6. Write `aml.alert_created` audit event on alert creation.
7. Add retained runtime script `product/scripts/runtime/reg_phase08_aml_dormancy_break_alert.sh`.

## 4. Rule Model

Use deterministic local defaults:

```text
dormancy period = 30 days
recent lookback = 24 hours
aggregate threshold = EUR 1,000.00
severity = HIGH
```

The script may override these values through request body if exposed, but defaults must be deterministic.

## 5. DB / Model Expectations

No new table is required if `aml.aml_alerts` and `aml.aml_rule_evaluations` can store:

- `rule_code = 'DORMANCY_BREAK'`;
- severity;
- recent movement count;
- metadata with dormancy days, aggregate threshold and observed aggregate amount.

Do not rewrite the existing AML schema.

## 6. Runtime Verification

Target check:

- `AML-03` — synthetic activity trips dormancy-break rule and opens alert.

The retained script should:

1. Seed an active end user.
2. Seed one old completed movement before the dormant period.
3. Seed no completed movement during the dormant gap.
4. Seed recent completed movement(s) exceeding EUR 1,000.00 aggregate in the latest 24h.
5. Run AML dormancy-break evaluation.
6. Assert exactly one `OPEN` alert exists with:
   - `rule_code = 'DORMANCY_BREAK'`;
   - severity `HIGH`;
   - correct end user id.
7. Assert alert metadata records observed aggregate amount.
8. Assert alert creation audit row exists.
9. Rerun evaluation and assert no duplicate open alert is created.

Targeted regressions:

- `AML-02`;
- `AML-01`;
- `RUN-01`;
- `LDG-05` only if ledger tables are touched directly.

## 7. Explicitly Out Of Scope

Do not implement:

- critical auto-freeze (`AML-04`);
- AML alert review decisions;
- account freeze/unfreeze;
- SoF threshold (`WLT-03`);
- two-eyes enforcement (`WLT-04`);
- AML frontend UI;
- SAR filing workflow;
- vendor/ML fraud integrations.

Do not claim:

- `AML-04`;
- `WLT-03`;
- `WLT-04`;
- frontend `UI-*`.
