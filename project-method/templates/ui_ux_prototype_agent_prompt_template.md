# UI/UX Prototype Agent Prompt Template

Шаблон prompt'а, который основной project agent выводит в чат пользователю, чтобы пользователь передал его отдельному UI/UX-specialized agent.

Назначение:

```text
создать один standalone HTML prototype artifact
до product code для соответствующего screen/workflow
```

Основной агент не должен подменять UI/UX-агента и сразу писать production SPA code. Его задача:

1. выбрать следующий нужный prototype screen из `planning/design-details/ui_prototypes.md`;
2. собрать context files;
3. указать visual references, если они уже есть;
4. написать точный prompt;
5. вывести prompt в чат, не создавая project-local prompt file;
6. после получения файла положить его в `prototypes/<track>/NN_<screen>.html` или project-approved prototypes folder;
7. использовать prototype как input для будущего coding slice.

---

## Prompt

```text
You are designing the next standalone HTML prototype for <Product Name>.

Attached context files:
- <planning/01_business_requirements.md>
- <planning/02_user_journeys.md>
- <planning/03_functional_requirements.md>
- <planning/04_architecture.md, if relevant>
- <planning/design-details/access_matrix.md, if relevant>
- <planning/design-details/api_contracts.md, if relevant>
- <planning/design-details/state_machines.md, if relevant>
- <planning/design-details/ui_prototypes.md>
- <previous prototype HTML files, if any>

Use the Markdown files as product/domain context.
Use the previous HTML prototype files as visual system references.

Keep the same:
- typography
- color system
- app shell / navigation structure
- density
- table / panel / badge / button style
- drawer / modal interaction style
- empty / error / loading state treatment

Important:
- The product name is exactly: "<Product Name>".
- Do not include unrelated product, marketplace, vendor, or demo brand names.
- HTML title must be exactly: "<Product Name> — <Screen Name>".
- Create one standalone HTML file.
- Use embedded CSS and, if useful, embedded React.
- No backend calls.
- Use realistic sample data.
- UI copy should be in <language>.
- Desktop-first, optimized for <target width, e.g. 1440px>.
- Responsive enough for tablet unless explicitly out of scope.
- This is a serious operational product, not a marketing page.
- Environment label should be "<Environment Label>".

Task:
Design the <Screen Name> screen.

Audience:
<Primary roles / users>.

Primary user:
<Name, role, organization or tenant>.

Screen goal:
<What the user must understand or accomplish on this screen>.

Domain sample:
<Concrete realistic sample data for the main entity / workflow>.

Must include:

1. App shell
- <navigation / active item / tenant / current user / environment requirements>

2. <Major section>
- <required fields, states, and actions>

3. <Major section>
- <required fields, states, and actions>

4. Important states
- <state 1>
- <state 2>
- <state 3>

5. Interaction
- <interaction 1>
- <interaction 2>
- <interaction 3>

Deliverable:
Return one complete standalone HTML prototype.

Save the file as:
<NN_screen_name.html>
```

---

## Main Agent Checklist

Before giving this prompt to the user:

- verify that the requested screen exists in `planning/design-details/ui_prototypes.md`;
- include enough product/domain context, but do not attach unrelated docs;
- include previous HTML prototypes as visual references when this is not the first prototype;
- make the output filename deterministic and ordered;
- state where the returned file should be stored in the project;
- do not create `prototypes/**/prompts/` or commit project-specific prompt files.

After the user provides the HTML file:

- place it in the approved prototypes folder;
- update `README.md` / `CURRENT.md` / planning status if the project uses them for handoff;
- commit the prototype artifact separately from product implementation code;
- commit only the returned HTML prototype and related status/docs, not the temporary prompt text;
- do not start implementing that specific frontend screen in product code until the relevant standalone HTML prototype is accepted and the coding slice planning note is approved;
- continue unrelated backend/domain/runtime work while the UI/UX agent is producing the prototype, if that work is already approved and does not depend on the missing design.
