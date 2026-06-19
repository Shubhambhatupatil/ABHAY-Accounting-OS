from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field

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
def run_ai_command(payload: AiCommandRequest) -> AiCommandResponse:
    command = payload.command.strip().lower()

    if "invoice" in command or "bill" in command:
        return AiCommandResponse(
            ok=True,
            summary="ABHAY can route this through the invoice upload and extraction workflow for human review.",
            actions=[
                "Upload a text PDF invoice or bill.",
                "Review extracted party, GSTIN, taxable amount and tax split.",
                "Approve the suggested voucher only after checking the debit and credit lines.",
            ],
            confidence=0.86,
        )

    if "gst" in command:
        return AiCommandResponse(
            ok=True,
            summary="ABHAY can prepare GST-ready assistance, but filing values still need CA review.",
            actions=[
                "Review output GST, input GST and net payable.",
                "Check GSTIN, place of supply and CGST/SGST versus IGST treatment.",
                "Resolve missing GST rates before using the report for filing decisions.",
            ],
            confidence=0.84,
        )

    if "ledger" in command:
        return AiCommandResponse(
            ok=True,
            summary="ABHAY can help map this to the right ledger group and remember corrections for future suggestions.",
            actions=[
                "Search existing ledgers for a close match.",
                "Suggest a new ledger only if no suitable ledger exists.",
                "Store user correction as Smart Ledger Memory after review.",
            ],
            confidence=0.82,
        )

    return AiCommandResponse(
        ok=True,
        summary="ABHAY Alpha can analyze this accounting request and suggest the safest next review workflow.",
        actions=[
            "Convert the instruction into a reviewable accounting task.",
            "Check whether a voucher, ledger, GST review or invoice workflow is needed.",
            "Ask for human approval before any accounting entry is posted.",
        ],
        confidence=0.78,
    )
