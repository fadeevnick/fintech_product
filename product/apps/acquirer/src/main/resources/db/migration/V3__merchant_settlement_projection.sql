create schema if not exists merchant_settlement;

create table merchant_settlement.balance_projection (
    id uuid primary key,
    platform_settlement_item_id uuid not null unique,
    platform_batch_id uuid not null,
    merchant_id uuid not null,
    payment_intent_id uuid not null,
    gross_amount numeric(19,4) not null,
    merchant_net_amount numeric(19,4) not null,
    interchange_amount numeric(19,4) not null,
    network_assessment_amount numeric(19,4) not null,
    acquirer_margin_amount numeric(19,4) not null,
    currency text not null,
    platform_settled_at timestamptz,
    projected_at timestamptz not null default now()
);

create index idx_merchant_settlement_projection_merchant
    on merchant_settlement.balance_projection (merchant_id, projected_at desc);
