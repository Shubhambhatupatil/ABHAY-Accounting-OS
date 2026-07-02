create table if not exists memory_profiles (
    id uuid primary key,
    company_id uuid not null references companies(id),
    enabled boolean not null default true,
    retention_days integer not null default 2555,
    last_rebuilt_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_memory_profile_company unique (company_id),
    constraint ck_memory_retention check (retention_days >= 365)
);

create table if not exists memory_events (
    id uuid primary key,
    company_id uuid not null references companies(id),
    memory_type varchar(50) not null,
    event_type varchar(80) not null,
    entity_type varchar(80) not null,
    entity_id uuid not null,
    actor_id uuid references users(id),
    subject_key varchar(300),
    context_json text not null,
    occurred_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_memory_event_type check (memory_type in (
        'VENDOR_MEMORY','CUSTOMER_MEMORY','LEDGER_MAPPING_MEMORY','GST_TREATMENT_MEMORY',
        'VOUCHER_PATTERN_MEMORY','INVOICE_PATTERN_MEMORY','DOCUMENT_CORRECTION_MEMORY',
        'BANK_RECONCILIATION_MEMORY','INVENTORY_MEMORY','USER_PREFERENCE_MEMORY','COMPANY_BEHAVIOR_MEMORY'))
);

create table if not exists memory_patterns (
    id uuid primary key,
    company_id uuid not null references companies(id),
    memory_type varchar(50) not null,
    pattern_key varchar(500) not null,
    subject_key varchar(300) not null,
    suggestion_type varchar(80) not null,
    suggested_value varchar(1000) not null,
    occurrence_count bigint not null default 0,
    success_count bigint not null default 0,
    failure_count bigint not null default 0,
    confidence_score numeric(5,4) not null default 0,
    last_used_at timestamp with time zone,
    last_similar_at timestamp with time zone,
    explanation varchar(1000) not null,
    evidence_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_memory_pattern_key unique (company_id, pattern_key),
    constraint ck_memory_pattern_counts check (occurrence_count >= 0 and success_count >= 0 and failure_count >= 0),
    constraint ck_memory_pattern_confidence check (confidence_score between 0 and 1)
);

create table if not exists memory_preferences (
    id uuid primary key,
    company_id uuid not null references companies(id),
    user_id uuid references users(id),
    preference_key varchar(160) not null,
    preference_value varchar(2000) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_memory_preference unique (company_id, user_id, preference_key)
);

create table if not exists memory_suggestions (
    id uuid primary key,
    company_id uuid not null references companies(id),
    pattern_id uuid references memory_patterns(id),
    suggestion_type varchar(80) not null,
    input_key varchar(500) not null,
    suggested_value varchar(1000),
    confidence_score numeric(5,4) not null,
    low_confidence boolean not null,
    status varchar(20) not null default 'PENDING',
    supporting_event_count bigint not null default 0,
    last_similar_at timestamp with time zone,
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_memory_suggestion_status check (status in ('PENDING','ACCEPTED','REJECTED','CORRECTED')),
    constraint ck_memory_suggestion_confidence check (confidence_score between 0 and 1)
);

create table if not exists memory_feedback (
    id uuid primary key,
    company_id uuid not null references companies(id),
    suggestion_id uuid not null references memory_suggestions(id),
    user_id uuid not null references users(id),
    action varchar(20) not null,
    corrected_value varchar(1000),
    comment varchar(1000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_memory_feedback_suggestion unique (suggestion_id),
    constraint ck_memory_feedback_action check (action in ('ACCEPTED','REJECTED','CORRECTED'))
);

create table if not exists memory_confidence_scores (
    id uuid primary key,
    company_id uuid not null references companies(id),
    pattern_id uuid not null references memory_patterns(id),
    confidence_score numeric(5,4) not null,
    occurrence_count bigint not null,
    success_count bigint not null,
    failure_count bigint not null,
    reason varchar(500) not null,
    calculated_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_memory_score_confidence check (confidence_score between 0 and 1)
);

create table if not exists memory_explanations (
    id uuid primary key,
    company_id uuid not null references companies(id),
    suggestion_id uuid not null references memory_suggestions(id),
    reason varchar(1000) not null,
    supporting_event_count bigint not null,
    last_similar_at timestamp with time zone,
    warning varchar(500),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_memory_explanation_suggestion unique (suggestion_id)
);

create index if not exists idx_memory_events_company on memory_events(company_id, occurred_at);
create index if not exists idx_memory_events_subject on memory_events(company_id, memory_type, subject_key);
create index if not exists idx_memory_patterns_lookup on memory_patterns(company_id, suggestion_type, subject_key, confidence_score);
create index if not exists idx_memory_preferences_company on memory_preferences(company_id, user_id);
create index if not exists idx_memory_feedback_company on memory_feedback(company_id, created_at);
create index if not exists idx_memory_scores_pattern on memory_confidence_scores(company_id, pattern_id, calculated_at);
create index if not exists idx_memory_suggestions_company on memory_suggestions(company_id, suggestion_type, created_at);
create index if not exists idx_memory_explanations_company on memory_explanations(company_id, created_at);
