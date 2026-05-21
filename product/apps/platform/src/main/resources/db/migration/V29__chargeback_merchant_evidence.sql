create table if not exists chargeback.evidence_submissions (
    id uuid primary key,
    dispute_id uuid not null unique references chargeback.disputes(id),
    merchant_id uuid not null references merchant.merchants(id),
    submitted_by_employee_id uuid not null references identity.merchant_employees(id),
    narrative text not null,
    created_at timestamptz not null default now()
);

create table if not exists chargeback.evidence_attachments (
    id uuid primary key,
    evidence_submission_id uuid not null references chargeback.evidence_submissions(id),
    file_name text not null,
    content_type text not null,
    storage_key text not null,
    size_bytes bigint not null check (size_bytes > 0),
    created_at timestamptz not null default now()
);

create index if not exists idx_chargeback_evidence_attachments_submission
    on chargeback.evidence_attachments (evidence_submission_id);
