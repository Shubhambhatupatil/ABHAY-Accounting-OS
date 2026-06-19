create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  plan_name text not null default 'Free Trial',
  trial_start timestamptz not null default now(),
  trial_end timestamptz not null default (now() + interval '14 days'),
  status text not null default 'trialing',
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  razorpay_payment_id text not null,
  amount integer not null,
  status text not null,
  created_at timestamptz not null default now()
);

create index if not exists subscriptions_user_id_created_at_idx
  on public.subscriptions(user_id, created_at desc);

create index if not exists payments_user_id_created_at_idx
  on public.payments(user_id, created_at desc);

alter table public.profiles enable row level security;
alter table public.subscriptions enable row level security;
alter table public.payments enable row level security;

drop policy if exists "Users can read own profile" on public.profiles;
create policy "Users can read own profile"
  on public.profiles for select
  using (auth.uid() = id);

drop policy if exists "Users can insert own profile" on public.profiles;
create policy "Users can insert own profile"
  on public.profiles for insert
  with check (auth.uid() = id);

drop policy if exists "Users can read own subscriptions" on public.subscriptions;
create policy "Users can read own subscriptions"
  on public.subscriptions for select
  using (auth.uid() = user_id);

drop policy if exists "Users can insert own subscriptions" on public.subscriptions;
create policy "Users can insert own subscriptions"
  on public.subscriptions for insert
  with check (auth.uid() = user_id);

drop policy if exists "Users can update own subscriptions" on public.subscriptions;
create policy "Users can update own subscriptions"
  on public.subscriptions for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "Users can read own payments" on public.payments;
create policy "Users can read own payments"
  on public.payments for select
  using (auth.uid() = user_id);

drop policy if exists "Users can insert own payments" on public.payments;
create policy "Users can insert own payments"
  on public.payments for insert
  with check (auth.uid() = user_id);

create or replace function public.create_profile_and_trial()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email)
  values (new.id, coalesce(new.email, ''))
  on conflict (id) do nothing;

  insert into public.subscriptions (
    user_id,
    plan_name,
    trial_start,
    trial_end,
    status,
    active
  )
  values (
    new.id,
    'Free Trial',
    now(),
    now() + interval '14 days',
    'trialing',
    true
  )
  on conflict do nothing;

  return new;
end;
$$;

drop trigger if exists create_profile_and_trial_on_signup on auth.users;
create trigger create_profile_and_trial_on_signup
  after insert on auth.users
  for each row execute function public.create_profile_and_trial();
