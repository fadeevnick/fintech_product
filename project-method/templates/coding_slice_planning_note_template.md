# Coding Slice Planning Note Template

Шаблон для `planning/implementation-slices/phase_NN_slice_MM_<short_name>_planning.md`.

Назначение:

```text
до начала coding pass зафиксировать:
- что делает этот slice;
- почему именно сейчас;
- что в scope, что — нет;
- какие файлы трогаются;
- какие runtime checks slice exercises.
```

Это до-кодовый артефакт. Не журнал работы.

См. также [`../guides/coding_slice_discipline_guide.md`](../guides/coding_slice_discipline_guide.md).

---

## Template

```md
# Phase NN Slice MM — <Short Name> — Planning Note

Этот документ фиксирует узкий coding slice внутри `Phase NN — <Phase Name>`.

Он нужен для exact scope перед началом кода.

## 1. Decision

`Phase NN slice MM`:

```text
<ONE-LINE WHAT THIS SLICE DOES>
```

Это означает:

- <CONSEQUENCE 1>
- <CONSEQUENCE 2>
- <CONSEQUENCE 3>

## 2. Why This Slice Is Next

Этот slice выбран следующим, потому что:

- <REASON 1: e.g., backend read shell already consumes contract>
- <REASON 2: e.g., previous slice unblocked migration>
- <REASON 3: e.g., delaying it would force premature edits in next slice>

Если начать не с этого slice, а, например, с <ALTERNATIVE>, то <NEGATIVE_CONSEQUENCE>.

## 3. Exact Scope

В этот slice входят:

1. <STEP 1>
2. <STEP 2>
3. <STEP 3>
4. <STEP 4>

## 4. Explicitly In Scope

### Code Changes

- <CHANGE 1>
- <CHANGE 2>
- <CHANGE 3>

### Behavioral Outcomes

- <OUTCOME 1>
- <OUTCOME 2>

### Verification Outcomes

- <CHECK ID 1 from runtime_checklists.md>
- <CHECK ID 2>

## 5. Explicitly Out Of Scope

В этот slice **не** входят:

- <OUT 1>
- <OUT 2>
- <OUT 3>

Отдельно важно:

- не трогать сейчас <SCOPE-CREEP CANDIDATE 1>
- не добавлять <SCOPE-CREEP CANDIDATE 2>
- не расползаться в <SCOPE-CREEP CANDIDATE 3>

## 6. Concrete Files To Touch

### 6.1 Modify

- `<RELATIVE_PATH 1>`
- `<RELATIVE_PATH 2>`

### 6.2 Create

- `<RELATIVE_PATH 1>`
- `<RELATIVE_PATH 2>`

### 6.3 Do NOT Touch

- `<EXPLICITLY UNTOUCHED PATH 1>` — потому что <REASON>
- `<EXPLICITLY UNTOUCHED PATH 2>` — потому что <REASON>

## 7. Recommended Change Order

1. <ORDER 1>
2. <ORDER 2>
3. <ORDER 3>

## 8. Linked Runtime Checks

This slice exercises:

- `XXX-01` — <one-line description>
- `XXX-02` — <one-line description>
- `XXX-03` — <one-line description>

If a needed check ID does not yet exist in `planning/runtime_checklists.md`, add it там FIRST, then reference here.

## 9. Linked ADRs

- ADR-<NN> — <one-line title> (применим, потому что <REASON>)

## 10. Verification Shape

После имплементации в этом slice будет запущено:

- check IDs: <list>
- expected runtime artifacts: <e.g., docker compose up + curl + DB select>
- verification scripts:
  - existing: `<product/scripts/runtime/reg_<name>.<ext> OR NONE>`
  - new to create: `<product/scripts/runtime/reg_<name>.<ext> OR tmp_<name>.<ext> OR NONE>`
- evidence will be appended to: `planning/runtime_evidence_log.md`

## 11. Pass Criteria

Slice considered `implemented` when:

- code changes из секций §6.1, §6.2 присутствуют;
- linked check IDs из §8 имеют `pass` или honest `pass-indirect` entries в `planning/runtime_evidence_log.md`;
- `planning/implementation_status.md` обновлён.
```

---

## How To Use This Template

### Заполнить ДО кода, не ПО ХОДУ

Главное правило. Planning note заполняется до кода. Фактические результаты после кода идут в `runtime_evidence_log.md` и `implementation_status.md`; значимые решения — в ADR.

### `Explicitly Out Of Scope` — обязательная секция

Это самая важная защита от scope-creep. Если она пуста, скорее всего slice растёт.

### Один slice — узкий

Если §3 (Exact Scope) расширяется до 10+ steps или §6 (Files To Touch) трогает 30+ файлов из 5 модулей — это не slice, это phase.

Лечение: разделить на N slices, у каждого своё planning note.

### Linked Runtime Checks — обязательны

Slice без check IDs = slice без verification. Если check'ов ещё нет — сначала обновить `planning/runtime_checklists.md`.

### Naming convention

```text
phase_NN_slice_MM_<short_name>_planning.md
```

Примеры:

- `phase_01_slice_01_auth_session_hardening_planning.md`
- `phase_02_slice_03_opportunity_baseline_planning.md`
- `phase_04_slice_01_cart_endpoints_planning.md`

`short_name` — snake_case, 2-4 слова.
