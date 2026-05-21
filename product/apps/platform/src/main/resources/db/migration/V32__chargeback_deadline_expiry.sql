alter table chargeback.disputes
    add column if not exists deadline_expiry_journal_id uuid references ledger.journal_entries(id),
    add column if not exists deadline_expired_at timestamptz;

