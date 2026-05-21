# CURRENT

Last updated: 2026-05-21.

## Current State

`AML-05` is complete: Phase 08 Slice 07 AML alert review decisions is implemented, runtime-verified and documented.

Read first:
- `README.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`

## Latest Verified Evidence

Runtime checks completed on temporary compose project `mfp-aml05`:
- `AML-05` — pass: all 7 verification parts passed (list/detail, short-rationale rejection, CLOSED_FALSE_POSITIVE, repeat-decision 409, unfreeze on false-positive of frozen alert, ESCALATED, MARKED_FOR_SAR compliance gate and allowed).
- Flyway migration V34 applied cleanly.

Static/backend checks completed:
- `bash -n product/scripts/runtime/reg_phase08_aml_review_decisions.sh`
- `product/gradlew --no-daemon -p product :apps:platform:compileKotlin`
- `product/gradlew --no-daemon -p product :apps:platform:bootJar`

Temporary Docker stack `mfp-aml05` was stopped with volumes removed.

## Next Planned Step

Choose and draft the next narrow backend/runtime planning note before implementation. Frontend/UI work is handled separately and should not be touched unless explicitly requested.
