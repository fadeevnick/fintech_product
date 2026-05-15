# Access Matrix — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

Inputs: 01 APPROVED v0.3, 02 APPROVED v0.2, 03 APPROVED v0.2, 04 APPROVED v0.2, 05 APPROVED v0.4, 06 APPROVED v0.2.

---

## 1. Purpose

This artifact defines role, service and resource access rules before implementation.

It is the source for:
- backend RBAC middleware;
- UI navigation visibility;
- read-audit requirements;
- runtime check IDs in `planning/runtime_checklists.md`.

## 2. Actor Types

| Actor | Auth source | Primary surfaces |
|---|---|---|
| `end_user` | Platform session cookie | End-user web, hosted payment form |
| `merchant_admin` | Platform session cookie | Merchant dashboard, Public Payments API via API key |
| `merchant_member` | Platform session cookie | Merchant dashboard read/limited actions |
| `backoffice_operator` | Keycloak OIDC | Backoffice workflow UI |
| `compliance_officer` | Keycloak OIDC | Backoffice workflow UI |
| `senior_compliance` | Keycloak OIDC | Backoffice workflow UI |
| `service:acquirer` | Signed service token | Platform internal APIs, Network internal APIs |
| `service:network` | Signed service token | Issuer/Acquirer internal APIs |
| `service:issuer` | Signed service token | Platform Ledger, Vault detokenize, Network chargeback |
| `service:vault` | Signed service token for outbound audit only | Platform audit internal API |

## 3. User-Facing Access Matrix

| Capability | end_user | merchant_admin | merchant_member | backoffice_operator | compliance_officer | senior_compliance |
|---|---:|---:|---:|---:|---:|---:|
| Register/login own pool | yes | yes | yes | no, OIDC only | no, OIDC only | no, OIDC only |
| View own wallet/cards/history | own | no | no | read by case need | read by case need | read all compliance scope |
| Request deposit/withdraw | own | no | no | no | no | no |
| Internal transfer | own sender only | no | no | no | no | no |
| Issue/block own card | own | no | no | no | no | no |
| Initiate chargeback | own payment only | no | no | no | no | no |
| Manage merchant API keys | no | own merchant | no | no | no | emergency read only |
| Configure merchant webhooks | no | own merchant | no | no | no | emergency read only |
| Create refunds | no | own merchant | no | no | no | no |
| Submit chargeback evidence | no | own merchant | own merchant | no | no | no |
| Process manual deposit/withdraw | no | no | no | yes, with RBAC limits | yes | yes |
| Second approval high-value ops | no | no | no | yes, different operator | yes, different actor | yes / override |
| KYC review queue | no | no | no | yes | yes | yes |
| AML low/medium alerts | no | no | no | yes | yes | yes |
| AML high/critical/frozen accounts | no | no | no | no | yes | yes |
| Sanctions hit queue | no | no | no | no | yes | yes |
| Account freeze/unfreeze | no | no | no | no | yes | yes |
| Audit log viewer | no | no | no | limited | yes | yes |
| Vault detokenize | no | no | no | no | no direct UI access | no direct UI access |

Legend:
- `own` means server-side ownership check is mandatory.
- `read by case need` means access must be linked to active case/review context and read-audited when sensitive.

## 4. Service Access Matrix

| Caller | Target | Allowed operations | Auth |
|---|---|---|---|
| `acquirer` | `platform` | ledger postings, ledger balance/projection reconciliation, case open, audit write, merchant lookup | signed service token |
| `issuer` | `platform` | wallet lookup, ledger balance, hold posting, actual debit/credit, case open, audit write | signed service token |
| `network` | `issuer` | authorization route, clearing message, chargeback route | signed service token |
| `acquirer` | `network` | authorization request, capture/clearing submission, chargeback receive route | signed service token |
| `issuer` | `vault` | tokenize response linkage, detokenize, forward-to-issuer path | signed service token, `iss=issuer`, `aud=vault` |
| any non-issuer service | `vault.detokenize` | denied | n/a |

## 5. Read-Audit Requirements

Synchronous read-audit required before returning:
- sanctions hit details;
- AML HIGH/CRITICAL alert details;
- frozen account review;
- full PAN reveal;
- KYC document preview;
- audit log viewer access.

Asynchronous audit acceptable for:
- regular state-changing domain events;
- non-sensitive list views;
- public API call fingerprint events.

## 6. Resolution Notes

1. **Merchant refunds** — `merchant_admin` only for MVP. `merchant_member` cannot trigger refunds. Merchant-level fine-grained permissions are later scope.
2. **KYC document preview** — `backoffice_operator` may view KYC document previews only inside KYC review queue, with rate limits and synchronous read-audit. Compliance roles retain broader compliance-sensitive access.
