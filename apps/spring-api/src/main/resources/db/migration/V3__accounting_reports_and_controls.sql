alter table financial_years add column if not exists locked boolean not null default false;
alter table financial_years add column if not exists locked_at timestamp with time zone;
alter table financial_years add column if not exists locked_by uuid references users(id);

alter table ledgers add column if not exists ledger_type varchar(20) not null default 'GENERAL';
alter table ledgers add constraint ck_ledger_type
    check (ledger_type in ('GENERAL', 'CASH', 'BANK', 'CUSTOMER', 'VENDOR', 'TAX'));

update ledgers set ledger_type = 'CASH' where upper(name) = 'CASH';
update ledgers set ledger_type = 'BANK' where upper(name) in ('PRIMARY BANK', 'BANK');
update ledgers set ledger_type = 'CUSTOMER' where upper(name) like '%CUSTOMER%';
update ledgers set ledger_type = 'VENDOR' where upper(name) like '%VENDOR%';
update ledgers set ledger_type = 'TAX' where upper(name) like 'GST %';

create table if not exists ledger_opening_balances (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    ledger_id uuid not null references ledgers(id),
    opening_debit numeric(19,2) not null default 0,
    opening_credit numeric(19,2) not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_ledger_opening_scope unique (company_id, financial_year_id, ledger_id),
    constraint ck_fy_ledger_opening_debit check (opening_debit >= 0),
    constraint ck_fy_ledger_opening_credit check (opening_credit >= 0),
    constraint ck_fy_ledger_opening_side check (opening_debit = 0 or opening_credit = 0)
);

create table if not exists voucher_attachments (
    id uuid primary key,
    company_id uuid not null references companies(id),
    voucher_id uuid not null references vouchers(id),
    file_name varchar(255) not null,
    file_type varchar(120) not null,
    file_size bigint not null,
    storage_key varchar(500) not null,
    uploaded_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_voucher_attachment_storage_key unique (storage_key),
    constraint ck_voucher_attachment_size check (file_size >= 0)
);

create index if not exists idx_ledger_opening_company_fy
    on ledger_opening_balances(company_id, financial_year_id);
create index if not exists idx_voucher_attachments_company_voucher
    on voucher_attachments(company_id, voucher_id);
