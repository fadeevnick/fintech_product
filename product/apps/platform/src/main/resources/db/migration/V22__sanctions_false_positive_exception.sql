create table sanctions.sanctions_hit_decisions (
    id uuid primary key,
    hit_id uuid not null references sanctions.sanctions_hits(id),
    previous_status text not null,
    decision text not null check (decision in ('CLEAR_FALSE_POSITIVE')),
    resulting_status text not null check (resulting_status in ('CLEARED_FALSE_POSITIVE')),
    rationale text not null,
    decided_by_subject text not null,
    decided_by_role text,
    decided_at timestamptz not null default now()
);

create table sanctions.sanctions_false_positive_exceptions (
    id uuid primary key,
    end_user_id uuid not null references identity.end_users(id),
    vendor text not null check (vendor in ('OPENSANCTIONS')),
    matched_entity_id text not null,
    rationale text not null,
    created_from_hit_id uuid not null references sanctions.sanctions_hits(id),
    created_by_subject text not null,
    created_at timestamptz not null default now(),
    revoked_at timestamptz,
    unique (end_user_id, vendor, matched_entity_id)
);

create index sanctions_hit_decisions_hit_idx on sanctions.sanctions_hit_decisions (hit_id, decided_at desc);
create index sanctions_false_positive_exceptions_active_idx on sanctions.sanctions_false_positive_exceptions (end_user_id, vendor, matched_entity_id) where revoked_at is null;
