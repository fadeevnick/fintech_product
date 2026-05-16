create table if not exists wallet.internal_transfers (
    id uuid primary key,
    sender_user_id uuid not null references identity.end_users (id),
    receiver_user_id uuid not null references identity.end_users (id),
    sender_wallet_account_id uuid not null references wallet.wallet_accounts (id),
    receiver_wallet_account_id uuid not null references wallet.wallet_accounts (id),
    amount numeric(20,4) not null check (amount > 0 and amount < 10000.0000),
    currency text not null default 'EUR' check (currency = 'EUR'),
    state text not null check (state in ('PENDING', 'COMPLETED')),
    journal_entry_id uuid unique references ledger.journal_entries (id),
    idempotency_key text,
    request_fingerprint text,
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    check (sender_user_id <> receiver_user_id)
);

create unique index if not exists idx_wallet_internal_transfers_sender_idempotency
    on wallet.internal_transfers (sender_user_id, idempotency_key)
    where idempotency_key is not null;

create index if not exists idx_wallet_internal_transfers_sender
    on wallet.internal_transfers (sender_user_id, created_at desc);

create index if not exists idx_wallet_internal_transfers_receiver
    on wallet.internal_transfers (receiver_user_id, created_at desc);
