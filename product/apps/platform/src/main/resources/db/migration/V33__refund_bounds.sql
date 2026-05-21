alter table merchant.payment_intents
    drop constraint if exists payment_intents_state_check;

alter table merchant.payment_intents
    add constraint payment_intents_state_check
        check (state in (
            'REQUIRES_PAYMENT_METHOD',
            'AUTHORIZED',
            'FAILED',
            'CAPTURED',
            'SETTLED',
            'DISPUTED',
            'PARTIALLY_REFUNDED',
            'REFUNDED'
        ));

create table if not exists merchant.refunds (
    id uuid primary key,
    payment_intent_id uuid not null references merchant.payment_intents(id),
    merchant_id uuid not null references merchant.merchants(id),
    api_key_id uuid not null references merchant.api_keys(id),
    amount numeric(20,4) not null check (amount > 0),
    currency text not null check (currency = 'EUR'),
    reason text,
    state text not null check (state in ('SUCCEEDED')),
    ledger_journal_id uuid references ledger.journal_entries(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create index if not exists idx_merchant_refunds_payment_intent
    on merchant.refunds (payment_intent_id, created_at desc);

create index if not exists idx_merchant_refunds_merchant_created
    on merchant.refunds (merchant_id, created_at desc);
