alter table chargeback.disputes
    add column if not exists merchant_acceptance_journal_id uuid references ledger.journal_entries(id),
    add column if not exists merchant_accepted_by_employee_id uuid,
    add column if not exists merchant_accepted_at timestamptz;

