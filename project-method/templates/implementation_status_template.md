# Implementation Status Template

Шаблон для `planning/implementation_status.md`.

Назначение:

```text
описывать factual implementation/runtime state на сейчас:
- какая фаза текущая;
- что существует в коде;
- что verified runtime evidence;
- какие gaps остались;
- какой следующий exact step.
```

Это не planning scratchpad.
Это не journal: хронология идёт в `planning/runtime_evidence_log.md`.

---

## Template

```md
# Implementation Status

## 1. Position

- Product selected: <PRODUCT>
- Current phase: <Phase NN — Name | none>
- Highest verified phase: <Phase NN — Name | none>
- Exact next implementation/runtime step: [`<FILE OR SLICE OR CHECK>`](<FILE OR SLICE OR CHECK>)

## 2. Phase Coverage Summary

| Phase | Status | Verified Checks | Notes |
|---|---|---|---|
| `Phase 00 — <Name>` | <not started | in progress | verified | blocked> | <NN/NN> | <one-line interpretation> |
| `Phase 01 — <Name>` | ... | ... | ... |

## 3. Current Phase Detail

### `Phase NN — <Name>`

**Status:** <state line>

#### What Now Exists

Factual list of capability currently present in code/runtime. Not a journal.

- <FACT 1>
- <FACT 2>
- <FACT 3>

#### Verified Runtime Checks

| Check ID | Result | Evidence Log Date |
|---|---|---|
| `XXX-01` | <pass | pass-indirect | partial | fail> | YYYY-MM-DD |

For full evidence see [`planning/runtime_evidence_log.md`](runtime_evidence_log.md).

#### Capability Forms Inside This Phase

**Cut:**
- <CUT 1>

**Manual:**
- <MANUAL 1>

**Simple production:**
- <SIMPLE 1>

Rule: every in-scope capability is cut / manual / simple production. Fake is a bug.

## 4. Earlier Phase State

For each earlier phase, summarize status + remaining gaps if any.

### `Phase NN-1 — <Name>`

- Status: <line>
- Verified checks: <NN/NN>
- Remaining gaps: <bullet list or none>

## 5. Current Guardrails

- do not introduce fake/stub implementations;
- do not erase remaining gaps without new evidence;
- do not turn `pass-indirect` / `partial` into a full claim;
- if credentials for production integration are unavailable, choose manual operation or cut;
- prefer simplest production-working implementation before adding complexity.

## 6. Next Exact Step

```text
<ONE EXACT NEXT IMPLEMENTATION/RUNTIME STEP>
```

Why this is now the next step:

1. <REASON 1>
2. <REASON 2>
3. <REASON 3>

## 7. Rule

```text
implementation_status.md describes current state, not history.
Chronology lives in runtime_evidence_log.md.
Do not mark a phase verified without evidence.
Do not erase remaining gaps without evidence covering them.
```
```

---

## How To Use This Template

### What Belongs Here

- what is implemented;
- what is verified;
- what remains;
- current phase;
- next exact step.

### What Does Not Belong Here

- product brainstorming;
- design speculation;
- chronological journal;
- raw command outputs;
- detailed per-slice planning.

### Update Triggers

- after completing a coding slice;
- after runtime checks;
- after changing remaining gaps;
- after changing the next exact step.
