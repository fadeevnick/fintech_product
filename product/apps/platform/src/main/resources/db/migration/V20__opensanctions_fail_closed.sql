create schema if not exists sanctions;

create table sanctions.sanctions_hits (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    kyc_profile_id uuid references kyc.kyc_profiles(id),
    status text not null check (status in ('OPEN','IN_REVIEW','CLEARED_FALSE_POSITIVE','TRUE_MATCH')),
    reason text not null check (reason in ('SCREENING_UNAVAILABLE','POSSIBLE_MATCH')),
    vendor text not null check (vendor in ('OPENSANCTIONS')),
    match_score numeric(5,4),
    matched_entity_id text,
    matched_name text,
    request_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index sanctions_hits_end_user_idx on sanctions.sanctions_hits (end_user_id, created_at desc);
create index sanctions_hits_kyc_profile_idx on sanctions.sanctions_hits (kyc_profile_id, created_at desc);
