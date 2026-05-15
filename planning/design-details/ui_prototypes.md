# UI Prototypes — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

---

## 1. Purpose

This artifact defines workflow-level UI prototypes for the three SPAs.

It is not pixel-perfect design. It defines screens, navigation, actions, states, RBAC visibility, audit/read-audit points and links to API/runtime checks.

## 2. UI Surfaces

| Surface | Host | Primary roles | Purpose |
|---|---|---|---|
| End-user web | `app.miniefin.local` | `end_user` | Wallet, KYC, card, transfer, deposit/withdraw, disputes |
| Merchant dashboard | `merchants.miniefin.local` | `merchant_admin`, `merchant_member` | Onboarding, API keys, payments, refunds, webhooks, settlements, disputes |
| Backoffice workflow UI | `back.miniefin.local` | `backoffice_operator`, `compliance_officer`, `senior_compliance` | Manual ops, KYC/AML/sanctions queues, chargeback arbitration, audit |

## 3. End-User Web Screen Inventory

| Screen ID | Screen | Purpose | Critical actions |
|---|---|---|---|
| `UEW-UI-01` | Login/register | Identity entry | register, login |
| `UEW-UI-02` | KYC start/status | Sumsub flow entry | start KYC, view status |
| `UEW-UI-03` | Wallet home | Balance/history | request deposit, withdraw, transfer |
| `UEW-UI-04` | Deposit request | Manual deposit request | SoF when threshold requires |
| `UEW-UI-05` | Transfer | Internal transfer | submit transfer |
| `UEW-UI-06` | Cards | Card list/detail | issue, block, reveal PAN |
| `UEW-UI-07` | Transaction detail | Payment details | initiate dispute |

## 4. Merchant Dashboard Screen Inventory

| Screen ID | Screen | Purpose | Critical actions |
|---|---|---|---|
| `MDB-UI-01` | Login/register | Merchant identity entry | register/login |
| `MDB-UI-02` | Onboarding status | Stripe Connect flow | start onboarding |
| `MDB-UI-03` | API keys | Key management | generate/revoke |
| `MDB-UI-04` | Webhooks | Endpoint config and DLQ | configure/replay |
| `MDB-UI-05` | Payments | Payment list/detail | refund |
| `MDB-UI-06` | Settlements | Balance/batches | payout |
| `MDB-UI-07` | Disputes | Chargeback evidence | accept/represent |

## 5. Backoffice Screen Inventory

| Screen ID | Screen | Purpose | Critical actions |
|---|---|---|---|
| `BOF-UI-01` | OIDC login landing | Backoffice auth | login |
| `BOF-UI-02` | Work queue home | Queue routing | select queue |
| `BOF-UI-03` | Manual deposits | Process deposit | approve/reject/second review |
| `BOF-UI-04` | Manual withdrawals | Process withdraw | approve/reject/complete |
| `BOF-UI-05` | KYC queue | Manual KYC review | approve/reject/resubmit |
| `BOF-UI-06` | AML alerts | Alert review | close/escalate/freeze |
| `BOF-UI-07` | Sanctions hits | Sanctions decision | clear/true match |
| `BOF-UI-08` | Chargeback arbitration | Network decision simulation | won/lost |
| `BOF-UI-09` | Audit log | Compliance audit viewer | search/view |

## 6. Workflow Prototype — Manual Deposit

Actor: end user + backoffice operator.

Screens:
1. `UEW-UI-04` — user creates deposit request.
2. `BOF-UI-03` — operator reviews pending request.
3. `UEW-UI-03` — user sees balance after completion.

States:
- `deposit_request`: `REQUESTED -> PENDING_OPERATOR_REVIEW -> APPROVED -> COMPLETED`
- high value: `PENDING_OPERATOR_REVIEW -> READY_FOR_SECOND_REVIEW -> APPROVED`

Audit:
- `deposit.requested`
- `deposit.processed`
- two-eyes audit for high-value operations.

## 7. Workflow Prototype — Merchant Payment

Actor: merchant admin/API caller + end user cardholder.

Screens:
1. `MDB-UI-05` — merchant sees payment state.
2. Hosted payment form later surface — card tokenization.
3. `UEW-UI-07` — cardholder sees transaction.

States:
- `payment_intent`: `CREATED -> AUTHORIZED -> CAPTURED -> SETTLED`
- `card_authorization`: `REQUESTED -> APPROVED -> HELD -> CAPTURED`

Audit:
- merchant API call fingerprint;
- payment lifecycle events;
- webhook delivery.

## 8. Workflow Prototype — Backoffice Compliance Review

Actor: compliance officer.

Screens:
1. `BOF-UI-06` — AML alert queue.
2. User/case detail panel.
3. `BOF-UI-09` — audit viewer when needed.

States:
- `aml_alert`: `OPEN -> IN_REVIEW -> CLOSED_FALSE_POSITIVE | ESCALATED | ACCOUNT_FROZEN_PERMANENT`

Read-audit:
- AML HIGH/CRITICAL read;
- frozen account review;
- audit log viewer access.

## 9. Resolution Notes

1. **Backoffice case review layout** — use detail pages for MVP. Evidence, documents, audit history and decision rationale need more space than a drawer.
2. **Hosted payment form surface** — document it as a separate hosted payment surface. Implement later as the smallest secure static/payment page under the approved host, not as a full fourth dashboard SPA.
