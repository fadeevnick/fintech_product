# Project Bootstrap Checklist

Operational checklist для запуска нового standalone project.

Использовать вместе с:

- [`artifact-driven-project-guide.md`](artifact-driven-project-guide.md)
- [`runtime_verification_guide.md`](runtime_verification_guide.md)
- [`coding_slice_discipline_guide.md`](coding_slice_discipline_guide.md)
- [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md)
- все templates из [`../templates/`](../templates/)

---

## 1. Before You Start

В short-kit проект стартует не с пустой идеи. Перед первой AI-сессией в project folder уже должны быть baseline planning inputs:

- `planning/01_business_requirements.md`
- `planning/02_user_journeys.md`
- `planning/03_functional_requirements.md`
- `planning/04_architecture.md`
- `planning/05_tech_stack.md`
- `planning/prototypes/` — если прототипы уже подготовлены

AI treats `planning/01..05` and `planning/prototypes/` as approved baseline unless user explicitly asks to revise them.

---

## 2. Confirm The Product Boundary

Перед созданием implementation artifacts AI должен прочитать `README.md`, `planning/01..05` и `planning/prototypes/` если есть, затем кратко подтвердить:

- concrete product;
- main roles / user journeys;
- major architecture constraints;
- chosen stack;
- important do-not-change assumptions.

---

## 3. Create The Project Skeleton

Создай project root и базовые файлы.

### 3.1 At project root

```text
README.md
planning/
  ├── 01_business_requirements.md
  ├── 02_user_journeys.md
  ├── 03_functional_requirements.md
  ├── 04_architecture.md
  ├── 05_tech_stack.md
  ├── prototypes/                         # baseline prototypes, if provided
  └── 06_implementation_guide.md          # first AI-generated planning artifact
prototypes/
  └── ui/                                 # standalone HTML prototypes from UI/UX agent
product/                           # bootstrap позже когда пишется первый slice
```

`planning/implementation_status.md`, `planning/runtime_checklists.md`, `planning/design-details/`, `planning/runtime_evidence_log.md`, `product/scripts/runtime/` создаются позже, когда дошло до implementation planning / runtime verification.

`CURRENT.md` (корень проекта) не создавать как permanent skeleton file. Только при незавершённом workstream.

### 3.2 Initialize Git Immediately

После создания project root и базовых файлов инициализируй Git repository в корне проекта:

```bash
git init
```

Первый commit должен появиться в самом начале, как только есть осмысленный baseline/skeleton. Дальше не копи большую незакоммиченную историю: после каждого завершённого этапа, approved artifact или verified coding slice делай отдельный логический commit.

Commit boundary должен соответствовать смысловой границе:

- approved planning artifact или pack;
- product skeleton;
- runtime scripts / verification tooling;
- implemented and verified coding slice;
- status/evidence/handoff update.

Не смешивай unrelated planning и product-code changes в один commit, если их можно безопасно разделить.

### 3.3 Use templates

Не клонируй status templates заранее. Используй templates по мере появления реальной ответственности:

- `implementation_status_template.md` — когда начинается factual implementation/runtime tracking;
- `CURRENT_template.md` — только если сессия остановилась на незавершённом workstream;
- остальные templates — по мере прохождения этапов.

---

## 4. Create The Implementation Guide

`planning/01..05` и `planning/prototypes/` уже являются baseline. Первый AI-generated planning artifact:

```text
planning/06_implementation_guide.md
```

Он выводится из project baseline, а не из brainstorming. До `06_implementation_guide.md` не начинать product code.

---

## 5. Keep File Roles Separate

| File | Role |
|---|---|
| `planning/06_implementation_guide.md` | phased implementation plan |
| `planning/implementation_status.md` | factual implementation/runtime state |
| `planning/runtime_evidence_log.md` | append-only evidence log (когда runtime начался) |
| `CURRENT.md` | temporary handoff state |

Никогда не смешивай эти роли.

---

## 6. Build The Implementation-Near Planning Pack

В `planning/design-details/`:

- `access_matrix.md`
- `api_contracts.md`
- `state_machines.md`
- `schema_drafts.md`
- `ui_prototypes.md` — для UI-heavy / workflow-heavy products; workflow prototypes, не pixel-perfect дизайн
- `test_matrix.md`
- `cut_register.md`
- `adr/` (high-cost architectural decisions, через [`../templates/adr_template.md`](../templates/adr_template.md))

Также создай:

- `planning/implementation-slices/` (subfolder для slice notes)

Правило:

```text
implementation-near, not implementation-fake
```

Для UI-heavy / workflow-heavy products после `ui_prototypes.md` основной AI-agent должен определить, какие standalone HTML prototypes нужны до coding. Он не обязан сам создавать эти HTML-файлы. Вместо этого он должен:

1. выбрать следующий prototype screen/workflow;
2. подготовить prompt для отдельного UI/UX-specialized agent по [`../templates/ui_ux_prototype_agent_prompt_template.md`](../templates/ui_ux_prototype_agent_prompt_template.md);
3. вывести prompt в чат и попросить пользователя предоставить готовый HTML-файл;
4. сохранить полученный файл в `prototypes/<track>/NN_<screen>.html` или в agreed project prototypes folder;
5. использовать prototype как input для slice planning и product implementation.

Не создавай и не коммить project-local `prototypes/**/prompts/` files. Конкретный prompt — temporary chat handoff, не artifact проекта.

`product/apps/**` не считать prototype artifact. Это implementation code.

UI/UX prototype work may run in parallel with non-frontend implementation. Do not stop all product work just because a design/prototype is still being produced. Continue backend/domain/runtime/infrastructure tasks that do not depend on the missing screen design. Stop only when the next planned task is frontend implementation for a screen/workflow whose accepted HTML prototype is not available yet.

---

## 7. Build Runtime Checklists

Создай `planning/runtime_checklists.md` — глобальную ID-таблицу проверок (см. [`../templates/runtime_checklists_template.md`](../templates/runtime_checklists_template.md)).

Verification scripts живут в `product/scripts/runtime/`, когда появляется первый runtime check. Convention: `tmp_*` одноразовые (удалять после прогона), `reg_*` retained (стабильные имена, могут линковаться к check IDs). Решение `tmp_*` vs `reg_*` — про lifecycle, не про назначение. Подробнее в [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md) §3.5.

См. подробнее [`runtime_verification_guide.md`](runtime_verification_guide.md).

---

## 8. Slice-By-Slice Coding

Каждый coding pass:

1. Создай planning note: `planning/implementation-slices/phase_NN_slice_MM_<name>_planning.md` (см. [`../templates/coding_slice_planning_note_template.md`](../templates/coding_slice_planning_note_template.md)).
2. Имплементация только в scope planning note.
3. Runtime checks из `runtime_checklists.md` исполняются и фиксируются evidence.
4. Запиши evidence в `planning/runtime_evidence_log.md` (см. [`../templates/runtime_evidence_log_template.md`](../templates/runtime_evidence_log_template.md)).
5. Обнови `planning/implementation_status.md` (factual state, не narrative).
6. Если принято significant decision, создай ADR.
7. После завершения и verification сделай логический Git commit для slice. Если evidence ссылается на retained script, после commit укажи commit hash в `planning/runtime_evidence_log.md` или обнови evidence follow-up отдельным docs commit.

Подробнее — в [`coding_slice_discipline_guide.md`](coding_slice_discipline_guide.md).

### Multi-agent Implementation Workflow

If work is parallelized across local branches/worktrees, use these roles:

- **Orchestrator AI** owns planning and review.
  - Creates/updates `planning/implementation-slices/*_planning.md`.
  - Marks planning artifacts approved when routine approval has been delegated by the project owner.
  - Creates a temporary `AGENT_TASK.md` for each executor only after the relevant planning note is approved.
  - Reviews merged executor branches, resolves small integration issues, updates status/evidence, and commits accepted results.
- **Executor AI** owns implementation.
  - Starts from `AGENT_TASK.md`.
  - Implements only the approved planning note scope referenced there.
  - Runs the required runtime checks and records factual evidence/status.
  - Does not create new planning notes or decide slice boundaries by default.

`AGENT_TASK.md` is temporary local orchestration context. It must not be included in the final merge to the main orchestration branch. If a planning artifact mentions it, replace that with a generic phrase such as "temporary orchestration task files".

---

## 9. Keep The Project Resumable

После каждого meaningful stop point обновляй:

- `CURRENT.md`, если активный workstream не завершён;
- `planning/implementation_status.md`, если implementation/runtime tracking уже начался;
- `planning/runtime_evidence_log.md` (новые evidence записи).

На каждом завершении этапа / артефакта AI должен явно написать **следующий планируемый шаг**: в финальном ответе пользователю и в `CURRENT.md`, если файл существует. Следующий шаг должен быть конкретным (`planning/<artifact>.md`, slice planning note или check ID range), а не общим «продолжить работу».

Если ты заканчиваешь сессию с незавершённым workstream, создай или обнови `CURRENT.md` со всеми обязательными полями (см. [`../templates/CURRENT_template.md`](../templates/CURRENT_template.md)).

Когда workstream завершён, `CURRENT.md` нужно удалить.

---

## 10. Start Clean Sessions

Для старта новой AI-сессии человек может использовать launch prompts из kit root:

- `../prompts/project_new_project_prompt_template.md` — если это **первая** сессия нового проекта с готовым baseline.
- `../prompts/project_resume_prompt_template.md` — если проект уже существует и работа продолжается.

Эти файлы не являются обязательным read-first набором внутри уже начатой рабочей сессии.

В resume-промпте обязательно указывать:

- absolute project path;
- source-of-truth files in stage-aware read order;
- last completed artifact or slice;
- next exact artifact, slice или check ID;
- runtime policy;
- do-not-do-yet rules.

---

## 11. Runtime Verification Discipline

- Не считать фазу done без runtime verification.
- Не писать в status, что всё работает, если runtime не запускался.
- После runtime verification фиксировать в `planning/runtime_evidence_log.md`:
  - что проверено (по check ID);
  - что не прошло;
  - какие gaps остались;
  - какой следующий implementation step.

---

## 12. Minimal Bootstrap Rule Set

- Start product-first, not stack-first.
- Choose a concrete product, not a generic genre.
- Build artifact by artifact.
- Separate guide, status, evidence, handoff.
- Initialize Git at project start and commit after each completed stage/artifact/slice.
- Preserve resumability with `CURRENT.md` when work remains unfinished.
- Use implementation-near planning before deep coding.
- Use ADRs for high-cost architectural boundaries.
- Keep phase scope and exit criteria in `planning/06_implementation_guide.md`.
- Use slice planning notes for each coding pass.
- Use runtime checklists ID schema for cross-cutting verification.
- Use evidence log to keep status files factual.
- Keep each slice narrow, production-working, and verified with evidence.

---

## 13. Final Startup Flow

Самый короткий правильный старт:

1. Подготовь `README.md`, `planning/01..05` и `planning/prototypes/`, если прототипы уже есть.
2. Инициализируй Git в project root и сделай первый логический commit, когда baseline/skeleton готов.
3. AI читает kit + project baseline.
4. AI создаёт `planning/06_implementation_guide.md`, после approval — commit.
5. Собери `planning/design-details/` planning pack + ADRs (including `ui_prototypes.md` для UI-heavy / workflow-heavy products), после approval — commit.
6. Создай `planning/runtime_checklists.md`, после approval — commit.
7. Slice-by-slice coding с planning notes и evidence log; после каждого verified slice — commit.
8. Если останавливаешься с незавершённой работой — `CURRENT.md`. Когда workstream завершён — удалить.
