alter table companies add column if not exists inventory_valuation_method varchar(30) not null default 'FIFO';
alter table companies add column if not exists allow_negative_stock boolean not null default false;

create table if not exists inventory_units (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(80) not null,
    symbol varchar(20) not null,
    decimal_places integer not null default 2,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_inventory_unit_company_symbol unique (company_id, symbol),
    constraint ck_inventory_unit_decimals check (decimal_places between 0 and 4)
);

create table if not exists item_categories (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(160) not null,
    description varchar(500),
    parent_id uuid references item_categories(id),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_item_category_company_name unique (company_id, name)
);

create table if not exists warehouses (
    id uuid primary key,
    company_id uuid not null references companies(id),
    name varchar(160) not null,
    code varchar(40) not null,
    address varchar(1000),
    primary_warehouse boolean not null default false,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_warehouse_company_name unique (company_id, name),
    constraint uk_warehouse_company_code unique (company_id, code)
);

alter table items add column if not exists inventory_unit_id uuid references inventory_units(id);
alter table items add column if not exists item_category_id uuid references item_categories(id);
alter table items add column if not exists reorder_level numeric(19,4) not null default 0;
alter table items add column if not exists track_inventory boolean not null default false;
alter table items add constraint ck_item_reorder_level check (reorder_level >= 0);

update items set track_inventory = true where item_type = 'PRODUCT';

create table if not exists stock_movements (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    warehouse_id uuid not null references warehouses(id),
    movement_type varchar(30) not null,
    movement_date date not null,
    quantity numeric(19,4) not null,
    unit_cost numeric(19,4) not null default 0,
    total_value numeric(19,2) not null default 0,
    reference_type varchar(40) not null,
    reference_id uuid not null,
    narration varchar(500),
    reversal_of_id uuid references stock_movements(id),
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_stock_movement_type check (
        movement_type in ('OPENING', 'PURCHASE', 'SALE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
                          'TRANSFER_IN', 'TRANSFER_OUT', 'REVERSAL_IN', 'REVERSAL_OUT')
    ),
    constraint ck_stock_movement_quantity check (quantity > 0),
    constraint ck_stock_movement_cost check (unit_cost >= 0 and total_value >= 0)
);

create table if not exists stock_ledger_entries (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    warehouse_id uuid not null references warehouses(id),
    stock_movement_id uuid not null unique references stock_movements(id),
    entry_date date not null,
    quantity_delta numeric(19,4) not null,
    value_delta numeric(19,2) not null,
    balance_quantity numeric(19,4) not null,
    balance_value numeric(19,2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists stock_batches (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    warehouse_id uuid not null references warehouses(id),
    source_movement_id uuid not null references stock_movements(id),
    batch_number varchar(100) not null,
    received_date date not null,
    original_quantity numeric(19,4) not null,
    available_quantity numeric(19,4) not null,
    unit_cost numeric(19,4) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_stock_batch_quantity check (
        original_quantity > 0 and available_quantity >= 0 and available_quantity <= original_quantity
    ),
    constraint ck_stock_batch_cost check (unit_cost >= 0)
);

create table if not exists stock_valuation_layers (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    warehouse_id uuid not null references warehouses(id),
    stock_batch_id uuid not null references stock_batches(id),
    valuation_method varchar(30) not null,
    original_quantity numeric(19,4) not null,
    remaining_quantity numeric(19,4) not null,
    unit_cost numeric(19,4) not null,
    received_date date not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_stock_layer_method check (valuation_method in ('FIFO', 'WEIGHTED_AVERAGE')),
    constraint ck_stock_layer_quantity check (
        original_quantity > 0 and remaining_quantity >= 0 and remaining_quantity <= original_quantity
    ),
    constraint ck_stock_layer_cost check (unit_cost >= 0)
);

create table if not exists stock_adjustments (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    warehouse_id uuid not null references warehouses(id),
    adjustment_type varchar(20) not null,
    adjustment_date date not null,
    quantity numeric(19,4) not null,
    unit_cost numeric(19,4) not null default 0,
    reason varchar(500) not null,
    stock_movement_id uuid not null references stock_movements(id),
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_stock_adjustment_type check (adjustment_type in ('INCREASE', 'DECREASE')),
    constraint ck_stock_adjustment_quantity check (quantity > 0)
);

create table if not exists stock_transfers (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    from_warehouse_id uuid not null references warehouses(id),
    to_warehouse_id uuid not null references warehouses(id),
    transfer_date date not null,
    quantity numeric(19,4) not null,
    unit_cost numeric(19,4) not null,
    total_value numeric(19,2) not null,
    narration varchar(500),
    out_movement_id uuid not null references stock_movements(id),
    in_movement_id uuid not null references stock_movements(id),
    created_by uuid not null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_stock_transfer_warehouses check (from_warehouse_id <> to_warehouse_id),
    constraint ck_stock_transfer_quantity check (quantity > 0)
);

create table if not exists inventory_alerts (
    id uuid primary key,
    company_id uuid not null references companies(id),
    item_id uuid not null references items(id),
    warehouse_id uuid references warehouses(id),
    alert_type varchar(40) not null,
    severity varchar(20) not null,
    message varchar(500) not null,
    current_quantity numeric(19,4) not null,
    reorder_level numeric(19,4) not null,
    resolved boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ck_inventory_alert_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

create index if not exists idx_inventory_units_company on inventory_units(company_id, active);
create index if not exists idx_item_categories_company on item_categories(company_id, active);
create index if not exists idx_warehouses_company on warehouses(company_id, active);
create index if not exists idx_items_inventory_scope on items(company_id, track_inventory, item_category_id);
create index if not exists idx_stock_movements_scope on stock_movements(company_id, item_id, warehouse_id, movement_date);
create index if not exists idx_stock_movements_reference on stock_movements(company_id, reference_type, reference_id);
create index if not exists idx_stock_ledger_scope on stock_ledger_entries(company_id, item_id, warehouse_id, entry_date);
create index if not exists idx_stock_batches_available on stock_batches(company_id, item_id, warehouse_id, available_quantity);
create index if not exists idx_stock_layers_available on stock_valuation_layers(company_id, item_id, warehouse_id, remaining_quantity, received_date);
create index if not exists idx_stock_adjustments_company on stock_adjustments(company_id, adjustment_date);
create index if not exists idx_stock_transfers_company on stock_transfers(company_id, transfer_date);
create index if not exists idx_inventory_alerts_open on inventory_alerts(company_id, resolved, created_at);
