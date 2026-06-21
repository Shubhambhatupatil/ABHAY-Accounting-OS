from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class DocumentLineItem(BaseModel):
    description: str
    hsn_sac: str | None = None
    quantity: Decimal | None = None
    unit_price: Decimal | None = None
    amount: Decimal | None = None


class DocumentExtractedFields(BaseModel):
    document_type: str
    vendor_name: str | None = None
    customer_name: str | None = None
    gstin: str | None = None
    invoice_number: str | None = None
    invoice_date: str | None = None
    subtotal: Decimal | None = None
    gst_rate: Decimal | None = None
    gst_amount: Decimal | None = None
    total_amount: Decimal | None = None
    line_items: list[DocumentLineItem] = Field(default_factory=list)
    confidence_score: Decimal


class DocumentAccountingSuggestion(BaseModel):
    suggested_voucher_type: str
    debit_ledger: str
    credit_ledger: str
    gst_treatment: str
    summary: str
    warnings: list[str] = Field(default_factory=list)


class DocumentIntelligenceResponse(BaseModel):
    id: UUID
    file_name: str
    document_type: str
    extracted_text_summary: str
    extracted_text_available: bool
    fields: DocumentExtractedFields
    accounting_suggestion: DocumentAccountingSuggestion
    confidence_score: Decimal
    warnings: list[str] = Field(default_factory=list)
    human_approval_required: bool = True
    draft_only: bool = True
