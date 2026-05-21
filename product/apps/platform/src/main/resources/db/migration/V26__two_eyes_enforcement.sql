alter table wallet.deposit_requests
    drop constraint if exists deposit_requests_state_check;

alter table wallet.deposit_requests
    add constraint deposit_requests_state_check check (state in (
        'REQUESTED',
        'PENDING_OPERATOR_REVIEW',
        'READY_FOR_SECOND_REVIEW',
        'APPROVED',
        'COMPLETED',
        'REJECTED'
    ));

alter table wallet.deposit_requests
    add column if not exists first_review_actor_type text,
    add column if not exists first_review_actor_id uuid,
    add column if not exists first_review_reference text,
    add column if not exists first_review_reason text,
    add column if not exists first_reviewed_at timestamptz;

alter table wallet.withdraw_requests
    drop constraint if exists withdraw_requests_amount_check;

alter table wallet.withdraw_requests
    drop constraint if exists withdraw_requests_state_check;

alter table wallet.withdraw_requests
    add constraint withdraw_requests_amount_check check (amount > 0 and amount <= 1000000.0000);

alter table wallet.withdraw_requests
    add constraint withdraw_requests_state_check check (state in (
        'PENDING',
        'HELD',
        'READY_FOR_SECOND_REVIEW',
        'COMPLETED',
        'REJECTED'
    ));

alter table wallet.withdraw_requests
    add column if not exists first_review_actor_type text,
    add column if not exists first_review_actor_id uuid,
    add column if not exists first_review_reference text,
    add column if not exists first_review_reason text,
    add column if not exists first_reviewed_at timestamptz;
