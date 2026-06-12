from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import create_engine
from sqlalchemy.engine import Engine
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
    if not settings.alpha_demo_mode and not is_sqlite_database_url(settings.database_url):
        return
    import app.models.accounting  # noqa: F401

    Base.metadata.create_all(bind=get_engine())


def create_session() -> Session:
    session_factory = sessionmaker(bind=get_engine(), autoflush=False, autocommit=False)
    return session_factory()


def get_db() -> Generator[Session, None, None]:
    db = create_session()
    try:
        yield db
    finally:
        db.close()
