create schema if not exists aml;

create table if not exists aml.aml_alerts (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users (id),
    rule_code text not null,
    severity text not null check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status text not null check (status in ('OPEN', 'IN_REVIEW', 'CLOSED_FALSE_POSITIVE', 'ESCALATED', 'MARKED_FOR_SAR', 'ACCOUNT_FROZEN_PERMANENT')),
    window_started_at timestamptz not null,
    window_ended_at timestamptz not null,
    observed_count integer not null,
    threshold_count integer not null,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index if not exists idx_aml_alerts_open_subject_rule_window
    on aml.aml_alerts (end_user_id, rule_code, window_started_at, window_ended_at)
    where status = 'OPEN';

create index if not exists idx_aml_alerts_subject_created
    on aml.aml_alerts (end_user_id, created_at desc);

create table if not exists aml.aml_rule_evaluations (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users (id),
    rule_code text not null,
    window_started_at timestamptz not null,
    window_ended_at timestamptz not null,
    observed_count integer not null,
    threshold_count integer not null,
    tripped boolean not null,
    alert_id uuid references aml.aml_alerts (id),
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);
