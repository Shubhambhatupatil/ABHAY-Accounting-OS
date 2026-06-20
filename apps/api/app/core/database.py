from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
from sqlalchemy import text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.core.config import get_settings


class Base(DeclarativeBase):
    pass


def normalized_database_url() -> str:
    database_url = get_settings().database_url
    if database_url.startswith("postgres://"):
        return database_url.replace("postgres://", "postgresql+psycopg://", 1)
    return database_url


def is_sqlite_database_url(database_url: str | None = None) -> bool:
    return (database_url or normalized_database_url()).startswith("sqlite")


@lru_cache
def get_engine() -> Engine:
    database_url = normalized_database_url()
    connect_args = {"check_same_thread": False} if is_sqlite_database_url(database_url) else {"connect_timeout": 5}
    return create_engine(
        database_url,
        pool_pre_ping=True,
        connect_args=connect_args,
    )


def create_alpha_schema_if_needed() -> None:
    settings = get_settings()
    if (
        settings.app_env != "local"
        and not settings.alpha_demo_mode
        and not is_sqlite_database_url(settings.database_url)
    ):
        return
    import app.models.accounting  # noqa: F401

    Base.metadata.create_all(bind=get_engine())
    apply_alpha_postgres_compatibility(settings.database_url)


def apply_alpha_postgres_compatibility(database_url: str | None = None) -> None:
    if is_sqlite_database_url(database_url):
        return

    statements = [
        "create extension if not exists pgcrypto",
        """
        do $$
        begin
          if not exists (select 1 from pg_type where typname = 'membership_status') then
            create type membership_status as enum ('invited', 'active', 'suspended', 'removed');
          end if;
        end $$;
        """,
        "alter table if exists profiles add column if not exists full_name text",
        "alter table if exists profiles add column if not exists email text",
        "alter table if exists companies add column if not exists legal_name text",
        "alter table if exists companies add column if not exists trade_name text",
        "alter table if exists companies add column if not exists state_code varchar(2)",
        "alter table if exists companies add column if not exists created_by uuid",
        """
        do $$
        begin
          if exists (
            select 1 from information_schema.columns
            where table_schema = 'public' and table_name = 'companies' and column_name = 'company_name'
          ) then
            update companies
              set legal_name = coalesce(legal_name, company_name),
                  trade_name = coalesce(trade_name, company_name)
              where legal_name is null or trade_name is null;
          end if;
          if exists (
            select 1 from information_schema.columns
            where table_schema = 'public' and table_name = 'companies' and column_name = 'state'
          ) then
            update companies
              set state_code = coalesce(state_code, state)
              where state_code is null;
          end if;
          update companies
            set legal_name = coalesce(legal_name, 'ANVRITAI Demo Company'),
                trade_name = coalesce(trade_name, legal_name, 'ANVRITAI Demo Company')
            where legal_name is null or trade_name is null;
        end $$;
        """,
        "alter table if exists company_members add column if not exists profile_id uuid",
        "alter table if exists company_members add column if not exists user_id uuid",
        "alter table if exists company_members add column if not exists role_id uuid",
        "alter table if exists company_members add column if not exists status membership_status",
        """
        do $$
        begin
          if exists (
            select 1 from information_schema.columns
            where table_schema = 'public'
              and table_name = 'company_members'
              and column_name = 'status'
              and udt_name <> 'membership_status'
          ) then
            alter table company_members
              alter column status type membership_status
              using coalesce(status, 'active')::membership_status;
          end if;
        end $$;
        """,
        """
        insert into roles (id, code, name, description)
        select gen_random_uuid(), 'owner', 'Owner', 'Company owner'
        where not exists (select 1 from roles where code = 'owner');
        insert into roles (id, code, name, description)
        select gen_random_uuid(), 'admin', 'Admin', 'Company admin'
        where not exists (select 1 from roles where code = 'admin');
        insert into roles (id, code, name, description)
        select gen_random_uuid(), 'accountant', 'Accountant', 'Company accountant'
        where not exists (select 1 from roles where code = 'accountant');
        insert into roles (id, code, name, description)
        select gen_random_uuid(), 'auditor', 'Auditor', 'Company auditor'
        where not exists (select 1 from roles where code = 'auditor');
        insert into roles (id, code, name, description)
        select gen_random_uuid(), 'viewer', 'Viewer', 'Read-only viewer'
        where not exists (select 1 from roles where code = 'viewer');
        """,
        """
        update company_members
          set profile_id = user_id
          where profile_id is null and user_id is not null;
        """,
        """
        update company_members
          set user_id = profile_id
          where user_id is null and profile_id is not null;
        """,
        "update company_members set status = 'active'::membership_status where status is null",
        """
        update company_members
          set role_id = (select id from roles where code = 'owner' limit 1)
          where role_id is null;
        """,
    ]

    engine = get_engine()
    with engine.begin() as connection:
        for statement in statements:
            connection.execute(text(statement))


def create_session() -> Session:
    session_factory = sessionmaker(bind=get_engine(), autoflush=False, autocommit=False)
    return session_factory()


def get_db() -> Generator[Session, None, None]:
    db = create_session()
    try:
        yield db
    finally:
        db.close()
