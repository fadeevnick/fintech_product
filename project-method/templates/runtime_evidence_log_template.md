# Runtime Evidence Log Template

Шаблон для project-local `planning/runtime_evidence_log.md`.

Назначение:

```text
append-only лог фактически выполненных runtime checks с воспроизводимой evidence
```

Это факт исполнения, в отличие от `planning/runtime_checklists.md` (контракт того, что нужно проверить).
Это журнал, в отличие от `planning/implementation_status.md` (state на сейчас).

См. также [`../guides/traceability_and_evidence_guide.md`](../guides/traceability_and_evidence_guide.md).

---

## Template

```md
# Runtime Evidence Log

Каждая запись = одна exercised проверка с обязательными полями.

## Rules

```text
no runtime claim without an entry here.
one entry = one check ID execution.
entries are not edited; superseded entries are appended fresh.
```

## Field Format

- `date` (ISO `YYYY-MM-DD`)
- `time` (если важно)
- `runtime phase` (`Phase NN — Name`)
- `check ID` (`AUTH-01`, `PAY-03`, ...)
- `actor` (роль / seeded user / system)
- `preconditions`
- `action`
- `expected result`
- `actual result`
- `result tag` (`pass` / `pass-indirect` / `partial` / `fail`)
- `evidence reference` (logs, DB rows, screenshot, provider callback, migration filename)
- `verification script` (путь в `product/scripts/runtime/` + commit hash, или `manual` с inline шагами)

## Entries

Append below. Выбрать порядок один раз: newest-first или append-at-bottom.

---

### Entry NNN

- date: 2026-MM-DD
- time: HH:MM (UTC | local)
- runtime phase: Phase NN — <Name>
- check ID: XXX-01
- actor: <SEEDED USER OR ROLE>
- preconditions:
  - <PRECONDITION 1>
  - <PRECONDITION 2>
- action:
  - <STEP 1>
  - <STEP 2>
- expected result:
  - <EXPECTATION 1>
  - <EXPECTATION 2>
- actual result:
  - <OBSERVATION 1>
  - <OBSERVATION 2>
- result tag: pass
- evidence reference:
  - <LOG FILE PATH or DB ROW IDS or HTTP STATUS+BODY FIELD or MIGRATION FILENAME or SCREENSHOT FILE>
- verification script:
  - <product/scripts/runtime/reg_<name>.<ext> (commit <SHA>) | manual: <steps inline>>
```

---

## How To Use This Template

### Append-Only

Записи добавляются. Не редактируются.

Если предыдущая запись оказалась некорректной, создать новую запись с той же check ID и пометкой `supersedes entry NNN`.

### Evidence Reference Discipline

Valid:

- path to log file;
- DB row IDs;
- HTTP status + key response body field;
- screenshot file;
- email/notification preview;
- provider callback payload;
- migration filename.

Not valid:

- “works”;
- “checked manually” without steps;
- “ok”;
- “as expected”.

### Result Tags

`pass-indirect` и `partial` честно фиксируют неполное доказательство. Они требуют follow-up или remaining gap.

### Linkage Back

После новой записи:

1. Обновить `planning/implementation_status.md`.
2. Если result = `partial` / `pass-indirect`, добавить remaining gap.
3. Если result = `fail`, добавить follow-up в `planning/implementation_status.md` или `CURRENT.md` при незавершённом handoff.

### Verification Script Field

- Скрипт: путь + commit hash.
- Одноразовый `tmp_*`: inline payload или promote в `reg_*`.
- Manual: inline steps, чтобы воспроизводимость не зависела от памяти.

### Anti-Patterns

- Editing entries.
- Bulk pass entries без actual result.
- Missing check ID.
- “Pass” без observation.
- `verification script: manual` без inline steps.
- Reference на удалённый `tmp_*`.
