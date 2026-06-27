import enum
import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import JSON, Boolean, Date, DateTime, Enum, ForeignKey, Integer, Numeric, String, Text, Uuid
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

UUID = Uuid


class AccountNature(str, enum.Enum):
    asset = "asset"
    liability = "liability"
    equity = "equity"
    income = "income"
    expense = "expense"


class MembershipStatus(str, enum.Enum):
    invited = "invited"
    active = "active"
    suspended = "suspended"
    removed = "removed"


class AccessRequestStatus(str, enum.Enum):
    pending = "pending"
    approved = "approved"
    rejected = "rejected"


class LedgerCategory(str, enum.Enum):
    cash = "cash"
    bank = "bank"
    sundry_debtor = "sundry_debtor"
    sundry_creditor = "sundry_creditor"
    sales = "sales"
    purchase = "purchase"
    direct_expense = "direct_expense"
    indirect_expense = "indirect_expense"
    direct_income = "direct_income"
    indirect_income = "indirect_income"
    input_gst = "input_gst"
    output_gst = "output_gst"
    round_off = "round_off"
    capital = "capital"
    loan = "loan"
    other = "other"


class VoucherType(str, enum.Enum):
    journal = "journal"
    payment = "payment"
    receipt = "receipt"
    contra = "contra"
    sales = "sales"
    purchase = "purchase"
    debit_note = "debit_note"
    credit_note = "credit_note"
    opening_balance = "opening_balance"


class VoucherStatus(str, enum.Enum):
    draft = "draft"
    pending_approval = "pending_approval"
    posted = "posted"
    reversed = "reversed"
    void = "void"


class ReconciliationStatus(str, enum.Enum):
    unmatched = "unmatched"
    suggested_match = "suggested_match"
    matched = "matched"
    ignored = "ignored"


class InvoiceType(str, enum.Enum):
    sales = "sales"
    purchase = "purchase"


class GstSupplyType(str, enum.Enum):
    intra_state = "intra_state"
    inter_state = "inter_state"
    export = "export"
    import_ = "import"
    reverse_charge = "reverse_charge"


class Company(Base):
    __tablename__ = "companies"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    legal_name: Mapped[str] = mapped_column(Text)
    trade_name: Mapped[str | None] = mapped_column(Text)
    gstin: Mapped[str | None] = mapped_column(String(15))
    state_code: Mapped[str | None] = mapped_column(String(2))
    created_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))


class Profile(Base):
    __tablename__ = "profiles"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    full_name: Mapped[str] = mapped_column(Text)
    email: Mapped[str | None] = mapped_column(Text)


class Role(Base):
    __tablename__ = "roles"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    code: Mapped[str] = mapped_column(Text)
    name: Mapped[str] = mapped_column(Text)
    description: Mapped[str] = mapped_column(Text)


class CompanyMember(Base):
    __tablename__ = "company_members"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    profile_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    role_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("roles.id"))
    status: Mapped[MembershipStatus] = mapped_column(Enum(MembershipStatus, name="membership_status"))
    role: Mapped[Role] = relationship()


class Subscription(Base):
    __tablename__ = "subscriptions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    profile_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    plan_name: Mapped[str] = mapped_column(Text)
    trial_start: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    trial_end: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    status: Mapped[str] = mapped_column(Text)
    active: Mapped[bool] = mapped_column(Boolean)
    current_period_start: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    current_period_end: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class Payment(Base):
    __tablename__ = "payments"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    profile_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    razorpay_payment_id: Mapped[str | None] = mapped_column(Text)
    amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    status: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class AccountingEntry(Base):
    __tablename__ = "accounting_entries"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    voucher_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("vouchers.id"))
    journal_entry_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("journal_entries.id"))
    entry_type: Mapped[str] = mapped_column(Text)
    payload: Mapped[dict] = mapped_column(JSON)
    created_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class AiLog(Base):
    __tablename__ = "ai_logs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    profile_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    action_type: Mapped[str] = mapped_column(Text)
    input_payload: Mapped[dict] = mapped_column(JSON)
    output_payload: Mapped[dict] = mapped_column(JSON)
    confidence: Mapped[Decimal | None] = mapped_column(Numeric(5, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class DocumentAiLog(Base):
    __tablename__ = "document_ai_logs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    user_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    file_name: Mapped[str] = mapped_column(Text)
    document_type: Mapped[str] = mapped_column(Text)
    extracted_text: Mapped[str] = mapped_column(Text)
    extracted_json: Mapped[dict] = mapped_column(JSON)
    confidence_score: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class AuditLog(Base):
    __tablename__ = "audit_logs"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    actor_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    action_type: Mapped[str] = mapped_column(Text)
    entity_type: Mapped[str] = mapped_column(Text)
    entity_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    event_payload: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class CompanyAccessRequest(Base):
    __tablename__ = "company_access_requests"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    requester_profile_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    requester_email: Mapped[str | None] = mapped_column(Text)
    requested_role: Mapped[str] = mapped_column(Text)
    status: Mapped[AccessRequestStatus] = mapped_column(
        Enum(AccessRequestStatus, name="access_request_status")
    )
    decided_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    company: Mapped[Company] = relationship()


class LedgerGroup(Base):
    __tablename__ = "ledger_groups"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    name: Mapped[str] = mapped_column(Text)
    account_nature: Mapped[AccountNature] = mapped_column(Enum(AccountNature, name="account_nature"))
    parent_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    is_system: Mapped[bool] = mapped_column(Boolean)


class Ledger(Base):
    __tablename__ = "ledgers"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    ledger_group_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ledger_groups.id"))
    name: Mapped[str] = mapped_column(Text)
    category: Mapped[LedgerCategory] = mapped_column(Enum(LedgerCategory, name="ledger_category"))
    account_nature: Mapped[AccountNature] = mapped_column(Enum(AccountNature, name="account_nature"))
    opening_balance: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    opening_balance_type: Mapped[str] = mapped_column(String(2))
    gstin: Mapped[str | None] = mapped_column(String(15))
    state_code: Mapped[str | None] = mapped_column(String(2))
    is_system: Mapped[bool] = mapped_column(Boolean)
    is_active: Mapped[bool] = mapped_column(Boolean)
    group: Mapped[LedgerGroup] = relationship()


class Voucher(Base):
    __tablename__ = "vouchers"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    voucher_number: Mapped[str] = mapped_column(Text)
    voucher_type: Mapped[VoucherType] = mapped_column(Enum(VoucherType, name="voucher_type"))
    voucher_date: Mapped[date] = mapped_column(Date)
    status: Mapped[VoucherStatus] = mapped_column(Enum(VoucherStatus, name="voucher_status"))
    narration: Mapped[str | None] = mapped_column(Text)
    source: Mapped[str] = mapped_column(Text)
    created_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    approved_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    posted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    entries: Mapped[list["JournalEntry"]] = relationship(cascade="all, delete-orphan")


class JournalEntry(Base):
    __tablename__ = "journal_entries"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    voucher_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("vouchers.id"))
    ledger_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ledgers.id"))
    line_number: Mapped[int] = mapped_column(Integer)
    debit: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    credit: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    narration: Mapped[str | None] = mapped_column(Text)
    ledger: Mapped[Ledger] = relationship()


class VoucherLine(Base):
    __tablename__ = "voucher_lines"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    voucher_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("vouchers.id"))
    ledger_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    ledger_name: Mapped[str | None] = mapped_column(Text)
    line_number: Mapped[int] = mapped_column(Integer)
    debit: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    credit: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    narration: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class VoucherAuditEvent(Base):
    __tablename__ = "voucher_audit_events"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    voucher_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    actor_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    event_type: Mapped[str] = mapped_column(Text)
    event_payload: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class Invoice(Base):
    __tablename__ = "invoices"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    invoice_type: Mapped[InvoiceType] = mapped_column(Enum(InvoiceType, name="invoice_type"))
    invoice_number: Mapped[str] = mapped_column(Text)
    invoice_date: Mapped[date] = mapped_column(Date)
    due_date: Mapped[date | None] = mapped_column(Date)
    party_ledger_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ledgers.id"))
    voucher_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    gst_supply_type: Mapped[GstSupplyType] = mapped_column(Enum(GstSupplyType, name="gst_supply_type"))
    taxable_value: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    cgst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    sgst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    igst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    total_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    notes: Mapped[str | None] = mapped_column(Text)
    created_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    party_ledger: Mapped[Ledger] = relationship()
    lines: Mapped[list["InvoiceLine"]] = relationship(cascade="all, delete-orphan")


class InvoiceLine(Base):
    __tablename__ = "invoice_lines"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    invoice_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("invoices.id"))
    line_number: Mapped[int] = mapped_column(Integer)
    description: Mapped[str] = mapped_column(Text)
    hsn_sac: Mapped[str | None] = mapped_column(Text)
    quantity: Mapped[Decimal] = mapped_column(Numeric(18, 3))
    unit: Mapped[str] = mapped_column(Text)
    unit_price: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    discount_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    gst_rate: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    taxable_value: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    cgst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    sgst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    igst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    total_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))


class InvoiceItem(Base):
    __tablename__ = "invoice_items"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    invoice_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("invoices.id"))
    line_number: Mapped[int] = mapped_column(Integer)
    description: Mapped[str] = mapped_column(Text)
    hsn_sac: Mapped[str | None] = mapped_column(Text)
    quantity: Mapped[Decimal] = mapped_column(Numeric(18, 3))
    unit: Mapped[str] = mapped_column(Text)
    unit_price: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    discount_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    gst_rate: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    taxable_value: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    cgst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    sgst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    igst_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    total_amount: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class GstRate(Base):
    __tablename__ = "gst_rates"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    label: Mapped[str] = mapped_column(Text)
    rate: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    is_active: Mapped[bool] = mapped_column(Boolean)


class BankAccount(Base):
    __tablename__ = "bank_accounts"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    ledger_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("ledgers.id"))
    bank_name: Mapped[str] = mapped_column(Text)
    account_number_last4: Mapped[str | None] = mapped_column(String(4))
    ifsc: Mapped[str | None] = mapped_column(String(11))
    is_active: Mapped[bool] = mapped_column(Boolean)
    ledger: Mapped[Ledger] = relationship()


class BankStatement(Base):
    __tablename__ = "bank_statements"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    bank_account_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("bank_accounts.id")
    )
    file_path: Mapped[str] = mapped_column(Text)
    statement_from: Mapped[date | None] = mapped_column(Date)
    statement_to: Mapped[date | None] = mapped_column(Date)
    uploaded_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class BankTransaction(Base):
    __tablename__ = "bank_transactions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    bank_statement_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("bank_statements.id")
    )
    transaction_date: Mapped[date] = mapped_column(Date)
    description: Mapped[str] = mapped_column(Text)
    reference_number: Mapped[str | None] = mapped_column(Text)
    debit: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    credit: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    balance: Mapped[Decimal | None] = mapped_column(Numeric(18, 2))
    reconciliation_status: Mapped[ReconciliationStatus] = mapped_column(
        Enum(ReconciliationStatus, name="reconciliation_status")
    )


class ReconciliationMatch(Base):
    __tablename__ = "reconciliation_matches"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    bank_transaction_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("bank_transactions.id")
    )
    journal_entry_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("journal_entries.id"))
    confidence: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    matched_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    matched_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))


class BankMatch(Base):
    __tablename__ = "bank_matches"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    bank_transaction_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    voucher_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    journal_entry_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    confidence: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    status: Mapped[str] = mapped_column(Text)
    matched_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    matched_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class InventoryItem(Base):
    __tablename__ = "inventory_items"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    item_name: Mapped[str] = mapped_column(Text)
    sku: Mapped[str | None] = mapped_column(Text)
    unit: Mapped[str] = mapped_column(Text)
    hsn_sac: Mapped[str | None] = mapped_column(Text)
    opening_stock: Mapped[Decimal] = mapped_column(Numeric(18, 3))
    purchase_stock: Mapped[Decimal] = mapped_column(Numeric(18, 3))
    sales_stock: Mapped[Decimal] = mapped_column(Numeric(18, 3))
    closing_stock: Mapped[Decimal] = mapped_column(Numeric(18, 3))
    rate: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    stock_value: Mapped[Decimal] = mapped_column(Numeric(18, 2))
    created_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)


class AiSuggestionStatus(str, enum.Enum):
    draft = "draft"
    needs_review = "needs_review"
    approved = "approved"
    rejected = "rejected"
    expired = "expired"


class AiSuggestion(Base):
    __tablename__ = "ai_suggestions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    requested_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    input_text: Mapped[str] = mapped_column(Text)
    input_language: Mapped[str] = mapped_column(Text)
    intent: Mapped[str] = mapped_column(Text)
    status: Mapped[AiSuggestionStatus] = mapped_column(
        Enum(AiSuggestionStatus, name="ai_suggestion_status")
    )
    confidence: Mapped[Decimal] = mapped_column(Numeric(5, 2))
    proposed_payload: Mapped[dict] = mapped_column(JSON)
    validation_errors: Mapped[list] = mapped_column(JSON)
    approved_voucher_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))
    model_name: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    decided_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    decided_by: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True))


class AiFeedbackExample(Base):
    __tablename__ = "ai_feedback_examples"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    company_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("companies.id"))
    ai_suggestion_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("ai_suggestions.id"))
    corrected_payload: Mapped[dict] = mapped_column(JSON)
    created_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=datetime.utcnow)
