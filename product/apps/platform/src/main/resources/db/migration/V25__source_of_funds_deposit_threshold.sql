alter table wallet.deposit_requests
    drop constraint if exists deposit_requests_amount_check;

alter table wallet.deposit_requests
    add constraint deposit_requests_amount_check check (amount > 0 and amount <= 1000000.0000);

create table if not exists wallet.source_of_funds_declarations (
    id uuid primary key,
    deposit_request_id uuid not null unique references wallet.deposit_requests (id),
    user_id uuid not null references identity.end_users (id),
    source_category text not null check (source_category in ('SALARY', 'BUSINESS_INCOME', 'SAVINGS', 'INVESTMENT', 'OTHER')),
    description text not null,
    submitted_at timestamptz not null default now()
);

create index if not exists idx_sof_declarations_user
    on wallet.source_of_funds_declarations (user_id, submitted_at desc);
