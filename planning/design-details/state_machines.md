# State Machines — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

---

## 1. Purpose

This artifact captures explicit state machines that implementation must enforce and audit.

Rules:
- every transition is explicit;
- invalid transitions are rejected;
- transition actor/cause is audit logged;
- terminal states are immutable except through documented reversal/compensation flows.

## 2. Payment Intent

Owner: `acquirer`.

States:

```text
CREATED
AUTHORIZED
CAPTURED
SETTLED
FAILED
EXPIRED
REFUNDED
DISPUTED
CHARGED_BACK
```

Happy path:

```text
CREATED -> AUTHORIZED -> CAPTURED -> SETTLED
```

Allowed alternates:
- `CREATED -> FAILED`
- `AUTHORIZED -> EXPIRED`
- `AUTHORIZED -> CAPTURED -> REFUNDED`
- `SETTLED -> REFUNDED`
- `SETTLED -> DISPUTED -> CHARGED_BACK`

## 3. Card Authorization

Owner: `issuer`.

States:

```text
REQUESTED -> APPROVED -> HELD -> CAPTURED -> SETTLED
REQUESTED -> DECLINED
HELD -> EXPIRED
```

Rules:
- `APPROVED` must create or be followed by a ledger hold before merchant-visible success is final.
- `EXPIRED` must release hold.
- `DECLINED` must include structured reason.

## 4. KYC Case

Owner: `platform/kyc`.

```text
NOT_STARTED -> SUBMITTED -> IN_REVIEW -> APPROVED
                                      -> REJECTED
                                      -> NEEDS_RESUBMIT
```

Rules:
- email verification is required before `SUBMITTED`;
- Sumsub `consider` keeps case in `IN_REVIEW`;
- manual decision requires rationale.

## 5. Sanctions Hit

Owner: `platform/sanctions`.

```text
OPEN -> IN_REVIEW -> CLEARED_FALSE_POSITIVE
                  -> TRUE_MATCH
```

Rules:
- `TRUE_MATCH` permanently blocks account;
- `CLEARED_FALSE_POSITIVE` creates exception for same `(user, watchlist_entry)`.

## 6. AML Alert

Owner: `platform/aml`.

```text
OPEN -> IN_REVIEW -> CLOSED_FALSE_POSITIVE
                  -> ESCALATED
                  -> MARKED_FOR_SAR
                  -> ACCOUNT_FROZEN_PERMANENT
```

Rules:
- CRITICAL alert auto-creates active freeze;
- false-positive closure may unfreeze when no other active freeze reason exists.

## 7. Deposit Request

Owner: `platform/wallet`.

```text
REQUESTED -> PENDING_OPERATOR_REVIEW -> APPROVED -> COMPLETED
REQUESTED -> SOF_REQUIRED -> PENDING_OPERATOR_REVIEW
PENDING_OPERATOR_REVIEW -> READY_FOR_SECOND_REVIEW -> APPROVED
PENDING_OPERATOR_REVIEW -> REJECTED
```

Rules:
- amount > EUR 10k requires SoF before instructions;
- amount >= EUR 10k requires second actor approval.

## 8. Withdraw Request

Owner: `platform/wallet`.

```text
PENDING -> HELD -> READY_FOR_SECOND_REVIEW -> APPROVED -> COMPLETED
PENDING -> HELD -> APPROVED -> COMPLETED
PENDING -> HELD -> HELD_FOR_REVIEW
HELD -> REJECTED
```

Rules:
- hold placed before operator completion;
- rejection releases hold;
- same actor cannot do both approvals.

## 9. Payout

Owner: `acquirer`, Stripe adapter.

```text
INITIATED -> PAYOUT_PENDING -> PAYOUT_SUCCEEDED
                           -> PAYOUT_FAILED
```

Rules:
- initiation debits merchant settlement to Stripe clearing;
- failure reverses back to available balance.

## 10. Chargeback

Owner: shared; acquirer owns merchant-facing outer state.

```text
INITIATED -> MERCHANT_NOTIFIED -> EVIDENCE_SUBMITTED -> ARBITRATION -> WON
                                                                  -> LOST
MERCHANT_NOTIFIED -> MERCHANT_ACCEPTED
MERCHANT_NOTIFIED -> MERCHANT_DEADLINE_EXPIRED
```

Rules:
- `INITIATED` provisionally credits cardholder;
- `WON` reverses cardholder provisional credit;
- `LOST` makes cardholder credit permanent and debits merchant.

## 11. Resolution Notes

1. **Deposit initial state** — use `REQUESTED`. User action creates a request before it enters operator review.
2. **Merchant accepted chargeback** — keep `MERCHANT_ACCEPTED` as a separate internal terminal state; expose it externally as `lost` for merchant/cardholder-facing status.
