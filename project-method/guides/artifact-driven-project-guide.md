# Artifact-Driven Project Guide

Главный guide по построению standalone projects через артефакты, slice discipline и runtime verification, а не через ранний код.

Этот документ — entry point метода:

```text
prepared project baseline
→ implementation-near planning pack
→ slice-by-slice code with per-slice planning
→ runtime verification with recorded evidence
→ explicit handoff state for clean future sessions
```

Он отвечает на вопрос:

```text
как строить новый проект так,
чтобы не скатиться в premature coding,
не смешивать план и факт,
не терять контекст между сессиями,
не overclaim'ить runtime done,
и постепенно расширять scope продукта до launch-ready состояния, удерживая production quality на каждом шаге
```

---

## 1. When To Use This Method

Использовать этот guide, когда проект:

- новый;
- domain-heavy;
- архитектурно неоднозначный;
- требует не только implementation, но и product/system design;
- делается вместе с AI в нескольких сессиях;
- должен расти через документы, артефакты и фазовое уточнение.

Особенно полезен для:

- CRM / ERP
- Fintech
- E-commerce / Marketplace
- SaaS platforms
- workflow-heavy internal systems
- approval / policy / metadata-driven products

Если задача маленькая и локальная, этот процесс может быть избыточен.

---

## 2. Main Principle

Не начинать проект:

- со стека;
- с базы данных;
- со списка таблиц;
- с generic architecture;
- с набора экранов;
- с кода.

Начинать проект так:

```text
prepared project baseline
→ implementation guide
→ implementation-near planning pack
→ slice-by-slice code
→ runtime verification with evidence
```

Ключевая дисциплина:

```text
artifact by artifact, slice by slice, evidence by evidence
```

---

## 3. What This Method Gives You

- архитектура выводится из продукта, а не из любимого стека;
- scope становится контролируемым;
- AI может продолжать работу в новых clean sessions без потери контекста;
- planning, status и handoff перестают смешиваться;
- каждый coding pass начинается с явного planning note и закрывается evidence/status;
- runtime verification становится evidence-based, а не verbal claim;
- evidence per check фиксируется отдельно и не топит implementation status в журнале;
- дорогие архитектурные решения фиксируются до реализации через ADRs.

---

## 4. Core Rules

### 4.1 Product-first before code

Сначала product design chain. Потом architecture. Потом tech stack. Потом implementation.

### 4.2 Concrete product before generic category

Нужно выбрать **один конкретный продукт**, не genre.

Пример: не `CRM`, а `B2B Sales Operations CRM with approvals`.

### 4.3 Artifacts are first-class engineering objects

Артефакты — это и есть способ принятия решений, не вторичная документация.

### 4.4 Planning and implementation are different tracks

Пока нет достаточно сильного planning pack, проект не должен притворяться, что он уже реально реализуется.

### 4.5 Slice-level planning before slice-level code

Каждый узкий coding pass начинается **с отдельного planning note**, а не «по памяти из file plan».

См. [`coding_slice_discipline_guide.md`](coding_slice_discipline_guide.md).

### 4.6 Runtime verification is evidence-based

Runtime verification обязательна для каждого meaningful slice.

Default scope: узкий production-working path. Later scope фиксируется явно, без runtime level labels.

См. [`runtime_verification_guide.md`](runtime_verification_guide.md).

### 4.7 No runtime claim without recorded evidence

Если check не попал в `planning/runtime_evidence_log.md` со всеми обязательными полями, его не было.

См. [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md).

### 4.8 Every session must preserve resumability

Каждая большая сессия должна оставлять after-state, пригодный для продолжения в новой чистой сессии.

`CURRENT.md` создаётся только при незавершённом workstream и удаляется по завершении.

At every meaningful stop point or phase/artifact boundary, AI must explicitly state the **next planned step** in the final response and, if `CURRENT.md` exists, in `CURRENT.md`. The next step must be concrete: artifact name, slice name, or check ID range.

### 4.9 Git history is part of the method

Инициализируй Git repository в project root в самом начале проекта, сразу после создания baseline/skeleton files:

```bash
git init
```

После каждого завершённого этапа, approved artifact, approved planning pack или verified coding slice делай отдельный логический commit. Commit должен быть маленькой точкой восстановления: по нему должно быть понятно, какой artifact/slice завершён и какие runtime claims уже имеют evidence.

Не оставляй несколько этапов в одной большой незакоммиченной массе. Если работа была сделана до `git init`, разложи первый history на несколько логических commit'ов по текущим file groups, не переписывая файлы ради искусственной археологии.

---

## 5. Required Inputs

Перед началом нового проекта в project folder уже должен быть baseline:

- `planning/01_business_requirements.md`
- `planning/02_user_journeys.md`
- `planning/03_functional_requirements.md`
- `planning/04_architecture.md`
- `planning/05_tech_stack.md`
- `planning/prototypes/` — если прототипы уже подготовлены

---

## 6. Source Of Truth Model

Source of truth для нового проекта обычно состоит из:

- current project docs;
- current project `CURRENT.md`, if there is an unfinished handoff;
- current implementation/runtime status, if implementation has started.

Новый AI не должен начинать с общих соображений.
Он должен сначала дочитать source-of-truth files.

---

## 7. Project Skeleton

Структура проекта чётко разделена на три корневые папки:

- **`planning/`** — вся методология: design chain, planning pack, runtime verification markdown, status файлы.
- **`prototypes/`** — standalone HTML/UI artifacts from a UI/UX prototype agent.
- **`product/`** — реальный deliverable: код приложения, миграции, verification scripts, deploy конфиги.

```text
project-root/
├── README.md                             # navigation: что есть planning/, что есть product/
├── CURRENT.md                            # only if there is unfinished work; deleted when workstream closed
│
├── planning/                             # ВСЯ методология
│   ├── 01_business_requirements.md
│   ├── 02_user_journeys.md
│   ├── 03_functional_requirements.md
│   ├── 04_architecture.md
│   ├── 05_tech_stack.md
│   ├── prototypes/                       # baseline prototypes, if provided
│   ├── 06_implementation_guide.md
│   ├── implementation-slices/            # slice notes that detail 06_implementation_guide.md
│   │   └── phase_NN_slice_MM_<name>_planning.md
│   ├── implementation_status.md
│   ├── runtime_checklists.md             # appears when runtime checks are defined
│   ├── runtime_evidence_log.md           # appears when runtime verification starts
│   └── design-details/                   # appears when planning pack is built
│       ├── access_matrix.md
│       ├── api_contracts.md
│       ├── state_machines.md
│       ├── schema_drafts.md
│       ├── ui_prototypes.md              # workflow specs for UI-heavy products
│       ├── test_matrix.md
│       ├── cut_register.md
│       ├── adr/
│       │   └── adr-NNN-*.md
│
├── prototypes/
│   └── ui/                               # standalone HTML prototypes from UI/UX agent
│
└── product/                              # реальный проект (deliverable)
    ├── README.md                         # как развернуть, как запустить, env vars, dependencies
    ├── apps/                             # или src/, или package-stack-specific layout
    │   ├── api/                          # backend
    │   └── web/                          # frontend
    ├── packages/                         # shared libs
    ├── migrations/                       # или co-located внутри apps/api/
    ├── scripts/
    │   ├── runtime/                      # verification scripts
    │   │   ├── tmp_<name>.<ext>          # one-off, delete after use
    │   │   └── reg_<name>.<ext>          # retained replayable scripts (any purpose)
    │   └── deploy/                       # deploy/ops scripts
    ├── deploy/                           # docker-compose, k8s manifests, terraform
    └── package.json / pyproject.toml / pom.xml / Cargo.toml
```

### Two-folder principle

- `planning/` отвечает на «как мы делаем проект и где мы сейчас».
- `product/` отвечает на «что мы делаем».
- Корень содержит только два навигационных файла: `README.md` и (опционально) `CURRENT.md`.

### CURRENT.md location

`CURRENT.md` лежит **в корне** проекта (рядом с `README.md`), не внутри `planning/`. Это handoff-state, который читается первым в новой AI-сессии — должен быть на видном месте. Создаётся только если работа останавливается в незавершённой точке. Когда workstream завершён — удалить.

### Cross-references between planning/ and product/

Verification scripts живут в `product/scripts/runtime/`, но их evidence записывается в `planning/runtime_evidence_log.md`. Evidence reference указывает путь от project-root:

```md
verification script: product/scripts/runtime/reg_phase_04_check.mjs
commit: a1b2c3d
```

Это позволяет cross-reference'ам быть стабильными независимо от того, где исполняется evidence log (внутри сессии AI или при ручной проверке).

### Root README role vs product/README role

- Корневой `README.md` — навигация по проекту: «вот planning/, вот product/, начни читать с CURRENT.md если есть, затем baseline (`planning/01..05` + `planning/prototypes/` если есть), потом 06/implementation_status если они уже есть».
- `product/README.md` — техническая документация запуска: «как поднять локально, какие env vars нужны, как запустить миграции, как запустить smoke tests».

Это разные документы, не дублируются.

---

## 8. Correct Project Sequence

### Step 1. Read prepared baseline

```text
planning/01..05 and planning/prototypes/ are approved baseline inputs
```

### Step 2. Create the implementation guide

Первый AI-generated planning artifact:

```text
planning/06_implementation_guide.md
```

Не начинать code или planning pack, пока `06_implementation_guide.md` не создан из project baseline.

### Step 3. Build the implementation-near planning pack

После design chain, но до глубокого кода, подготовь в `planning/design-details/`:

- access matrix
- API contracts
- state machines
- schema drafts
- UI prototypes (для UI-heavy / workflow-heavy products)
- test matrix
- cut register
- ADR pack (high-cost architectural decisions)

Правило:

```text
implementation-near, not implementation-fake
```

`ui_prototypes.md` не является pixel-perfect дизайн-документом. Его роль — implementation-near workflow prototype:

- экраны / queues / forms по ролям;
- доступные actions и RBAC visibility;
- state / empty / error states;
- audit / read-audit touchpoints;
- links к API contracts, access matrix, state machines и runtime checks.

Standalone HTML prototypes are separate artifacts:

```text
prototypes/<track>/NN_<screen>.html
```

They are visual/UX planning artifacts, not product implementation code. `product/apps/**` must not be described as a prototype just because it contains an early UI checkpoint.

Если прототипы были подготовлены до старта проекта, они могут жить в `planning/prototypes/` как baseline input. Если UI/workflow прототипирование появляется после `06_implementation_guide.md`, держи textual spec in `planning/design-details/ui_prototypes.md`, а standalone HTML artifacts in `prototypes/<track>/`.

Для таких HTML prototypes основной project agent acts as orchestrator:

1. selects the next screen/workflow from `ui_prototypes.md`;
2. writes a temporary chat prompt for a separate UI/UX-specialized agent using [`../templates/ui_ux_prototype_agent_prompt_template.md`](../templates/ui_ux_prototype_agent_prompt_template.md);
3. asks the user to provide the returned HTML file;
4. stores the file as a planning artifact;
5. uses it as input to later slice planning and product code.

Do not create or commit project-local prompt files such as `prototypes/**/prompts/*`. The prompt text is a temporary chat handoff; the returned HTML file is the artifact.

Prototype work is a frontend gate, not a project-wide stop sign. While a UI/UX agent is producing HTML prototypes, the main project agent should continue any approved backend/domain/runtime/infrastructure work that does not depend on the missing design. The main agent stops only when the next planned work is frontend implementation for a screen/workflow without an accepted standalone HTML prototype.

### Step 4. Build runtime checklists

Создай `planning/runtime_checklists.md` — глобальную ID-таблицу проверок (см. [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md)).

### Step 5. Slice-by-slice coding

Каждый coding pass:

1. `planning/implementation-slices/phase_NN_slice_MM_<name>_planning.md` — exact scope, in/out, files.
2. Имплементация только того, что в scope (внутри `product/`).
3. Verification scripts появляются в `product/scripts/runtime/`.
4. Runtime check'и из `planning/runtime_checklists.md` исполняются и фиксируются в `planning/runtime_evidence_log.md`.
5. Status обновляется в `planning/implementation_status.md`; significant decisions фиксируются в ADR.

См. [`coding_slice_discipline_guide.md`](coding_slice_discipline_guide.md).

### Step 6. Runtime verification

Default runtime scope — узкий production-working path. Расширения scope добавляются явно как later scope, когда они становятся реальной задачей.

**Non-negotiable:** любая capability в коде должна **реально делать своё дело**. Реализация может быть простой (sync SMTP send, DB LIKE search, локальная папка на single-server деплое) или manual (admin одобряет KYC из admin panel, payouts руками раз в неделю). Запрещены только fake (`console.log` вместо отправки письма, `dev_mock` вместо provider call, in-memory volatile, hardcoded results).

Три легитимные формы handle'а capability'а:
- **cut** — feature нет в коде, в `cut_register.md`;
- **manual** — feature есть, операция руками админа;
- **simple production** — feature есть, простейший реально работающий код.

Запрещена только **fake** — feature «есть» в коде, но не делает дело по-настоящему.

См. [`runtime_verification_guide.md`](runtime_verification_guide.md).

---

## 9. File Role Separation

Это самая важная дисциплина метода. **Никогда не смешивать роли.**

| File | Role |
|---|---|
| `planning/06_implementation_guide.md` | phased implementation plan (что в каждой фазе) |
| `planning/implementation_status.md` | реальный implementation/runtime progress (factual state, **не журнал**) |
| `planning/runtime_evidence_log.md` | append-only лог runtime checks с evidence |
| `planning/implementation-slices/*_planning.md` | что делает один coding slice внутри `06_implementation_guide.md` |
| `CURRENT.md` (root) | handoff/resume state, существует только при unfinished work |
| `product/` | реальный код проекта, миграции, verification scripts, deploy конфиги |
| `product/scripts/runtime/` | verification scripts (`tmp_*` / `reg_*`) |
| `product/README.md` | техническая документация запуска (env vars, dependencies, run commands) |

Сигналы нарушения дисциплины:

- В `planning/implementation_status.md` появилась хронология «started X, then ran Y, then verified Z on YYYY-MM-DD» — этот текст должен быть в `planning/runtime_evidence_log.md`.
- `CURRENT.md` стал ~300+ строк и дублирует README — это нарушение.
- `planning/06_implementation_guide.md` начал содержать конкретные данные (даты, проверенные команды) — нет, это implementation_status / runtime_evidence_log.
- В `planning/` появились code/script файлы — это нарушение, такие файлы живут в `product/`.
- В `product/README.md` появился implementation status / phase tracking — это нарушение, status живёт в `planning/`.

---

## 10. Implementation-Near Planning Pack

Этот слой нужен между design chain и глубоким coding.

Он должен уменьшить ключевую неоднозначность:

- access model
- API shape
- state transitions
- schema boundaries
- UI/workflow shape for UI-heavy products
- test expectations
- runtime verification shape
- scope cuts
- ADR decisions

Правило:

```text
implementation-near, not implementation-fake
```

Не надо писать псевдокод на сотни строк. Надо доводить проект до точки, где **следующий coding slice уже точен**.

---

## 11. Runtime Checks

Когда planning pack собран, создаётся runtime check registry:

- `planning/runtime_checklists.md` — глобальная таблица проверок с ID (`AUTH-01`, `PAY-02`, и т.д.). Это контракт того, что нужно проверить.

См. [`runtime_verification_guide.md`](runtime_verification_guide.md).

---

## 12. Coding Slice Discipline

Каждый coding pass:

1. Начинается с **planning note** (`planning/implementation-slices/phase_NN_slice_MM_<name>_planning.md`) — Decision / Why First / Exact Scope / Explicitly In / Explicitly Out / Files To Touch.
2. Имплементация только того, что в scope (внутри `product/`).
3. Verification scripts (если нужны) появляются в `product/scripts/runtime/`.
4. Runtime checks из `planning/runtime_checklists.md` исполняются.
5. Evidence фиксируется в `planning/runtime_evidence_log.md`.
6. Status обновляется в `planning/implementation_status.md` (factual state, не narrative).
7. Significant decisions фиксируются в ADR.
8. Завершённый и verified slice фиксируется отдельным Git commit. Если retained verification script указан в evidence, evidence должен ссылаться на script path и commit hash после commit.

См. [`coding_slice_discipline_guide.md`](coding_slice_discipline_guide.md).

---

## 13. Traceability and Evidence

Cross-cutting проверки именуются по схеме:

```text
{MODULE}-{NN}
```

Примеры: `AUTH-01`, `CHK-04`, `KYC-12`, `ORD-03`.

Каждый ID живёт в `planning/runtime_checklists.md` (контракт) и проверяется через `planning/runtime_evidence_log.md` (факт).

Каждая запись в evidence log должна содержать:

- date / time
- runtime phase
- check ID
- actor used
- preconditions
- action taken
- expected result
- actual result
- evidence reference (logs, DB state, screen, provider callback)
- verification script (если использовался — путь в `product/scripts/runtime/` плюс commit hash)

Verification scripts (ad-hoc `.mjs` / `.sh` / `.sql` / curl-rig'и) живут в `product/scripts/runtime/` с lifecycle convention:

- `tmp_<name>.<ext>` — одноразовые, удаляются после прогона;
- `reg_<name>.<ext>` — retained replayable scripts любого назначения (forward verification, smoke, regression, health probe), стабильные имена, могут линковаться к check IDs.

Это **cross-folder reference**: контракт и evidence живут в `planning/`, исполняемые скрипты — в `product/`. Это умышленно: контракт = методология, скрипт = код.

См. [`traceability_and_evidence_guide.md`](traceability_and_evidence_guide.md).

---

## 14. `CURRENT.md` And Resumability

`CURRENT.md` — это temporary handoff-state для новой AI-сессии.

Хороший `CURRENT.md` отвечает:

- last completed artifact или slice
- next exact artifact, slice или check ID
- read-first order
- do-not-do-yet rules

Если новый AI после чтения `CURRENT.md` не понимает exact next step — файл плохой.

Правила:

- `CURRENT.md` не должен повторять README.
- Не должен дублировать implementation_status.
- Когда workstream завершён — удалить.

---

## 15. Clean Session Discipline

Для новой AI-сессии:

1. Сначала project-local files.
2. Потом current nearest artifacts.
3. Потом supporting references только если нужно.

Не надо тащить весь repo в контекст. Не надо требовать в read-first порядке файлы, которых проект ещё не создал. Read order должен быть **stage-aware**.

Launch prompt templates лежат в `../prompts/`. Их использует человек перед стартом новой AI-сессии; внутри рабочей сессии они не являются read-first source of truth.

---

## 16. When To Start Coding

Код можно начинать, когда:

- product idea конкретная;
- business requirements и journeys достаточно сильные;
- architecture выведена из flows;
- implementation guide есть;
- planning pack уменьшил ключевую неоднозначность;
- есть один exact next coding slice (с planning note);
- runtime checks для slice определены и привязаны к check IDs.

Плохой сигнал: `давай просто что-нибудь начнём писать`.

Хороший сигнал: `Phase 2 slice 03: backend opportunity baseline — narrow production check first`.

---

## 17. Runtime Verification Discipline

Не считать фазу done без runtime verification.

Не писать `done` / `verified` / `works`, если ничего не запускалось — для in-progress использовать:

- `drafted`
- `started`
- `runtime verification pending`
- `bootstrap created`
- `verified on production implementation`

Каждый verified check должен иметь запись в `planning/runtime_evidence_log.md`.

---

## 18. Minimal Quality Bar

Хороший проект, идущий по этому методу:

1. начинается не со стека;
2. выбирает concrete product;
3. проходит design chain in order;
4. держит guide/status/evidence/handoff раздельно;
5. сохраняет resumability через `CURRENT.md`;
6. строит planning pack перед deep coding;
7. держит каждый slice узким и runtime-verifiable;
8. использует slice planning notes для каждого coding pass;
9. имеет глобальную ID-таблицу runtime checks;
10. фиксирует evidence в отдельном log файле, не в status.
