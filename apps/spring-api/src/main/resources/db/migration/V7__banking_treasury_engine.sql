create table if not exists bank_accounts (
    id uuid primary key,
    company_id uuid not null references companies(id),
    ledger_id uuid not null references ledgers(id),
    bank_name varchar(160) not null,
    account_name varchar(160) not null,
    account_number_masked varchar(40) not null,
    account_type varchar(20) not null,
    ifsc varchar(20),
    branch varchar(160),
    currency varchar(3) not null default 'INR',
    opening_balance numeric(19,2) not null default 0,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_bank_account_company_ledger unique (company_id, ledger_id),
    constraint ck_bank_account_type check (account_type in ('CURRENT','SAVINGS','OD','CC','CASH','WALLET','UPI')),
    constraint ck_bank_account_masked check (length(account_number_masked) between 4 and 40)
);

create table if not exists bank_statement_imports (
    id uuid primary key,
    company_id uuid not null references companies(id),
    bank_account_id uuid not null references bank_accounts(id),
    file_name varchar(255) not null,
    file_size bigint not null,
    file_hash varchar(64) not null,
    imported_rows integer not null default 0,
    duplicate_rows integer not null default 0,
    status varchar(20) not null,
    imported_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_bank_import_file unique (company_id, bank_account_id, file_hash),
    constraint ck_bank_import_status check (status in ('PROCESSING','COMPLETED','FAILED')),
    constraint ck_bank_import_counts check (imported_rows >= 0 and duplicate_rows >= 0)
);

create table if not exists bank_transactions (
    id uuid primary key,
    company_id uuid not null references companies(id),
    bank_account_id uuid not null references bank_accounts(id),
    statement_import_id uuid not null references bank_statement_imports(id),
    transaction_date date not null,
    description varchar(1000) not null,
    reference varchar(160),
    debit numeric(19,2) not null default 0,
    credit numeric(19,2) not null default 0,
    balance numeric(19,2),
    counterparty varchar(200),
    raw_hash varchar(64) not null,
    reconciliation_status varchar(20) not null default 'UNMATCHED',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_bank_transaction_raw unique (company_id, bank_account_id, raw_hash),
    constraint ck_bank_transaction_amounts check (debit >= 0 and credit >= 0 and ((debit > 0 and credit = 0) or (credit > 0 and debit = 0))),
    constraint ck_bank_reconciliation_status check (reconciliation_status in ('UNMATCHED','SUGGESTED','MATCHED','IGNORED'))
);

create table if not exists bank_reconciliation_matches (
    id uuid primary key,
    company_id uuid not null references companies(id),
    bank_transaction_id uuid not null references bank_transactions(id),
    voucher_id uuid references vouchers(id),
    invoice_payment_id uuid references invoice_payments(id),
    match_type varchar(30) not null,
    confidence numeric(5,4) not null,
    active boolean not null default true,
    confirmed_by uuid not null references users(id),
    confirmed_at timestamp with time zone not null,
    unmatched_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_bank_match_transaction unique (bank_transaction_id),
    constraint ck_bank_match_type check (match_type in ('MANUAL','SUGGESTED','EXACT')),
    constraint ck_bank_match_target check ((voucher_id is not null and invoice_payment_id is null) or (voucher_id is null and invoice_payment_id is not null)),
    constraint ck_bank_match_confidence check (confidence between 0 and 1)
);

create table if not exists bank_reconciliation_suggestions (
    id uuid primary key,
    company_id uuid not null references companies(id),
    bank_transaction_id uuid not null references bank_transactions(id),
    voucher_id uuid references vouchers(id),
    invoice_payment_id uuid references invoice_payments(id),
    target_type varchar(30) not null,
    confidence numeric(5,4) not null,
    reason varchar(500) not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_bank_suggestion_target_type check (target_type in ('VOUCHER','INVOICE_PAYMENT')),
    constraint ck_bank_suggestion_target check ((voucher_id is not null and invoice_payment_id is null) or (voucher_id is null and invoice_payment_id is not null)),
    constraint ck_bank_suggestion_confidence check (confidence between 0 and 1)
);

create table if not exists payment_methods (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(100) not null,
    method_type varchar(30) not null,
    bank_account_id uuid references bank_accounts(id),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_payment_method_company_name unique (company_id, name)
);

create table if not exists cash_flow_snapshots (
    id uuid primary key,
    company_id uuid not null references companies(id),
    snapshot_date date not null,
    bank_balance numeric(19,2) not null,
    cash_balance numeric(19,2) not null,
    total_liquidity numeric(19,2) not null,
    unreconciled_net numeric(19,2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_cash_flow_snapshot_company_date unique (company_id, snapshot_date)
);

create table if not exists treasury_alerts (
    id uuid primary key,
    company_id uuid not null references companies(id),
    alert_type varchar(40) not null,
    severity varchar(20) not null,
    message varchar(500) not null,
    amount numeric(19,2),
    resolved boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_treasury_alert_severity check (severity in ('LOW','MEDIUM','HIGH','CRITICAL'))
);

create index if not exists idx_bank_accounts_company on bank_accounts(company_id, active);
create index if not exists idx_bank_imports_scope on bank_statement_imports(company_id, bank_account_id, created_at);
create index if not exists idx_bank_transactions_scope on bank_transactions(company_id, bank_account_id, transaction_date);
create index if not exists idx_bank_transactions_status on bank_transactions(company_id, reconciliation_status, transaction_date);
create index if not exists idx_bank_matches_company on bank_reconciliation_matches(company_id, active, confirmed_at);
create index if not exists idx_bank_suggestions_company on bank_reconciliation_suggestions(company_id, active, confidence);
create index if not exists idx_payment_methods_company on payment_methods(company_id, active);
create index if not exists idx_cash_flow_snapshots_company on cash_flow_snapshots(company_id, snapshot_date);
create index if not exists idx_treasury_alerts_open on treasury_alerts(company_id, resolved, created_at);
