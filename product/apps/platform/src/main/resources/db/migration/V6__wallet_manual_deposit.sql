create schema if not exists wallet;

-- system clearing account for manual deposits (idempotent seed via INSERT ... ON CONFLICT)
insert into ledger.accounts (
    id,
    code,
    currency,
    account_type,
    normal_side,
    owner_type,
    owner_id
)
values (
    gen_random_uuid(),
    'EXTERNAL_DEPOSIT_CLEARING',
    'EUR',
    'EXTERNAL_CLEARING',
    'DEBIT',
    null,
    null
)
on conflict (code) do nothing;

create table if not exists wallet.wallet_accounts (
    id uuid primary key,
    user_id uuid not null unique references identity.end_users (id),
    ledger_account_id uuid not null unique references ledger.accounts (id),
    currency text not null default 'EUR' check (currency = 'EUR'),
    created_at timestamptz not null default now()
);

create table if not exists wallet.deposit_requests (
    id uuid primary key,
    user_id uuid not null references identity.end_users (id),
    wallet_account_id uuid not null references wallet.wallet_accounts (id),
    amount numeric(20,4) not null check (amount > 0 and amount < 10000.0000),
    currency text not null default 'EUR' check (currency = 'EUR'),
    state text not null check (state in (
        'REQUESTED',
        'PENDING_OPERATOR_REVIEW',
        'APPROVED',
        'COMPLETED',
        'REJECTED'
    )),
    reason text,
    journal_entry_id uuid references ledger.journal_entries (id),
    decided_by_actor_type text,
    decided_by_actor_id uuid,
    decided_by_reference text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    decided_at timestamptz
);

create index if not exists idx_wallet_deposit_requests_state
    on wallet.deposit_requests (state);

create index if not exists idx_wallet_deposit_requests_user
    on wallet.deposit_requests (user_id);

create or replace function wallet.touch_deposit_request_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists trg_wallet_deposit_requests_touch on wallet.deposit_requests;
create trigger trg_wallet_deposit_requests_touch
before update on wallet.deposit_requests
for each row execute function wallet.touch_deposit_request_updated_at();
