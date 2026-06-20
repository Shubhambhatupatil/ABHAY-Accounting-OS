create extension if not exists pgcrypto;

create table if not exists profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text,
  created_at timestamptz not null default now()
);

alter table profiles
  add column if not exists email text;

alter table profiles
  add column if not exists created_at timestamptz not null default now();

create table if not exists subscriptions (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  profile_id uuid,
  user_id uuid,
  plan_name text not null default 'Free Trial',
  trial_start timestamptz,
  trial_end timestamptz,
  status text not null default 'trial',
  active boolean not null default true,
  created_at timestamptz not null default now()
);

alter table subscriptions
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table subscriptions
  add column if not exists profile_id uuid;

alter table subscriptions
  add column if not exists user_id uuid;

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

alter table payments
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table payments
  add column if not exists profile_id uuid;

alter table payments
  add column if not exists user_id uuid;

create table if not exists vouchers (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_number text not null,
  voucher_type text not null,
  voucher_date date not null default current_date,
  status text not null default 'draft',
  narration text,
  source text not null default 'manual',
  created_by uuid,
  approved_by uuid,
  posted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists voucher_lines (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid not null references vouchers(id) on delete cascade,
  ledger_id uuid,
  ledger_name text,
  line_number integer not null default 1,
  debit numeric(18, 2) not null default 0,
  credit numeric(18, 2) not null default 0,
  narration text,
  created_at timestamptz not null default now()
);

create table if not exists invoices (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  invoice_type text not null default 'sales',
  invoice_number text not null,
  invoice_date date not null default current_date,
  due_date date,
  party_name text,
  party_gstin text,
  party_ledger_id uuid,
  voucher_id uuid references vouchers(id) on delete set null,
  gst_supply_type text,
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

create table if not exists accounting_entries (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid references vouchers(id) on delete set null,
  invoice_id uuid references invoices(id) on delete set null,
  entry_type text not null,
  payload jsonb not null default '{}'::jsonb,
  created_by uuid,
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

create table if not exists site_visits (
  id uuid primary key default gen_random_uuid(),
  path text not null,
  referrer text,
  user_agent text,
  ip_hash text not null,
  created_at timestamptz not null default now()
);

alter table vouchers
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table voucher_lines
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table invoices
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table accounting_entries
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table ai_logs
  add column if not exists company_id uuid references companies(id) on delete cascade;

alter table audit_logs
  add column if not exists company_id uuid references companies(id) on delete cascade;

create index if not exists profiles_email_idx on profiles(email);
create index if not exists subscriptions_company_id_idx on subscriptions(company_id);
create index if not exists subscriptions_profile_id_idx on subscriptions(profile_id);
create index if not exists subscriptions_user_id_idx on subscriptions(user_id);
create index if not exists payments_company_id_idx on payments(company_id);
create index if not exists payments_profile_id_idx on payments(profile_id);
create index if not exists payments_user_id_idx on payments(user_id);
create index if not exists vouchers_company_id_idx on vouchers(company_id);
create index if not exists vouchers_company_date_idx on vouchers(company_id, voucher_date);
create index if not exists voucher_lines_company_id_idx on voucher_lines(company_id);
create index if not exists voucher_lines_voucher_id_idx on voucher_lines(voucher_id);
create index if not exists invoices_company_id_idx on invoices(company_id);
create index if not exists invoices_company_date_idx on invoices(company_id, invoice_date);
create index if not exists accounting_entries_company_id_idx on accounting_entries(company_id);
create index if not exists accounting_entries_voucher_id_idx on accounting_entries(voucher_id);
create index if not exists accounting_entries_invoice_id_idx on accounting_entries(invoice_id);
create index if not exists ai_logs_company_id_idx on ai_logs(company_id);
create index if not exists ai_logs_profile_id_idx on ai_logs(profile_id);
create index if not exists ai_logs_user_id_idx on ai_logs(user_id);
create index if not exists audit_logs_company_id_idx on audit_logs(company_id);
create index if not exists audit_logs_entity_idx on audit_logs(entity_type, entity_id);
create index if not exists site_visits_created_at_idx on site_visits(created_at desc);
create index if not exists site_visits_path_idx on site_visits(path);
create index if not exists site_visits_ip_hash_idx on site_visits(ip_hash);

create or replace function public.abhay_user_has_company_access(target_company_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  has_profile_id boolean;
  has_user_id boolean;
  has_status boolean;
  allowed boolean := false;
begin
  if target_company_id is null or auth.uid() is null then
    return false;
  end if;

  select exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'company_members'
      and column_name = 'profile_id'
  ) into has_profile_id;

  select exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'company_members'
      and column_name = 'user_id'
  ) into has_user_id;

  select exists (
    select 1
    from information_schema.columns
    where table_schema = 'public'
      and table_name = 'company_members'
      and column_name = 'status'
  ) into has_status;

  if has_profile_id then
    execute format(
      'select exists (
        select 1 from public.company_members
        where company_id = $1 and profile_id = $2 %s
      )',
      case when has_status then 'and coalesce(status::text, ''active'') in (''active'', ''owner'', ''admin'', ''accountant'', ''auditor'', ''viewer'')' else '' end
    )
    using target_company_id, auth.uid()
    into allowed;

    if allowed then
      return true;
    end if;
  end if;

  if has_user_id then
    execute format(
      'select exists (
        select 1 from public.company_members
        where company_id = $1 and user_id = $2 %s
      )',
      case when has_status then 'and coalesce(status::text, ''active'') in (''active'', ''owner'', ''admin'', ''accountant'', ''auditor'', ''viewer'')' else '' end
    )
    using target_company_id, auth.uid()
    into allowed;
  end if;

  return allowed;
end;
$$;

grant execute on function public.abhay_user_has_company_access(uuid) to authenticated;

alter table subscriptions enable row level security;
alter table payments enable row level security;
alter table vouchers enable row level security;
alter table voucher_lines enable row level security;
alter table invoices enable row level security;
alter table accounting_entries enable row level security;
alter table ai_logs enable row level security;
alter table audit_logs enable row level security;
alter table site_visits enable row level security;

grant insert on site_visits to anon, authenticated;
grant select on site_visits to authenticated;

do $$
begin
  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'subscriptions' and policyname = 'company members can read subscriptions') then
    create policy "company members can read subscriptions"
      on subscriptions for select
      to authenticated
      using (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'subscriptions' and policyname = 'company members can write subscriptions') then
    create policy "company members can write subscriptions"
      on subscriptions for all
      to authenticated
      using (public.abhay_user_has_company_access(company_id))
      with check (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'payments' and policyname = 'company members can read payments') then
    create policy "company members can read payments"
      on payments for select
      to authenticated
      using (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'payments' and policyname = 'company members can write payments') then
    create policy "company members can write payments"
      on payments for all
      to authenticated
      using (public.abhay_user_has_company_access(company_id))
      with check (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'vouchers' and policyname = 'company members can read vouchers') then
    create policy "company members can read vouchers"
      on vouchers for select
      to authenticated
      using (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'vouchers' and policyname = 'company members can write vouchers') then
    create policy "company members can write vouchers"
      on vouchers for all
      to authenticated
      using (public.abhay_user_has_company_access(company_id))
      with check (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'voucher_lines' and policyname = 'company members can read voucher lines') then
    create policy "company members can read voucher lines"
      on voucher_lines for select
      to authenticated
      using (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'voucher_lines' and policyname = 'company members can write voucher lines') then
    create policy "company members can write voucher lines"
      on voucher_lines for all
      to authenticated
      using (public.abhay_user_has_company_access(company_id))
      with check (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'invoices' and policyname = 'company members can read invoices') then
    create policy "company members can read invoices"
      on invoices for select
      to authenticated
      using (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'invoices' and policyname = 'company members can write invoices') then
    create policy "company members can write invoices"
      on invoices for all
      to authenticated
      using (public.abhay_user_has_company_access(company_id))
      with check (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'accounting_entries' and policyname = 'company members can read accounting entries') then
    create policy "company members can read accounting entries"
      on accounting_entries for select
      to authenticated
      using (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'accounting_entries' and policyname = 'company members can write accounting entries') then
    create policy "company members can write accounting entries"
      on accounting_entries for all
      to authenticated
      using (public.abhay_user_has_company_access(company_id))
      with check (public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'ai_logs' and policyname = 'company members can read ai logs') then
    create policy "company members can read ai logs"
      on ai_logs for select
      to authenticated
      using (company_id is null or public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'ai_logs' and policyname = 'company members can write ai logs') then
    create policy "company members can write ai logs"
      on ai_logs for all
      to authenticated
      using (company_id is null or public.abhay_user_has_company_access(company_id))
      with check (company_id is null or public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'audit_logs' and policyname = 'company members can read audit logs') then
    create policy "company members can read audit logs"
      on audit_logs for select
      to authenticated
      using (company_id is null or public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'audit_logs' and policyname = 'company members can write audit logs') then
    create policy "company members can write audit logs"
      on audit_logs for all
      to authenticated
      using (company_id is null or public.abhay_user_has_company_access(company_id))
      with check (company_id is null or public.abhay_user_has_company_access(company_id));
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'site_visits' and policyname = 'public can insert privacy safe site visits') then
    create policy "public can insert privacy safe site visits"
      on site_visits for insert
      to anon, authenticated
      with check (true);
  end if;

  if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'site_visits' and policyname = 'authenticated users can read site visits') then
    create policy "authenticated users can read site visits"
      on site_visits for select
      to authenticated
      using (true);
  end if;
end $$;
