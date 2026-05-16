# Phase 05 Slice 01 — Vault Tokenization and Card Issuance Foundation — Planning Note

Status: **DRAFT v0.1 — planning-only; no product code implemented**.

This document fixes the first implementation slice inside `Phase 05 — Vault, Card Issuance and Authorization`.

It is a pre-code scope contract. Runtime evidence will live in `planning/runtime_evidence_log.md`; factual implementation state will live in `planning/implementation_status.md`.

---

## 1. Decision

`Phase 05 slice 01`:

```text
Implement the vault tokenization foundation and the minimal issuer card-record path needed for an end user
to issue a test card, while proving PAN isolation, detokenize authorization and PAN log masking. Do not
implement authorization, capture, settlement or payment lifecycle progression yet.
```

This means:

- `vault` becomes the only service that persists full PAN values;
- `vault` returns an opaque `card_token` plus non-sensitive metadata (`last4`, expiration month/year, BIN) to callers;
- `issuer` stores card records linked to end-user/wallet identity by token and metadata only, never by PAN;
- `platform` exposes the end-user card issuance API and delegates to `issuer` through a narrow internal service path;
- `issuer` delegates PAN generation/tokenization to `vault`;
- non-issuer service identities cannot call `vault.detokenize`;
- PAN-like values are masked in general application logs;
- `PAY-04` and `PAY-05` remain for the next Phase 05 authorization slice.

## 2. Why This Slice Is Next

This slice is next because:

- Phase 04 established merchant API keys, public API authentication, public/dashboard idempotency and the minimal payment-intent shell required before card-network work;
- `planning/06_implementation_guide.md` makes Phase 05 responsible for Vault isolation, card issuance and the sync authorization path;
- the full authorization fast path depends on existing cards and a trustworthy vault boundary, so card issuance must land before `Acquirer → Network → Issuer` authorization;
- `planning/runtime_checklists.md` keeps `VLT-01`, `VLT-02` and `VLT-03` pending until this exact isolation/log-masking behavior is proven;
- existing Phase 03 wallet/account foundations give Platform enough end-user/wallet identity context to create issuer card records without adding payment authorization holds yet;
- `MRC-01` remains blocked on Stripe sandbox credentials and is not a prerequisite for internal three-party card simulation.

If authorization were implemented first, it would either need card/PAN fixtures or an unsafe shortcut around Vault. This slice avoids that by creating the card-data boundary before money-moving card payments.

## 3. Exact Scope

In the later coding slice:

1. Add Vault Flyway migration for the `vault.card_tokens`, `vault.detokenize_audit_log` and `vault.key_versions` foundation if the baseline migration does not already contain production-ready shapes.
2. Add Issuer Flyway migration for `issuer.cards` minimal card records and any required uniqueness/state constraints.
3. Add service-auth enforcement for the narrow Platform → Issuer issuance call and Issuer → Vault tokenize/detokenize calls using the existing signed service-token direction from `planning/04_architecture.md` and `planning/design-details/access_matrix.md`.
4. Implement Vault tokenization:
   - generate a test PAN for the project-owned BIN;
   - store encrypted PAN, expiration metadata, token and status in Vault DB;
   - never persist CVV;
   - return only token, last4, expiration and BIN.
5. Implement Vault detokenize endpoint only for `service:issuer`, with append-only detokenize audit rows.
6. Implement Issuer card issuance:
   - accept a service-authenticated request from Platform;
   - call Vault tokenize;
   - persist `issuer.cards` with end-user/wallet link, token, last4, expiration, BIN and state `ACTIVE`;
   - return safe card metadata.
7. Implement Platform end-user `POST /api/v1/cards` as the user-facing entrypoint:
   - require end-user session;
   - require the existing write-control guard;
   - resolve/provision wallet identity through existing wallet/account foundation;
   - call Issuer internal issuance path;
   - return safe card metadata using the common `{data, errors}` response shape.
8. Add retained runtime scripts for:
   - PAN stored only in Vault;
   - non-issuer detokenize denial;
   - PAN log masking.
9. Run relevant Phase 05 checks and foundational regressions; record evidence/status in the implementation slice that performs the code.

## 4. Explicitly In Scope

### Code Changes Expected In The Later Coding Slice

- Vault service migration(s) under `product/apps/vault/src/main/resources/db/migration/`.
- Issuer service migration(s) under `product/apps/issuer/src/main/resources/db/migration/`.
- Vault Kotlin code for token generation, PAN encryption/decryption, token repository, tokenize endpoint, restricted detokenize endpoint and detokenize audit writer.
- Issuer Kotlin code for cards repository/service/controller and Vault client.
- Platform Kotlin code for the end-user `POST /api/v1/cards` controller/service and Issuer client.
- Shared/internal contract DTOs under `product/backend/libs/contracts-internal` if the existing service-contract shape requires it.
- Service-auth configuration/wiring for:
  - Platform → Issuer card issuance;
  - Issuer → Vault tokenize/detokenize.
- Logging mask configuration or filter for PAN-like values across `platform`, `issuer` and `vault`.
- Retained runtime scripts under `product/scripts/runtime/`.
- Documentation/status/evidence updates after implementation.

### Behavioral Outcomes

- `POST /api/v1/cards` issued by an authenticated end user creates one test card in `issuer.cards`.
- The response contains only safe card metadata:

```json
{
  "data": {
    "card": {
      "id": "card_...",
      "state": "ACTIVE",
      "last4": "1234",
      "expirationMonth": 12,
      "expirationYear": 2030,
      "bin": "400000"
    }
  },
  "errors": []
}
```

- The full PAN exists only in the Vault database, encrypted at column level or through the approved local equivalent for this slice.
- `issuer.cards` stores `card_token`, `last4`, expiration, BIN, state and user/wallet references, but no PAN and no CVV.
- Platform stores no card record in its own DB except existing user/wallet identity state; if a local read projection is needed later, it is explicitly out of this slice.
- `service:issuer` can call Vault `detokenize` and produces a `vault.detokenize_audit_log` row.
- `service:platform`, `service:acquirer`, `service:network` and unauthenticated callers cannot detokenize and receive a structured denial.
- PAN-like input/output is masked in general service logs; retained verification must search logs for the issued PAN and fail if it appears unmasked.
- Card initial state is `ACTIVE`; block/unblock/lost/replacement flows are not implemented here.

### Verification Outcomes

- `VLT-01` — pass when issuing a card stores PAN only in Vault and all non-vault stores contain only token/last4/expiration/BIN metadata.
- `VLT-02` — pass when non-issuer detokenize attempts are denied and issuer detokenize is audit-logged.
- `VLT-03` — pass when the issued PAN does not appear unmasked in `platform`, `issuer` or `vault` general application logs.

## 5. Explicitly Out Of Scope

This slice does **not** implement:

- payment authorization, capture, clearing, settlement, refunds or chargebacks;
- `PAY-04` approved authorization hold;
- `PAY-05` structured authorization declines;
- Acquirer → Network → Issuer authorization routing;
- merchant-facing payment intent transition beyond the existing shell;
- card blocking/unblocking, lost-card handling or replacement with new PAN;
- end-user full PAN reveal (`POST /api/v1/cards/{id}/reveal`) and its re-auth/rate-limit/read-audit flow;
- hosted payment form tokenization;
- server-to-server merchant card-data ingestion;
- CVV persistence;
- Kafka/outbox events for card issuance;
- UI prototypes or frontend implementation;
- compose topology changes;
- changes to approved baseline docs under `planning/01..06`, `planning/design-details/**` or `planning/runtime_checklists.md`;
- Stripe Connect onboarding (`MRC-01`), which remains blocked on real Stripe sandbox credentials.

Specifically:

- do not use hardcoded success responses or in-memory card stores;
- do not store PAN in Platform, Issuer, Acquirer, Network or runtime scripts' persistent outputs;
- do not log raw PAN for debugging;
- do not add fake authorization decisions; the next authorization slice owns those checks;
- do not broaden Vault detokenize access beyond `service:issuer`.

## 6. Service And Module Boundaries

### 6.1 Platform

Platform owns the end-user session and wallet identity context.

Expected route:

```http
POST /api/v1/cards
Cookie: MFP_SESSION=...
Idempotency-Key: <if dashboard/session idempotency wrapper supports this route>
```

Platform responsibilities:

- authenticate end user;
- enforce actor write controls (`FROZEN`/`BLOCKED` must deny card issuance);
- resolve or lazily provision the user's wallet account if the existing wallet foundation supports that pattern;
- call Issuer through an internal service-authenticated client;
- return only issuer-provided safe metadata to the SPA-facing API.

Platform must not:

- generate PAN;
- receive PAN from Vault;
- store card PAN or CVV;
- call Vault directly in this slice.

### 6.2 Issuer

Issuer owns card records and the card state machine foundation.

Expected internal route:

```http
POST /internal/issuer/cards
Authorization: ServiceToken platform -> issuer
```

Minimal request fields:

- `end_user_id`;
- `wallet_account_id` or stable wallet reference;
- `request_id` / `correlation_id`.

Minimal response fields:

- `card_id`;
- `state`;
- `card_token`;
- `last4`;
- `expiration_month`;
- `expiration_year`;
- `bin`.

Issuer responsibilities:

- validate service-authenticated Platform caller;
- call Vault tokenize as `service:issuer`;
- persist card record without PAN/CVV;
- enforce initial state `ACTIVE`;
- make card IDs/tokens externally opaque.

### 6.3 Vault

Vault owns card-data storage and detokenization.

Expected internal routes:

```http
POST /internal/vault/tokenize
Authorization: ServiceToken issuer -> vault

POST /internal/vault/detokenize
Authorization: ServiceToken issuer -> vault
```

Vault responsibilities:

- generate token values that are opaque and not derived from PAN;
- generate a deterministic-testable but non-hardcoded PAN shape for local runtime checks;
- store encrypted PAN and expiration metadata;
- never persist CVV;
- return only safe metadata from tokenize;
- return PAN only from detokenize to an authorized `service:issuer` caller;
- append a detokenize audit row for every allowed detokenize call;
- deny all non-issuer callers.

## 7. DB Migration Expectations

### 7.1 Vault DB

Expected `vault.card_tokens` shape:

```sql
create table vault.card_tokens (
    id uuid primary key,
    card_token text not null unique,
    pan_ciphertext bytea not null,
    pan_last4 text not null,
    bin text not null,
    expiration_month integer not null,
    expiration_year integer not null,
    status text not null check (status in ('ACTIVE','DEACTIVATED')),
    key_version integer not null,
    created_at timestamptz not null default now()
);
```

Expected `vault.detokenize_audit_log` shape:

```sql
create table vault.detokenize_audit_log (
    id uuid primary key,
    card_token text not null,
    caller_service text not null,
    outcome text not null check (outcome in ('ALLOWED','DENIED')),
    reason text,
    created_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);
```

Expected `vault.key_versions` shape:

```sql
create table vault.key_versions (
    version integer primary key,
    status text not null check (status in ('ACTIVE','RETIRED')),
    created_at timestamptz not null default now()
);
```

Notes:

- `pan_ciphertext` uses `pgcrypto` (`pgp_sym_encrypt`/`pgp_sym_decrypt`) or the approved local equivalent wired through env-based secret material.
- Key rotation can be represented by `key_versions`, but real rotation is later scope.
- `detokenize_audit_log` is append-only by app convention in this slice; DB-level append-only trigger can be added if low-friction.

### 7.2 Issuer DB

Expected `issuer.cards` shape:

```sql
create table issuer.cards (
    id uuid primary key,
    end_user_id uuid not null,
    wallet_account_id uuid not null,
    card_token text not null unique,
    last4 text not null,
    bin text not null,
    expiration_month integer not null,
    expiration_year integer not null,
    state text not null check (state in ('ACTIVE','BLOCKED','EXPIRED','LOST')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);
```

Notes:

- No `pan`, `pan_ciphertext`, `cvv`, `cvv_hash` or equivalent sensitive fields are allowed in Issuer DB.
- The coding slice should use UUID/public IDs for cross-service references, consistent with `schema_drafts.md`.
- `issuer.card_authorizations` and `issuer.holds` remain for the next authorization slice.

## 8. Service-Auth Expectations And Deliberate Cuts

The later coding slice should use the narrowest service-auth behavior sufficient to prove the Phase 05 boundary:

- Platform may call only Issuer card issuance.
- Issuer may call Vault tokenize and detokenize.
- Vault must validate both issuer identity and audience/scope-equivalent intent before returning PAN.
- Non-issuer service-token callers must be explicitly denied and included in `VLT-02` evidence.

Deliberate cuts:

- Full mTLS is not required in this MVP slice; signed service tokens are the accepted Phase 04/05 internal-auth baseline.
- Service-token rotation, JWKS distribution and per-endpoint fine-grained scopes are later hardening unless already present in shared service-auth.
- Vault `forward_to_issuer` remains out of scope until hosted-payment or authorization work needs it.
- Detokenize exists only to prove restricted access and audit; the next authorization slice may prefer `forward_to_issuer` if that better contains PAN.

## 9. API Endpoints For The Later Coding Slice

### 9.1 End-User API

```http
POST /api/v1/cards
```

Success:

```json
{
  "data": {
    "card": {
      "id": "card_...",
      "state": "ACTIVE",
      "last4": "1234",
      "expirationMonth": 12,
      "expirationYear": 2030,
      "bin": "400000"
    }
  },
  "errors": []
}
```

Expected errors:

- `401 unauthenticated`;
- `403 actor_control_blocked`;
- `409 card_issuance_conflict` for duplicate/concurrent issuance conflicts if idempotency cannot replay;
- `502 issuer_unavailable` if Issuer call fails;
- `502 vault_unavailable` surfaced through Issuer only if Vault call fails.

### 9.2 Issuer Internal API

```http
POST /internal/issuer/cards
Authorization: ServiceToken platform -> issuer
```

Expected errors:

- `401/403 service_auth_denied`;
- `409 duplicate_card_request` if the same idempotency/request key already created a card;
- `502 vault_unavailable`.

### 9.3 Vault Internal API

```http
POST /internal/vault/tokenize
Authorization: ServiceToken issuer -> vault

POST /internal/vault/detokenize
Authorization: ServiceToken issuer -> vault
```

Expected errors:

- `401/403 service_auth_denied`;
- `404 token_not_found`;
- `409 token_inactive`.

## 10. Runtime Scripts And Check IDs To Add

Expected retained scripts:

- `product/scripts/runtime/lib_phase05_vault_card.sh`
- `product/scripts/runtime/reg_phase05_card_issue_pan_isolation.sh`
- `product/scripts/runtime/reg_phase05_vault_detokenize_restriction.sh`
- `product/scripts/runtime/reg_phase05_pan_log_masking.sh`

This slice exercises:

- `VLT-01` — PAN stored only in Vault.
- `VLT-02` — Detokenize restriction.
- `VLT-03` — PAN log masking.

This slice should also rerun foundational regressions:

- `RUN-01` — all affected backend services healthy after adding real behavior to `platform`, `issuer` and `vault`.
- `LDG-05` or `LDG-99` — ledger reconciliation remains clean if the card issuance path touches wallet provisioning/ledger account lookup but should not create money movement.
- `AUTH-01`/`AUTH-05` subset — end-user session and protected-endpoint denial still work.
- `WLT-02` precursor — actor-control write guard denies frozen/blocked end users from issuing cards if existing scripts can cover the same guard.

No new check IDs are required. `PAY-04` and `PAY-05` are intentionally deferred to the next authorization slice.

## 11. Expected Evidence Updates

After implementation, append a new section to `planning/runtime_evidence_log.md` for:

```text
2026-..-.. — Phase 05 Slice 01 Vault/Card Issuance Runtime Verification
```

Evidence must include:

- actor used:
  - authenticated `end_user` for `POST /api/v1/cards`;
  - `service:issuer` for allowed detokenize;
  - at least one non-issuer service identity or unauthenticated caller for denied detokenize;
- preconditions:
  - Platform, Issuer and Vault services running;
  - Vault key material configured;
  - an end user with a wallet context exists;
- action taken:
  - issue card;
  - inspect Platform/Issuer/Vault persistence boundaries;
  - attempt detokenize as issuer and non-issuer;
  - scan logs for raw PAN;
- expected and actual results for `VLT-01`, `VLT-02`, `VLT-03`;
- verification script paths and the commit hash that contains retained scripts;
- result tags (`pass`, `partial` or `fail`) with explicit remaining gaps for any non-pass.

Update `planning/implementation_status.md` only after code/runtime verification, with factual state rather than a journal.

For this planning-only branch, `planning/implementation_status.md` does not need a runtime progress update unless the owner wants draft planning artifacts listed there.

## 12. Concrete Files To Touch In The Later Coding Slice

### 12.1 Modify

- `README.md`
- `CURRENT.md`
- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `product/README.md`
- `product/.env.example`
- `product/apps/platform/src/main/resources/application.yml`
- `product/apps/issuer/src/main/resources/application.yml`
- `product/apps/vault/src/main/resources/application.yml`
- shared service-auth configuration/classes if present.

### 12.2 Create

- Vault migration under `product/apps/vault/src/main/resources/db/migration/`.
- Issuer migration under `product/apps/issuer/src/main/resources/db/migration/`.
- Vault tokenization/detokenize Kotlin classes.
- Issuer card issuance Kotlin classes.
- Platform end-user cards API classes.
- Internal contract DTOs if needed.
- Retained runtime scripts listed in §10.

### 12.3 Do NOT Touch

- `planning/01_business_requirements.md` … `planning/06_implementation_guide.md`, `planning/design-details/**`, `planning/runtime_checklists.md` — approved baseline; existing check IDs already cover this slice.
- Acquirer and Network product code — the next authorization slice owns them.
- Public Payments API payment intent/capture/refund routes — out of scope.
- Frontend SPA code and standalone UI prototypes — out of scope.
- Compose topology — all five services already exist from Phase 01.

## 13. Recommended Change Order For The Later Coding Slice

1. Confirm current migration version numbers in `issuer` and `vault`.
2. Add Vault migration for token storage, key version and detokenize audit.
3. Add Issuer migration for minimal cards table.
4. Implement Vault tokenization, encryption and restricted detokenize.
5. Implement Issuer card issuance and Vault client.
6. Implement Platform `POST /api/v1/cards` and Issuer client.
7. Add log masking configuration/tests.
8. Add retained runtime scripts.
9. Build/start only the needed services using the assigned runtime slot.
10. Run `VLT-01`..`VLT-03` scripts and targeted regressions.
11. Update evidence/status/CURRENT/README/product README.
12. Commit the completed coding slice.

## 14. Linked Runtime Checks

This slice exercises:

- `VLT-01` — PAN stored only in Vault.
- `VLT-02` — Detokenize restriction.
- `VLT-03` — PAN log masking.

This slice **does not** exercise:

- `PAY-04` — approved authorization hold; next authorization slice.
- `PAY-05` — structured authorization declines; next authorization slice.
- `SET-*`, `WBH-*`, `CHB-*` — later phases.

## 15. Linked ADRs

- ADR-002 — SQL-first persistence for card/vault tables and sensitive data constraints.
- ADR-003 — selective services topology: Vault and Issuer remain separate deployment units.
- ADR-004 — ledger central source of truth; this slice does not create holds or money movement.

No new ADR is required for this planning note. A future ADR may be needed if the implementation changes the accepted Vault isolation model, chooses a non-Postgres encryption/key-management approach, or replaces signed service tokens with mTLS before the hardening phase.

## 16. Verification Shape

Expected coding-slice verification:

```bash
cd product
docker compose -f deploy/docker-compose.yml build platform issuer vault
docker compose -f deploy/docker-compose.yml up -d platform issuer vault
scripts/runtime/reg_phase05_card_issue_pan_isolation.sh
scripts/runtime/reg_phase05_vault_detokenize_restriction.sh
scripts/runtime/reg_phase05_pan_log_masking.sh
scripts/runtime/reg_phase01_runtime_health.sh
scripts/runtime/reg_phase03_ledger_reconciliation.sh
```

In executor branches, run these only with the runtime isolation variables assigned by the orchestrator for that branch.

The log-masking script must:

- capture the issued test PAN from an authorized Vault/Issuer-controlled path;
- query relevant service logs through the existing logs pipeline or Docker logs if the current runtime script convention uses that;
- fail if the raw PAN appears in general logs;
- allow masked forms such as `XXXX-XXXX-XXXX-1234` or equivalent.

## 17. Pass Criteria

Slice considered implemented only when:

- `POST /api/v1/cards` creates a card for an authenticated end user and returns only safe metadata;
- Vault DB contains encrypted PAN for the issued card token;
- Issuer DB contains token/last4/expiration/BIN/state/user/wallet references and no PAN/CVV-equivalent columns or values;
- Platform DB contains no PAN or issuer-owned card record;
- issuer detokenize succeeds and writes `vault.detokenize_audit_log`;
- non-issuer detokenize is denied and does not return PAN;
- raw PAN is absent from general service logs;
- retained scripts for `VLT-01`, `VLT-02` and `VLT-03` pass;
- `PAY-04` and `PAY-05` remain explicitly unclaimed for the next authorization slice;
- evidence/status files record the factual runtime result.

## 18. Risks And Open Questions

### 18.1 Risks

- **Service-auth gap** — if existing signed service-token support is still too skeletal, the coding slice must either harden only the narrow Platform→Issuer and Issuer→Vault paths or stop before weakening Vault access.
- **PAN encryption key handling** — local env-based key material is acceptable for MVP runtime, but key rotation and KMS are later hardening. The slice must not silently store plaintext PAN.
- **Log masking false confidence** — verification must search actual service logs after exercising issuance/detokenize, not only inspect configuration.
- **Wallet identity reference mismatch** — existing Platform wallet account IDs must be usable as stable cross-service references. If not, the slice should add a narrow stable reference without changing ledger semantics.

### 18.2 Open Questions

No owner-level open questions are required before implementation. The coding agent should resolve tactical details from the current codebase:

1. exact next Flyway version numbers for Vault and Issuer;
2. existing service-auth helper names and token format;
3. whether card issuance should require an explicit `Idempotency-Key` header or rely on session-route duplicate protection already present in Platform;
4. the project-owned test BIN value to use if no constant already exists.
