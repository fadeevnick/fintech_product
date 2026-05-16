create schema if not exists merchant;

create table if not exists merchant.merchants (
    id uuid primary key,
    company_name text not null,
    country text not null,
    business_type text not null,
    kyb_status text not null check (kyb_status in ('NOT_STARTED', 'PENDING', 'VERIFIED', 'REJECTED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table if not exists identity.merchant_employees (
    id uuid primary key,
    merchant_id uuid not null references merchant.merchants (id),
    email text not null,
    normalized_email text not null unique,
    password_hash text not null,
    role text not null check (role in ('merchant_admin', 'merchant_member')),
    status text not null check (status in ('EMAIL_UNVERIFIED', 'ACTIVE', 'BLOCKED', 'FROZEN')),
    email_verified_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create index if not exists idx_merchant_employees_merchant_id
    on identity.merchant_employees (merchant_id);

create table if not exists identity.merchant_email_verifications (
    id uuid primary key,
    merchant_employee_id uuid not null references identity.merchant_employees (id),
    token_hash text not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_merchant_email_verifications_employee_id
    on identity.merchant_email_verifications (merchant_employee_id);

alter table identity.sessions
    drop constraint if exists sessions_actor_type_check;

alter table identity.sessions
    add constraint sessions_actor_type_check
    check (actor_type in ('END_USER', 'MERCHANT_EMPLOYEE'));
