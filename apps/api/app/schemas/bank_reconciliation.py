from datetime import date
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field


class BankStatementUploadRequest(BaseModel):
    bank_ledger_id: UUID | None = None
    bank_name: str = Field(default="Demo Bank", min_length=2, max_length=120)
    filename: str = Field(min_length=3, max_length=180)
    csv_content: str = Field(min_length=10)


class BankStatementUploadResponse(BaseModel):
    statement_id: UUID
    imported_count: int


class BankTransactionResponse(BaseModel):
    id: UUID
    transaction_date: date
    description: str
    reference_number: str | None
    debit: Decimal
    credit: Decimal
    balance: Decimal | None
    reconciliation_status: str


class SuggestedMatchResponse(BaseModel):
    bank_transaction_id: UUID
    voucher_id: UUID
    journal_entry_id: UUID
    voucher_number: str
    confidence: Decimal
    reason: str


class ConfirmMatchRequest(BaseModel):
    bank_transaction_id: UUID
    journal_entry_id: UUID
    confidence: Decimal = Decimal("100.00")


class IgnoreTransactionRequest(BaseModel):
    bank_transaction_id: UUID


class ReconciliationSummary(BaseModel):
    total_transactions: int
    matched: int
    unmatched: int
    suggested_match: int
    ignored: int
    matched_amount: Decimal
    unreconciled_amount: Decimal
