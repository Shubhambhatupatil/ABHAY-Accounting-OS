create extension if not exists pgcrypto;

create table if not exists public.companies (
  id uuid primary key default gen_random_uuid(),
  company_name text not null,
  gstin text,
  industry text,
  state text,
  financial_year text,
  created_at timestamptz not null default now()
);

create table if not exists public.company_members (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references public.companies(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('Owner', 'Admin', 'Accountant', 'Auditor', 'Viewer')),
  created_at timestamptz not null default now(),
  unique (company_id, user_id)
);

create index if not exists company_members_company_id_idx
  on public.company_members(company_id);

create index if not exists company_members_user_id_idx
  on public.company_members(user_id);

alter table public.companies enable row level security;
alter table public.company_members enable row level security;

create or replace function public.is_company_member(target_company_id uuid)
returns boolean
language sql
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.company_members cm
    where cm.company_id = target_company_id
    and cm.user_id = auth.uid()
  );
$$;

create or replace function public.can_manage_company(target_company_id uuid)
returns boolean
language sql
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.company_members cm
    where cm.company_id = target_company_id
    and cm.user_id = auth.uid()
    and cm.role in ('Owner', 'Admin')
  );
$$;

create or replace function public.company_has_any_members(target_company_id uuid)
returns boolean
language sql
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.company_members cm
    where cm.company_id = target_company_id
  );
$$;

drop policy if exists "Members can read their companies" on public.companies;
create policy "Members can read their companies"
  on public.companies for select
  using (public.is_company_member(id));

drop policy if exists "Authenticated users can create companies" on public.companies;
create policy "Authenticated users can create companies"
  on public.companies for insert
  with check (auth.uid() is not null);

drop policy if exists "Owners and admins can update companies" on public.companies;
create policy "Owners and admins can update companies"
  on public.companies for update
  using (public.can_manage_company(id))
  with check (public.can_manage_company(id));

drop policy if exists "Members can read company members" on public.company_members;
create policy "Members can read company members"
  on public.company_members for select
  using (user_id = auth.uid() or public.is_company_member(company_id));

drop policy if exists "First creator becomes owner" on public.company_members;
create policy "First creator becomes owner"
  on public.company_members for insert
  with check (
    user_id = auth.uid()
    and role = 'Owner'
    and not public.company_has_any_members(company_id)
  );

drop policy if exists "Owners and admins invite members" on public.company_members;
create policy "Owners and admins invite members"
  on public.company_members for insert
  with check (public.can_manage_company(company_id));

drop policy if exists "Owners and admins manage member roles" on public.company_members;
create policy "Owners and admins manage member roles"
  on public.company_members for update
  using (public.can_manage_company(company_id))
  with check (public.can_manage_company(company_id));
