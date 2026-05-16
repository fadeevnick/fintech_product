# CURRENT Template

Шаблон для `CURRENT.md` в standalone projects.

Назначение:

```text
дать новой clean AI-сессии точную точку входа,
точную точку остановки,
и один точный следующий шаг
```

Это не runtime status.
Это не journal.
Это handoff-state.

Этот файл не должен жить в проекте постоянно.

Правило:

```text
CURRENT.md exists only for unfinished work.
When the workstream is complete, delete CURRENT.md.
```

---

## Template

```md
# CURRENT — <Project Name>

Этот файл нужен как handoff-state для новой AI-сессии, если текущий workstream не завершён.

Его цель:

- быстро восстановить контекст;
- не путать planning state с runtime implementation state;
- дать один точный следующий шаг.

## What Is Already Complete

Завершены и зафиксированы:

- `<FILE OR ARTIFACT 1>`
- `<FILE OR ARTIFACT 2>`
- `<FILE OR ARTIFACT 3>`

## What This Session Was Doing

The current workstream was:

```text
<ONE-LINE WORKSTREAM SUMMARY>
```

We explicitly chose:

- <RULE 1>
- <RULE 2>
- <RULE 3>

## Last Completed Artifact Or Slice

The last completed artifact or slice is:

[`<FILE OR SLICE>`](<FILE OR SLICE>)

It defines or fixes:

- <POINT 1>
- <POINT 2>
- <POINT 3>

without:

- <OUT-OF-SCOPE 1>
- <OUT-OF-SCOPE 2>

## Next Exact Step

The next exact artifact, slice or check ID:

`<FILE OR SLICE OR CHECK ID OR CHECK ID RANGE>`

Its scope should be:

- <SCOPE POINT 1>
- <SCOPE POINT 2>
- <SCOPE POINT 3>

This same next planned step should be stated in the AI final response when the session stops at a phase/artifact boundary.

## Read First

Any new AI session should read these files first, in this order:

1. [`README.md`](README.md)
2. [`CURRENT.md`](CURRENT.md)
3. [`planning/01_business_requirements.md`](planning/01_business_requirements.md)
4. [`planning/02_user_journeys.md`](planning/02_user_journeys.md)
5. [`planning/03_functional_requirements.md`](planning/03_functional_requirements.md)
6. [`planning/04_architecture.md`](planning/04_architecture.md)
7. [`planning/05_tech_stack.md`](planning/05_tech_stack.md)
8. [`planning/prototypes/`](planning/prototypes/)                              # if present
9. [`planning/06_implementation_guide.md`](planning/06_implementation_guide.md)  # if present
10. [`planning/design-details/ui_prototypes.md`](planning/design-details/ui_prototypes.md)  # if UI/workflow prototypes exist
11. [`planning/implementation_status.md`](planning/implementation_status.md)     # if present
12. [`planning/runtime_evidence_log.md`](planning/runtime_evidence_log.md)       # if runtime started
13. [`<RELEVANT FILE 1>`](<RELEVANT FILE 1>)
14. [`<RELEVANT FILE 2>`](<RELEVANT FILE 2>)
15. [`<RELEVANT FILE 3>`](<RELEVANT FILE 3>)

If runtime checks are defined:

16. [`planning/runtime_checklists.md`](planning/runtime_checklists.md)

If a slice is in progress:

15. [`planning/implementation-slices/<CURRENT_SLICE_PLANNING_NOTE>`](planning/implementation-slices/<CURRENT_SLICE_PLANNING_NOTE>)

If coding work в product/ уже идёт:

16. [`product/README.md`](product/README.md)

## Do Not Do Yet

Do not do these things unless the user explicitly redirects:

- <RULE 1>
- <RULE 2>
- <RULE 3>
- <RULE 4>

## Current Interpretation Of File Roles

- `planning/06_implementation_guide.md` = phased implementation plan
- `planning/implementation_status.md` = factual implementation/runtime state (not a journal)
- `planning/runtime_evidence_log.md` = chronological per-check evidence (when runtime active)
- `product/` = actual deliverable (code, scripts, deploy)
- `CURRENT.md` (root) = this handoff state

## Recommended Next Action

The next action is singular and explicit:

```text
<NEXT EXACT ACTION>
```

That file, slice or check should:

- <GOAL 1>
- <GOAL 2>
- <GOAL 3>
```

---

## How To Use This Template

### Keep it operational

`CURRENT.md` должен быть:

- коротким;
- точным;
- конкретным;
- immediately usable.

Он не должен становиться вторым `README.md`. Если файл подбирается к 200+ строкам и начинает дублировать `planning/implementation_status.md` — это сигнал нарушения дисциплины.

### Always name one exact next step

Если после чтения `CURRENT.md` новый AI не понимает, какой exact next artifact / slice / check, значит файл плохой.

### Update at every meaningful stop point

Обновлять `CURRENT.md` нужно:

- только если workstream остаётся незавершённым;
- после завершения крупного planning artifact;
- после завершения coding slice;
- перед завершением сессии.

### Delete it when done

Когда workstream завершён и handoff больше не нужен, `CURRENT.md` нужно удалить.

Он не должен оставаться как permanent project file.

### Keep roles separate

Никогда не использовать `CURRENT.md` как:

- implementation guide;
- status tracker;
- runtime evidence log;
- dumping ground for notes.

### Keep read order stage-aware

Не перечисляй в `Read First` файлы, которых проект ещё не создал. Особенно:

- `planning/runtime_checklists.md` если runtime checks ещё не определены;
- `planning/implementation-slices/*` если ещё нет slice work;
- `planning/runtime_evidence_log.md` если runtime ещё не стартовал;
- `product/*` если coding ещё не стартовал.
