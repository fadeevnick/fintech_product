alter table merchant.payment_intents drop constraint if exists payment_intents_state_check;
alter table merchant.payment_intents add constraint payment_intents_state_check check (state in ('REQUIRES_PAYMENT_METHOD','AUTHORIZED','FAILED'));
alter table merchant.payment_intents add column if not exists card_token text;
alter table merchant.payment_intents add column if not exists authorization_id uuid;
alter table merchant.payment_intents add column if not exists auth_code text;
alter table merchant.payment_intents add column if not exists decline_code text;
alter table merchant.payment_intents add column if not exists decline_message text;
