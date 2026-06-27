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
        # TODO(production-auth): restore Supabase JWT signature and audience verification
        # before removing Alpha Demo Mode.
        claims = jwt.decode(
            token,
            options={
                "verify_signature": False,
                "verify_aud": False,
            },
        )
        if not claims.get("sub"):
            raise jwt.PyJWTError("Missing sub")
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
    if token == LOCAL_DEMO_TOKEN and (settings.app_env == "local" or settings.alpha_demo_mode or settings.client_demo_mode):
        # Client demo fallback: this public token is scoped to a dedicated demo
        # owner identity. Keep production JWT verification for real users.
        return AuthenticatedUser(
            id="local-demo-owner",
            email="demo@abhay.test",
            role="owner",
            claims={"sub": "local-demo-owner", "role": "owner", "demo": True},
        )
    return decode_supabase_token(token, settings)
