# Runtime Checklists Template

Шаблон для `planning/runtime_checklists.md`.

Назначение:

```text
глобальная ID-таблица runtime checks
```

Это контракт «что нужно проверить», не «что уже проверено».
Факт исполнения живёт в `planning/runtime_evidence_log.md`.

См. также [`../guides/traceability_and_evidence_guide.md`](../guides/traceability_and_evidence_guide.md).

---

## Template

```md
# Runtime Checklists

## 1. ID Schema

Каждая runtime проверка получает уникальный ID:

```text
{MODULE}-{NN}
```

- `MODULE` = uppercase alias подсистемы.
- `NN` = sequential номер внутри module.

Примеры:

- `INF-01`
- `AUTH-01`
- `ORD-03`
- `PAY-07`
- `AUD-02`

## 2. Module Aliases

| Alias | Module |
|---|---|
| `INF` | infrastructure / health / observability |
| `AUTH` | authentication / session |
| `<MOD>` | <module name> |

Alias выбирается один раз и не меняется.

## 3. Evidence Rules

Каждая запись в `planning/runtime_evidence_log.md`, ссылающаяся на check ID, обязана содержать:

- date / time
- runtime phase
- check ID
- actor used
- preconditions
- action taken
- expected result
- actual result
- evidence reference
- verification script or manual steps

Минимальное правило:

```text
no runtime claim without recorded evidence
```

Result tags:

- `pass` — actual соответствует expected
- `pass-indirect` — actual соответствует через косвенную проверку
- `partial` — часть expected покрыта
- `fail` — actual не соответствует

`pass-indirect` и `partial` требуют явного follow-up или remaining gap.

## 4. Phase Checks

### 4.1 Phase 00 — <Name>

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `INF-01` | <description> | <expected> | `product/scripts/runtime/reg_<name>.<ext>` |
| `INF-02` | <description> | <expected> | manual |

### 4.2 Phase 01 — <Name>

| Check ID | What to verify | Expected result | Verification script |
|---|---|---|---|
| `<MOD>-01` | <description> | <expected> | `product/scripts/runtime/reg_<name>.<ext>` |
| `<MOD>-02` | <description> | <expected> | manual / pending |

### 4.3 Phase NN — <Name>

(repeat per phase)

## 5. Cross-Phase Checks

Некоторые checks затрагивают несколько фаз: end-to-end flow, audit integrity, restart/replay behavior.

| Check ID | What to verify | Expected result | Relevant phases |
|---|---|---|---|
| `E2E-01` | <full happy path through multiple phases> | <expected> | 01..NN |

## 6. Retired Checks

| Check ID | Why retired | Date |
|---|---|---|
| `AUTH-01` | superseded by `AUTH-09` | YYYY-MM-DD |

Retired IDs are never reused.

## 7. Linked ADRs

- `AUTH-04` → ADR-002 shell/auth boundary
- `PAY-04` → ADR-005 payment webhook finalization
```

---

## How To Use This Template

### Why IDs Exist

ID цепляет:

```text
slice planning note
→ runtime_checklists.md
→ runtime_evidence_log.md
→ implementation_status.md
```

Без IDs status и evidence становятся nameless.

### Numbering

Внутри `MODULE` нумерация sequential. Удалённые checks помечаются `retired`, ID не переиспользуется.

### What Does Not Belong Here

- даты исполнения checks;
- pass/fail метки;
- per-slice planning details;
- runbook запуска docker/test commands.

### Update Triggers

- появилась новая capability в scope → добавить check IDs;
- capability удалена → пометить checks retired;
- ADR изменил behavior → обновить linked checks.
