create extension if not exists pgcrypto;

create schema if not exists vault;

create table vault.key_versions (
    version integer primary key,
    status text not null check (status in ('ACTIVE', 'RETIRED')),
    created_at timestamptz not null default now()
);

insert into vault.key_versions (version, status)
values (1, 'ACTIVE')
on conflict (version) do nothing;

create table vault.card_tokens (
    id uuid primary key,
    card_token text not null unique,
    pan_ciphertext bytea not null,
    pan_last4 text not null check (pan_last4 ~ '^[0-9]{4}$'),
    bin text not null check (bin ~ '^[0-9]{6,8}$'),
    expiration_month integer not null check (expiration_month between 1 and 12),
    expiration_year integer not null check (expiration_year between 2026 and 2100),
    status text not null check (status in ('ACTIVE', 'DEACTIVATED')),
    key_version integer not null references vault.key_versions(version),
    created_at timestamptz not null default now()
);

create table vault.detokenize_audit_log (
    id uuid primary key,
    card_token text not null,
    caller_service text not null,
    outcome text not null check (outcome in ('ALLOWED', 'DENIED')),
    reason text,
    created_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);
