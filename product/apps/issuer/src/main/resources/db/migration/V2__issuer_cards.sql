create schema if not exists issuer;

create table issuer.cards (
    id uuid primary key,
    end_user_id uuid not null,
    wallet_account_id uuid not null,
    card_token text not null unique,
    last4 text not null check (last4 ~ '^[0-9]{4}$'),
    bin text not null check (bin ~ '^[0-9]{6,8}$'),
    expiration_month integer not null check (expiration_month between 1 and 12),
    expiration_year integer not null check (expiration_year between 2026 and 2100),
    state text not null check (state in ('ACTIVE', 'BLOCKED', 'EXPIRED', 'LOST')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);

create index issuer_cards_end_user_id_idx on issuer.cards(end_user_id);
