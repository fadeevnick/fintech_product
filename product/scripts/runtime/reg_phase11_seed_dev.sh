#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/lib_phase05_vault_card.sh"

seed_platform() {
  p05_platform_psql "
insert into identity.end_users (id, email, normalized_email, password_hash, status, email_verified_at)
values
  ('10000000-0000-0000-0000-000000000001'::uuid, 'demo.approved@enduser.local', 'demo.approved@enduser.local', 'demo-hash', 'ACTIVE', now()),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'demo.review@enduser.local', 'demo.review@enduser.local', 'demo-hash', 'ACTIVE', now()),
  ('10000000-0000-0000-0000-000000000003'::uuid, 'demo.rejected@enduser.local', 'demo.rejected@enduser.local', 'demo-hash', 'ACTIVE', now())
on conflict (id) do update
set email = excluded.email,
    normalized_email = excluded.normalized_email,
    status = excluded.status,
    email_verified_at = excluded.email_verified_at;

insert into identity.email_reservations (normalized_email, pool, owner_id)
values
  ('demo.approved@enduser.local', 'END_USER', '10000000-0000-0000-0000-000000000001'::uuid),
  ('demo.review@enduser.local', 'END_USER', '10000000-0000-0000-0000-000000000002'::uuid),
  ('demo.rejected@enduser.local', 'END_USER', '10000000-0000-0000-0000-000000000003'::uuid)
on conflict (normalized_email) do update
set pool = excluded.pool,
    owner_id = excluded.owner_id;

insert into kyc.kyc_profiles (
  id, end_user_id, status, vendor, vendor_applicant_id, level_name, external_user_id,
  review_answer, review_reject_type, review_moderation_comment,
  submitted_at, in_review_at, approved_at, rejected_at
)
values
  ('11000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, 'APPROVED', 'SUMSUB', 'demo-sumsub-approved', 'basic-kyc-level', 'enduser:10000000-0000-0000-0000-000000000001', 'GREEN', null, 'deterministic demo approved profile', now(), now(), now(), null),
  ('11000000-0000-0000-0000-000000000002'::uuid, '10000000-0000-0000-0000-000000000002'::uuid, 'IN_REVIEW', 'SUMSUB', 'demo-sumsub-review', 'basic-kyc-level', 'enduser:10000000-0000-0000-0000-000000000002', null, null, 'deterministic demo in-review profile', now(), now(), null, null),
  ('11000000-0000-0000-0000-000000000003'::uuid, '10000000-0000-0000-0000-000000000003'::uuid, 'REJECTED', 'SUMSUB', 'demo-sumsub-rejected', 'basic-kyc-level', 'enduser:10000000-0000-0000-0000-000000000003', 'RED', 'FINAL', 'deterministic demo rejected profile', now(), now(), null, now())
on conflict (end_user_id) do update
set status = excluded.status,
    vendor_applicant_id = excluded.vendor_applicant_id,
    level_name = excluded.level_name,
    external_user_id = excluded.external_user_id,
    review_answer = excluded.review_answer,
    review_reject_type = excluded.review_reject_type,
    review_moderation_comment = excluded.review_moderation_comment,
    submitted_at = excluded.submitted_at,
    in_review_at = excluded.in_review_at,
    approved_at = excluded.approved_at,
    rejected_at = excluded.rejected_at;

insert into merchant.merchants (id, company_name, country, business_type, kyb_status)
values
  ('20000000-0000-0000-0000-000000000001'::uuid, 'Demo Verified Merchant', 'FR', 'saas', 'VERIFIED'),
  ('20000000-0000-0000-0000-000000000002'::uuid, 'Demo Pending Merchant', 'FR', 'marketplace', 'PENDING')
on conflict (id) do update
set company_name = excluded.company_name,
    country = excluded.country,
    business_type = excluded.business_type,
    kyb_status = excluded.kyb_status;

insert into identity.merchant_employees (id, merchant_id, email, normalized_email, password_hash, role, status, email_verified_at)
values
  ('21000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, 'admin@demo-verified.local', 'admin@demo-verified.local', 'demo-hash', 'merchant_admin', 'ACTIVE', now()),
  ('21000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000002'::uuid, 'admin@demo-pending.local', 'admin@demo-pending.local', 'demo-hash', 'merchant_admin', 'ACTIVE', now())
on conflict (id) do update
set merchant_id = excluded.merchant_id,
    email = excluded.email,
    normalized_email = excluded.normalized_email,
    role = excluded.role,
    status = excluded.status,
    email_verified_at = excluded.email_verified_at;

insert into identity.email_reservations (normalized_email, pool, owner_id)
values
  ('admin@demo-verified.local', 'MERCHANT_EMPLOYEE', '21000000-0000-0000-0000-000000000001'::uuid),
  ('admin@demo-pending.local', 'MERCHANT_EMPLOYEE', '21000000-0000-0000-0000-000000000002'::uuid)
on conflict (normalized_email) do update
set pool = excluded.pool,
    owner_id = excluded.owner_id;

insert into merchant.stripe_account_links (id, merchant_id, stripe_account_id, charges_enabled, payouts_enabled, details_submitted)
values
  ('22000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid, 'acct_demo_verified', true, true, true),
  ('22000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000002'::uuid, 'acct_demo_pending', false, false, false)
on conflict (merchant_id) do update
set stripe_account_id = excluded.stripe_account_id,
    charges_enabled = excluded.charges_enabled,
    payouts_enabled = excluded.payouts_enabled,
    details_submitted = excluded.details_submitted;

insert into ledger.accounts (id, code, currency, account_type, normal_side, owner_type, owner_id)
values ('30000000-0000-0000-0000-000000000001'::uuid, 'WALLET_USER:10000000-0000-0000-0000-000000000001', 'EUR', 'WALLET_USER', 'CREDIT', 'END_USER', '10000000-0000-0000-0000-000000000001'::uuid)
on conflict (code) do update
set owner_id = excluded.owner_id;

insert into wallet.wallet_accounts (id, user_id, ledger_account_id, currency)
values ('31000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid, 'EUR')
on conflict (user_id) do update
set ledger_account_id = excluded.ledger_account_id,
    currency = excluded.currency;

do \$\$
declare
  external_account_id uuid;
begin
  if not exists (select 1 from ledger.journal_entries where id = '32000000-0000-0000-0000-000000000001'::uuid) then
    select id into external_account_id from ledger.accounts where code = 'EXTERNAL_DEPOSIT_CLEARING';
    perform ledger.post_journal(
      '32000000-0000-0000-0000-000000000001'::uuid,
      'DEMO_SEED_FUNDING',
      'DEMO_SEED',
      '10000000-0000-0000-0000-000000000001'::uuid,
      'EUR',
      'REC-01 deterministic approved user wallet funding',
      jsonb_build_array(
        jsonb_build_object('accountId', external_account_id::text, 'side', 'DEBIT', 'amount', '75.0000'),
        jsonb_build_object('accountId', '30000000-0000-0000-0000-000000000001', 'side', 'CREDIT', 'amount', '75.0000')
      )
    );
  end if;
end \$\$;

insert into cards.issued_cards (
  id, end_user_id, wallet_account_id, card_token, state, last4, bin, expiration_month, expiration_year
)
values (
  '40000000-0000-0000-0000-000000000001'::uuid,
  '10000000-0000-0000-0000-000000000001'::uuid,
  '31000000-0000-0000-0000-000000000001'::uuid,
  'tok_demo_approved_card',
  'ACTIVE',
  '4242',
  '400000',
  12,
  2030
)
on conflict (id) do update
set card_token = excluded.card_token,
    state = excluded.state,
    last4 = excluded.last4,
    bin = excluded.bin,
    expiration_month = excluded.expiration_month,
    expiration_year = excluded.expiration_year;

insert into merchant.api_keys (
  id, merchant_id, label, key_prefix, key_hash, fingerprint, status, created_by_employee_id
)
values (
  '50000000-0000-0000-0000-000000000001'::uuid,
  '20000000-0000-0000-0000-000000000001'::uuid,
  'Demo API key',
  'mfp_demo',
  'demo-key-hash-rec01',
  'demo-key-fingerprint-rec01',
  'ACTIVE',
  '21000000-0000-0000-0000-000000000001'::uuid
)
on conflict (id) do update
set label = excluded.label,
    status = excluded.status;

insert into merchant.payment_intents (
  id, merchant_id, api_key_id, amount, currency, description, state
)
values (
  '51000000-0000-0000-0000-000000000001'::uuid,
  '20000000-0000-0000-0000-000000000001'::uuid,
  '50000000-0000-0000-0000-000000000001'::uuid,
  42.5000,
  'EUR',
  'Demo seeded payment intent',
  'REQUIRES_PAYMENT_METHOD'
)
on conflict (id) do update
set amount = excluded.amount,
    description = excluded.description,
    state = excluded.state;
"
}

seed_issuer() {
  p05_issuer_psql "
insert into issuer.cards (
  id, end_user_id, wallet_account_id, card_token, last4, bin, expiration_month, expiration_year, state, request_id, correlation_id
)
values (
  '40000000-0000-0000-0000-000000000001'::uuid,
  '10000000-0000-0000-0000-000000000001'::uuid,
  '31000000-0000-0000-0000-000000000001'::uuid,
  'tok_demo_approved_card',
  '4242',
  '400000',
  12,
  2030,
  'ACTIVE',
  'rec01-seed',
  'rec01-seed'
)
on conflict (card_token) do update
set state = excluded.state,
    last4 = excluded.last4,
    bin = excluded.bin,
    expiration_month = excluded.expiration_month,
    expiration_year = excluded.expiration_year;
"
}

seed_vault() {
  p05_vault_psql "
insert into vault.card_tokens (
  id, card_token, pan_ciphertext, pan_last4, bin, expiration_month, expiration_year, status, key_version
)
values (
  '41000000-0000-0000-0000-000000000001'::uuid,
  'tok_demo_approved_card',
  decode('64656d6f2d70616e2d63697068657274657874', 'hex'),
  '4242',
  '400000',
  12,
  2030,
  'ACTIVE',
  1
)
on conflict (card_token) do update
set pan_last4 = excluded.pan_last4,
    bin = excluded.bin,
    expiration_month = excluded.expiration_month,
    expiration_year = excluded.expiration_year,
    status = excluded.status;
"
}

assert_seed() {
  test "$(p05_platform_psql "select count(*) from identity.end_users where normalized_email like 'demo.%@enduser.local';")" = "3"
  test "$(p05_platform_psql "select count(*) from kyc.kyc_profiles where vendor_applicant_id like 'demo-sumsub-%';")" = "3"
  test "$(p05_platform_psql "select count(*) from merchant.merchants where company_name like 'Demo % Merchant';")" = "2"
  test "$(p05_platform_psql "select count(*) from ledger.journal_entries where id = '32000000-0000-0000-0000-000000000001'::uuid;")" = "1"
  test "$(p05_platform_psql "select count(*) from cards.issued_cards where card_token = 'tok_demo_approved_card';")" = "1"
  test "$(p05_platform_psql "select count(*) from merchant.payment_intents where id = '51000000-0000-0000-0000-000000000001'::uuid;")" = "1"
  test "$(p05_issuer_psql "select count(*) from issuer.cards where card_token = 'tok_demo_approved_card';")" = "1"
  test "$(p05_vault_psql "select count(*) from vault.card_tokens where card_token = 'tok_demo_approved_card';")" = "1"
}

seed_platform >/dev/null
seed_issuer >/dev/null
seed_vault >/dev/null
assert_seed

echo "REC-01 seed dev data pass approved_user=10000000-0000-0000-0000-000000000001 merchant=20000000-0000-0000-0000-000000000001 payment_intent=51000000-0000-0000-0000-000000000001"
