# Schema Drafts — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

---

## 1. Purpose

This artifact drafts service/schema ownership before Flyway migrations.

Rules:
- one PostgreSQL instance per application service;
- no cross-service joins;
- service APIs/events are the only cross-service data access path;
- ledger/audit/card-data tables require stricter append-only/security controls.

## 2. Platform Database

Database: `mfp_platform`.

Schemas:
- `identity`
- `kyc`
- `sanctions`
- `aml`
- `cases`
- `ledger`
- `wallet`
- `merchant`
- `audit`
- `outbox`
- `idempotency`

Initial tables:
- `identity.end_users`
- `identity.merchant_employees`
- `identity.backoffice_users`
- `identity.sessions`
- `identity.email_verifications`
- `ledger.accounts`
- `ledger.journal_entries`
- `ledger.postings`
- `wallet.wallet_accounts`
- `wallet.deposit_requests`
- `wallet.withdraw_requests`
- `wallet.internal_transfers`
- `wallet.sof_declarations`
- `cases.cases`
- `cases.case_actions`
- `cases.case_attachments`
- `audit.audit_log`
- `outbox.outbox_events`
- `idempotency.idempotency_keys`

Ledger notes:
- use `NUMERIC(20,4)` for amount;
- `currency` default `EUR`;
- no UPDATE/DELETE grants on append-only tables through app role;
- balanced journal entry enforcement via stored procedure or deferred trigger, final design before Phase 03.

## 3. Acquirer Database

Database: `mfp_acquirer`.

Schemas:
- `acquirer`
- `payment_intents`
- `merchant_settlement`
- `webhook`
- `outbox`

Initial tables:
- `acquirer.payment_intents`
- `acquirer.refunds`
- `acquirer.chargebacks`
- `merchant_settlement.balance_projection`
- `merchant_settlement.entries`
- `webhook.endpoints`
- `webhook.delivery_attempts`
- `webhook.dlq`
- `outbox.outbox_events`

## 4. Network Database

Database: `mfp_network`.

Schema: `network`.

Initial tables:
- `network.bin_registry`
- `network.authorization_routes`
- `network.clearing_batches`
- `network.clearing_items`
- `network.settlement_batches`
- `network.settlement_positions`
- `network.network_audit_log`

## 5. Issuer Database

Database: `mfp_issuer`.

Schemas:
- `issuer`
- `outbox`

Initial tables:
- `issuer.cards`
- `issuer.card_authorizations`
- `issuer.holds`
- `issuer.chargeback_initiations`
- `outbox.outbox_events`

## 6. Vault Database

Database: `mfp_vault`.

Schema: `vault`.

Initial tables:
- `vault.card_tokens`
- `vault.detokenize_audit_log`
- `vault.key_versions`

Security notes:
- PAN encrypted at column level;
- CVV never persisted;
- app logging masks PAN-like patterns;
- detokenize audit log append-only.

## 7. Common Columns

For domain tables unless inappropriate:
- `id`
- `created_at`
- `updated_at` for mutable state tables only;
- `version` for optimistic concurrency where state transitions occur;
- `request_id`
- `correlation_id`

Append-only tables should not have `updated_at`.

## 8. Resolution Notes

1. **Ledger balanced-entry enforcement** — use stored-procedure-only insert for ledger journal entries as the primary enforcement path. Add a defensive deferred trigger if feasible during Phase 03 schema implementation.
2. **ID strategy** — use UUID/public IDs for external API and cross-service references. BIGSERIAL/internal numeric IDs are acceptable inside a single service DB. Never expose internal numeric IDs in public API.
