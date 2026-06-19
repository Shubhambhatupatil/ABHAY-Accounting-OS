from typing import Any
from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.accounting import AiLog, AuditLog, Company

router = APIRouter(prefix="/ai", tags=["ai-command"])


class AiCommandRequest(BaseModel):
    command: str = Field(min_length=1, max_length=1000)
    context: dict[str, Any] | None = None


class AiCommandResponse(BaseModel):
    ok: bool
    summary: str
    actions: list[str]
    confidence: float


@router.post("/command", response_model=AiCommandResponse)
def run_ai_command(
    payload: AiCommandRequest,
    db: Session = Depends(get_db),
) -> AiCommandResponse:
    command = payload.command.strip().lower()

    if "invoice" in command or "bill" in command:
        response = AiCommandResponse(
            ok=True,
            summary="ABHAY can route this through the invoice upload and extraction workflow for human review.",
            actions=[
                "Upload a text PDF invoice or bill.",
                "Review extracted party, GSTIN, taxable amount and tax split.",
                "Approve the suggested voucher only after checking the debit and credit lines.",
            ],
            confidence=0.86,
        )
        persist_ai_command_log_safely(db, payload, response)
        return response

    if "gst" in command:
        response = AiCommandResponse(
            ok=True,
            summary="ABHAY can prepare GST-ready assistance, but filing values still need CA review.",
            actions=[
                "Review output GST, input GST and net payable.",
                "Check GSTIN, place of supply and CGST/SGST versus IGST treatment.",
                "Resolve missing GST rates before using the report for filing decisions.",
            ],
            confidence=0.84,
        )
        persist_ai_command_log_safely(db, payload, response)
        return response

    if "ledger" in command:
        response = AiCommandResponse(
            ok=True,
            summary="ABHAY can help map this to the right ledger group and remember corrections for future suggestions.",
            actions=[
                "Search existing ledgers for a close match.",
                "Suggest a new ledger only if no suitable ledger exists.",
                "Store user correction as Smart Ledger Memory after review.",
            ],
            confidence=0.82,
        )
        persist_ai_command_log_safely(db, payload, response)
        return response

    response = AiCommandResponse(
        ok=True,
        summary="ABHAY Alpha can analyze this accounting request and suggest the safest next review workflow.",
        actions=[
            "Convert the instruction into a reviewable accounting task.",
            "Check whether a voucher, ledger, GST review or invoice workflow is needed.",
            "Ask for human approval before any accounting entry is posted.",
        ],
        confidence=0.78,
    )
    persist_ai_command_log_safely(db, payload, response)
    return response


def persist_ai_command_log_safely(
    db: Session,
    payload: AiCommandRequest,
    response: AiCommandResponse,
) -> None:
    try:
        persist_ai_command_log(db, payload, response)
    except SQLAlchemyError:
        db.rollback()


def persist_ai_command_log(db: Session, payload: AiCommandRequest, response: AiCommandResponse) -> None:
    context = payload.context or {}
    company_id = parse_uuid(context.get("companyId") or context.get("company_id"))
    profile_id = parse_uuid(context.get("profileId") or context.get("profile_id"))
    if company_id and db.get(Company, company_id) is None:
        company_id = None
    now = datetime.now(timezone.utc)
    db.add(
        AiLog(
            id=uuid4(),
            company_id=company_id,
            profile_id=profile_id,
            action_type="ai.command",
            input_payload={"command": payload.command, "context": context},
            output_payload=response.model_dump(),
            confidence=Decimal(str(response.confidence)),
            created_at=now,
        )
    )
    db.add(
        AuditLog(
            id=uuid4(),
            company_id=company_id,
            actor_id=profile_id,
            action_type="ai_command.created",
            entity_type="ai_log",
            entity_id=None,
            event_payload={"command": payload.command, "summary": response.summary},
            created_at=now,
        )
    )
    db.commit()


def parse_uuid(value: Any) -> UUID | None:
    if not value:
        return None
    try:
        return UUID(str(value))
    except ValueError:
        return None
