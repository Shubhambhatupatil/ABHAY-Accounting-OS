create table if not exists documents (
    id uuid primary key,
    company_id uuid not null references companies(id),
    uploaded_by uuid not null references users(id),
    document_type varchar(40) not null,
    original_file_name varchar(255) not null,
    file_type varchar(20) not null,
    file_size bigint not null,
    file_hash_sha256 varchar(64) not null,
    storage_key varchar(500) not null,
    status varchar(30) not null,
    source varchar(30) not null,
    confidence_score numeric(5,4) not null default 0,
    linked_invoice_id uuid references invoices(id),
    linked_voucher_id uuid references vouchers(id),
    notes varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_document_type check (document_type in (
        'SALES_INVOICE','PURCHASE_INVOICE','CREDIT_NOTE','DEBIT_NOTE','RECEIPT','PAYMENT_ADVICE',
        'BANK_STATEMENT','GST_RETURN','GST_NOTICE','PURCHASE_ORDER','QUOTATION','DELIVERY_CHALLAN','OTHER')),
    constraint ck_document_status check (status in (
        'UPLOADED','PROCESSING','EXTRACTED','REVIEW_REQUIRED','APPROVED','REJECTED','CONVERTED')),
    constraint ck_document_source check (source in ('MANUAL_UPLOAD','EMAIL','WHATSAPP','API','BANK_IMPORT')),
    constraint ck_document_size check (file_size > 0 and file_size <= 10485760),
    constraint ck_document_confidence check (confidence_score between 0 and 1)
);

create table if not exists document_versions (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    version_number integer not null,
    storage_key varchar(500) not null,
    file_hash_sha256 varchar(64) not null,
    file_size bigint not null,
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_document_version unique (document_id, version_number)
);

create table if not exists document_pages (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    page_number integer not null,
    extracted_text text,
    ocr_status varchar(30) not null,
    confidence_score numeric(5,4) not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_document_page unique (document_id, page_number),
    constraint ck_document_page_number check (page_number > 0)
);

create table if not exists document_fields (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    field_name varchar(80) not null,
    raw_value varchar(2000),
    normalized_value varchar(2000),
    confidence_score numeric(5,4) not null default 0,
    corrected boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_document_field unique (document_id, field_name),
    constraint ck_document_field_confidence check (confidence_score between 0 and 1)
);

create table if not exists document_processing_jobs (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    job_type varchar(30) not null,
    status varchar(30) not null,
    attempts integer not null default 0,
    message varchar(500),
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_document_job_status check (status in ('PENDING','PROCESSING','COMPLETED','FAILED','OCR_PENDING')),
    constraint ck_document_job_attempts check (attempts >= 0)
);

create table if not exists document_extraction_results (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    processing_job_id uuid not null references document_processing_jobs(id),
    extractor varchar(80) not null,
    extracted_text text,
    result_json text not null,
    confidence_score numeric(5,4) not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_document_result_confidence check (confidence_score between 0 and 1)
);

create table if not exists document_review_actions (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    document_field_id uuid references document_fields(id),
    action varchar(30) not null,
    old_value varchar(2000),
    new_value varchar(2000),
    comment varchar(1000),
    reviewed_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_document_review_action check (action in ('FIELD_CORRECTED','APPROVED','REJECTED','CONVERTED'))
);

create table if not exists document_tags (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    tag varchar(80) not null,
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_document_tag unique (document_id, tag)
);

create table if not exists document_duplicates (
    id uuid primary key,
    company_id uuid not null references companies(id),
    document_id uuid not null references documents(id),
    duplicate_of_document_id uuid not null references documents(id),
    match_type varchar(30) not null,
    confidence_score numeric(5,4) not null,
    resolved boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_document_duplicate_pair unique (document_id, duplicate_of_document_id),
    constraint ck_document_duplicate_distinct check (document_id <> duplicate_of_document_id)
);

create index if not exists idx_documents_company_created on documents(company_id, created_at);
create index if not exists idx_documents_company_status on documents(company_id, status, document_type);
create index if not exists idx_documents_company_hash on documents(company_id, file_hash_sha256);
create index if not exists idx_document_versions_scope on document_versions(company_id, document_id);
create index if not exists idx_document_pages_scope on document_pages(company_id, document_id, page_number);
create index if not exists idx_document_fields_scope on document_fields(company_id, document_id, field_name);
create index if not exists idx_document_jobs_scope on document_processing_jobs(company_id, document_id, status);
create index if not exists idx_document_results_scope on document_extraction_results(company_id, document_id);
create index if not exists idx_document_reviews_scope on document_review_actions(company_id, document_id, created_at);
create index if not exists idx_document_tags_scope on document_tags(company_id, tag);
create index if not exists idx_document_duplicates_scope on document_duplicates(company_id, resolved, created_at);
