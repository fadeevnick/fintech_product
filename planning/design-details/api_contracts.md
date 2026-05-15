# API Contracts — Mini Fintech Platform

Status: **APPROVED v0.2** (approved by project owner 2026-05-15).

---

## 1. Purpose

This artifact defines implementation-near API surfaces before code.

Detailed request/response schemas will be refined slice-by-slice, but the route ownership, auth mode, error shape and idempotency requirements are fixed here.

## 2. Common Response Shape

Public and SPA-facing APIs return:

```json
{
  "data": {},
  "errors": []
}
```

Error item:

```json
{
  "code": "string",
  "message": "string",
  "field": "optional.string",
  "hint": "optional.string"
}
```

Internal APIs may return leaner JSON but must include `request_id` in logs/traces.

## 3. Auth Modes

| Surface | Auth |
|---|---|
| End-user SPA API | Platform opaque session cookie + CSRF |
| Merchant dashboard API | Platform opaque session cookie + CSRF |
| Backoffice API | Keycloak OIDC access token + RBAC |
| Public Payments API | Merchant API key + idempotency key for writes |
| Internal service API | Signed service token |
| Vendor webhooks | Provider signature verification + idempotency by vendor event ID |

## 4. Route Groups

### 4.1 Platform End-User API

Base host: `app.miniefin.local`

| Method | Route | Purpose | Auth | Idempotency |
|---|---|---|---|---|
| POST | `/api/v1/enduser/register` | Register end user | none | no |
| POST | `/api/v1/enduser/login` | Login | none | no |
| POST | `/api/v1/enduser/email/verify` | Verify email token | none | yes |
| GET | `/api/v1/enduser/me` | Current user/session | session | no |
| POST | `/api/v1/kyc/start` | Start Sumsub KYC | session | yes |
| GET | `/api/v1/wallet` | Wallet balance/history summary | session | no |
| POST | `/api/v1/deposits` | Request deposit | session | yes |
| POST | `/api/v1/withdrawals` | Request withdraw | session | yes |
| POST | `/api/v1/transfers` | Internal transfer | session | yes |
| POST | `/api/v1/cards` | Issue card | session | yes |
| POST | `/api/v1/cards/{id}/block` | Block card | session | yes |
| POST | `/api/v1/cards/{id}/reveal` | Full PAN reveal | session + re-auth | yes |
| POST | `/api/v1/disputes` | Initiate chargeback | session | yes |

### 4.2 Merchant Dashboard API

Base host: `merchants.miniefin.local`

| Method | Route | Purpose | Auth | Idempotency |
|---|---|---|---|---|
| POST | `/api/v1/merchant/register` | Register merchant employee/entity | none/session | yes |
| POST | `/api/v1/merchant/stripe/onboarding-link` | Create Stripe Account Link | session | yes |
| GET | `/api/v1/merchant/status` | Merchant KYB/status | session | no |
| POST | `/api/v1/merchant/api-keys` | Generate key | merchant_admin | yes |
| POST | `/api/v1/merchant/api-keys/{id}/revoke` | Revoke key | merchant_admin | yes |
| PUT | `/api/v1/merchant/webhook-endpoint` | Configure webhook URL/events | merchant_admin | yes |
| GET | `/api/v1/payments` | Payment list | merchant scope | no |
| POST | `/api/v1/payments/{id}/refunds` | Refund from dashboard | merchant_admin | yes |
| GET | `/api/v1/disputes` | Dispute list | merchant scope | no |
| POST | `/api/v1/disputes/{id}/evidence` | Submit evidence | merchant scope | yes |
| GET | `/api/v1/settlements` | Settlement list | merchant scope | no |
| POST | `/api/v1/payouts` | Trigger Stripe payout | merchant_admin | yes |

### 4.3 Backoffice API

Base host: `back.miniefin.local`

| Method | Route | Purpose | Role |
|---|---|---|---|
| GET | `/api/v1/backoffice/users` | User search/list | operator+ |
| GET | `/api/v1/backoffice/users/{id}` | User detail | operator+, scoped/read-audited if sensitive |
| GET | `/api/v1/backoffice/kyc-cases` | KYC queue | operator+ |
| POST | `/api/v1/backoffice/kyc-cases/{id}/decision` | Manual KYC decision | operator+ |
| GET | `/api/v1/backoffice/aml-alerts` | AML queue | operator/compliance by severity |
| POST | `/api/v1/backoffice/aml-alerts/{id}/decision` | AML decision | operator/compliance by severity |
| GET | `/api/v1/backoffice/sanctions-hits` | Sanctions queue | compliance+ |
| POST | `/api/v1/backoffice/sanctions-hits/{id}/decision` | Sanctions decision | compliance+ |
| GET | `/api/v1/backoffice/manual-ops/deposits` | Pending deposits | operator+ |
| POST | `/api/v1/backoffice/manual-ops/deposits/{id}/decision` | Process deposit | operator+, two-eyes when needed |
| GET | `/api/v1/backoffice/audit-log` | Audit viewer | compliance/senior |

### 4.4 Public Payments API

Base host: `api.miniefin.local`

| Method | Route | Purpose | Auth | Idempotency |
|---|---|---|---|---|
| POST | `/v1/payment_intents` | Create payment intent | API key | required |
| POST | `/v1/payment_intents/{id}/capture` | Capture | API key | required |
| POST | `/v1/payment_intents/{id}/refund` | Refund | API key | required |
| GET | `/v1/payment_intents/{id}` | Get status | API key | no |
| GET | `/v1/payments` | List payments | API key | no |
| POST | `/v1/disputes/{id}/submit_evidence` | Evidence via API | API key | required |
| GET | `/v1/settlements` | Settlement list | API key | no |
| POST | `/v1/payouts` | Payout | API key | required |

### 4.5 Internal APIs

Namespace: `/internal/<context>/<operation>`.

Initial internal routes:
- `POST /internal/ledger/postings`
- `GET /internal/ledger/balance`
- `POST /internal/audit/write`
- `POST /internal/case/open`
- `GET /internal/wallet/account/{user_id}`
- `POST /internal/vault/tokenize`
- `POST /internal/vault/detokenize`
- `POST /internal/network/authorize`
- `POST /internal/issuer/authorize`

## 5. Vendor Webhooks

| Provider | Route | Verification | Idempotency |
|---|---|---|---|
| Sumsub | `/webhooks/sumsub/v1` | Sumsub HMAC | vendor event ID |
| Stripe | `/webhooks/stripe/v1` | Stripe signature | Stripe event ID |

## 6. Resolution Notes

1. **Dashboard refund route** — merchant dashboard uses a separate dashboard API route (`POST /api/v1/payments/{id}/refunds`) that calls the same application service as public API refund. It does not call public API over HTTP internally.
2. **Public refund route naming** — keep `POST /v1/payment_intents/{id}/refund` for MVP because approved FRs list that route. Plural `/refunds` can be reconsidered during later public API cleanup.
