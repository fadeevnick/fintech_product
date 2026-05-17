alter table merchant.webhook_endpoints
    add column if not exists signing_secret_hash text,
    add column if not exists secret_prefix text,
    add column if not exists secret_rotated_at timestamptz not null default now();

update merchant.webhook_endpoints
set signing_secret_hash = coalesce(signing_secret_hash, repeat('0', 64)),
    secret_prefix = coalesce(secret_prefix, 'legacy')
where signing_secret_hash is null or secret_prefix is null;

alter table merchant.webhook_endpoints
    alter column signing_secret_hash set not null,
    alter column secret_prefix set not null;

create table if not exists merchant.webhook_events (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    event_type text not null,
    aggregate_type text not null,
    aggregate_id uuid not null,
    payload jsonb not null,
    status text not null check (status in ('PENDING', 'DELIVERED', 'FAILED', 'DLQ')),
    created_at timestamptz not null default now(),
    delivered_at timestamptz,
    correlation_id text,
    request_id text,
    unique (event_type, aggregate_type, aggregate_id)
);

create table if not exists merchant.webhook_delivery_attempts (
    id uuid primary key,
    event_id uuid not null references merchant.webhook_events (id),
    endpoint_id uuid not null references merchant.webhook_endpoints (id),
    attempt_number integer not null,
    status text not null check (status in ('SUCCEEDED', 'FAILED')),
    http_status integer,
    response_body_snippet text,
    error_type text,
    error_message text,
    attempted_at timestamptz not null default now(),
    next_retry_at timestamptz,
    request_id text,
    correlation_id text,
    unique (event_id, endpoint_id, attempt_number)
);

create index if not exists idx_merchant_webhook_events_status_created
    on merchant.webhook_events (status, created_at);

create index if not exists idx_merchant_webhook_events_merchant_type_created
    on merchant.webhook_events (merchant_id, event_type, created_at desc);

create index if not exists idx_merchant_webhook_delivery_attempts_event_endpoint
    on merchant.webhook_delivery_attempts (event_id, endpoint_id, attempt_number);

create index if not exists idx_merchant_webhook_delivery_attempts_endpoint_attempted
    on merchant.webhook_delivery_attempts (endpoint_id, attempted_at desc);
