# ADR-003 — Selective Services Topology

Status: **Accepted**  
Date: 2026-05-15

## Context

The platform must teach realistic fintech boundaries without turning into full microservices sprawl. Approved architecture uses five application services: `platform`, `acquirer`, `network`, `issuer`, `vault`.

## Decision

Implement five application services from Phase 01 as empty/full-shape shells:
- `platform`
- `acquirer`
- `network`
- `issuer`
- `vault`

Each service owns its own Postgres instance. Cross-service access is via HTTP/Kafka only.

## Consequences

Positive:
- service boundaries match card network simulation;
- early Compose/network/build feedback;
- Vault isolation is explicit from the start.

Negative:
- Phase 01 infra is heavier;
- more service wiring before first business feature.

## Links

- `planning/04_architecture.md`
- `planning/06_implementation_guide.md`
