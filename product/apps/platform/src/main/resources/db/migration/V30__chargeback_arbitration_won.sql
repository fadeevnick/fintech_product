alter table chargeback.disputes
    add column if not exists arbitration_outcome text check (arbitration_outcome in ('WON','LOST')),
    add column if not exists arbitration_rationale text,
    add column if not exists arbitration_decided_by_subject text,
    add column if not exists arbitration_decided_by_role text,
    add column if not exists arbitration_decided_at timestamptz,
    add column if not exists arbitration_journal_id uuid references ledger.journal_entries(id);
