create table kyc.kyc_manual_decisions (
    id uuid primary key,
    kyc_profile_id uuid not null references kyc.kyc_profiles(id),
    previous_status text not null,
    decision text not null check (decision in ('APPROVE','REJECT','REQUEST_RESUBMIT')),
    resulting_status text not null check (resulting_status in ('APPROVED','REJECTED','NEEDS_RESUBMIT')),
    rationale text not null,
    decided_by_subject text not null,
    decided_by_role text,
    decided_at timestamptz not null default now()
);

create index kyc_manual_decisions_profile_idx on kyc.kyc_manual_decisions (kyc_profile_id, decided_at desc);
