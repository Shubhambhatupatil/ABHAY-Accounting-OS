create extension if not exists pgcrypto;

create table if not exists profiles (
  id uuid primary key,
  email text,
  full_name text,
  created_at timestamptz not null default now()
);

create table if not exists companies (
  id uuid primary key default gen_random_uuid(),
  company_name text,
  legal_name text,
  trade_name text,
  gstin text,
  industry text,
  state text,
  state_code text,
  financial_year text,
  created_by uuid,
  created_at timestamptz not null default now()
);

alter table companies add column if not exists company_name text;
alter table companies add column if not exists legal_name text;
alter table companies add column if not exists trade_name text;
alter table companies add column if not exists gstin text;
alter table companies add column if not exists industry text;
alter table companies add column if not exists state text;
alter table companies add column if not exists state_code text;
alter table companies add column if not exists financial_year text;
alter table companies add column if not exists created_by uuid;
alter table companies add column if not exists created_at timestamptz not null default now();

update companies
  set legal_name = coalesce(legal_name, company_name, trade_name, 'ANVRITAI Demo Company'),
      trade_name = coalesce(trade_name, legal_name, company_name, 'ANVRITAI Demo Company'),
      company_name = coalesce(company_name, legal_name, trade_name, 'ANVRITAI Demo Company'),
      state_code = coalesce(state_code, state)
  where legal_name is null or trade_name is null or company_name is null or state_code is null;

create table if not exists roles (
  id uuid primary key default gen_random_uuid(),
  code text not null unique,
  name text not null,
  description text not null default ''
);

alter table roles add column if not exists code text;
alter table roles add column if not exists name text;
alter table roles add column if not exists description text not null default '';

insert into roles (code, name, description)
select seed.code, seed.name, seed.description
from (
  values
    ('owner', 'Owner', 'Company owner'),
    ('admin', 'Admin', 'Company admin'),
    ('accountant', 'Accountant', 'Company accountant'),
    ('auditor', 'Auditor', 'Company auditor'),
    ('viewer', 'Viewer', 'Read-only viewer')
) as seed(code, name, description)
where not exists (
  select 1
  from roles
  where roles.code = seed.code
);

do $$
begin
  if not exists (
    select 1
    from pg_indexes
    where schemaname = 'public'
      and indexname = 'roles_code_unique_idx'
  )
  and not exists (
    select 1
    from roles
    where code is not null
    group by code
    having count(*) > 1
  ) then
    create unique index roles_code_unique_idx on roles(code) where code is not null;
  end if;
end $$;

create table if not exists company_members (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  profile_id uuid,
  user_id uuid,
  role_id uuid references roles(id),
  role text,
  status text not null default 'active',
  created_at timestamptz not null default now()
);

alter table company_members add column if not exists profile_id uuid;
alter table company_members add column if not exists user_id uuid;
alter table company_members add column if not exists role_id uuid references roles(id);
alter table company_members add column if not exists role text;
alter table company_members add column if not exists status text not null default 'active';
alter table company_members add column if not exists created_at timestamptz not null default now();

update company_members set profile_id = user_id where profile_id is null and user_id is not null;
update company_members set user_id = profile_id where user_id is null and profile_id is not null;
update company_members set status = coalesce(status, 'active');
update company_members
  set role_id = coalesce(role_id, (select id from roles where code = 'owner' limit 1)),
      role = coalesce(role, 'Owner');

create table if not exists subscriptions (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  profile_id uuid,
  user_id uuid,
  plan_name text not null default 'Free Trial',
  trial_start timestamptz,
  trial_end timestamptz,
  status text not null default 'trialing',
  active boolean not null default true,
  current_period_start timestamptz,
  current_period_end timestamptz,
  created_at timestamptz not null default now()
);

alter table subscriptions add column if not exists company_id uuid references companies(id) on delete cascade;
alter table subscriptions add column if not exists profile_id uuid;
alter table subscriptions add column if not exists user_id uuid;
alter table subscriptions add column if not exists current_period_start timestamptz;
alter table subscriptions add column if not exists current_period_end timestamptz;

create table if not exists payments (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  profile_id uuid,
  user_id uuid,
  razorpay_payment_id text,
  amount numeric(18, 2) not null default 0,
  status text not null default 'pending',
  created_at timestamptz not null default now()
);

alter table payments add column if not exists company_id uuid references companies(id) on delete cascade;
alter table payments add column if not exists profile_id uuid;
alter table payments add column if not exists user_id uuid;
alter table payments add column if not exists razorpay_payment_id text;

create table if not exists ledger_groups (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  name text not null,
  account_nature text not null,
  parent_id uuid,
  is_system boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists ledgers (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  ledger_group_id uuid references ledger_groups(id),
  name text not null,
  category text not null default 'other',
  account_nature text not null,
  opening_balance numeric(18, 2) not null default 0,
  opening_balance_type text not null default 'dr',
  gstin text,
  state_code text,
  is_system boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists vouchers (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_number text not null,
  voucher_type text not null,
  voucher_date date not null default current_date,
  status text not null default 'posted',
  narration text,
  source text not null default 'manual',
  created_by uuid,
  approved_by uuid,
  posted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table vouchers add column if not exists updated_at timestamptz not null default now();

create table if not exists journal_entries (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid not null references vouchers(id) on delete cascade,
  ledger_id uuid references ledgers(id),
  line_number integer not null default 1,
  debit numeric(18, 2) not null default 0,
  credit numeric(18, 2) not null default 0,
  narration text,
  created_at timestamptz not null default now()
);

create table if not exists voucher_lines (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid references vouchers(id) on delete cascade,
  ledger_id uuid references ledgers(id),
  ledger_name text,
  line_number integer not null default 1,
  debit numeric(18, 2) not null default 0,
  credit numeric(18, 2) not null default 0,
  narration text,
  created_at timestamptz not null default now()
);

create table if not exists accounting_entries (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid references vouchers(id) on delete set null,
  journal_entry_id uuid references journal_entries(id) on delete set null,
  invoice_id uuid,
  entry_type text not null,
  payload jsonb not null default '{}'::jsonb,
  created_by uuid,
  created_at timestamptz not null default now()
);

alter table accounting_entries add column if not exists invoice_id uuid;

create table if not exists invoices (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_type text not null default 'sales',
  invoice_number text not null,
  invoice_date date not null default current_date,
  due_date date,
  party_name text,
  party_gstin text,
  party_ledger_id uuid references ledgers(id),
  voucher_id uuid references vouchers(id) on delete set null,
  gst_supply_type text not null default 'intra_state',
  status text not null default 'issued',
  taxable_value numeric(18, 2) not null default 0,
  cgst_amount numeric(18, 2) not null default 0,
  sgst_amount numeric(18, 2) not null default 0,
  igst_amount numeric(18, 2) not null default 0,
  total_amount numeric(18, 2) not null default 0,
  notes text,
  created_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table invoices add column if not exists status text not null default 'issued';
alter table invoices add column if not exists updated_at timestamptz not null default now();

create table if not exists invoice_lines (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_id uuid not null references invoices(id) on delete cascade,
  line_number integer not null default 1,
  description text not null,
  hsn_sac text,
  quantity numeric(18, 3) not null default 1,
  unit text not null default 'NOS',
  unit_price numeric(18, 2) not null default 0,
  discount_amount numeric(18, 2) not null default 0,
  gst_rate numeric(5, 2) not null default 0,
  taxable_value numeric(18, 2) not null default 0,
  cgst_amount numeric(18, 2) not null default 0,
  sgst_amount numeric(18, 2) not null default 0,
  igst_amount numeric(18, 2) not null default 0,
  total_amount numeric(18, 2) not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists invoice_items (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_id uuid references invoices(id) on delete cascade,
  line_number integer not null default 1,
  description text not null,
  hsn_sac text,
  quantity numeric(18, 3) not null default 1,
  unit text not null default 'NOS',
  unit_price numeric(18, 2) not null default 0,
  discount_amount numeric(18, 2) not null default 0,
  gst_rate numeric(5, 2) not null default 0,
  taxable_value numeric(18, 2) not null default 0,
  cgst_amount numeric(18, 2) not null default 0,
  sgst_amount numeric(18, 2) not null default 0,
  igst_amount numeric(18, 2) not null default 0,
  total_amount numeric(18, 2) not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists gst_rates (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  label text not null,
  rate numeric(5, 2) not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists bank_accounts (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  ledger_id uuid references ledgers(id),
  bank_name text not null,
  account_number_last4 text,
  ifsc text,
  is_active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists bank_statements (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_account_id uuid references bank_accounts(id),
  file_path text not null default '',
  statement_from date,
  statement_to date,
  uploaded_by uuid,
  created_at timestamptz not null default now()
);

create table if not exists bank_transactions (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_statement_id uuid references bank_statements(id),
  transaction_date date not null default current_date,
  description text not null,
  reference_number text,
  debit numeric(18, 2) not null default 0,
  credit numeric(18, 2) not null default 0,
  balance numeric(18, 2),
  reconciliation_status text not null default 'unmatched',
  created_at timestamptz not null default now()
);

create table if not exists reconciliation_matches (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_transaction_id uuid references bank_transactions(id) on delete cascade,
  journal_entry_id uuid references journal_entries(id) on delete cascade,
  confidence numeric(5, 2) not null default 0,
  matched_by uuid,
  matched_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists bank_matches (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  bank_transaction_id uuid references bank_transactions(id) on delete cascade,
  voucher_id uuid references vouchers(id) on delete cascade,
  journal_entry_id uuid references journal_entries(id) on delete cascade,
  confidence numeric(5, 2) not null default 0,
  status text not null default 'suggested',
  matched_by uuid,
  matched_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists ai_logs (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  profile_id uuid,
  user_id uuid,
  action_type text not null,
  input_payload jsonb not null default '{}'::jsonb,
  output_payload jsonb not null default '{}'::jsonb,
  confidence numeric(5, 2),
  created_at timestamptz not null default now()
);

create table if not exists document_ai_logs (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  user_id uuid,
  file_name text not null,
  document_type text not null,
  extracted_text text not null default '',
  extracted_json jsonb not null default '{}'::jsonb,
  confidence_score numeric(5, 2) not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists audit_logs (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  actor_id uuid,
  action_type text not null,
  entity_type text not null,
  entity_id uuid,
  event_payload jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table if not exists inventory_items (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  item_name text not null,
  sku text,
  unit text not null default 'NOS',
  hsn_sac text,
  opening_stock numeric(18, 3) not null default 0,
  purchase_stock numeric(18, 3) not null default 0,
  sales_stock numeric(18, 3) not null default 0,
  closing_stock numeric(18, 3) not null default 0,
  rate numeric(18, 2) not null default 0,
  stock_value numeric(18, 2) not null default 0,
  created_by uuid,
  created_at timestamptz not null default now()
);

create table if not exists site_visits (
  id uuid primary key default gen_random_uuid(),
  path text not null,
  referrer text,
  user_agent text,
  ip_hash text not null,
  created_at timestamptz not null default now()
);

alter table profiles add column if not exists created_at timestamptz default now();
alter table company_members add column if not exists created_at timestamptz default now();
alter table subscriptions add column if not exists created_at timestamptz default now();
alter table payments add column if not exists created_at timestamptz default now();
alter table ledger_groups add column if not exists created_at timestamptz default now();
alter table ledgers add column if not exists created_at timestamptz default now();
alter table vouchers add column if not exists created_at timestamptz default now();
alter table journal_entries add column if not exists created_at timestamptz default now();
alter table voucher_lines add column if not exists created_at timestamptz default now();
alter table accounting_entries add column if not exists created_at timestamptz default now();
alter table invoices add column if not exists created_at timestamptz default now();
alter table invoice_lines add column if not exists created_at timestamptz default now();
alter table invoice_items add column if not exists created_at timestamptz default now();
alter table gst_rates add column if not exists created_at timestamptz default now();
alter table bank_accounts add column if not exists created_at timestamptz default now();
alter table bank_statements add column if not exists created_at timestamptz default now();
alter table bank_transactions add column if not exists created_at timestamptz default now();
alter table reconciliation_matches add column if not exists created_at timestamptz default now();
alter table bank_matches add column if not exists created_at timestamptz default now();
alter table ai_logs add column if not exists created_at timestamptz default now();
alter table document_ai_logs add column if not exists created_at timestamptz default now();
alter table audit_logs add column if not exists created_at timestamptz default now();
alter table inventory_items add column if not exists created_at timestamptz default now();
alter table site_visits add column if not exists created_at timestamptz default now();

create index if not exists companies_created_by_idx on companies(created_by);
create index if not exists company_members_company_id_idx on company_members(company_id);
create index if not exists company_members_profile_id_idx on company_members(profile_id);
create index if not exists company_members_user_id_idx on company_members(user_id);
create index if not exists subscriptions_company_id_idx on subscriptions(company_id);
create index if not exists subscriptions_user_id_idx on subscriptions(user_id);
create index if not exists subscriptions_created_at_idx on subscriptions(created_at desc);
create index if not exists payments_company_id_idx on payments(company_id);
create index if not exists payments_user_id_idx on payments(user_id);
create unique index if not exists payments_razorpay_payment_id_unique_idx on payments(razorpay_payment_id) where razorpay_payment_id is not null;
create index if not exists ledger_groups_company_id_idx on ledger_groups(company_id);
create index if not exists ledgers_company_id_idx on ledgers(company_id);
create index if not exists vouchers_company_id_idx on vouchers(company_id);
create index if not exists vouchers_company_date_idx on vouchers(company_id, voucher_date);
create index if not exists journal_entries_company_id_idx on journal_entries(company_id);
create index if not exists journal_entries_voucher_id_idx on journal_entries(voucher_id);
create index if not exists voucher_lines_company_id_idx on voucher_lines(company_id);
create index if not exists voucher_lines_voucher_id_idx on voucher_lines(voucher_id);
create index if not exists accounting_entries_company_id_idx on accounting_entries(company_id);
create index if not exists accounting_entries_voucher_id_idx on accounting_entries(voucher_id);
create index if not exists accounting_entries_invoice_id_idx on accounting_entries(invoice_id);
create index if not exists invoices_company_id_idx on invoices(company_id);
create index if not exists invoices_company_date_idx on invoices(company_id, invoice_date);
create index if not exists invoice_lines_company_id_idx on invoice_lines(company_id);
create index if not exists invoice_lines_invoice_id_idx on invoice_lines(invoice_id);
create index if not exists invoice_items_company_id_idx on invoice_items(company_id);
create index if not exists invoice_items_invoice_id_idx on invoice_items(invoice_id);
create index if not exists gst_rates_company_id_idx on gst_rates(company_id);
create index if not exists bank_transactions_company_id_idx on bank_transactions(company_id);
create index if not exists bank_transactions_created_at_idx on bank_transactions(created_at desc);
create index if not exists bank_matches_company_id_idx on bank_matches(company_id);
create index if not exists ai_logs_company_id_idx on ai_logs(company_id);
create index if not exists ai_logs_user_id_idx on ai_logs(user_id);
create index if not exists document_ai_logs_company_id_idx on document_ai_logs(company_id);
create index if not exists document_ai_logs_user_id_idx on document_ai_logs(user_id);
create index if not exists audit_logs_company_id_idx on audit_logs(company_id);
create index if not exists audit_logs_created_at_idx on audit_logs(created_at desc);
create index if not exists inventory_items_company_id_idx on inventory_items(company_id);
create index if not exists inventory_items_created_at_idx on inventory_items(created_at desc);
create index if not exists site_visits_created_at_idx on site_visits(created_at desc);

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'vouchers_status_check') then
    alter table vouchers add constraint vouchers_status_check check (status::text in ('draft', 'posted', 'cancelled')) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'invoices_status_check') then
    alter table invoices add constraint invoices_status_check check (status::text in ('draft', 'issued', 'paid', 'cancelled')) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'subscriptions_status_check') then
    alter table subscriptions add constraint subscriptions_status_check check (status::text in ('trialing', 'active', 'expired', 'cancelled', 'payment_pending')) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'journal_entries_amount_non_negative') then
    alter table journal_entries add constraint journal_entries_amount_non_negative check (debit >= 0 and credit >= 0) not valid;
  end if;
  if not exists (select 1 from pg_constraint where conname = 'voucher_lines_amount_non_negative') then
    alter table voucher_lines add constraint voucher_lines_amount_non_negative check (debit >= 0 and credit >= 0) not valid;
  end if;
end $$;

create or replace function public.abhay_validate_posted_voucher_balance()
returns trigger
language plpgsql
as $$
declare
  target_voucher_id uuid;
  debit_total numeric(18, 2);
  credit_total numeric(18, 2);
  voucher_status text;
begin
  target_voucher_id := coalesce(new.voucher_id, old.voucher_id);
  if target_voucher_id is null then
    return coalesce(new, old);
  end if;

  select status::text into voucher_status from vouchers where id = target_voucher_id;
  if voucher_status <> 'posted' then
    return coalesce(new, old);
  end if;

  select coalesce(sum(debit), 0), coalesce(sum(credit), 0)
    into debit_total, credit_total
    from journal_entries
    where voucher_id = target_voucher_id;

  if debit_total <> credit_total then
    raise exception 'Posted voucher % is not balanced: debit %, credit %', target_voucher_id, debit_total, credit_total;
  end if;

  return coalesce(new, old);
end;
$$;

do $$
begin
  if not exists (select 1 from pg_trigger where tgname = 'abhay_posted_voucher_balance_check') then
    create constraint trigger abhay_posted_voucher_balance_check
      after insert or update or delete on journal_entries
      deferrable initially deferred
      for each row execute function public.abhay_validate_posted_voucher_balance();
  end if;
end $$;

create or replace function public.abhay_user_has_company_access(target_company_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if target_company_id is null or auth.uid() is null then
    return false;
  end if;
  return exists (
    select 1
    from public.company_members
    where company_id = target_company_id
      and coalesce(profile_id, user_id) = auth.uid()
      and coalesce(status::text, 'active') in ('active', 'owner', 'admin', 'accountant', 'auditor', 'viewer')
  )
  or exists (
    select 1
    from public.company_members
    where company_id = target_company_id
      and user_id = auth.uid()
      and coalesce(status::text, 'active') in ('active', 'owner', 'admin', 'accountant', 'auditor', 'viewer')
  );
end;
$$;

grant execute on function public.abhay_user_has_company_access(uuid) to authenticated;

alter table companies enable row level security;
alter table company_members enable row level security;
alter table subscriptions enable row level security;
alter table payments enable row level security;
alter table ledger_groups enable row level security;
alter table ledgers enable row level security;
alter table vouchers enable row level security;
alter table journal_entries enable row level security;
alter table voucher_lines enable row level security;
alter table accounting_entries enable row level security;
alter table invoices enable row level security;
alter table invoice_lines enable row level security;
alter table invoice_items enable row level security;
alter table gst_rates enable row level security;
alter table bank_accounts enable row level security;
alter table bank_statements enable row level security;
alter table bank_transactions enable row level security;
alter table reconciliation_matches enable row level security;
alter table bank_matches enable row level security;
alter table ai_logs enable row level security;
alter table document_ai_logs enable row level security;
alter table audit_logs enable row level security;
alter table inventory_items enable row level security;
alter table site_visits enable row level security;

do $$
declare
  table_name text;
  business_tables text[] := array[
    'companies', 'company_members', 'ledger_groups', 'ledgers', 'vouchers', 'journal_entries',
    'voucher_lines', 'accounting_entries', 'invoices', 'invoice_lines', 'invoice_items',
    'gst_rates', 'bank_accounts', 'bank_statements', 'bank_transactions', 'reconciliation_matches',
    'bank_matches', 'ai_logs', 'document_ai_logs', 'audit_logs', 'inventory_items'
  ];
begin
  foreach table_name in array business_tables loop
    if not exists (
      select 1 from pg_policies
      where schemaname = 'public' and tablename = table_name and policyname = 'company members can read company data'
    ) then
      execute format(
        'create policy "company members can read company data" on %I for select to authenticated using (
          case
            when %L in (''companies'') then public.abhay_user_has_company_access(id)
            when %L in (''company_members'') then public.abhay_user_has_company_access(company_id)
            when %L in (''gst_rates'') then company_id is null or public.abhay_user_has_company_access(company_id)
            else public.abhay_user_has_company_access(company_id)
          end
        )',
        table_name, table_name, table_name, table_name
      );
    end if;

    if not exists (
      select 1 from pg_policies
      where schemaname = 'public' and tablename = table_name and policyname = 'company members can write company data'
    ) then
      execute format(
        'create policy "company members can write company data" on %I for all to authenticated using (
          case
            when %L in (''companies'') then created_by = auth.uid() or public.abhay_user_has_company_access(id)
            when %L in (''company_members'') then public.abhay_user_has_company_access(company_id)
            when %L in (''gst_rates'') then company_id is null or public.abhay_user_has_company_access(company_id)
            else public.abhay_user_has_company_access(company_id)
          end
        ) with check (
          case
            when %L in (''companies'') then created_by = auth.uid() or public.abhay_user_has_company_access(id)
            when %L in (''company_members'') then public.abhay_user_has_company_access(company_id)
            when %L in (''gst_rates'') then company_id is null or public.abhay_user_has_company_access(company_id)
            else public.abhay_user_has_company_access(company_id)
          end
        )',
        table_name, table_name, table_name, table_name, table_name, table_name, table_name
      );
    end if;
  end loop;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'subscriptions' and policyname = 'users can manage own subscriptions') then
    create policy "users can manage own subscriptions"
      on subscriptions for all
      to authenticated
      using (user_id = auth.uid() or profile_id = auth.uid() or public.abhay_user_has_company_access(company_id))
      with check (user_id = auth.uid() or profile_id = auth.uid() or public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'payments' and policyname = 'users can manage own payments') then
    create policy "users can manage own payments"
      on payments for all
      to authenticated
      using (user_id = auth.uid() or profile_id = auth.uid() or public.abhay_user_has_company_access(company_id))
      with check (user_id = auth.uid() or profile_id = auth.uid() or public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'site_visits' and policyname = 'public can insert privacy safe site visits') then
    create policy "public can insert privacy safe site visits"
      on site_visits for insert
      to anon, authenticated
      with check (true);
  end if;
end $$;
