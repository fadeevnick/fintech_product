# Coding Slice Discipline Guide

Этот guide описывает дисциплину, как правильно вести coding work в стандартном проекте этого метода.

Главная идея:

```text
каждый узкий coding pass начинается с отдельного planning note
и закрывается через implementation status + runtime evidence
```

Это не бюрократия. Это способ удержать проект:

- **резумабельным** — новая AI-сессия может подхватить slice по planning note;
- **узким** — когда scope записан явно, scope-creep становится виден;
- **трассируемым** — slice ID связывается с runtime check'ами и evidence записями;
- **проверяемым** — обычный slice закрывается через runtime evidence и factual status.

---

## 1. Что такое coding slice

`Slice` — это **минимальный coding pass с одной целью**:

- одну подсистему / эндпоинт / capability;
- которая может быть проверена narrow production-working runtime check'ами;
- которая не зависит от других slices внутри этой же сессии.

Примеры хорошего slice:

- `backend session/current-user hardening`
- `backend account baseline (DTO + repository + service + controller + GET/POST)`
- `frontend CRM read shell (read-only лист + детали для accounts/opportunities)`
- `phase_04 cart endpoints`

Примеры плохого slice (слишком широкий):

- `whole Phase 1`
- `everything CRM`
- `auth + KYC`

Если описание slice занимает абзац, скорее всего это уже не slice, а несколько слитых.

---

## 2. Slice Planning Note

Перед каждым coding pass'ом создаётся файл:

```text
planning/implementation-slices/phase_NN_slice_MM_<short_name>_planning.md
```

Где:

- `NN` — номер runtime phase;
- `MM` — порядковый номер slice внутри фазы (`01`, `02`, …);
- `<short_name>` — короткое имя в snake_case.

Шаблон: [`../templates/coding_slice_planning_note_template.md`](../templates/coding_slice_planning_note_template.md).

Структура planning note:

1. **Decision** — одна строка: что именно делает этот slice.
2. **Why This Slice Is Next** — почему именно сейчас, какие предыдущие slices это разблокировало.
3. **Exact Scope** — конкретные шаги.
4. **Explicitly In Scope** — что обязательно появится / изменится.
5. **Explicitly Out Of Scope** — что **не** делается (и почему).
6. **Concrete Files To Touch** — список файлов с пометкой `modify` / `create`.
7. **Recommended Change Order** — последовательность изменений внутри slice.
8. **Linked Runtime Checks** — какие `{MODULE}-{NN}` ID должны быть exercised этим slice.
9. **Linked ADRs** — ссылки на ADRs, которые применимы.
10. **Verification Shape** — какие именно runtime check'и будут запущены.

---

## 3. Why Explicit Out Of Scope

`Explicitly Out Of Scope` — самая важная секция planning note.

Без неё в slice инвитируются:

- early refactors;
- adjacent улучшения;
- «попутно поправлю»;
- premature edge cases.

Правило:

```text
если планируешь сделать X внутри slice, но не записал X в Explicitly In Scope —
не делать X.

если думаешь сделать Y «по пути», но не уверен —
записать Y в Explicitly Out Of Scope, отложить до следующего slice.
```

---

## 4. How To Close A Slice

После имплементации slice не создаётся отдельный post-slice artifact.

Закрытие slice фиксируется так:

- runtime facts, checks, actual result, fail/partial/pass — в `planning/runtime_evidence_log.md`;
- factual state, completed capability, gaps, current blockers — в `planning/implementation_status.md`;
- significant architecture/product decision — в ADR;
- unfinished handoff — в `CURRENT.md`.

Если реальность отличается от planning note, не редактируй planning note задним числом. Запиши факт в evidence/status, а если это меняет архитектурное решение — создай ADR.

---

## 5. Slice ↔ Runtime Check ID Mapping

Каждый slice **должен** ссылаться на конкретные runtime check IDs из `planning/runtime_checklists.md`.

Пример: slice `phase_02_slice_03_kyc_admin_review_endpoints`:

```md
## Linked Runtime Checks

This slice exercises:

- KYC-04 (admin can read pending queue)
- KYC-05 (admin approve transitions to APPROVED)
- KYC-06 (admin reject transitions to REJECTED)
- KYC-08 (vendor cannot read raw KYC documents)
```

Это создаёт двунаправленную traceability:

- от slice → check IDs;
- от check IDs → evidence в `planning/runtime_evidence_log.md`.

См. [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md).

---

## 6. Slice Lifecycle

```text
1. Pick the next slice from `planning/06_implementation_guide.md`.
2. Create planning note in planning/implementation-slices/.
3. Implement only what is in scope.
4. Run linked runtime checks.
   If verification needs an ad-hoc script:
   - put it in product/scripts/runtime/tmp_<name>.<ext>;
   - if the same script will be reused → promote to product/scripts/runtime/reg_<name>.<ext> before second use;
   - reference the script (path + commit hash) in evidence log entries.
5. Append evidence entries to planning/runtime_evidence_log.md.
6. Update planning/implementation_status.md (factual state, not narrative).
7. (If a significant decision was made) create ADR.
8. Commit the completed slice as a logical Git commit after verification/status/evidence are updated.
   - Keep planning note approval, product code, runtime scripts and evidence/status in separate commits when that separation is useful and low-friction.
   - If evidence references retained scripts, update evidence with the commit hash after the relevant commit exists.
9. (If session ends mid-slice) Update CURRENT.md.
10. Move on to next slice (back to step 1).
```

---

## 7. Pacing Choice

Размер slice зависит от phase и проекта. Эмпирические нормы:

- **Backend baseline slice** (DTO + repo + service + controller + 1-2 endpoints): обычно 1 сессия.
- **Frontend read shell**: обычно 1 сессия.
- **Migration + schema draft update**: обычно 1 сессия.
- **Crosscutting refactor** (auth boundary, error contract): 1 узкий slice — не пытаться комбинировать с feature work.

Если slice не помещается в 1 сессию — это сигнал, что его нужно разделить.

Если за сессию хочется сделать 3 slices — это сигнал, что их можно объединить, но **отдельные planning notes для каждого всё равно нужны**.

---

## 8. Anti-Patterns

### Anti-pattern 1. Imploding scope

Slice planning note начинается с одной capability, но в Concrete Files To Touch появляются 30+ файлов из 5 модулей.

Лечение: разбить на N slices, у каждого своё planning note.

### Anti-pattern 2. No linked check IDs

Slice планирует runtime check, но не ссылается на конкретные `{MODULE}-{NN}` IDs из checklists.

Лечение: либо добавить IDs, либо если их ещё нет — обновить `runtime_checklists.md` и потом ссылаться.

### Anti-pattern 3. Skipping planning note

«Простой slice, и так понятно» → имплементация → потом непонятно, что обещали в scope, что — нет.

Лечение: даже trivial slice имеет короткий planning note (5–10 строк).

### Anti-pattern 4. Planning note as journal

Planning note начинает писаться **по ходу** имплементации и заполняется тем, что реально делается, а не тем, что планировалось.

Лечение: planning note пишется **до** кода. Фактические отклонения фиксируются в `runtime_evidence_log.md` / `implementation_status.md`; значимые решения — в ADR.

### Anti-pattern 5. Status as journal

Вместо обновления `planning/implementation_status.md` как factual state — туда дописывается хронология «started X, then Y, then verified Z». Это нарушает file role separation.

Лечение: хронология идёт в `planning/runtime_evidence_log.md`. Status — это **state на сейчас**.

---

## 9. Slice Naming Convention

```text
phase_<NN>_slice_<MM>_<short_name>_planning.md
```

Примеры:

- `phase_01_slice_01_auth_session_hardening_planning.md`
- `phase_02_slice_03_opportunity_baseline_planning.md`
- `phase_04_slice_01_cart_endpoints_planning.md`

Это даёт consistent сортировку по filename и читабельную группировку по phase.

---

## 10. Связанные файлы

- [`artifact-driven-project-guide.md`](artifact-driven-project-guide.md) — общий метод.
- [`runtime_verification_guide.md`](runtime_verification_guide.md) — как проверять runtime behavior в slice.
- [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md) — как именовать check IDs и фиксировать evidence.
- [`../templates/coding_slice_planning_note_template.md`](../templates/coding_slice_planning_note_template.md)
