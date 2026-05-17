create table issuer.card_authorizations (
    id uuid primary key,
    card_id uuid references issuer.cards(id),
    card_token text not null,
    end_user_id uuid,
    wallet_account_id uuid,
    amount numeric(20,4) not null,
    currency text not null check (currency = 'EUR'),
    state text not null check (state in ('REQUESTED','APPROVED','HELD','DECLINED')),
    auth_code text,
    decline_code text,
    decline_message text,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    request_id text,
    correlation_id text
);

create table issuer.holds (
    id uuid primary key,
    authorization_id uuid not null references issuer.card_authorizations(id),
    card_token text not null,
    wallet_account_id uuid not null,
    ledger_journal_entry_id uuid not null,
    amount numeric(20,4) not null,
    currency text not null check (currency = 'EUR'),
    state text not null check (state in ('HELD','RELEASED','CAPTURED','EXPIRED')),
    expires_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
