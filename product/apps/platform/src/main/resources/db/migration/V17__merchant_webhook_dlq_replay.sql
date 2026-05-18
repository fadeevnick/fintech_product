alter table merchant.webhook_delivery_attempts
    add column if not exists trigger_type text not null default 'AUTO'
        check (trigger_type in ('AUTO', 'MANUAL_REPLAY'));

create index if not exists idx_merchant_webhook_delivery_attempts_manual_replay
    on merchant.webhook_delivery_attempts (event_id, attempted_at desc)
    where trigger_type = 'MANUAL_REPLAY';
