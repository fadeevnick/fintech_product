# ADR-004 — Ledger Central Source Of Truth

Status: **Accepted**  
Date: 2026-05-15

## Context

Wallet, issuer, acquirer, settlement, refunds and chargebacks all need consistent money movement. Local balance copies can exist only as projections.

## Decision

Platform Ledger is the central source of truth. Other services call internal Ledger APIs for postings/balance-critical operations. Acquirer may maintain a merchant settlement projection, reconciled against Platform Ledger.

## Consequences

Positive:
- one authoritative ledger invariant model;
- easier reconciliation;
- avoids distributed write ownership for money.

Negative:
- authorization path has sync HTTP dependency on Platform Ledger;
- local projections require reconciliation tooling.

## Links

- `planning/04_architecture.md`
- `planning/design-details/schema_drafts.md`
