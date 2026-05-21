insert into ledger.accounts (
    id,
    code,
    currency,
    account_type,
    normal_side,
    owner_type,
    owner_id
)
values (
    gen_random_uuid(),
    'ACQUIRER_DISPUTE_RESERVE',
    'EUR',
    'ACQUIRER_DISPUTE_RESERVE',
    'DEBIT',
    null,
    null
)
on conflict (code) do nothing;

alter table chargeback.disputes
    add column if not exists provisional_credit_journal_id uuid references ledger.journal_entries(id);
