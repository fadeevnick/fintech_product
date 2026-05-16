# Runtime Verification Guide

Этот guide описывает runtime verification как обязательную часть каждого implementation slice.

Главная дисциплина:

```text
implemented != verified
no runtime claim without recorded evidence
```

Default scope для каждого slice: **узкий production-working scope**.

---

## 1. Default Runtime Scope

По умолчанию slice должен доказать один узкий, но настоящий production path:

- один launch-critical happy path;
- критические access denials;
- основные state transitions;
- реальные persistence / provider / auth / storage paths;
- никакие fake/stub implementations.

Если scope шире, он описывается явно в `planning/06_implementation_guide.md`, checklists или slice planning note как `later scope` / `out of scope`.

---

## 2. Non-Negotiable Quality Bar

Любая capability в коде должна реально делать своё дело.

Разрешены:

- **cut** — feature отсутствует в коде и явно зафиксирован в `cut_register.md`;
- **manual** — операция выполняется человеком через admin/provider dashboard/runbook;
- **simple production** — простейший работающий код: sync SMTP, DB LIKE, local persistent volume, sync provider API call.

Запрещены:

- `console.log` вместо реальной отправки email;
- `dev_mock` provider, который не вызывает настоящий sandbox/production provider;
- in-memory storage для persistent data;
- hardcoded sample results;
- `if test then success`;
- любая заглушка с идеей “потом заменим”.

---

## 3. Commodity Tooling First

Для backoffice/admin ops/monitoring/secrets/provider operations сначала использовать готовые external / managed / internal-tools решения:

- Retool / Forest Admin / Appsmith;
- provider dashboards;
- Sentry / Grafana / Datadog / log platform;
- env-based secrets или managed secret store;
- queue/provider dashboards.

Custom UI/code оправдан, когда workflow domain-specific, user-facing, audit-critical или product-critical.

---

## 4. Per-Slice Runtime Preconditions

Перед implementation slice проверить прямо в slice planning note:

- relevant baseline artifacts are available (`planning/01..05` and `planning/prototypes/`, if present);
- `planning/06_implementation_guide.md` содержит phase для slice;
- нужные planning details готовы: access/API/state/schema/test/cut/ADR по необходимости;
- `planning/runtime_checklists.md` содержит check IDs для slice;
- `planning/06_implementation_guide.md` описывает scope и exit criteria текущей phase;
- для каждой capability в scope выбрана legitimate form: cut / manual / simple production.

---

## 5. Check IDs

Check IDs не содержат runtime level:

```text
{MODULE}-{NN}
```

Примеры:

- `AUTH-01`
- `ORD-03`
- `PAY-07`
- `AUD-02`

ID должен быть стабильным и не переиспользоваться после retirement.

---

## 6. Evidence

Каждый runtime check пишет запись в `planning/runtime_evidence_log.md`:

- date;
- phase;
- check ID;
- actor;
- preconditions;
- action;
- expected result;
- actual result;
- result tag;
- evidence reference;
- verification script or manual steps.

`pass-indirect` и `partial` допустимы как честные промежуточные tags, но они не должны превращаться в overclaim.

---

## 7. Later Scope

Если capability не входит в текущий узкий scope:

- не реализовывать её заглушкой;
- записать в `cut_register.md` или `later scope`;
- добавить check IDs только когда она становится реальным scope;
- не писать “done” для phase без указания remaining gaps.

---

## 8. Anti-Patterns

### Fake Instead Of Legitimate Form

Сигнал: временный `dev_mock`, `console.log`, hardcoded result, in-memory persistent data.

Лечение: заменить на cut / manual / simple production.

### Over-Engineering The First Production Path

Сигнал: первый slice начинается с outbox, worker, retry, distributed tracing, Meilisearch, S3, CI/CD без реальной необходимости.

Лечение: вернуть к простейшему работающему варианту. Усложнять после реального сигнала.

### Slice Without Runtime Checks

Сигнал: code merged, но нет linked check IDs и evidence.

Лечение: добавить check IDs, выполнить checks, записать evidence.

### Evidence Without Observable Result

Сигнал: `pass`, но actual result = “works”.

Лечение: записать наблюдаемый факт: HTTP status/body, DB row, log line, provider callback, screenshot, migration filename.

---

## 9. Quick Reference

| Concept | Where |
|---|---|
| Phase scope / later scope | `planning/06_implementation_guide.md` |
| Check IDs | `planning/runtime_checklists.md` |
| Per-check evidence | `planning/runtime_evidence_log.md` |
| Current implementation/runtime state | `planning/implementation_status.md` |
| Verification scripts | `product/scripts/runtime/` |
