create schema if not exists acquirer;

create table acquirer.payment_intents (
    id uuid primary key,
    merchant_id uuid not null,
    amount numeric(20,4) not null check (amount > 0),
    currency text not null check (currency = 'EUR'),
    state text not null check (state in ('CREATED','AUTHORIZED','FAILED')),
    card_token text,
    authorization_id uuid,
    auth_code text,
    decline_code text,
    decline_message text,
    platform_payment_intent_id uuid,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);

create index acquirer_payment_intents_merchant_idx on acquirer.payment_intents(merchant_id, created_at desc);

create table acquirer.authorization_attempts (
    id uuid primary key,
    payment_intent_id uuid not null references acquirer.payment_intents(id),
    merchant_id uuid not null,
    amount numeric(20,4) not null,
    currency text not null check (currency = 'EUR'),
    card_token text not null,
    status text not null check (status in ('APPROVED','DECLINED','ERROR')),
    authorization_id uuid,
    auth_code text,
    decline_code text,
    decline_message text,
    network_route_id uuid,
    created_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);
