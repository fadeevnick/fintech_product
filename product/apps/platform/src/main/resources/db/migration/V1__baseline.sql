create table if not exists service_runtime_marker (
    id smallint primary key,
    service_name text not null,
    created_at timestamptz not null default now()
);

insert into service_runtime_marker (id, service_name)
values (1, 'platform')
on conflict (id) do nothing;
