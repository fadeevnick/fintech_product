# Cut Register — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

---

## 1. Purpose

This register prevents hidden fake scope.

Every non-implemented capability must be explicitly one of:
- `cut`
- `manual`
- `later scope`

No code path may pretend a cut feature works.

## 2. MVP Manual Capabilities

| Capability | Form | Notes |
|---|---|---|
| End-user deposit bank rail | manual | Backoffice operator confirms received external transfer. |
| End-user withdraw bank rail | manual | Operator approves and performs external transfer outside system. |
| Chargeback arbitration decision | manual | Backoffice simulates card network arbitration. |
| Merchant settlement bank outflow | manual or Stripe sandbox | Stripe Connect test payout where possible; manual fallback. |

## 3. Later Scope

| Capability | Status | Reason |
|---|---|---|
| Multi-currency | later scope | MVP EUR only. |
| MFA | later scope | Valuable but not required for MVP learning path. |
| PEP screening | later scope | Sanctions screening first. |
| ML fraud detection | later scope | Hand-coded AML rules first. |
| Full KYB beyond Stripe | later scope | Stripe Connect KYB enough for MVP. |
| 3DS/SCA | later scope | Adds complexity after card lifecycle baseline. |
| Cloud/k8s production deployment | later scope | MVP local Docker Compose. |
| Real banking rails | non-goal | Requires license/partner bank. |
| Real PCI DSS certification | non-goal | PCI-like patterns only. |
| Real regulator filings | non-goal | No regulated entity status. |

## 4. Explicitly Forbidden Fake Implementations

| Area | Forbidden fake |
|---|---|
| Email | `console.log` instead of Mailpit/SMTP send path. |
| Vendor integrations | `dev_mock` success instead of sandbox/API call. |
| Persistence | in-memory state for persistent domain data. |
| Ledger | direct balance mutation outside ledger postings. |
| Webhooks | logging event instead of signed HTTP delivery attempt. |
| Object storage | storing only filename without actual object write. |

## 5. Resolution Notes

No open questions in v0.1. v0.2 keeps the same cut/manual/later-scope register.
