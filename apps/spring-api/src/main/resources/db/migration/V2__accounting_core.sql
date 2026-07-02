create table if not exists ledger_groups (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(160) not null,
    account_nature varchar(20) not null,
    parent_id uuid references ledger_groups(id),
    system_group boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_ledger_group_company_name unique (company_id, name),
    constraint ck_ledger_group_nature check (account_nature in ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE'))
);

create table if not exists ledgers (
    id uuid primary key,
    company_id uuid not null references companies(id),
    ledger_group_id uuid not null references ledger_groups(id),
    name varchar(200) not null,
    code varchar(40),
    normal_balance varchar(10) not null,
    opening_debit numeric(19,2) not null default 0,
    opening_credit numeric(19,2) not null default 0,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_ledger_company_name unique (company_id, name),
    constraint uk_ledger_company_code unique (company_id, code),
    constraint ck_ledger_normal_balance check (normal_balance in ('DEBIT', 'CREDIT')),
    constraint ck_ledger_opening_debit check (opening_debit >= 0),
    constraint ck_ledger_opening_credit check (opening_credit >= 0),
    constraint ck_ledger_opening_side check (opening_debit = 0 or opening_credit = 0)
);

create table if not exists voucher_types (
    id uuid primary key,
    code varchar(30) not null unique,
    name varchar(80) not null,
    system_type boolean not null default true,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists voucher_series (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    voucher_type_id uuid not null references voucher_types(id),
    prefix varchar(20) not null,
    next_number bigint not null default 1,
    padding integer not null default 6,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_voucher_series_scope unique (company_id, financial_year_id, voucher_type_id),
    constraint ck_voucher_series_next check (next_number > 0),
    constraint ck_voucher_series_padding check (padding between 1 and 12)
);

create table if not exists vouchers (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    voucher_type_id uuid not null references voucher_types(id),
    voucher_series_id uuid not null references voucher_series(id),
    voucher_number varchar(80) not null,
    voucher_date date not null,
    status varchar(20) not null,
    narration varchar(1000),
    created_by uuid not null references users(id),
    posted_by uuid references users(id),
    reversed_by uuid references users(id),
    reversal_of_id uuid references vouchers(id),
    reversal_voucher_id uuid references vouchers(id),
    posted_at timestamp with time zone,
    reversed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_voucher_number_scope unique (company_id, financial_year_id, voucher_type_id, voucher_number),
    constraint ck_voucher_status check (status in ('DRAFT', 'POSTED', 'REVERSED'))
);

create table if not exists voucher_lines (
    id uuid primary key,
    company_id uuid not null references companies(id),
    voucher_id uuid not null references vouchers(id),
    ledger_id uuid not null references ledgers(id),
    line_number integer not null,
    debit numeric(19,2) not null default 0,
    credit numeric(19,2) not null default 0,
    narration varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_voucher_line_number unique (voucher_id, line_number),
    constraint ck_voucher_line_debit check (debit >= 0),
    constraint ck_voucher_line_credit check (credit >= 0),
    constraint ck_voucher_line_amount check ((debit > 0 and credit = 0) or (credit > 0 and debit = 0))
);

create table if not exists journal_entries (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    voucher_id uuid not null unique references vouchers(id),
    entry_date date not null,
    description varchar(1000),
    reversal boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists journal_entry_lines (
    id uuid primary key,
    company_id uuid not null references companies(id),
    journal_entry_id uuid not null references journal_entries(id),
    ledger_id uuid not null references ledgers(id),
    line_number integer not null,
    debit numeric(19,2) not null default 0,
    credit numeric(19,2) not null default 0,
    narration varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_journal_line_number unique (journal_entry_id, line_number),
    constraint ck_journal_line_debit check (debit >= 0),
    constraint ck_journal_line_credit check (credit >= 0),
    constraint ck_journal_line_amount check ((debit > 0 and credit = 0) or (credit > 0 and debit = 0))
);

create table if not exists account_balances (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    ledger_id uuid not null references ledgers(id),
    debit_total numeric(19,2) not null default 0,
    credit_total numeric(19,2) not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_account_balance_scope unique (company_id, financial_year_id, ledger_id),
    constraint ck_account_balance_debit check (debit_total >= 0),
    constraint ck_account_balance_credit check (credit_total >= 0)
);

create table if not exists ai_memory_events (
    id uuid primary key,
    company_id uuid not null references companies(id),
    event_type varchar(80) not null,
    entity_type varchar(80) not null,
    entity_id uuid not null,
    payload_json text not null,
    processing_status varchar(20) not null default 'PENDING',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_ai_memory_status check (processing_status in ('PENDING', 'PROCESSED', 'FAILED'))
);

create index if not exists idx_ledger_groups_company on ledger_groups(company_id);
create index if not exists idx_ledgers_company on ledgers(company_id);
create index if not exists idx_vouchers_company_date on vouchers(company_id, voucher_date);
create index if not exists idx_voucher_lines_company on voucher_lines(company_id);
create index if not exists idx_journal_entries_company_date on journal_entries(company_id, entry_date);
create index if not exists idx_journal_lines_company_ledger on journal_entry_lines(company_id, ledger_id);
create index if not exists idx_account_balances_company on account_balances(company_id, financial_year_id);
create index if not exists idx_ai_memory_events_company on ai_memory_events(company_id, created_at);

insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001001', 'SALES', 'Sales', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'SALES');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001002', 'PURCHASE', 'Purchase', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'PURCHASE');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001003', 'PAYMENT', 'Payment', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'PAYMENT');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001004', 'RECEIPT', 'Receipt', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'RECEIPT');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001005', 'CONTRA', 'Contra', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'CONTRA');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001006', 'JOURNAL', 'Journal', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'JOURNAL');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001007', 'DEBIT_NOTE', 'Debit Note', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'DEBIT_NOTE');
insert into voucher_types (id, code, name, system_type, active, created_at, updated_at)
select '00000000-0000-0000-0000-000000001008', 'CREDIT_NOTE', 'Credit Note', true, true, current_timestamp, current_timestamp
where not exists (select 1 from voucher_types where code = 'CREDIT_NOTE');
