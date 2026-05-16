create table if not exists merchant.stripe_account_links (
    id uuid primary key,
    merchant_id uuid not null unique references merchant.merchants (id),
    stripe_account_id text not null unique,
    charges_enabled boolean not null default false,
    payouts_enabled boolean not null default false,
    details_submitted boolean not null default false,
    last_event_id text,
    last_event_received_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_stripe_account_links_account_id
    on merchant.stripe_account_links (stripe_account_id);

create table if not exists merchant.stripe_webhook_events (
    id uuid primary key,
    stripe_event_id text not null unique,
    event_type text not null,
    received_at timestamptz not null default now(),
    processed_at timestamptz,
    outcome text not null check (outcome in (
        'PROCESSED',
        'DUPLICATE',
        'IGNORED_UNKNOWN_ACCOUNT',
        'IGNORED_UNHANDLED_TYPE',
        'REJECTED_SIGNATURE',
        'REJECTED_TIMESTAMP',
        'REJECTED_PAYLOAD'
    )),
    payload_jsonb jsonb not null,
    signature_header text
);

create index if not exists idx_stripe_webhook_events_event_type
    on merchant.stripe_webhook_events (event_type);
