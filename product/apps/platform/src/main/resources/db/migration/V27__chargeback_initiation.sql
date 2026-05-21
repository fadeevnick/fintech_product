create schema if not exists cards;
create schema if not exists chargeback;

create table if not exists cards.issued_cards (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    wallet_account_id uuid not null references wallet.wallet_accounts(id),
    card_token text unique,
    state text not null,
    last4 text not null,
    bin text not null,
    expiration_month integer not null,
    expiration_year integer not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_issued_cards_end_user
    on cards.issued_cards (end_user_id, created_at desc);

alter table merchant.payment_intents
    drop constraint if exists payment_intents_state_check;

alter table merchant.payment_intents
    add constraint payment_intents_state_check
        check (state in ('REQUIRES_PAYMENT_METHOD', 'AUTHORIZED', 'FAILED', 'CAPTURED', 'SETTLED', 'DISPUTED'));

create table if not exists chargeback.disputes (
    id uuid primary key,
    payment_intent_id uuid not null references merchant.payment_intents(id),
    merchant_id uuid not null references merchant.merchants(id),
    cardholder_user_id uuid not null references identity.end_users(id),
    amount numeric(20,4) not null check (amount > 0),
    currency text not null check (currency = 'EUR'),
    reason_code text not null check (reason_code in ('fraud_no_authorization','goods_not_received','goods_not_as_described','duplicate_charge')),
    narrative text,
    state text not null check (state in ('MERCHANT_NOTIFIED','EVIDENCE_SUBMITTED','ARBITRATION','WON','LOST','MERCHANT_ACCEPTED','MERCHANT_DEADLINE_EXPIRED')),
    merchant_response_deadline timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    unique (payment_intent_id)
);

create index if not exists idx_chargeback_disputes_cardholder_created
    on chargeback.disputes (cardholder_user_id, created_at desc);

create index if not exists idx_chargeback_disputes_merchant_created
    on chargeback.disputes (merchant_id, created_at desc);
