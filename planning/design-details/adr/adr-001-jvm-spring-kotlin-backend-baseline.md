# ADR-001 — JVM/Spring/Kotlin Backend Baseline

Status: **Accepted**  
Date: 2026-05-15

## Context

The project is a fintech learning and portfolio system. Backend stack should be recognizable for payments/banking work and support transactions, security, Kafka, observability and long-lived maintainability.

## Decision

Use:
- Kotlin for backend application code;
- Java 21 LTS runtime;
- Spring Boot 3.5.x;
- Gradle 8.x Kotlin DSL.

Node.js remains only for frontend tooling and optional runtime scripts.

## Consequences

Positive:
- industry-aligned backend stack;
- strong Spring ecosystem for Security, Kafka, Actuator/Micrometer and Testcontainers;
- Kotlin reduces boilerplate for DTOs/state machines.

Negative:
- frontend and backend use different languages;
- Gradle/Spring/Kotlin compatibility must be pinned carefully.

## Links

- `planning/05_tech_stack.md`
- `planning/06_implementation_guide.md`
