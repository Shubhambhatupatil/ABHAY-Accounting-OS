alter table invoices add column if not exists gst_treatment varchar(30) not null default 'NORMAL';
alter table invoices add column if not exists place_of_supply varchar(2);
alter table invoices add column if not exists cess_total numeric(19,2) not null default 0;
alter table invoice_items add column if not exists cess_rate numeric(5,2) not null default 0;
alter table invoice_items add column if not exists cess_amount numeric(19,2) not null default 0;

alter table invoices add constraint ck_invoice_gst_treatment
    check (gst_treatment in ('NORMAL', 'REVERSE_CHARGE', 'COMPOSITION', 'EXPORT', 'SEZ'));
alter table invoices add constraint ck_invoice_place_of_supply
    check (place_of_supply is null or length(place_of_supply) = 2);
alter table invoices add constraint ck_invoice_cess_total check (cess_total >= 0);
alter table invoice_items add constraint ck_invoice_item_cess
    check (cess_rate >= 0 and cess_rate <= 100 and cess_amount >= 0);

create table if not exists gst_rates (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(100) not null,
    rate numeric(5,2) not null,
    cess_rate numeric(5,2) not null default 0,
    system_rate boolean not null default false,
    reverse_charge_allowed boolean not null default true,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_gst_rate_scope unique (company_id, rate, cess_rate),
    constraint ck_gst_rate_value check (rate >= 0 and rate <= 100),
    constraint ck_gst_rate_cess check (cess_rate >= 0 and cess_rate <= 100)
);

create table if not exists gst_hsn_sac (
    id uuid primary key,
    company_id uuid not null references companies(id),
    code varchar(20) not null,
    code_type varchar(10) not null,
    description varchar(500) not null,
    gst_rate numeric(5,2) not null,
    cess_rate numeric(5,2) not null default 0,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_gst_hsn_sac_scope unique (company_id, code),
    constraint ck_gst_hsn_sac_type check (code_type in ('HSN', 'SAC')),
    constraint ck_gst_hsn_sac_rate check (gst_rate >= 0 and gst_rate <= 100),
    constraint ck_gst_hsn_sac_cess check (cess_rate >= 0 and cess_rate <= 100)
);

create table if not exists gst_rules (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(160) not null,
    gst_treatment varchar(30) not null,
    hsn_sac_prefix varchar(20),
    gst_rate numeric(5,2) not null,
    cess_rate numeric(5,2) not null default 0,
    reverse_charge boolean not null default false,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_gst_rule_company_name unique (company_id, name),
    constraint ck_gst_rule_treatment
        check (gst_treatment in ('NORMAL', 'REVERSE_CHARGE', 'COMPOSITION', 'EXPORT', 'SEZ')),
    constraint ck_gst_rule_rate check (gst_rate >= 0 and gst_rate <= 100),
    constraint ck_gst_rule_cess check (cess_rate >= 0 and cess_rate <= 100)
);

create table if not exists gst_returns (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    return_type varchar(20) not null,
    period_start date not null,
    period_end date not null,
    status varchar(20) not null default 'DRAFT',
    snapshot_json text not null,
    generated_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_gst_return_type check (return_type in ('GSTR1', 'GSTR3B')),
    constraint ck_gst_return_status check (status in ('DRAFT', 'FINALIZED', 'CANCELLED')),
    constraint ck_gst_return_period check (period_end >= period_start)
);

create table if not exists gst_return_items (
    id uuid primary key,
    company_id uuid not null references companies(id),
    gst_return_id uuid not null references gst_returns(id),
    invoice_id uuid references invoices(id),
    section_code varchar(30) not null,
    taxable_amount numeric(19,2) not null default 0,
    cgst_amount numeric(19,2) not null default 0,
    sgst_amount numeric(19,2) not null default 0,
    igst_amount numeric(19,2) not null default 0,
    cess_amount numeric(19,2) not null default 0,
    total_amount numeric(19,2) not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_gst_return_item_values check (
        taxable_amount >= 0 and cgst_amount >= 0 and sgst_amount >= 0
        and igst_amount >= 0 and cess_amount >= 0 and total_amount >= 0
    )
);

create table if not exists gst_liability (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    period_start date not null,
    period_end date not null,
    output_tax numeric(19,2) not null default 0,
    input_credit numeric(19,2) not null default 0,
    reverse_charge_tax numeric(19,2) not null default 0,
    cess_liability numeric(19,2) not null default 0,
    net_liability numeric(19,2) not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_gst_liability_period unique (company_id, period_start, period_end),
    constraint ck_gst_liability_period check (period_end >= period_start)
);

create table if not exists gst_alerts (
    id uuid primary key,
    company_id uuid not null references companies(id),
    invoice_id uuid references invoices(id),
    alert_type varchar(50) not null,
    severity varchar(20) not null,
    message varchar(500) not null,
    reason varchar(1000) not null,
    confidence numeric(5,4) not null,
    resolved boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_gst_alert_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint ck_gst_alert_confidence check (confidence >= 0 and confidence <= 1)
);

create index if not exists idx_gst_rates_company on gst_rates(company_id, active);
create index if not exists idx_gst_hsn_sac_company_code on gst_hsn_sac(company_id, code);
create index if not exists idx_gst_rules_company on gst_rules(company_id, active);
create index if not exists idx_gst_returns_company_period on gst_returns(company_id, period_start, period_end);
create index if not exists idx_gst_return_items_company_return on gst_return_items(company_id, gst_return_id);
create index if not exists idx_gst_return_items_invoice on gst_return_items(company_id, invoice_id);
create index if not exists idx_gst_liability_company_period on gst_liability(company_id, period_start, period_end);
create index if not exists idx_gst_alerts_company_resolved on gst_alerts(company_id, resolved, created_at);
