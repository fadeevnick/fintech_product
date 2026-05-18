create schema if not exists kyc;

create table kyc.kyc_profiles (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    status text not null check (status in ('NOT_STARTED','SUBMITTED','IN_REVIEW','APPROVED','REJECTED','NEEDS_RESUBMIT')),
    vendor text not null check (vendor in ('SUMSUB')),
    vendor_applicant_id text,
    level_name text,
    external_user_id text not null,
    review_answer text,
    review_reject_type text,
    review_moderation_comment text,
    submitted_at timestamptz,
    in_review_at timestamptz,
    approved_at timestamptz,
    rejected_at timestamptz,
    needs_resubmit_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version bigint not null default 0,
    request_id text,
    correlation_id text,
    unique (end_user_id),
    unique (vendor, vendor_applicant_id)
);

create table kyc.kyc_sessions (
    id uuid primary key,
    profile_id uuid not null references kyc.kyc_profiles(id),
    vendor text not null check (vendor in ('SUMSUB')),
    vendor_applicant_id text,
    access_token_hash text,
    external_user_id text not null,
    status text not null check (status in ('CREATED','ACTIVE','EXPIRED','FAILED')),
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    failure_code text,
    failure_message text
);

create table kyc.sumsub_webhook_events (
    id uuid primary key,
    vendor_event_id text not null unique,
    vendor_applicant_id text,
    event_type text not null,
    review_status text,
    review_answer text,
    payload_sha256 text not null,
    received_at timestamptz not null default now(),
    applied_at timestamptz,
    duplicate boolean not null default false
);
