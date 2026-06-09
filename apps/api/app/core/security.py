from dataclasses import dataclass
from functools import lru_cache
from typing import Any

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jwt import PyJWKClient

from app.core.config import Settings, get_settings

bearer_scheme = HTTPBearer(auto_error=False)
LOCAL_DEMO_TOKEN = "abhay-local-demo-token"


@dataclass(frozen=True)
class AuthenticatedUser:
    id: str
    email: str | None
    role: str
    claims: dict[str, Any]


@lru_cache
def get_jwk_client(jwks_url: str) -> PyJWKClient:
    return PyJWKClient(jwks_url)


def decode_supabase_token(token: str, settings: Settings) -> AuthenticatedUser:
    try:
        signing_key = get_jwk_client(settings.supabase_jwks_url).get_signing_key_from_jwt(token)
        claims = jwt.decode(
            token,
            signing_key.key,
            algorithms=["ES256", "RS256"],
            audience=settings.supabase_jwt_audience,
            options={"require": ["sub", "exp"]},
        )
    except jwt.PyJWTError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid or expired authentication token",
        ) from exc

    return AuthenticatedUser(
        id=claims["sub"],
        email=claims.get("email"),
        role=claims.get("role", "authenticated"),
        claims=claims,
    )


def require_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    settings: Settings = Depends(get_settings),
) -> AuthenticatedUser:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing bearer authentication token",
        )
    token = credentials.credentials
    if settings.app_env == "local" and token == LOCAL_DEMO_TOKEN:
        return AuthenticatedUser(
            id="local-demo-owner",
            email="demo@abhay.test",
            role="owner",
            claims={"sub": "local-demo-owner", "role": "owner", "demo": True},
        )
    return decode_supabase_token(token, settings)
