create schema if not exists settlement;

alter table merchant.payment_intents
    drop constraint if exists payment_intents_state_check;

alter table merchant.payment_intents
    add constraint payment_intents_state_check
        check (state in ('REQUIRES_PAYMENT_METHOD', 'AUTHORIZED', 'FAILED', 'CAPTURED', 'SETTLED'));

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
    'CARD_SETTLEMENT_CLEARING',
    'EUR',
    'CARD_SETTLEMENT_CLEARING',
    'DEBIT',
    null,
    null
)
on conflict (code) do nothing;

create table if not exists settlement.settlement_batches (
    id uuid primary key,
    status text not null check (status in ('SETTLED')),
    created_at timestamptz not null default now(),
    settled_at timestamptz not null default now()
);

create table if not exists settlement.settlement_items (
    id uuid primary key,
    batch_id uuid not null references settlement.settlement_batches (id),
    payment_intent_id uuid not null references merchant.payment_intents (id),
    merchant_id uuid not null references merchant.merchants (id),
    gross_amount numeric(19, 4) not null check (gross_amount > 0),
    currency text not null,
    status text not null check (status in ('SETTLED')),
    ledger_journal_id uuid not null references ledger.journal_entries (id),
    created_at timestamptz not null default now(),
    unique (payment_intent_id)
);

create index if not exists idx_settlement_items_merchant_created
    on settlement.settlement_items (merchant_id, created_at desc);

create index if not exists idx_settlement_items_batch
    on settlement.settlement_items (batch_id);
