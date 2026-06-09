create extension if not exists "uuid-ossp";
create extension if not exists pgcrypto;

create type membership_status as enum ('invited', 'active', 'suspended', 'removed');
create type account_nature as enum ('asset', 'liability', 'equity', 'income', 'expense');
create type ledger_category as enum (
  'cash',
  'bank',
  'sundry_debtor',
  'sundry_creditor',
  'sales',
  'purchase',
  'direct_expense',
  'indirect_expense',
  'direct_income',
  'indirect_income',
  'input_gst',
  'output_gst',
  'round_off',
  'capital',
  'loan',
  'other'
);
create type voucher_type as enum (
  'journal',
  'payment',
  'receipt',
  'contra',
  'sales',
  'purchase',
  'debit_note',
  'credit_note',
  'opening_balance'
);
create type voucher_status as enum ('draft', 'pending_approval', 'posted', 'reversed', 'void');
create type invoice_type as enum ('sales', 'purchase');
create type gst_registration_type as enum ('regular', 'composition', 'unregistered', 'consumer', 'overseas');
create type gst_supply_type as enum ('intra_state', 'inter_state', 'export', 'import', 'reverse_charge');
create type reconciliation_status as enum ('unmatched', 'suggested', 'matched', 'ignored');
create type ai_suggestion_status as enum ('draft', 'needs_review', 'approved', 'rejected', 'expired');

create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null,
  phone text,
  locale text not null default 'en-IN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table companies (
  id uuid primary key default gen_random_uuid(),
  legal_name text not null,
  trade_name text,
  gstin varchar(15),
  pan varchar(10),
  base_currency char(3) not null default 'INR',
  financial_year_start_month smallint not null default 4 check (financial_year_start_month between 1 and 12),
  state_code char(2),
  address_line1 text,
  address_line2 text,
  city text,
  pincode varchar(10),
  country text not null default 'India',
  created_by uuid not null references profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint companies_gstin_format check (gstin is null or length(gstin) = 15),
  constraint companies_pan_format check (pan is null or length(pan) = 10)
);

create table roles (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  description text not null
);

create table permissions (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  description text not null
);

create table role_permissions (
  role_id uuid not null references roles(id) on delete cascade,
  permission_id uuid not null references permissions(id) on delete cascade,
  primary key (role_id, permission_id)
);

create table company_members (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  profile_id uuid not null references profiles(id) on delete cascade,
  role_id uuid not null references roles(id),
  status membership_status not null default 'active',
  invited_by uuid references profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, profile_id)
);

create table ledger_groups (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  name text not null,
  account_nature account_nature not null,
  parent_id uuid references ledger_groups(id),
  is_system boolean not null default false,
  created_at timestamptz not null default now(),
  unique (company_id, name)
);

create table ledgers (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  ledger_group_id uuid not null references ledger_groups(id),
  name text not null,
  category ledger_category not null default 'other',
  account_nature account_nature not null,
  opening_balance numeric(18,2) not null default 0,
  opening_balance_type char(2) not null default 'dr' check (opening_balance_type in ('dr', 'cr')),
  gstin varchar(15),
  gst_registration_type gst_registration_type,
  state_code char(2),
  is_system boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, name)
);

create table accounting_periods (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  name text not null,
  starts_on date not null,
  ends_on date not null,
  is_closed boolean not null default false,
  closed_at timestamptz,
  closed_by uuid references profiles(id),
  created_at timestamptz not null default now(),
  unique (company_id, name),
  check (starts_on <= ends_on)
);

create table vouchers (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_number text not null,
  voucher_type voucher_type not null,
  voucher_date date not null,
  status voucher_status not null default 'draft',
  narration text,
  source text not null default 'manual',
  source_reference_id uuid,
  reversed_voucher_id uuid references vouchers(id),
  created_by uuid not null references profiles(id),
  approved_by uuid references profiles(id),
  posted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, voucher_number)
);

create table journal_entries (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid not null references vouchers(id) on delete cascade,
  ledger_id uuid not null references ledgers(id),
  line_number integer not null,
  debit numeric(18,2) not null default 0 check (debit >= 0),
  credit numeric(18,2) not null default 0 check (credit >= 0),
  narration text,
  created_at timestamptz not null default now(),
  unique (voucher_id, line_number),
  check ((debit > 0 and credit = 0) or (credit > 0 and debit = 0))
);

create table voucher_audit_events (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid not null references vouchers(id) on delete cascade,
  actor_id uuid references profiles(id),
  event_type text not null,
  event_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table invoice_sequences (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_type invoice_type not null,
  prefix text not null,
  next_number bigint not null default 1,
  financial_year text not null,
  unique (company_id, invoice_type, prefix, financial_year)
);

create table invoices (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_type invoice_type not null,
  invoice_number text not null,
  invoice_date date not null,
  due_date date,
  party_ledger_id uuid not null references ledgers(id),
  voucher_id uuid references vouchers(id),
  gst_supply_type gst_supply_type not null,
  place_of_supply_state_code char(2),
  subtotal numeric(18,2) not null default 0,
  taxable_value numeric(18,2) not null default 0,
  cgst_amount numeric(18,2) not null default 0,
  sgst_amount numeric(18,2) not null default 0,
  igst_amount numeric(18,2) not null default 0,
  total_amount numeric(18,2) not null default 0,
  notes text,
  created_by uuid not null references profiles(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (company_id, invoice_number)
);

create table invoice_lines (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_id uuid not null references invoices(id) on delete cascade,
  line_number integer not null,
  description text not null,
  hsn_sac text,
  quantity numeric(18,3) not null default 1,
  unit text not null default 'NOS',
  unit_price numeric(18,2) not null,
  discount_amount numeric(18,2) not null default 0,
  gst_rate numeric(5,2) not null default 0,
  taxable_value numeric(18,2) not null,
  cgst_amount numeric(18,2) not null default 0,
  sgst_amount numeric(18,2) not null default 0,
  igst_amount numeric(18,2) not null default 0,
  total_amount numeric(18,2) not null,
  unique (invoice_id, line_number)
);

create table bank_accounts (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  ledger_id uuid not null references ledgers(id),
  bank_name text not null,
  account_number_last4 varchar(4),
  ifsc varchar(11),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (company_id, ledger_id)
);

create table bank_statements (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_account_id uuid not null references bank_accounts(id) on delete cascade,
  file_path text not null,
  statement_from date,
  statement_to date,
  uploaded_by uuid not null references profiles(id),
  created_at timestamptz not null default now()
);

create table bank_transactions (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_statement_id uuid not null references bank_statements(id) on delete cascade,
  transaction_date date not null,
  description text not null,
  reference_number text,
  debit numeric(18,2) not null default 0 check (debit >= 0),
  credit numeric(18,2) not null default 0 check (credit >= 0),
  balance numeric(18,2),
  reconciliation_status reconciliation_status not null default 'unmatched',
  created_at timestamptz not null default now(),
  check ((debit > 0 and credit = 0) or (credit > 0 and debit = 0))
);

create table reconciliation_matches (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_transaction_id uuid not null references bank_transactions(id) on delete cascade,
  journal_entry_id uuid not null references journal_entries(id),
  confidence numeric(5,2) not null default 100,
  matched_by uuid references profiles(id),
  matched_at timestamptz not null default now(),
  unique (bank_transaction_id, journal_entry_id)
);

create table ai_suggestions (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  requested_by uuid not null references profiles(id),
  input_text text not null,
  input_language text not null default 'hinglish',
  intent text not null,
  status ai_suggestion_status not null default 'needs_review',
  confidence numeric(5,2) not null default 0,
  proposed_payload jsonb not null,
  validation_errors jsonb not null default '[]'::jsonb,
  approved_voucher_id uuid references vouchers(id),
  model_name text not null,
  created_at timestamptz not null default now(),
  decided_at timestamptz,
  decided_by uuid references profiles(id)
);

create table ai_feedback_examples (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  ai_suggestion_id uuid references ai_suggestions(id) on delete set null,
  corrected_payload jsonb not null,
  created_by uuid not null references profiles(id),
  created_at timestamptz not null default now()
);

create table report_snapshots (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  report_type text not null,
  period_start date not null,
  period_end date not null,
  payload jsonb not null,
  source_watermark timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (company_id, report_type, period_start, period_end)
);

create index idx_company_members_profile on company_members(profile_id, status);
create index idx_ledgers_company_category on ledgers(company_id, category);
create index idx_vouchers_company_date_status on vouchers(company_id, voucher_date, status);
create index idx_journal_entries_company_ledger on journal_entries(company_id, ledger_id);
create index idx_journal_entries_company_voucher on journal_entries(company_id, voucher_id);
create index idx_invoices_company_date on invoices(company_id, invoice_date);
create index idx_bank_transactions_company_date on bank_transactions(company_id, transaction_date);
create index idx_ai_suggestions_company_status on ai_suggestions(company_id, status);

create or replace function assert_voucher_balanced(target_voucher_id uuid)
returns boolean
language plpgsql
as $$
declare
  debit_total numeric(18,2);
  credit_total numeric(18,2);
begin
  select coalesce(sum(debit), 0), coalesce(sum(credit), 0)
  into debit_total, credit_total
  from journal_entries
  where voucher_id = target_voucher_id;

  return debit_total = credit_total and debit_total > 0;
end;
$$;

create or replace function prevent_posted_journal_mutation()
returns trigger
language plpgsql
as $$
declare
  current_status voucher_status;
begin
  select status into current_status from vouchers where id = coalesce(old.voucher_id, new.voucher_id);
  if current_status in ('posted', 'reversed', 'void') then
    raise exception 'journal entries for finalized vouchers are immutable';
  end if;
  return coalesce(new, old);
end;
$$;

create trigger trg_prevent_posted_journal_update
before update or delete on journal_entries
for each row execute function prevent_posted_journal_mutation();

alter table profiles enable row level security;
alter table companies enable row level security;
alter table company_members enable row level security;
alter table ledger_groups enable row level security;
alter table ledgers enable row level security;
alter table accounting_periods enable row level security;
alter table vouchers enable row level security;
alter table journal_entries enable row level security;
alter table voucher_audit_events enable row level security;
alter table invoice_sequences enable row level security;
alter table invoices enable row level security;
alter table invoice_lines enable row level security;
alter table bank_accounts enable row level security;
alter table bank_statements enable row level security;
alter table bank_transactions enable row level security;
alter table reconciliation_matches enable row level security;
alter table ai_suggestions enable row level security;
alter table ai_feedback_examples enable row level security;
alter table report_snapshots enable row level security;

create or replace function is_company_member(target_company_id uuid)
returns boolean
language sql
security definer
set search_path = public
as $$
  select exists (
    select 1
    from company_members
    where company_id = target_company_id
      and profile_id = auth.uid()
      and status = 'active'
  );
$$;

create policy profiles_self_select on profiles
  for select using (id = auth.uid());

create policy profiles_self_update on profiles
  for update using (id = auth.uid());

create policy companies_member_select on companies
  for select using (is_company_member(id));

create policy companies_creator_insert on companies
  for insert with check (created_by = auth.uid());

create policy company_members_member_select on company_members
  for select using (profile_id = auth.uid() or is_company_member(company_id));

create policy ledger_groups_member_all on ledger_groups
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy ledgers_member_all on ledgers
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy accounting_periods_member_all on accounting_periods
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy vouchers_member_all on vouchers
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy journal_entries_member_all on journal_entries
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy voucher_audit_events_member_all on voucher_audit_events
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy invoice_sequences_member_all on invoice_sequences
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy invoices_member_all on invoices
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy invoice_lines_member_all on invoice_lines
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy bank_accounts_member_all on bank_accounts
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy bank_statements_member_all on bank_statements
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy bank_transactions_member_all on bank_transactions
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy reconciliation_matches_member_all on reconciliation_matches
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy ai_suggestions_member_all on ai_suggestions
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy ai_feedback_examples_member_all on ai_feedback_examples
  for all using (is_company_member(company_id))
  with check (is_company_member(company_id));

create policy report_snapshots_member_select on report_snapshots
  for select using (is_company_member(company_id));

insert into roles (code, name, description)
values
  ('owner', 'Owner', 'Company owner with full accounting and administration access'),
  ('admin', 'Admin', 'Company administrator with full accounting operations access'),
  ('accountant', 'Accountant', 'Accounting user who can create, approve, and report on accounting records'),
  ('auditor', 'Auditor', 'Read-only user for ledgers, vouchers, and reports'),
  ('staff', 'Staff', 'Limited user for draft vouchers and invoices')
on conflict (code) do nothing;

insert into permissions (code, description)
values
  ('company.manage', 'Manage company settings and members'),
  ('ledger.manage', 'Create and update ledgers'),
  ('voucher.draft', 'Create voucher drafts'),
  ('voucher.approve', 'Approve and post vouchers'),
  ('invoice.manage', 'Create, update, and post invoices'),
  ('banking.reconcile', 'Upload statements and reconcile bank transactions'),
  ('reports.read', 'View accounting and GST reports'),
  ('ai.use', 'Use ABHAY AI suggestions and insights')
on conflict (code) do nothing;

insert into role_permissions (role_id, permission_id)
select roles.id, permissions.id
from roles
cross join permissions
where roles.code in ('owner', 'admin')
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select roles.id, permissions.id
from roles
join permissions on permissions.code in (
  'ledger.manage',
  'voucher.draft',
  'voucher.approve',
  'invoice.manage',
  'banking.reconcile',
  'reports.read',
  'ai.use'
)
where roles.code = 'accountant'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select roles.id, permissions.id
from roles
join permissions on permissions.code = 'reports.read'
where roles.code = 'auditor'
on conflict do nothing;

insert into role_permissions (role_id, permission_id)
select roles.id, permissions.id
from roles
join permissions on permissions.code in ('voucher.draft', 'invoice.manage', 'ai.use')
where roles.code = 'staff'
on conflict do nothing;

create or replace function create_default_ledger_group(
  target_company_id uuid,
  group_name text,
  group_nature account_nature
)
returns uuid
language plpgsql
as $$
declare
  created_group_id uuid;
begin
  insert into ledger_groups (company_id, name, account_nature, is_system)
  values (target_company_id, group_name, group_nature, true)
  returning id into created_group_id;
  return created_group_id;
end;
$$;

create or replace function create_default_ledger(
  target_company_id uuid,
  target_group_id uuid,
  ledger_name text,
  target_category ledger_category,
  target_nature account_nature
)
returns void
language plpgsql
as $$
begin
  insert into ledgers (
    company_id,
    ledger_group_id,
    name,
    category,
    account_nature,
    is_system
  )
  values (
    target_company_id,
    target_group_id,
    ledger_name,
    target_category,
    target_nature,
    true
  );
end;
$$;

create or replace function provision_company_defaults(target_company_id uuid)
returns void
language plpgsql
as $$
declare
  assets_group_id uuid;
  liabilities_group_id uuid;
  income_group_id uuid;
  expense_group_id uuid;
  equity_group_id uuid;
begin
  assets_group_id := create_default_ledger_group(target_company_id, 'Assets', 'asset');
  liabilities_group_id := create_default_ledger_group(target_company_id, 'Liabilities', 'liability');
  income_group_id := create_default_ledger_group(target_company_id, 'Income', 'income');
  expense_group_id := create_default_ledger_group(target_company_id, 'Expenses', 'expense');
  equity_group_id := create_default_ledger_group(target_company_id, 'Equity', 'equity');

  perform create_default_ledger(target_company_id, assets_group_id, 'Cash', 'cash', 'asset');
  perform create_default_ledger(target_company_id, assets_group_id, 'Bank', 'bank', 'asset');
  perform create_default_ledger(target_company_id, assets_group_id, 'Sundry Debtors', 'sundry_debtor', 'asset');
  perform create_default_ledger(target_company_id, assets_group_id, 'Input GST', 'input_gst', 'asset');
  perform create_default_ledger(target_company_id, liabilities_group_id, 'Sundry Creditors', 'sundry_creditor', 'liability');
  perform create_default_ledger(target_company_id, liabilities_group_id, 'Output GST', 'output_gst', 'liability');
  perform create_default_ledger(target_company_id, income_group_id, 'Sales', 'sales', 'income');
  perform create_default_ledger(target_company_id, expense_group_id, 'Purchases', 'purchase', 'expense');
  perform create_default_ledger(target_company_id, expense_group_id, 'Direct Expenses', 'direct_expense', 'expense');
  perform create_default_ledger(target_company_id, expense_group_id, 'Indirect Expenses', 'indirect_expense', 'expense');
  perform create_default_ledger(target_company_id, equity_group_id, 'Capital Account', 'capital', 'equity');
end;
$$;

create or replace function handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  owner_role_id uuid;
  created_company_id uuid;
  company_name text;
begin
  select id into owner_role_id from roles where code = 'owner';
  company_name := coalesce(new.raw_user_meta_data ->> 'initial_company_name', 'My Company');

  insert into profiles (id, full_name, phone)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', split_part(new.email, '@', 1)),
    new.phone
  )
  on conflict (id) do nothing;

  insert into companies (legal_name, trade_name, created_by)
  values (company_name, company_name, new.id)
  returning id into created_company_id;

  insert into company_members (company_id, profile_id, role_id, status)
  values (created_company_id, new.id, owner_role_id, 'active');

  perform provision_company_defaults(created_company_id);
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute function handle_new_auth_user();
