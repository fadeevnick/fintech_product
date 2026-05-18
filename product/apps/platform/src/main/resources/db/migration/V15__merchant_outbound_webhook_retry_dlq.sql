alter table merchant.webhook_events
    add column if not exists retry_count integer not null default 0,
    add column if not exists max_attempts integer not null default 3,
    add column if not exists next_retry_at timestamptz,
    add column if not exists last_attempt_at timestamptz,
    add column if not exists last_error_type text,
    add column if not exists last_error_message text,
    add column if not exists last_http_status integer,
    add column if not exists dlq_at timestamptz;

create index if not exists idx_merchant_webhook_events_due_retry
    on merchant.webhook_events (status, next_retry_at)
    where status = 'FAILED' and next_retry_at is not null;

create index if not exists idx_merchant_webhook_events_dlq_created
    on merchant.webhook_events (merchant_id, created_at desc)
    where status = 'DLQ';
