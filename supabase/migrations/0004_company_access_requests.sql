create type access_request_status as enum ('pending', 'approved', 'rejected');

insert into roles (code, name, description)
values
  ('viewer', 'Viewer', 'Read-only company access for dashboards, ledgers, vouchers, invoices, and reports')
on conflict (code) do nothing;

insert into role_permissions (role_id, permission_id)
select roles.id, permissions.id
from roles
join permissions on permissions.code = 'reports.read'
where roles.code = 'viewer'
on conflict do nothing;

create table company_access_requests (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references companies(id) on delete cascade,
  requester_profile_id uuid not null references profiles(id) on delete cascade,
  requester_email text,
  requested_role text not null check (requested_role in ('accountant', 'viewer')),
  status access_request_status not null default 'pending',
  decided_by uuid references profiles(id),
  decided_at timestamptz,
  created_at timestamptz not null default now(),
  unique (company_id, requester_profile_id, status)
);

alter table company_access_requests enable row level security;

create policy company_access_requests_owner_select on company_access_requests
  for select using (is_company_owner(company_id));

create policy company_access_requests_requester_insert on company_access_requests
  for insert with check (requester_profile_id = auth.uid());

create policy company_access_requests_owner_update on company_access_requests
  for update using (is_company_owner(company_id))
  with check (is_company_owner(company_id));
