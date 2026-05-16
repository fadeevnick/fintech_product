-- Phase 04 Slice 01 — merchant API keys, public payment-intent shell and
-- public idempotency primitive.
--
-- Renumbered to V11 because a sibling branch grabbed V10 for Stripe Connect /
-- webhooks first; the two slices are independent and may be merged in either
-- order.

create schema if not exists idempotency;

create table if not exists merchant.api_keys (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    label text not null,
    key_prefix text not null,
    key_hash text not null unique,
    fingerprint text not null,
    status text not null check (status in ('ACTIVE', 'REVOKED')),
    created_by_employee_id uuid not null references identity.merchant_employees (id),
    last_used_at timestamptz,
    revoked_at timestamptz,
    revoked_by_employee_id uuid references identity.merchant_employees (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create index if not exists idx_merchant_api_keys_merchant
    on merchant.api_keys (merchant_id, created_at desc);

create index if not exists idx_merchant_api_keys_status_lookup
    on merchant.api_keys (status, key_hash);

create table if not exists merchant.payment_intents (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    api_key_id uuid not null references merchant.api_keys (id),
    amount numeric(20,4) not null check (amount > 0),
    currency text not null check (currency = 'EUR'),
    description text,
    state text not null check (state in ('REQUIRES_PAYMENT_METHOD')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create index if not exists idx_merchant_payment_intents_merchant
    on merchant.payment_intents (merchant_id, created_at desc);

-- Idempotency primitive scoped per (merchant, route, key) per
-- planning/03_functional_requirements.md.
--
-- response_status = 0 is the "reservation placeholder" sentinel: it means a
-- caller is currently executing the business operation under this key but has
-- not yet committed the cached response. The guard trigger below allows the
-- one-shot transition from placeholder to finalized and otherwise rejects
-- update/delete on this table.
create table if not exists idempotency.idempotency_keys (
    merchant_id uuid not null references merchant.merchants (id),
    idempotency_key text not null,
    method text not null,
    route text not null,
    request_fingerprint text not null,
    response_status integer not null,
    response_body text not null,
    created_at timestamptz not null default now(),
    primary key (merchant_id, route, idempotency_key)
);

create index if not exists idx_idempotency_keys_created_at
    on idempotency.idempotency_keys (created_at);

create or replace function idempotency.reject_idempotency_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'idempotency.idempotency_keys is append-only';
end;
$$;

create or replace function idempotency.guard_idempotency_update()
returns trigger
language plpgsql
as $$
begin
    if OLD.response_status <> 0 then
        raise exception 'idempotency.idempotency_keys row is immutable once finalized';
    end if;
    if NEW.merchant_id <> OLD.merchant_id
       or NEW.idempotency_key <> OLD.idempotency_key
       or NEW.route <> OLD.route
       or NEW.method <> OLD.method
       or NEW.request_fingerprint <> OLD.request_fingerprint then
        raise exception 'idempotency.idempotency_keys identity columns are immutable';
    end if;
    return NEW;
end;
$$;

drop trigger if exists trg_idempotency_keys_finalize_only on idempotency.idempotency_keys;
create trigger trg_idempotency_keys_finalize_only
before update on idempotency.idempotency_keys
for each row execute function idempotency.guard_idempotency_update();

drop trigger if exists trg_idempotency_keys_no_delete on idempotency.idempotency_keys;
create trigger trg_idempotency_keys_no_delete
before delete on idempotency.idempotency_keys
for each row execute function idempotency.reject_idempotency_mutation();
