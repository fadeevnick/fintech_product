# CURRENT

Last updated: 2026-05-21.

## Current State

`CHB-08` is complete: Phase 09 Slice 08 evidence object storage is implemented, runtime-verified and documented.

Read first:
- `README.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`
- `planning/implementation-slices/phase_09_slice_08_evidence_object_storage_planning.md`

## Latest Verified Evidence

Runtime checks completed on temporary compose project `mfp-chb08`:
- `CHB-08` — pass: evidence object written to/read from local SeaweedFS S3-compatible storage.
- `CHB-03` — pass targeted regression.
- `CHB-04` — pass targeted regression.
- `CHB-05` — pass targeted regression.
- `CHB-07` — pass targeted regression.
- `LDG-05` — pass targeted regression.

Static/backend checks completed:
- `bash -n` for changed chargeback runtime scripts.
- `product/gradlew --no-daemon -p product :apps:platform:compileKotlin`
- `product/gradlew --no-daemon -p product :apps:platform:bootJar :apps:acquirer:bootJar :apps:network:bootJar :apps:issuer:bootJar :apps:vault:bootJar`

Temporary Docker stack `mfp-chb08` was stopped with volumes removed.

## Next Planned Step

Choose and draft the next narrow backend/runtime planning note before implementation. Frontend/UI work is handled separately and should not be touched unless explicitly requested.
