# UI Prototypes Template

Шаблон для:

```text
planning/design-details/ui_prototypes.md
```

Назначение:

```text
зафиксировать implementation-near UI/workflow prototypes
до runtime_checklists и до product code
```

Это **не** pixel-perfect дизайн и не marketing UI. Это рабочий artifact для UI-heavy / workflow-heavy products, который связывает:

- роли и permissions;
- screens / queues / forms;
- actions;
- entity states;
- error / empty / loading states;
- audit / read-audit touchpoints;
- API contracts;
- runtime checks.

Если прототипы были подготовлены до старта проекта, они могут жить в `planning/prototypes/` как baseline input. Этот шаблон нужен для прототипов, которые создаются после `06_implementation_guide.md` внутри implementation-near planning pack.

Important distinction:

- `planning/design-details/ui_prototypes.md` is a UI/workflow specification.
- `prototypes/<track>/NN_<screen>.html` files are standalone visual prototype artifacts.
- `product/apps/**` is product implementation code, not a prototype.

For UI-heavy / workflow-heavy products, the main project agent should not design these standalone HTML files directly during product implementation. It should prepare a temporary chat prompt for a separate UI/UX-specialized agent using `ui_ux_prototype_agent_prompt_template.md`, ask the user to provide the generated HTML artifact, then treat that HTML file as planning input for future coding slices. Do not create or commit project-specific prompt files.

---

## 1. Product UI Surfaces

| Surface | Primary roles | Purpose | Route / host |
|---|---|---|---|
| `<surface>` | `<roles>` | `<purpose>` | `<host/routes>` |

## 2. Role-Based Navigation

| Role | Visible sections | Hidden sections | Notes |
|---|---|---|---|
| `<role>` | `<sections>` | `<sections>` | `<notes>` |

## 3. Screen Inventory

| Screen ID | Surface | Screen | Primary actor | Main entities | Runtime critical? |
|---|---|---|---|---|---|
| `UI-001` | `<surface>` | `<screen>` | `<role>` | `<entities>` | `yes/no` |

## 4. Workflow Prototypes

### 4.1 `<Workflow Name>`

Actor: `<role>`

Preconditions:
- `<precondition>`

Screens:
1. `<screen / route>`
2. `<screen / route>`
3. `<screen / route>`

Main actions:
- `<action>`
- `<action>`

State transitions:
- `<entity>`: `<STATE_A>` → `<STATE_B>`

Validation / error states:
- `<error / empty / loading state>`

Audit / read-audit:
- `<audit event or read-audit point>`

Linked artifacts:
- Access matrix: `<section>`
- API contracts: `<endpoint / section>`
- State machines: `<state machine>`
- Runtime checks: `<CHECK-ID>`

## 5. Component / Pattern Notes

| Pattern | Applies to | Notes |
|---|---|---|
| Queue table | `<screens>` | Filters, assignment, status badges, pagination. |
| Detail drawer/page | `<screens>` | Entity summary, actions, audit history. |
| Evidence upload | `<screens>` | Object storage, file metadata, virus scan later scope if relevant. |

## 6. Explicitly Out Of Scope

Not included in these prototypes:

- pixel-perfect visual design;
- branding / marketing pages;
- final copywriting;
- animation polish;
- non-critical responsive refinements unless they affect workflow correctness;
- implementation code.

## 7. Open Questions

1. `<Question>` — recommendation: `<recommended answer>`.

## 8. Resolution Notes

Replace Open Questions with resolution notes when approved.
