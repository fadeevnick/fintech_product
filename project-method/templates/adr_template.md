# ADR Template

Шаблон для `planning/design-details/adr/adr-NNN-<short-title>.md`.

Назначение:

```text
зафиксировать дорогое архитектурное решение,
которое влияет на несколько артефактов одновременно
и будет дорогим для пересборки после начала кода.
```

ADR нужен, когда решение:

- влияет на несколько runtime phases;
- задаёт storage / API / runtime shape;
- определяет access / tenant / consistency boundary;
- будет блокировать или enable'ить другие ADRs.

Если решение распыляется в `access_matrix`, `api_contracts`, `schema_drafts` и runtime phases одновременно — оно стоит ADR.

См. также [`../guides/artifact-driven-project-guide.md`](../guides/artifact-driven-project-guide.md) §10.

---

## Template

```md
# ADR-NNN — <Short Title>

## Status

<drafted | accepted | superseded by ADR-MMM | retired>

## Date

<YYYY-MM-DD>

## Context

<Конкретная архитектурная ситуация, в которой возникло решение.>

Связанные pressures:

- <PRESSURE 1>
- <PRESSURE 2>
- <PRESSURE 3>

Что произойдёт, если решение НЕ зафиксировать:

- <RISK 1>
- <RISK 2>

## Decision

`<FEATURE / BOUNDARY>` решается следующим образом:

- <DECISION POINT 1>
- <DECISION POINT 2>
- <DECISION POINT 3>

Это значит на storage / API / runtime boundary:

1. <IMPLICATION 1>
2. <IMPLICATION 2>
3. <IMPLICATION 3>

## Alternatives Considered

### Alternative A — <name>

- <PROS>
- <CONS>
- <WHY NOT CHOSEN>

### Alternative B — <name>

- <PROS>
- <CONS>
- <WHY NOT CHOSEN>

## Consequences

### Positive

- <PRO 1>
- <PRO 2>
- <PRO 3>

### Negative

- <CON 1>
- <CON 2>
- <CON 3>

### Neutral / Tradeoff

- <NEUTRAL 1>
- <NEUTRAL 2>

## Impacted Artifacts

ADR явно ссылается на артефакты, которые он определяет / влияет:

- `planning/design-details/access_matrix.md` — <строки / разделы, на которые влияет>
- `planning/design-details/schema_drafts.md` — <разделы>
- `planning/design-details/api_contracts.md` — <endpoints>
- `planning/design-details/state_machines.md` — <entities>
- `planning/06_implementation_guide.md` — <phase sections>
- `planning/runtime_checklists.md` — <check IDs>

## Linked Runtime Checks

Check IDs, прямо вытекающие из этого ADR:

- `XXX-01` — <one-line: что check проверяет относительно ADR>
- `XXX-02` — <one-line>

## Reversibility

Если позже придётся пересмотреть это решение:

- <COST 1: e.g., schema migration to redo>
- <COST 2: e.g., data backfill required>
- <COST 3: e.g., access matrix rewrite>

Reversibility: <cheap | moderate | expensive | one-way door>

## Supersedes / Superseded By

- supersedes: <ADR-NNN OR NONE>
- superseded by: <ADR-MMM OR NONE — если позже вводится>

## Notes

(опционально)

- <NOTE 1>
- <NOTE 2>
```

---

## How To Use This Template

### Когда создавать ADR

Когда решение проходит хотя бы 2 из этих критериев:

1. влияет на 2+ артефакта одновременно;
2. обратное решение будет дорогим;
3. без него команда / новая AI-сессия примет противоречивое решение по умолчанию;
4. оно меняет storage / API / runtime shape.

### Когда НЕ создавать ADR

- локальное coding decision (это идёт в slice planning note или `implementation_status.md`);
- стиль кода (это идёт в standards reference);
- pure refactor без architectural shift.

### Numbering

```text
adr-NNN-<short-title>.md
```

`NNN` — three-digit, sequential, zero-padded (`adr-001`, `adr-002`, ...). Не переиспользуются.

`short-title` — kebab-case, 3-5 слов.

### Status lifecycle

```text
drafted → accepted → (optionally) superseded by ADR-MMM
                  → (optionally) retired
```

Никогда не удалять ADR. Если решение пересмотрено — открыть **новый** ADR со статусом `accepted`, на который указывает `superseded by` в старом.

### Impacted Artifacts — обязательны

Без этой секции ADR теряет половину ценности. Backlinks делают ADR трассируемым в обе стороны.

### Linked Runtime Checks — рекомендуется

Каждое ADR обычно имеет proxy в виде check IDs. Например, ADR `Owner-First Vendor Access` создаст checks `AUTH-08` (non-owner vendor mutation denied) и `AUTH-12` (admin-assisted mutation path), если эти paths входят в scope.

### Reversibility — честно

Если решение `one-way door` (после реализации откат невозможен / очень дорог), это надо явно зафиксировать. Это меняет risk profile при принятии.

### Anti-patterns

- **ADR без альтернатив**: подозрительно. Если альтернатив нет, это вообще не decision — это assumption.
- **ADR без consequences**: не годится. Каждое решение имеет цену.
- **ADR без impacted artifacts**: ADR в вакууме — не ADR.
- **Изменение accepted ADR в-place**: нет, открыть новый ADR.
- **Слишком гранулярные ADRs**: 50 ADRs на маленьком проекте — сигнал, что ADR используется как journal вместо decision marker. ADR — для дорогих, многослойных решений.
