from datetime import date
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field

from app.models.accounting import AiSuggestionStatus, VoucherType
from app.schemas.ai_accountant import AiVoucherSuggestionResponse
from app.schemas.accounting import VoucherResponse


class AiEntryTextRequest(BaseModel):
    text: str = Field(min_length=3, max_length=1200)
    transaction_date: date | None = None
    language: str = "auto"
    source_type: str = "one_line_text"


class AiEntryPdfRequest(BaseModel):
    filename: str = Field(min_length=1, max_length=180)
    file_base64: str = Field(min_length=10, max_length=14_000_000)
    transaction_date: date | None = None
    language: str = "auto"


class AiExtractedInvoiceFields(BaseModel):
    vendor_or_customer_name: str | None = None
    invoice_number: str | None = None
    invoice_date: date | None = None
    gstin: str | None = None
    taxable_amount: Decimal | None = None
    cgst_amount: Decimal | None = None
    sgst_amount: Decimal | None = None
    igst_amount: Decimal | None = None
    total_amount: Decimal | None = None
    source_text_available: bool
    extraction_warning: str | None = None


class AiEntrySuggestionResponse(BaseModel):
    suggestion: AiVoucherSuggestionResponse
    workflow_state: str
    confidence_band: str
    clarification_questions: list[str]
    extracted_invoice: AiExtractedInvoiceFields | None = None
    doctor_findings: list[str] = []
    gst_risks: list[str] = []
    duplicate_warnings: list[str] = []


class AiEntrySuggestionListItem(BaseModel):
    suggestion_id: UUID
    input_text: str
    status: AiSuggestionStatus
    confidence: Decimal
    voucher_type: VoucherType
    amount: Decimal
    created_at: str
    decided_at: str | None


class AiEntryApproveRequest(BaseModel):
    suggestion_id: UUID
    corrected_payload: dict | None = None
    correction_note: str | None = Field(default=None, max_length=500)


class AiEntryRejectRequest(BaseModel):
    suggestion_id: UUID
    reason: str = Field(min_length=2, max_length=500)


class AiEntryApproveResponse(BaseModel):
    suggestion_id: UUID
    voucher: VoucherResponse
    correction_recorded: bool


class AiCorrectionResponse(BaseModel):
    id: UUID
    suggestion_id: UUID | None
    corrected_payload: dict
    created_at: str


class AiAccuracyDashboardResponse(BaseModel):
    total_suggestions: int
    approved_suggestions: int
    approved_without_edit: int
    rejected_suggestions: int
    corrected_suggestions: int
    approval_rate: Decimal
    correction_rate: Decimal
    estimated_accuracy: Decimal
    average_confidence: Decimal


class AiMonthEndReadinessResponse(BaseModel):
    books_completion_percent: Decimal
    pending_vouchers: int
    unreconciled_bank_entries: int
    gst_risk_count: int
    missing_bill_count: int
    readiness_score: Decimal


class AiOwnerReportResponse(BaseModel):
    month: str
    summary: str
    profit: Decimal
    cash_position: Decimal
    gst_payable: Decimal
