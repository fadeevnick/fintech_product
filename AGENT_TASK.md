# AGENT TASK — Phase 06 Slice 05 Settlement Fee Split

You are an implementation agent working in your own branch/worktree.

## Read First

Read these files before editing:

1. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/README.md`
2. `CURRENT.md`
3. `planning/implementation_status.md`
4. `planning/runtime_evidence_log.md`
5. `planning/runtime_checklists.md`
6. `planning/implementation-slices/phase_06_slice_04_capture_to_settlement_foundation_planning.md`
7. `planning/implementation-slices/phase_06_slice_05_settlement_fee_split_planning.md`
8. `product/README.md`

## Task

Implement `Phase 06 Slice 05 — Settlement Fee Split` backend/runtime scope only.

Target check:

- `SET-02` — settlement fee postings balance exactly.

Use the approved planning note as the contract:

- `planning/implementation-slices/phase_06_slice_05_settlement_fee_split_planning.md`

## Required Scope

Implement deterministic local settlement fee split for settled card payments:

- merchant net amount;
- issuer interchange;
- network assessment;
- acquirer margin.

Use the planning-note local rates:

- interchange: `1.20%`;
- network assessment: `0.15%`;
- acquirer margin: `0.65%`;
- merchant net: gross minus those fees.

Persist fee components on settlement item or a narrow child table.

Update settlement ledger postings so the journal remains exactly balanced and exposes the fee destinations.

Keep settlement processing idempotent:

- no duplicate settlement item;
- no duplicate fee rows;
- no duplicate ledger journals on rerun.

Add retained runtime script:

- `product/scripts/runtime/reg_phase06_settlement_fee_split.sh`

Update factual docs after implementation:

- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`
- `product/README.md`
- `CURRENT.md`

## Required Runtime Verification

Run the new script and targeted regressions if feasible:

- `product/scripts/runtime/reg_phase06_settlement_fee_split.sh`
- `product/scripts/runtime/reg_phase06_capture_to_settlement.sh`
- `product/scripts/runtime/reg_phase05_payment_capture.sh`
- `product/scripts/runtime/reg_phase04_public_api_response_shape.sh`
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh`

Use a parameterized Docker Compose project so you do not conflict with other agents or the owner stack. Suggested slot:

```bash
COMPOSE_PROJECT_NAME=agent-set02
PLATFORM_HTTP_HOST_PORT=19281
PLATFORM_DB_HOST_PORT=19533
ACQUIRER_HTTP_HOST_PORT=19282
ACQUIRER_DB_HOST_PORT=19534
NETWORK_HTTP_HOST_PORT=19283
NETWORK_DB_HOST_PORT=19535
ISSUER_HTTP_HOST_PORT=19284
ISSUER_DB_HOST_PORT=19536
VAULT_HTTP_HOST_PORT=19285
VAULT_DB_HOST_PORT=19537
KEYCLOAK_HOST_PORT=29280
KAFKA_HOST_PORT=19992
```

When running retained scripts against this slot, prefer compose-network URLs if host-published HTTP hangs:

```bash
PLATFORM_BASE_URL=http://platform:8080
ACQUIRER_BASE_URL=http://acquirer:8080
NETWORK_BASE_URL=http://network:8080
ISSUER_BASE_URL=http://issuer:8080
VAULT_BASE_URL=http://vault:8080
PLATFORM_CURL_CONTAINER_NETWORK=agent-set02_default
```

After runtime checks finish, stop your compose project:

```bash
docker compose -p agent-set02 -f product/deploy/docker-compose.yml down
```

## Out Of Scope

Do not implement:

- `SET-03` acquirer settlement projection/reconciliation;
- `SET-04` refunds;
- payouts/Stripe Connect;
- chargebacks;
- merchant settlement frontend;
- tenant pricing UI;
- AML/KYC/sanctions changes.

Do not edit `AGENT_TASK.md` into the final product merge; it is temporary orchestration context only.

## Finish Criteria

Before handing back:

1. Ensure code compiles or clearly state the blocker.
2. Ensure runtime scripts are executable.
3. Record exact commands and results in `planning/runtime_evidence_log.md`.
4. Update implementation/status docs honestly; do not claim checks you did not run.
5. Stop your compose project.
6. Commit your implementation with a concise logical message.
