create table if not exists audit.read_audit_log (
    id bigserial primary key,
    actor_type text not null,
    actor_id uuid,
    actor_reference text,
    subject_type text not null,
    subject_id uuid,
    resource_type text not null,
    resource_id uuid not null,
    purpose text not null,
    decision text not null check (decision in ('ALLOW', 'DENY')),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_read_audit_resource
    on audit.read_audit_log (resource_type, resource_id);

create index if not exists idx_read_audit_actor
    on audit.read_audit_log (actor_type, actor_id, created_at);

drop trigger if exists trg_read_audit_log_no_update on audit.read_audit_log;
create trigger trg_read_audit_log_no_update
before update on audit.read_audit_log
for each row execute function audit.reject_audit_log_mutation();

drop trigger if exists trg_read_audit_log_no_delete on audit.read_audit_log;
create trigger trg_read_audit_log_no_delete
before delete on audit.read_audit_log
for each row execute function audit.reject_audit_log_mutation();

create table if not exists identity.actor_controls (
    actor_type text not null check (actor_type in ('END_USER', 'MERCHANT')),
    actor_id uuid not null,
    state text not null check (state in ('ACTIVE', 'BLOCKED', 'FROZEN')),
    reason_code text not null,
    updated_by_actor_type text not null,
    updated_by_actor_id uuid,
    updated_by_reference text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    primary key (actor_type, actor_id)
);

create index if not exists idx_actor_controls_state
    on identity.actor_controls (state);
