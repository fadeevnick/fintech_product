create schema if not exists network;

create table network.bin_registry (
    bin text primary key,
    issuer_service text not null,
    status text not null check (status in ('ACTIVE','DISABLED')),
    created_at timestamptz not null default now()
);

insert into network.bin_registry (bin, issuer_service, status)
values ('400000', 'issuer', 'ACTIVE')
on conflict (bin) do nothing;

create table network.authorization_routes (
    id uuid primary key,
    payment_intent_id uuid not null,
    merchant_id uuid not null,
    amount numeric(20,4) not null,
    currency text not null check (currency = 'EUR'),
    card_token text not null,
    routed_bin text,
    issuer_service text,
    status text not null check (status in ('ROUTED','APPROVED','DECLINED','ERROR')),
    authorization_id uuid,
    decline_code text,
    decline_message text,
    request_id text,
    correlation_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table network.network_audit_log (
    id uuid primary key,
    route_id uuid references network.authorization_routes(id),
    event_type text not null,
    details jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
