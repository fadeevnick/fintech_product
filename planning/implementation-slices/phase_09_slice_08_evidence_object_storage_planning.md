# Phase 09 Slice 08 — Chargeback Evidence Object Storage — Planning Note

Status: **APPROVED v0.1 — backend/runtime sub-scope executed**.

This document fixes the next narrow chargeback slice after `Phase 09 Slice 07 — Merchant Deadline Expiry`.

Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md` after implementation.

---

## 1. Decision

`Phase 09 slice 08`:

```text
Implement CHB-08 only: merchant evidence attachments must be backed by real local S3-compatible object writes to the `chargeback-evidence` bucket before evidence metadata is accepted.
Do not implement browser multipart upload, presigned URLs, backoffice preview UI, KYC/SoF document storage, chargeback metrics or frontend UI.
```

## 2. Exact Backend/Runtime Scope

The implementation pass should:

1. Add a small object-storage adapter boundary for chargeback evidence using the existing local SeaweedFS S3-compatible HTTP endpoint.
2. Configure Platform with:
   - object storage endpoint URL;
   - chargeback evidence bucket name.
3. During `POST /api/v1/merchant/disputes/{disputeId}/evidence`, require each attachment to include inline `contentBase64` for this backend/runtime slice.
4. Validate:
   - attachment metadata remains bounded as in `CHB-03`;
   - decoded content size matches `sizeBytes`;
   - `storageKey` is under the configured bucket prefix rules for chargeback evidence.
5. Ensure bucket exists.
6. Write each attachment object to SeaweedFS before committing evidence metadata.
7. Persist existing evidence attachment metadata with the submitted storage key.
8. Keep the existing evidence submission state transition, audit and webhook behavior.
9. Add retained runtime script `product/scripts/runtime/reg_phase09_evidence_object_storage.sh`.

## 3. Runtime Verification

Target check:

- `CHB-08` — evidence attachment object is written to local S3-compatible object storage before metadata is accepted.

The retained script should:

1. Create a settled payment and dispute through the existing CHB helper path.
2. Submit merchant evidence with one inline base64 attachment.
3. Assert evidence metadata is persisted.
4. Read the object back from SeaweedFS and assert bytes match.
5. Assert bad base64 is rejected without evidence metadata.
6. Assert size mismatch is rejected without evidence metadata.

Targeted regressions:

- `CHB-03`;
- `CHB-07`;
- `LDG-05`.

## 4. Explicitly Out Of Scope

Do not implement:

- browser multipart upload flow;
- presigned URL generation;
- backoffice evidence preview/download UI;
- KYC or SoF object storage;
- chargeback rate metrics;
- frontend UI.
