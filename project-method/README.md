# Project Method (New)

Portable artifact-driven method для standalone product projects, с slice discipline и cross-cutting runtime evidence.

Этот каталог — самостоятельный reusable layer. Он не зависит от:

- root-level navigation другого repo;
- соседних training tracks;
- конкретного demo application.

Метод предполагает, что проект стартует с готового baseline: `planning/01..05` плюс `planning/prototypes/`, если прототипы уже подготовлены.

## Structure

```text
project-method/
├── README.md
├── guides/
│   ├── artifact-driven-project-guide.md
│   ├── project_bootstrap_checklist.md
│   ├── runtime_verification_guide.md
│   ├── coding_slice_discipline_guide.md
│   └── traceability_and_evidence_guide.md
└── templates/
    ├── CURRENT_template.md
    ├── implementation_status_template.md
    ├── runtime_evidence_log_template.md
    ├── runtime_checklists_template.md
    ├── ui_prototypes_template.md
    ├── ui_ux_prototype_agent_prompt_template.md
    ├── coding_slice_planning_note_template.md
    └── adr_template.md
```

## Read Order

Если нужен полный entrypoint в метод:

1. [Artifact-driven project guide](guides/artifact-driven-project-guide.md) — главный entry-point и source of truth метода.
2. [Project bootstrap checklist](guides/project_bootstrap_checklist.md) — операционный чек-лист старта.
3. [Runtime verification guide](guides/runtime_verification_guide.md) — per-slice runtime verification.
4. [Coding slice discipline guide](guides/coding_slice_discipline_guide.md) — паттерн узких coding passes через slice notes.
5. [Traceability and evidence guide](guides/traceability_and_evidence_guide.md) — ID-схема runtime checks и evidence log.
6. Templates — клонировать в проект по мере надобности.

Launch prompt templates лежат вне `project-method/`, в `../prompts/`. Это human entry layer для старта новой AI-сессии, а не часть read-first метода.

## What This Method Covers

- как стартовать проект от готового baseline (`planning/01..05` + `planning/prototypes/`, если есть);
- как собрать implementation-near planning pack;
- как добавить workflow-level UI specs и standalone HTML prototypes для UI-heavy products до runtime checklists и coding;
- как основной agent должен подготовить prompt для отдельного UI/UX prototype agent и дождаться HTML artifact от пользователя;
- как удержать `06_implementation_guide`, `implementation_status` и `CURRENT.md` в **разных** ролях;
- как декомпозировать coding work на узкие slices с per-slice planning;
- как доказать runtime behavior без overclaim'а;
- как проводить cross-cutting runtime checks с явной evidence;
- как удержать verification scripts (`product/scripts/runtime/`) в homed location с двухуровневым lifecycle (`tmp_*` / `reg_*`);
- как инициализировать Git в самом начале и фиксировать каждый завершённый этап / artifact / verified slice отдельным логическим commit'ом;
- как сохранить resumability между clean AI sessions.

## What This Method Does Not Require

- конкретный repo layout (workspace / отдельный GitHub repo / private project — всё подходит);
- внешний CI или test framework на старте.

## Core Rule

```text
prepared project baseline
→ git init + first logical commit
→ implementation guide
→ implementation-near planning
→ UI/UX prototype agent handoff for key screens
→ slice-by-slice code
→ runtime verification with recorded evidence
→ commit after each completed stage/artifact/slice
```

## Important Rule

Для активного проекта source of truth должен жить внутри project folder:

- project `README.md`
- project `CURRENT.md`, only if there is unfinished work
- project `planning/implementation_status.md`
- project `planning/runtime_evidence_log.md` (когда runtime начался)

Сам метод нужен, чтобы правильно запустить и вести проект.
Он не должен становиться постоянным daily entrypoint вместо project-local файлов.

`CURRENT.md` в этой модели не является постоянным файлом структуры:

```text
CURRENT.md exists only for unfinished work.
When the workstream is complete, delete CURRENT.md.
```
