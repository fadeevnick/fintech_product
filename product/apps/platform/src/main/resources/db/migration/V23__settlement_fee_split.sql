alter table settlement.settlement_items
    add column merchant_net_amount numeric(19, 4),
    add column interchange_amount numeric(19, 4),
    add column network_assessment_amount numeric(19, 4),
    add column acquirer_margin_amount numeric(19, 4);

insert into ledger.accounts (
    id,
    code,
    currency,
    account_type,
    normal_side,
    owner_type,
    owner_id
)
values
    (
        gen_random_uuid(),
        'ISSUER_INTERCHANGE_REVENUE',
        'EUR',
        'ISSUER_INTERCHANGE_REVENUE',
        'CREDIT',
        null,
        null
    ),
    (
        gen_random_uuid(),
        'NETWORK_ASSESSMENT_REVENUE',
        'EUR',
        'NETWORK_ASSESSMENT_REVENUE',
        'CREDIT',
        null,
        null
    ),
    (
        gen_random_uuid(),
        'ACQUIRER_MARGIN_REVENUE',
        'EUR',
        'ACQUIRER_MARGIN_REVENUE',
        'CREDIT',
        null,
        null
    )
on conflict (code) do nothing;
