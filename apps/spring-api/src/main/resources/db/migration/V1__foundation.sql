create table users (
    id uuid primary key,
    email varchar(320) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(120) not null,
    active boolean not null default true,
    selected_company_id uuid null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table roles (
    id uuid primary key,
    code varchar(30) not null unique,
    name varchar(80) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table permissions (
    id uuid primary key,
    code varchar(80) not null unique,
    description varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table role_permissions (
    id uuid primary key,
    role_id uuid not null references roles(id),
    permission_id uuid not null references permissions(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_role_permission unique (role_id, permission_id)
);

create table companies (
    id uuid primary key,
    legal_name varchar(200) not null,
    trade_name varchar(200),
    gstin varchar(15),
    state_code varchar(2),
    industry varchar(100),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

alter table users
    add constraint fk_users_selected_company foreign key (selected_company_id) references companies(id);

create table company_members (
    id uuid primary key,
    company_id uuid not null references companies(id),
    user_id uuid not null references users(id),
    role_id uuid not null references roles(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_company_member unique (company_id, user_id)
);

create table financial_years (
    id uuid primary key,
    company_id uuid not null references companies(id),
    label varchar(30) not null,
    starts_on date not null,
    ends_on date not null,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_company_financial_year unique (company_id, starts_on, ends_on),
    constraint ck_financial_year_dates check (ends_on >= starts_on)
);

create table audit_logs (
    id uuid primary key,
    company_id uuid not null references companies(id),
    actor_user_id uuid references users(id),
    action varchar(80) not null,
    entity_type varchar(80) not null,
    entity_id uuid,
    details_json text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_company_members_user on company_members(user_id);
create index idx_financial_years_company on financial_years(company_id);
create index idx_audit_logs_company_created on audit_logs(company_id, created_at);

insert into roles (id, code, name, created_at, updated_at) values
('00000000-0000-0000-0000-000000000101', 'OWNER', 'Owner', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000102', 'ADMIN', 'Admin', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000103', 'ACCOUNTANT', 'Accountant', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000104', 'AUDITOR', 'Auditor', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000105', 'VIEWER', 'Viewer', current_timestamp, current_timestamp);

insert into permissions (id, code, description, created_at, updated_at) values
('00000000-0000-0000-0000-000000000201', 'COMPANY_READ', 'Read company workspace', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000202', 'COMPANY_UPDATE', 'Update company settings', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000203', 'MEMBER_MANAGE', 'Add and manage company members', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000204', 'AUDIT_READ', 'Read company audit logs', current_timestamp, current_timestamp);

insert into role_permissions (id, role_id, permission_id, created_at, updated_at) values
('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000202', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000203', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000204', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000311', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000312', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000202', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000313', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000203', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000314', '00000000-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000204', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000321', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000324', '00000000-0000-0000-0000-000000000103', '00000000-0000-0000-0000-000000000204', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000331', '00000000-0000-0000-0000-000000000104', '00000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp),
('00000000-0000-0000-0000-000000000341', '00000000-0000-0000-0000-000000000105', '00000000-0000-0000-0000-000000000201', current_timestamp, current_timestamp);
