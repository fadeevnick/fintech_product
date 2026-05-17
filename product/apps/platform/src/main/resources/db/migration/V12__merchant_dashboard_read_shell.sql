-- Phase 04 Slice 03 — merchant dashboard payment read shell and webhook endpoint configuration.

create table if not exists merchant.webhook_endpoints (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    url text not null,
    enabled_events jsonb not null,
    status text not null check (status in ('ACTIVE', 'DISABLED', 'DELETED')),
    description text,
    created_by_employee_id uuid references identity.merchant_employees (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    version bigint not null default 0,
    constraint merchant_webhook_endpoint_url_not_blank check (length(trim(url)) > 0)
);

create index if not exists idx_merchant_webhook_endpoints_merchant
    on merchant.webhook_endpoints (merchant_id, status, created_at desc);

create unique index if not exists uq_merchant_webhook_endpoints_active_url
    on merchant.webhook_endpoints (merchant_id, url)
    where status <> 'DELETED';

create index if not exists idx_merchant_payment_intents_dashboard
    on merchant.payment_intents (merchant_id, created_at desc, id desc);
