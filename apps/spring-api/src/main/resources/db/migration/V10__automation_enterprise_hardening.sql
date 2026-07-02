create table if not exists automation_templates (
    id uuid primary key,
    code varchar(80) not null unique,
    name varchar(160) not null,
    description varchar(1000) not null,
    rule_type varchar(40) not null,
    default_schedule_type varchar(30) not null,
    default_parameters_json text not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists automation_rules (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(160) not null,
    rule_type varchar(40) not null,
    schedule_type varchar(30) not null,
    parameters_json text not null,
    notification_channel varchar(20) not null,
    active boolean not null default true,
    next_run_at timestamp with time zone,
    last_run_at timestamp with time zone,
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_automation_rule_type check (rule_type in ('GST_DUE','LOW_STOCK','RECEIVABLE_OVERDUE','BANK_UNRECONCILED','MONTH_END')),
    constraint ck_automation_schedule check (schedule_type in ('DAILY','WEEKLY','MONTHLY','FINANCIAL_YEAR','EVENT_TRIGGER')),
    constraint ck_automation_channel check (notification_channel in ('IN_APP','EMAIL','WHATSAPP','WEBHOOK'))
);

create table if not exists automation_runs (
    id uuid primary key,
    company_id uuid not null references companies(id),
    automation_rule_id uuid not null references automation_rules(id),
    trigger_type varchar(30) not null,
    status varchar(20) not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    matched_count integer not null default 0,
    summary varchar(1000),
    correlation_id varchar(100),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_automation_run_status check (status in ('PENDING','RUNNING','SUCCESS','FAILED','SKIPPED'))
);

create table if not exists automation_logs (
    id uuid primary key,
    company_id uuid not null references companies(id),
    automation_run_id uuid not null references automation_runs(id),
    level varchar(20) not null,
    message varchar(1000) not null,
    details_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_automation_log_level check (level in ('INFO','WARNING','ERROR','SUCCESS'))
);

create table if not exists automation_notifications (
    id uuid primary key,
    company_id uuid not null references companies(id),
    automation_rule_id uuid references automation_rules(id),
    automation_run_id uuid references automation_runs(id),
    channel varchar(20) not null,
    notification_type varchar(20) not null,
    title varchar(200) not null,
    message varchar(1200) not null,
    recipient_user_id uuid references users(id),
    delivery_status varchar(20) not null,
    read_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_notification_channel check (channel in ('IN_APP','EMAIL','WHATSAPP','WEBHOOK')),
    constraint ck_notification_type check (notification_type in ('INFO','WARNING','ERROR','SUCCESS')),
    constraint ck_notification_delivery check (delivery_status in ('PENDING','READY','DELIVERED','FAILED'))
);

create table if not exists background_jobs (
    id uuid primary key,
    company_id uuid not null references companies(id),
    job_type varchar(50) not null,
    status varchar(20) not null,
    payload_json text not null,
    result_json text,
    attempts integer not null default 0,
    max_attempts integer not null default 3,
    scheduled_at timestamp with time zone not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    correlation_id varchar(100),
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_background_job_type check (job_type in ('DOCUMENT_PROCESSING','MEMORY_REBUILD','GST_SUMMARY','INVENTORY_RECALCULATION','BANK_RECONCILIATION_SCAN','SCHEDULED_REPORT','AUTOMATION_RULE')),
    constraint ck_background_job_status check (status in ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    constraint ck_background_job_attempts check (attempts >= 0 and max_attempts between 1 and 10)
);

create table if not exists company_settings (
    id uuid primary key,
    company_id uuid not null references companies(id),
    category varchar(30) not null,
    setting_key varchar(120) not null,
    setting_value varchar(4000) not null,
    value_type varchar(20) not null,
    updated_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_company_setting unique (company_id, category, setting_key),
    constraint ck_setting_category check (category in ('COMPANY','ACCOUNTING','GST','INVENTORY','BANK','NOTIFICATION','AUTOMATION','AI_MEMORY')),
    constraint ck_setting_value_type check (value_type in ('STRING','BOOLEAN','INTEGER','DECIMAL','JSON'))
);

alter table audit_logs add column if not exists correlation_id varchar(100);

create index if not exists idx_automation_rules_due on automation_rules(active, next_run_at);
create index if not exists idx_automation_rules_company on automation_rules(company_id, created_at);
create index if not exists idx_automation_runs_company on automation_runs(company_id, created_at);
create index if not exists idx_automation_logs_run on automation_logs(automation_run_id, created_at);
create index if not exists idx_notifications_company on automation_notifications(company_id, read_at, created_at);
create index if not exists idx_background_jobs_due on background_jobs(status, scheduled_at);
create index if not exists idx_background_jobs_company on background_jobs(company_id, created_at);
create index if not exists idx_company_settings_company on company_settings(company_id, category);

insert into automation_templates (id, code, name, description, rule_type, default_schedule_type, default_parameters_json, active, created_at, updated_at)
select cast('10000000-0000-0000-0000-000000000001' as uuid), 'GST_DUE_5_DAYS', 'GST filing due reminder', 'Notify the owner when a configured GST filing date is within five days.', 'GST_DUE', 'DAILY', '{"daysBefore":5}', true, current_timestamp, current_timestamp
where not exists (select 1 from automation_templates where code = 'GST_DUE_5_DAYS');

insert into automation_templates (id, code, name, description, rule_type, default_schedule_type, default_parameters_json, active, created_at, updated_at)
select cast('10000000-0000-0000-0000-000000000002' as uuid), 'LOW_STOCK_ALERT', 'Low stock alert', 'Notify when unresolved inventory low-stock alerts exist.', 'LOW_STOCK', 'EVENT_TRIGGER', '{}', true, current_timestamp, current_timestamp
where not exists (select 1 from automation_templates where code = 'LOW_STOCK_ALERT');

insert into automation_templates (id, code, name, description, rule_type, default_schedule_type, default_parameters_json, active, created_at, updated_at)
select cast('10000000-0000-0000-0000-000000000003' as uuid), 'OVERDUE_RECEIVABLE', 'Overdue receivable reminder', 'Notify when customer invoices are overdue.', 'RECEIVABLE_OVERDUE', 'DAILY', '{}', true, current_timestamp, current_timestamp
where not exists (select 1 from automation_templates where code = 'OVERDUE_RECEIVABLE');

insert into automation_templates (id, code, name, description, rule_type, default_schedule_type, default_parameters_json, active, created_at, updated_at)
select cast('10000000-0000-0000-0000-000000000004' as uuid), 'BANK_UNRECONCILED', 'Unreconciled bank reminder', 'Notify when imported bank transactions remain unreconciled.', 'BANK_UNRECONCILED', 'DAILY', '{}', true, current_timestamp, current_timestamp
where not exists (select 1 from automation_templates where code = 'BANK_UNRECONCILED');

insert into automation_templates (id, code, name, description, rule_type, default_schedule_type, default_parameters_json, active, created_at, updated_at)
select cast('10000000-0000-0000-0000-000000000005' as uuid), 'MONTH_END_CHECKLIST', 'Month-end checklist', 'Create a review checklist near month end.', 'MONTH_END', 'MONTHLY', '{"daysBefore":2}', true, current_timestamp, current_timestamp
where not exists (select 1 from automation_templates where code = 'MONTH_END_CHECKLIST');
