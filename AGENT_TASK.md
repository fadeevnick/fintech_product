# AGENT TASK — Phase 08 Slice 01 AML Velocity Alert Foundation

You are an implementation agent working in your own branch/worktree.

## Read First

Read these files before editing:

1. `/home/nickf/Documents/sre_projects/project-kit-short/project-method/README.md`
2. `CURRENT.md`
3. `planning/implementation_status.md`
4. `planning/runtime_evidence_log.md`
5. `planning/runtime_checklists.md`
6. `planning/implementation-slices/phase_08_slice_01_aml_velocity_alert_planning.md`
7. `product/README.md`

## Task

Implement `Phase 08 Slice 01 — AML Velocity Alert Foundation` backend/runtime scope only.

Target check:

- `AML-01` — synthetic activity trips velocity rule and opens alert.

Use the approved planning note as the contract:

- `planning/implementation-slices/phase_08_slice_01_aml_velocity_alert_planning.md`

## Required Scope

Implement AML velocity alert foundation in `platform`:

- add `aml` schema/table foundation for AML alerts;
- add Platform AML service/rule engine boundary;
- implement one deterministic local velocity rule;
- add an internal/local runtime trigger, recommended `POST /internal/aml/evaluate-velocity`;
- create one `OPEN` AML alert when the rule trips;
- suppress duplicate open alerts for the same user/rule/window on rerun;
- write audit event(s) for alert creation.

Add retained runtime script:

- `product/scripts/runtime/reg_phase08_aml_velocity_alert.sh`

Update factual docs after implementation:

- `planning/implementation_status.md`
- `planning/runtime_evidence_log.md`
- `planning/runtime_checklists.md`
- `product/README.md`
- `CURRENT.md`

## Required Runtime Verification

Run the new script and targeted regressions if feasible:

- `product/scripts/runtime/reg_phase08_aml_velocity_alert.sh`
- `product/scripts/runtime/reg_phase01_runtime_health.sh`
- `product/scripts/runtime/reg_phase03_ledger_reconciliation.sh` only if ledger tables are touched directly.

Use a parameterized Docker Compose project so you do not conflict with other agents or the owner stack. Suggested slot:

```bash
COMPOSE_PROJECT_NAME=agent-aml01
PLATFORM_HTTP_HOST_PORT=19381
PLATFORM_DB_HOST_PORT=19633
KEYCLOAK_HOST_PORT=29380
KAFKA_HOST_PORT=19993
```

When running retained scripts against this slot, prefer compose-network URLs if host-published HTTP hangs:

```bash
PLATFORM_BASE_URL=http://platform:8080
PLATFORM_CURL_CONTAINER_NETWORK=agent-aml01_default
```

After runtime checks finish, stop your compose project:

```bash
docker compose -p agent-aml01 -f product/deploy/docker-compose.yml down
```

## Out Of Scope

Do not implement:

- `AML-02` structuring;
- `AML-03` dormancy-break;
- `AML-04` critical auto-freeze;
- AML alert review decisions;
- account freeze/unfreeze;
- SoF threshold (`WLT-03`);
- two-eyes enforcement (`WLT-04`);
- AML frontend UI;
- SAR workflow;
- settlement/refund/chargeback changes.

Do not edit `AGENT_TASK.md` into the final product merge; it is temporary orchestration context only.

## Finish Criteria

Before handing back:

1. Ensure code compiles or clearly state the blocker.
2. Ensure runtime scripts are executable.
3. Record exact commands and results in `planning/runtime_evidence_log.md`.
4. Update implementation/status docs honestly; do not claim checks you did not run.
5. Stop your compose project.
6. Commit your implementation with a concise logical message.
