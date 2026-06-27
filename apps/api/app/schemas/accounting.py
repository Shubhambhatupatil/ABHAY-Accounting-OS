from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field, field_validator

from app.models.accounting import AccountNature, GstSupplyType, InvoiceType, LedgerCategory, VoucherType


class CompanyResponse(BaseModel):
    id: UUID
    legal_name: str
    trade_name: str | None = None
    gstin: str | None = None
    state_code: str | None = None

    model_config = {"from_attributes": True}


class CompanyCreate(BaseModel):
    legal_name: str = Field(min_length=2, max_length=180)
    trade_name: str | None = Field(default=None, max_length=180)
    gstin: str | None = Field(default=None, min_length=15, max_length=15)
    state_code: str | None = Field(default=None, min_length=2, max_length=2)


class AccessRequestCreate(BaseModel):
    requested_role: str = Field(default="accountant", pattern="^(accountant|viewer)$")


class AccessRequestDecision(BaseModel):
    decision: str = Field(pattern="^(approve|reject)$")
    role: str = Field(default="accountant", pattern="^(accountant|viewer)$")


class AccessRequestResponse(BaseModel):
    id: UUID
    company_id: UUID
    company_legal_name: str
    requester_profile_id: UUID
    requester_email: str | None = None
    requested_role: str
    status: str
    created_at: datetime
    decided_at: datetime | None = None


class DemoCompanyResponse(BaseModel):
    company_id: UUID
    legal_name: str
    seeded_ledgers: int
    seeded_vouchers: int
    seeded_invoices: int
    seeded_bank_transactions: int


class DebugCountsResponse(BaseModel):
    ledgers: int
    vouchers: int
    voucher_lines: int
    accounting_entries: int
    invoices: int
    invoice_items: int
    bank_transactions: int
    ai_logs: int
    document_ai_logs: int
    audit_logs: int
    inventory_items: int


class LedgerGroupCreate(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    account_nature: AccountNature
    parent_id: UUID | None = None


class LedgerGroupResponse(LedgerGroupCreate):
    id: UUID
    is_system: bool

    model_config = {"from_attributes": True}


class LedgerCreate(BaseModel):
    name: str = Field(min_length=2, max_length=160)
    ledger_group_id: UUID
    category: LedgerCategory = LedgerCategory.other
    account_nature: AccountNature
    opening_balance: Decimal = Decimal("0.00")
    opening_balance_type: str = Field(default="dr", pattern="^(dr|cr)$")
    gstin: str | None = Field(default=None, min_length=15, max_length=15)
    state_code: str | None = Field(default=None, min_length=2, max_length=2)


class LedgerUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=160)
    ledger_group_id: UUID | None = None
    category: LedgerCategory | None = None
    account_nature: AccountNature | None = None
    opening_balance: Decimal | None = None
    opening_balance_type: str | None = Field(default=None, pattern="^(dr|cr)$")
    gstin: str | None = Field(default=None, min_length=15, max_length=15)
    state_code: str | None = Field(default=None, min_length=2, max_length=2)
    is_active: bool | None = None


class LedgerResponse(BaseModel):
    id: UUID
    name: str
    ledger_group_id: UUID
    group_name: str
    category: LedgerCategory
    account_nature: AccountNature
    opening_balance: Decimal
    opening_balance_type: str
    gstin: str | None
    state_code: str | None
    is_system: bool
    is_active: bool


class VoucherLineCreate(BaseModel):
    ledger_id: UUID
    debit: Decimal = Decimal("0.00")
    credit: Decimal = Decimal("0.00")
    narration: str | None = None

    @field_validator("debit", "credit")
    @classmethod
    def amount_cannot_be_negative(cls, value: Decimal) -> Decimal:
        if value < 0:
            raise ValueError("Amount cannot be negative")
        return value


class VoucherCreate(BaseModel):
    voucher_type: VoucherType
    voucher_date: date
    narration: str | None = None
    lines: list[VoucherLineCreate] = Field(min_length=2)


class VoucherLineResponse(BaseModel):
    id: UUID
    ledger_id: UUID
    ledger_name: str
    debit: Decimal
    credit: Decimal
    narration: str | None


class VoucherResponse(BaseModel):
    id: UUID
    voucher_number: str
    voucher_type: VoucherType
    voucher_date: date
    status: str
    narration: str | None
    posted_at: datetime | None
    lines: list[VoucherLineResponse]


class AuditEventResponse(BaseModel):
    id: UUID
    created_by: UUID | None = None
    updated_by: UUID | None = None
    created_at: datetime
    updated_at: datetime | None = None
    action_type: str
    entity_type: str
    entity_id: UUID
    summary: str


class TrialBalanceRow(BaseModel):
    ledger_id: UUID
    ledger_name: str
    account_nature: AccountNature
    category: LedgerCategory
    debit: Decimal
    credit: Decimal


class ProfitAndLossResponse(BaseModel):
    revenue: Decimal
    expenses: Decimal
    profit: Decimal


class BalanceSheetResponse(BaseModel):
    assets: Decimal
    liabilities: Decimal
    equity: Decimal
    check_difference: Decimal


class CashFlowResponse(BaseModel):
    operating_cash_flow: Decimal
    investing_cash_flow: Decimal
    financing_cash_flow: Decimal
    net_cash_flow: Decimal


class DashboardMetrics(BaseModel):
    revenue: Decimal
    expenses: Decimal
    profit: Decimal
    cash_position: Decimal
    receivables: Decimal
    payables: Decimal


class InvoiceLineCreate(BaseModel):
    description: str = Field(min_length=2, max_length=240)
    hsn_sac: str | None = None
    quantity: Decimal = Field(gt=0)
    unit: str = "NOS"
    unit_price: Decimal = Field(ge=0)
    discount_amount: Decimal = Field(default=Decimal("0.00"), ge=0)
    gst_rate: Decimal = Field(default=Decimal("18.00"), ge=0)


class InvoiceCreate(BaseModel):
    invoice_type: InvoiceType
    invoice_number: str = Field(min_length=1, max_length=80)
    invoice_date: date
    due_date: date | None = None
    party_ledger_id: UUID
    gst_supply_type: GstSupplyType
    notes: str | None = None
    lines: list[InvoiceLineCreate] = Field(min_length=1)


class InvoiceLineResponse(BaseModel):
    id: UUID
    description: str
    hsn_sac: str | None
    quantity: Decimal
    unit: str
    unit_price: Decimal
    gst_rate: Decimal
    taxable_value: Decimal
    cgst_amount: Decimal
    sgst_amount: Decimal
    igst_amount: Decimal
    total_amount: Decimal


class InvoiceResponse(BaseModel):
    id: UUID
    invoice_type: InvoiceType
    invoice_number: str
    invoice_date: date
    due_date: date | None = None
    party_ledger_id: UUID
    party_ledger_name: str | None = None
    voucher_id: UUID | None = None
    taxable_value: Decimal
    cgst_amount: Decimal
    sgst_amount: Decimal
    igst_amount: Decimal
    total_amount: Decimal
    notes: str | None = None
    lines: list[InvoiceLineResponse]


class GstRateResponse(BaseModel):
    label: str
    rate: Decimal


class GstReportResponse(BaseModel):
    input_gst: Decimal
    output_gst: Decimal
    net_payable: Decimal


class InvoiceGstSummaryRow(BaseModel):
    invoice_id: UUID
    invoice_number: str
    invoice_type: InvoiceType
    invoice_date: date
    party_ledger_name: str
    taxable_value: Decimal
    cgst_amount: Decimal
    sgst_amount: Decimal
    igst_amount: Decimal
    total_amount: Decimal


class TdsCalculatorRequest(BaseModel):
    amount: Decimal = Field(ge=0)
    rate_percent: Decimal = Field(default=Decimal("10.00"), ge=0)


class TdsCalculatorResponse(BaseModel):
    taxable_amount: Decimal
    rate_percent: Decimal
    tds_amount: Decimal
    net_payable: Decimal


class PfCalculatorRequest(BaseModel):
    monthly_basic_wage: Decimal = Field(ge=0)
    employee_rate_percent: Decimal = Field(default=Decimal("12.00"), ge=0)
    employer_rate_percent: Decimal = Field(default=Decimal("12.00"), ge=0)
    wage_ceiling: Decimal = Field(default=Decimal("15000.00"), ge=0)


class PfCalculatorResponse(BaseModel):
    eligible_wage: Decimal
    employee_contribution: Decimal
    employer_contribution: Decimal
    total_contribution: Decimal


class EsicCalculatorRequest(BaseModel):
    monthly_gross_wage: Decimal = Field(ge=0)
    employee_rate_percent: Decimal = Field(default=Decimal("0.75"), ge=0)
    employer_rate_percent: Decimal = Field(default=Decimal("3.25"), ge=0)
    wage_limit: Decimal = Field(default=Decimal("21000.00"), ge=0)


class EsicCalculatorResponse(BaseModel):
    eligible: bool
    eligible_wage: Decimal
    employee_contribution: Decimal
    employer_contribution: Decimal
    total_contribution: Decimal


class LedgerScrutinyIssue(BaseModel):
    severity: str
    title: str
    detail: str
    amount: Decimal | None = None


class LedgerScrutinyResponse(BaseModel):
    issue_count: int
    high_risk_count: int
    warning_count: int
    issues: list[LedgerScrutinyIssue]
