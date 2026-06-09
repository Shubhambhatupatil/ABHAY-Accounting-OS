from datetime import date
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field

from app.models.accounting import LedgerCategory, VoucherType
from app.schemas.accounting import VoucherResponse


class NaturalLanguageEntryRequest(BaseModel):
    text: str = Field(min_length=3, max_length=800)
    transaction_date: date | None = None
    language: str = "auto"


class SuggestedLedger(BaseModel):
    ledger_id: UUID | None
    ledger_name: str
    category: LedgerCategory
    reason: str
    should_create: bool = False


class SuggestedPostingLine(BaseModel):
    ledger_id: UUID | None
    ledger_name: str
    debit: Decimal
    credit: Decimal
    reason: str


class AiVoucherSuggestionResponse(BaseModel):
    suggestion_id: UUID
    input_text: str
    voucher_type: VoucherType
    voucher_date: date
    amount: Decimal
    confidence: Decimal
    gst_applicable: bool
    suggested_gst_rate: Decimal | None
    suggested_ledgers: list[SuggestedLedger]
    lines: list[SuggestedPostingLine]
    explanation: str
    validation_errors: list[str]
    can_post: bool
    model_name: str


class ConfirmAiPostingRequest(BaseModel):
    suggestion_id: UUID


class RejectAiSuggestionRequest(BaseModel):
    reason: str = Field(min_length=2, max_length=300)


class ConfirmAiPostingResponse(BaseModel):
    suggestion_id: UUID
    voucher: VoucherResponse
