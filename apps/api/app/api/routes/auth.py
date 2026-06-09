from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.core.security import AuthenticatedUser, require_user

router = APIRouter()


class SessionUserResponse(BaseModel):
    id: str
    email: str | None
    auth_role: str


@router.post("/session/verify", response_model=SessionUserResponse)
def verify_session(user: AuthenticatedUser = Depends(require_user)) -> SessionUserResponse:
    return SessionUserResponse(id=user.id, email=user.email, auth_role=user.role)


@router.get("/me", response_model=SessionUserResponse)
def get_me(user: AuthenticatedUser = Depends(require_user)) -> SessionUserResponse:
    return SessionUserResponse(id=user.id, email=user.email, auth_role=user.role)
