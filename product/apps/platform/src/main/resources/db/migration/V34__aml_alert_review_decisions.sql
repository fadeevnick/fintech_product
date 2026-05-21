create table if not exists aml.aml_alert_decisions (
    id uuid primary key,
    alert_id uuid not null references aml.aml_alerts (id),
    previous_status text not null,
    resulting_status text not null,
    decision text not null,
    rationale text not null,
    decided_by_subject text not null,
    decided_by_role text,
    created_at timestamptz not null default now()
);

create index if not exists idx_aml_alert_decisions_alert
    on aml.aml_alert_decisions (alert_id);
