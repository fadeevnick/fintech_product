# ADR-005 — Local Object Storage Via SeaweedFS S3 API

Status: **Accepted**  
Date: 2026-05-15

## Context

The system needs local S3-compatible storage for KYC documents, chargeback evidence and Source of Funds documents. Earlier architecture named MinIO, but current upstream archival/read-only status makes it a poor new default. LocalStack was considered but is heavier when only S3 semantics are needed.

## Decision

Use SeaweedFS S3 API as default local object storage. Keep application boundary generic:

```text
ObjectStoragePort + AWS SDK for Java S3-compatible client
```

LocalStack remains optional for future AWS-emulation tests if the project later adds AWS services beyond S3.

## Consequences

Positive:
- local-first S3-compatible behavior;
- avoids MinIO maintenance risk;
- keeps future provider swap cheap.

Negative:
- fewer common tutorials than MinIO;
- SeaweedFS-specific Compose/runtime behavior must be validated in Phase 01.

## Links

- `planning/05_tech_stack.md`
- `planning/design-details/schema_drafts.md`
- `planning/design-details/test_matrix.md`
