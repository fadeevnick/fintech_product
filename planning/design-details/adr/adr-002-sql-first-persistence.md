# ADR-002 — SQL-First Persistence

Status: **Accepted**  
Date: 2026-05-15

## Context

Ledger, audit, outbox, idempotency and financial state transitions need explicit SQL, transaction boundaries, locks and append-only constraints. ORM-first persistence can hide writes and state mutation.

## Decision

Use:
- Flyway SQL migrations per service;
- jOOQ for generated/type-safe SQL where useful;
- Spring JDBC/JdbcClient and raw SQL for ledger/audit/outbox critical paths;
- JPA only case-by-case for low-risk CRUD, never for ledger/audit/outbox/card-data paths.

## Consequences

Positive:
- transparent query and transaction behavior;
- better fit for ledger invariants and append-only controls;
- easier DB-level constraints and reconciliation.

Negative:
- more manual mapping/query code;
- CRUD screens may take longer than with ORM-first approach.

## Links

- `planning/05_tech_stack.md`
- `planning/design-details/schema_drafts.md`
