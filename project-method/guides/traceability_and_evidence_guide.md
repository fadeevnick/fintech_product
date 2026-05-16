# Traceability And Evidence Guide

Этот guide описывает, как связывать runtime checks, evidence и implementation status.

Цель:

- каждый runtime claim имеет check ID;
- каждый check ID имеет evidence;
- `implementation_status.md` остаётся state, а не journal;
- verification scripts живут в `product/scripts/runtime/`.

---

## 1. Check ID Schema

```text
{MODULE}-{NN}
```

Примеры:

- `AUTH-01`
- `KYC-04`
- `PAY-07`
- `AUD-02`

Правила:

- `MODULE` — стабильный uppercase alias подсистемы;
- `NN` — sequential number внутри module;
- retired IDs не переиспользуются;
- ID не содержит scope marker.

---

## 2. Trace Chain

```text
planning/implementation-slices/*_planning.md
  -> planning/runtime_checklists.md
  -> planning/runtime_evidence_log.md
  -> planning/implementation_status.md
```

`runtime_checklists.md` = contract.

`runtime_evidence_log.md` = facts.

`implementation_status.md` = current state summary.

---

## 3. Runtime Evidence Entry

Каждая evidence запись содержит:

- date / time;
- runtime phase;
- check ID;
- actor;
- preconditions;
- action;
- expected result;
- actual result;
- result tag;
- evidence reference;
- verification script or manual steps.

Result tags:

- `pass`;
- `pass-indirect`;
- `partial`;
- `fail`.

`pass-indirect` и `partial` не являются full claim. Они требуют follow-up или remaining gap.

---

## 4. Verification Scripts Location

Scripts are project code, not planning docs.

Location:

```text
product/scripts/runtime/
```

Lifecycle:

- `tmp_*` — one-off script, delete after use or inline payload into evidence;
- `reg_*` — retained script, stable name, can be referenced by check IDs.

Examples:

- `product/scripts/runtime/tmp_checkout_debug.mjs`
- `product/scripts/runtime/reg_checkout_happy_path.mjs`

---

## 5. Evidence References

Valid evidence:

- HTTP status + response field;
- DB row IDs;
- log file path;
- screenshot path;
- provider callback payload;
- email preview / delivered message ID;
- migration filename;
- command output summary.

Invalid evidence:

- “works”;
- “checked manually” without steps;
- “ok”;
- “looks fine”.

---

## 6. Updating Implementation Status

After runtime checks:

1. Append entries to `planning/runtime_evidence_log.md`.
2. Update `planning/implementation_status.md` current state.
3. Record remaining gaps for `partial`, `pass-indirect`, and `fail`.
4. If work is unfinished, update `CURRENT.md`.

Do not copy full evidence into `implementation_status.md`; link to evidence entries instead.

---

## 7. Anti-Patterns

### Claim Without Evidence

Signal: “verified” appears in status, but no evidence entry exists.

Fix: run the check or downgrade the claim.

### Evidence Without Observation

Signal: result says “works”.

Fix: record concrete observed facts.

### Check Without ID

Signal: evidence describes a test but has no check ID.

Fix: add ID to `runtime_checklists.md`, then record evidence.

### Status As Journal

Signal: `implementation_status.md` says “started X, then ran Y, then fixed Z”.

Fix: move chronology to `runtime_evidence_log.md`; keep current gaps/blockers in `implementation_status.md`.

---

## 8. Quick Reference

| Question | Answer |
|---|---|
| How to name a check? | `{MODULE}-{NN}` |
| Where is the contract? | `planning/runtime_checklists.md` |
| Where is evidence? | `planning/runtime_evidence_log.md` |
| Where are scripts? | `product/scripts/runtime/` |
| Where is current state? | `planning/implementation_status.md` |
