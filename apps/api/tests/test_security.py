import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from fastapi.testclient import TestClient

from app.main import app
from app.core.config import Settings
from app.core.security import LOCAL_DEMO_TOKEN, decode_supabase_token, require_user


def test_invalid_token_is_rejected() -> None:
    settings = Settings(
        supabase_url="https://example.supabase.co",
        database_url="postgresql+psycopg://postgres:postgres@localhost:5432/postgres",
    )

    with pytest.raises(HTTPException) as exc_info:
        decode_supabase_token("not-a-valid-token", settings)

    assert exc_info.value.status_code == 401


def test_local_demo_token_is_accepted_in_local_env() -> None:
    settings = Settings(
        app_env="local",
        supabase_url="https://example.supabase.co",
        database_url="postgresql+psycopg://postgres:postgres@localhost:5432/postgres",
    )
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials=LOCAL_DEMO_TOKEN)

    user = require_user(credentials=credentials, settings=settings)

    assert user.id == "local-demo-owner"
    assert user.email == "demo@abhay.test"
    assert user.role == "owner"


def test_demo_token_is_accepted_for_hosted_alpha_when_enabled() -> None:
    settings = Settings(
        app_env="production",
        alpha_demo_mode=True,
        supabase_url="https://example.supabase.co",
        database_url="postgresql+psycopg://postgres:postgres@localhost:5432/postgres",
    )
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials=LOCAL_DEMO_TOKEN)

    user = require_user(credentials=credentials, settings=settings)

    assert user.id == "local-demo-owner"
    assert user.email == "demo@abhay.test"
    assert user.role == "owner"


def test_demo_token_is_rejected_in_production_without_alpha_demo_mode() -> None:
    settings = Settings(
        app_env="production",
        alpha_demo_mode=False,
        supabase_url="https://example.supabase.co",
        database_url="postgresql+psycopg://postgres:postgres@localhost:5432/postgres",
    )
    credentials = HTTPAuthorizationCredentials(scheme="Bearer", credentials=LOCAL_DEMO_TOKEN)

    with pytest.raises(HTTPException) as exc_info:
        require_user(credentials=credentials, settings=settings)

    assert exc_info.value.status_code == 401


def test_local_demo_session_verify_response() -> None:
    client = TestClient(app)

    response = client.post(
        "/auth/session/verify",
        headers={"Authorization": f"Bearer {LOCAL_DEMO_TOKEN}"},
    )

    assert response.status_code == 200
    assert response.json() == {
        "id": "local-demo-owner",
        "email": "demo@abhay.test",
        "auth_role": "owner",
    }
