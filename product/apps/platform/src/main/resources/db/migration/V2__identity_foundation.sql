create schema if not exists identity;
create schema if not exists audit;

create table if not exists identity.end_users (
    id uuid primary key,
    email text not null,
    normalized_email text not null unique,
    password_hash text not null,
    status text not null check (status in ('EMAIL_UNVERIFIED', 'ACTIVE', 'BLOCKED', 'FROZEN')),
    email_verified_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0
);

create table if not exists identity.email_reservations (
    normalized_email text primary key,
    pool text not null check (pool in ('END_USER', 'MERCHANT_EMPLOYEE', 'BACKOFFICE')),
    owner_id uuid not null,
    created_at timestamptz not null default now()
);

create table if not exists identity.email_verifications (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users (id),
    token_hash text not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);

create index if not exists idx_email_verifications_end_user_id
    on identity.email_verifications (end_user_id);

create table if not exists identity.sessions (
    id uuid primary key,
    session_token_hash text not null unique,
    actor_type text not null check (actor_type in ('END_USER')),
    actor_id uuid not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

create index if not exists idx_sessions_actor
    on identity.sessions (actor_type, actor_id);

create table if not exists audit.audit_log (
    id bigserial primary key,
    event_type text not null,
    actor_type text not null,
    actor_id uuid,
    subject_type text not null,
    subject_id uuid,
    outcome text not null,
    metadata jsonb not null default '{}'::jsonb,
    request_id text,
    correlation_id text,
    created_at timestamptz not null default now()
);

create or replace function audit.reject_audit_log_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'audit.audit_log is append-only';
end;
$$;

drop trigger if exists trg_audit_log_no_update on audit.audit_log;
create trigger trg_audit_log_no_update
before update on audit.audit_log
for each row execute function audit.reject_audit_log_mutation();

drop trigger if exists trg_audit_log_no_delete on audit.audit_log;
create trigger trg_audit_log_no_delete
before delete on audit.audit_log
for each row execute function audit.reject_audit_log_mutation();
