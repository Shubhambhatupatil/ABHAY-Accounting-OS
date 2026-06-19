from fastapi import APIRouter, Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials
from pydantic import BaseModel

from app.core.config import Settings, get_settings
from app.core.security import AuthenticatedUser, bearer_scheme, require_user

router = APIRouter()


class SessionUserResponse(BaseModel):
    id: str
    email: str | None
    auth_role: str


@router.post("/session/verify", response_model=SessionUserResponse)
def verify_session(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    settings: Settings = Depends(get_settings),
) -> SessionUserResponse:
    try:
        user = require_user(credentials=credentials, settings=settings)
    except HTTPException:
        if not settings.alpha_demo_mode:
            raise
        return SessionUserResponse(id="alpha-optional", email=None, auth_role="optional")
    return SessionUserResponse(id=user.id, email=user.email, auth_role=user.role)


@router.get("/me", response_model=SessionUserResponse)
def get_me(user: AuthenticatedUser = Depends(require_user)) -> SessionUserResponse:
    return SessionUserResponse(id=user.id, email=user.email, auth_role=user.role)
