create table if not exists customers (
    id uuid primary key,
    company_id uuid not null references companies(id),
    ledger_id uuid not null references ledgers(id),
    name varchar(200) not null,
    display_name varchar(200) not null,
    gstin varchar(15),
    pan varchar(10),
    email varchar(254),
    phone varchar(30),
    billing_address varchar(1000),
    shipping_address varchar(1000),
    state varchar(100),
    country varchar(100) not null default 'India',
    credit_limit numeric(19,2) not null default 0,
    payment_terms_days integer not null default 0,
    opening_balance numeric(19,2) not null default 0,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_customer_company_ledger unique (company_id, ledger_id),
    constraint ck_customer_credit_limit check (credit_limit >= 0),
    constraint ck_customer_terms check (payment_terms_days >= 0),
    constraint ck_customer_opening check (opening_balance >= 0)
);

create table if not exists vendors (
    id uuid primary key,
    company_id uuid not null references companies(id),
    ledger_id uuid not null references ledgers(id),
    name varchar(200) not null,
    display_name varchar(200) not null,
    gstin varchar(15),
    pan varchar(10),
    email varchar(254),
    phone varchar(30),
    address varchar(1000),
    state varchar(100),
    country varchar(100) not null default 'India',
    payment_terms_days integer not null default 0,
    opening_balance numeric(19,2) not null default 0,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_vendor_company_ledger unique (company_id, ledger_id),
    constraint ck_vendor_terms check (payment_terms_days >= 0),
    constraint ck_vendor_opening check (opening_balance >= 0)
);

create table if not exists items (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(200) not null,
    item_type varchar(20) not null,
    sku varchar(80),
    hsn_sac varchar(20),
    unit varchar(30) not null,
    sales_price numeric(19,2) not null default 0,
    purchase_price numeric(19,2) not null default 0,
    gst_rate numeric(5,2) not null default 0,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_item_company_name unique (company_id, name),
    constraint uk_item_company_sku unique (company_id, sku),
    constraint ck_item_type check (item_type in ('PRODUCT', 'SERVICE')),
    constraint ck_item_prices check (sales_price >= 0 and purchase_price >= 0),
    constraint ck_item_gst_rate check (gst_rate >= 0 and gst_rate <= 100)
);

create table if not exists invoices (
    id uuid primary key,
    company_id uuid not null references companies(id),
    financial_year_id uuid not null references financial_years(id),
    invoice_type varchar(20) not null,
    invoice_number varchar(80) not null,
    invoice_date date not null,
    due_date date not null,
    customer_id uuid references customers(id),
    vendor_id uuid references vendors(id),
    party_key uuid not null,
    status varchar(20) not null,
    subtotal numeric(19,2) not null default 0,
    cgst_total numeric(19,2) not null default 0,
    sgst_total numeric(19,2) not null default 0,
    igst_total numeric(19,2) not null default 0,
    total numeric(19,2) not null default 0,
    notes varchar(2000),
    posted_voucher_id uuid references vouchers(id),
    created_by uuid not null references users(id),
    approved_by uuid references users(id),
    approved_at timestamp with time zone,
    posted_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_invoice_party_number unique
        (company_id, financial_year_id, invoice_type, party_key, invoice_number),
    constraint ck_invoice_type check (invoice_type in ('SALES', 'PURCHASE')),
    constraint ck_invoice_status check (status in ('DRAFT', 'APPROVED', 'POSTED', 'PAID', 'CANCELLED')),
    constraint ck_invoice_party check (
        (invoice_type = 'SALES' and customer_id is not null and vendor_id is null)
        or (invoice_type = 'PURCHASE' and vendor_id is not null and customer_id is null)
    ),
    constraint ck_invoice_dates check (due_date >= invoice_date),
    constraint ck_invoice_totals check (
        subtotal >= 0 and cgst_total >= 0 and sgst_total >= 0 and igst_total >= 0 and total >= 0
    )
);

create table if not exists invoice_items (
    id uuid primary key,
    company_id uuid not null references companies(id),
    invoice_id uuid not null references invoices(id),
    item_id uuid references items(id),
    line_number integer not null,
    description varchar(500) not null,
    quantity numeric(19,4) not null,
    unit_price numeric(19,2) not null,
    gst_rate numeric(5,2) not null,
    taxable_amount numeric(19,2) not null,
    cgst_amount numeric(19,2) not null default 0,
    sgst_amount numeric(19,2) not null default 0,
    igst_amount numeric(19,2) not null default 0,
    line_total numeric(19,2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_invoice_item_line unique (invoice_id, line_number),
    constraint ck_invoice_item_values check (
        quantity > 0 and unit_price >= 0 and gst_rate >= 0 and gst_rate <= 100
        and taxable_amount >= 0 and cgst_amount >= 0 and sgst_amount >= 0
        and igst_amount >= 0 and line_total >= 0
    )
);

create table if not exists invoice_payments (
    id uuid primary key,
    company_id uuid not null references companies(id),
    invoice_id uuid not null references invoices(id),
    payment_date date not null,
    amount numeric(19,2) not null,
    mode varchar(30) not null,
    reference varchar(120),
    linked_voucher_id uuid not null references vouchers(id),
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_invoice_payment_amount check (amount > 0)
);

create index if not exists idx_customers_company on customers(company_id);
create index if not exists idx_vendors_company on vendors(company_id);
create index if not exists idx_items_company on items(company_id);
create index if not exists idx_invoices_company_date on invoices(company_id, invoice_date);
create index if not exists idx_invoices_company_number on invoices(company_id, invoice_number);
create index if not exists idx_invoices_company_status on invoices(company_id, status);
create index if not exists idx_invoices_customer on invoices(company_id, customer_id);
create index if not exists idx_invoices_vendor on invoices(company_id, vendor_id);
create index if not exists idx_invoice_items_company_item on invoice_items(company_id, item_id);
create index if not exists idx_invoice_payments_company_invoice on invoice_payments(company_id, invoice_id);
