create extension if not exists pgcrypto;

alter table profiles
  add column if not exists email text;

create table if not exists subscriptions (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  profile_id uuid not null,
  plan_name text not null,
  trial_start timestamptz,
  trial_end timestamptz,
  status text not null,
  active boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists payments (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  profile_id uuid not null,
  razorpay_payment_id text,
  amount numeric(18, 2) not null,
  status text not null,
  created_at timestamptz not null default now()
);

create table if not exists accounting_entries (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  voucher_id uuid references vouchers(id) on delete set null,
  journal_entry_id uuid references journal_entries(id) on delete set null,
  entry_type text not null,
  payload jsonb not null default '{}'::jsonb,
  created_by uuid,
  created_at timestamptz not null default now()
);

create table if not exists ai_logs (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  profile_id uuid,
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

create index if not exists subscriptions_company_id_idx on subscriptions(company_id);
create index if not exists subscriptions_profile_id_idx on subscriptions(profile_id);
create index if not exists payments_company_id_idx on payments(company_id);
create index if not exists accounting_entries_company_id_idx on accounting_entries(company_id);
create index if not exists accounting_entries_voucher_id_idx on accounting_entries(voucher_id);
create index if not exists ai_logs_company_id_idx on ai_logs(company_id);
create index if not exists audit_logs_company_id_idx on audit_logs(company_id);
create index if not exists audit_logs_entity_idx on audit_logs(entity_type, entity_id);
