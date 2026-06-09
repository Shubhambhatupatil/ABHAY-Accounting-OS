create table if not exists gst_rates (
  id uuid primary key default gen_random_uuid(),
  company_id uuid references companies(id) on delete cascade,
  label text not null,
  rate numeric(5,2) not null check (rate >= 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (company_id, rate)
);

alter table gst_rates enable row level security;

create policy gst_rates_member_select on gst_rates
  for select using (company_id is null or is_company_member(company_id));

create policy gst_rates_member_all on gst_rates
  for all using (company_id is not null and is_company_member(company_id))
  with check (company_id is not null and is_company_member(company_id));

insert into gst_rates (company_id, label, rate, is_active)
values
  (null, 'GST 0%', 0.00, true),
  (null, 'GST 5%', 5.00, true),
  (null, 'GST 12%', 12.00, true),
  (null, 'GST 18%', 18.00, true),
  (null, 'GST 28%', 28.00, true)
on conflict (company_id, rate) do nothing;
