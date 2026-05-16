create schema if not exists ledger;
create extension if not exists pgcrypto;

create table if not exists ledger.accounts (
    id uuid primary key,
    code text not null unique,
    currency text not null default 'EUR',
    account_type text not null,
    normal_side text not null check (normal_side in ('DEBIT', 'CREDIT')),
    owner_type text,
    owner_id uuid,
    created_at timestamptz not null default now()
);

create table if not exists ledger.journal_entries (
    id uuid primary key,
    journal_type text not null,
    reference_type text not null,
    reference_id uuid not null,
    currency text not null default 'EUR',
    description text,
    created_at timestamptz not null default now()
);

create table if not exists ledger.postings (
    id uuid primary key,
    journal_entry_id uuid not null references ledger.journal_entries (id),
    account_id uuid not null references ledger.accounts (id),
    side text not null check (side in ('DEBIT', 'CREDIT')),
    amount numeric(20,4) not null check (amount > 0),
    currency text not null default 'EUR',
    created_at timestamptz not null default now()
);

create index if not exists idx_ledger_postings_journal
    on ledger.postings (journal_entry_id);

create index if not exists idx_ledger_postings_account
    on ledger.postings (account_id);

create or replace function ledger.reject_ledger_mutation()
returns trigger
language plpgsql
as $$
begin
    raise exception 'ledger tables are append-only';
end;
$$;

drop trigger if exists trg_ledger_journal_entries_no_update on ledger.journal_entries;
create trigger trg_ledger_journal_entries_no_update
before update on ledger.journal_entries
for each row execute function ledger.reject_ledger_mutation();

drop trigger if exists trg_ledger_journal_entries_no_delete on ledger.journal_entries;
create trigger trg_ledger_journal_entries_no_delete
before delete on ledger.journal_entries
for each row execute function ledger.reject_ledger_mutation();

drop trigger if exists trg_ledger_postings_no_update on ledger.postings;
create trigger trg_ledger_postings_no_update
before update on ledger.postings
for each row execute function ledger.reject_ledger_mutation();

drop trigger if exists trg_ledger_postings_no_delete on ledger.postings;
create trigger trg_ledger_postings_no_delete
before delete on ledger.postings
for each row execute function ledger.reject_ledger_mutation();

create or replace function ledger.post_journal(
    p_journal_id uuid,
    p_journal_type text,
    p_reference_type text,
    p_reference_id uuid,
    p_currency text,
    p_description text,
    p_postings jsonb
)
returns uuid
language plpgsql
as $$
declare
    v_posting jsonb;
    v_posting_count integer;
    v_account_currency text;
    v_debit_total numeric(20,4);
    v_credit_total numeric(20,4);
begin
    if p_journal_id is null then
        raise exception 'journal_id_required';
    end if;
    if p_currency is null or length(trim(p_currency)) = 0 then
        raise exception 'currency_required';
    end if;
    if p_postings is null or jsonb_typeof(p_postings) <> 'array' then
        raise exception 'postings_array_required';
    end if;

    select count(*)
    into v_posting_count
    from jsonb_array_elements(p_postings);

    if v_posting_count < 2 then
        raise exception 'ledger_journal_requires_two_postings';
    end if;

    select coalesce(sum((posting ->> 'amount')::numeric) filter (where posting ->> 'side' = 'DEBIT'), 0),
           coalesce(sum((posting ->> 'amount')::numeric) filter (where posting ->> 'side' = 'CREDIT'), 0)
    into v_debit_total, v_credit_total
    from jsonb_array_elements(p_postings) as posting;

    if v_debit_total <> v_credit_total then
        raise exception 'ledger_journal_unbalanced';
    end if;
    if v_debit_total <= 0 then
        raise exception 'ledger_journal_amount_required';
    end if;

    insert into ledger.journal_entries (
        id,
        journal_type,
        reference_type,
        reference_id,
        currency,
        description
    )
    values (
        p_journal_id,
        p_journal_type,
        p_reference_type,
        p_reference_id,
        p_currency,
        p_description
    );

    for v_posting in select * from jsonb_array_elements(p_postings)
    loop
        if (v_posting ->> 'side') not in ('DEBIT', 'CREDIT') then
            raise exception 'ledger_posting_side_invalid';
        end if;
        if (v_posting ->> 'amount')::numeric <= 0 then
            raise exception 'ledger_posting_amount_invalid';
        end if;

        select currency
        into v_account_currency
        from ledger.accounts
        where id = (v_posting ->> 'accountId')::uuid;

        if v_account_currency is null then
            raise exception 'ledger_account_missing';
        end if;
        if v_account_currency <> p_currency then
            raise exception 'ledger_account_currency_mismatch';
        end if;

        insert into ledger.postings (
            id,
            journal_entry_id,
            account_id,
            side,
            amount,
            currency
        )
        values (
            coalesce((v_posting ->> 'postingId')::uuid, gen_random_uuid()),
            p_journal_id,
            (v_posting ->> 'accountId')::uuid,
            v_posting ->> 'side',
            (v_posting ->> 'amount')::numeric(20,4),
            p_currency
        );
    end loop;

    return p_journal_id;
end;
$$;

create or replace view ledger.account_balances as
select
    a.id as account_id,
    a.code,
    a.currency,
    a.normal_side,
    coalesce(
        sum(
            case
                when p.side = a.normal_side then p.amount
                else -p.amount
            end
        ),
        0::numeric
    )::numeric(20,4) as balance
from ledger.accounts a
left join ledger.postings p on p.account_id = a.id
group by a.id, a.code, a.currency, a.normal_side;
