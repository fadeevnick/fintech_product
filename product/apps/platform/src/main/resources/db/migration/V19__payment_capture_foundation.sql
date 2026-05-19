alter table merchant.payment_intents
    drop constraint if exists payment_intents_state_check;

alter table merchant.payment_intents
    add constraint payment_intents_state_check
        check (state in ('REQUIRES_PAYMENT_METHOD', 'AUTHORIZED', 'FAILED', 'CAPTURED'));

alter table merchant.payment_intents
    add column if not exists captured_at       timestamptz,
    add column if not exists captured_amount   numeric(19, 4),
    add column if not exists capture_request_id text;

create index if not exists idx_merchant_payment_intents_captured
    on merchant.payment_intents (merchant_id, captured_at desc)
    where state = 'CAPTURED';
