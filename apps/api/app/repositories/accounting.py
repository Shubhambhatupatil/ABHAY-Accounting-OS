from datetime import date, datetime, timezone
from decimal import Decimal
from uuid import UUID, uuid4

from sqlalchemy import Select, func, select, text
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.domain.accounting.engine import PostingLine, money, validate_double_entry
from app.domain.accounting.demo_seed import DEMO_BANK_CSV, DEMO_COMPANY_NAME, DEMO_DATE, DemoSeedResult
from app.domain.accounting.financial_intelligence import IntelligenceLedgerBalance
from app.domain.accounting.invoices import (
    build_invoice_posting_lines,
    calculate_invoice_tax_line,
    sum_invoice_totals,
)
from app.models.accounting import (
    AccountNature,
    AccessRequestStatus,
    BankAccount,
    BankStatement,
    BankTransaction,
    Company,
    CompanyAccessRequest,
    CompanyMember,
    GstRate,
    Invoice,
    InvoiceType,
    InvoiceLine,
    JournalEntry,
    GstSupplyType,
    Ledger,
    LedgerCategory,
    LedgerGroup,
    MembershipStatus,
    ReconciliationMatch,
    ReconciliationStatus,
    Role,
    Voucher,
    VoucherAuditEvent,
    VoucherStatus,
    VoucherType,
)
from app.schemas.accounting import InvoiceCreate, InvoiceLineCreate, LedgerCreate, LedgerUpdate, VoucherCreate, VoucherLineCreate
from app.domain.banking.reconciliation import parse_bank_csv


class AccountingRepository:
    def __init__(self, db: Session):
        self.db = db

    def ensure_member(self, company_id: UUID, profile_id: UUID) -> None:
        member = self.db.scalar(
            select(CompanyMember).where(
                CompanyMember.company_id == company_id,
                CompanyMember.profile_id == profile_id,
                CompanyMember.status == MembershipStatus.active,
            )
        )
        if member is None:
            raise PermissionError("You do not have access to this company.")

    def ensure_owner(self, company_id: UUID, profile_id: UUID) -> None:
        role_code = self.member_role_code(company_id, profile_id)
        if role_code != "owner":
            raise PermissionError("Only the company owner can approve access requests.")

    def member_role_code(self, company_id: UUID, profile_id: UUID) -> str | None:
        return self.db.scalar(
            select(Role.code)
            .join(CompanyMember, CompanyMember.role_id == Role.id)
            .where(
                CompanyMember.company_id == company_id,
                CompanyMember.profile_id == profile_id,
                CompanyMember.status == MembershipStatus.active,
            )
        )

    def list_companies(self, profile_id: UUID) -> list[Company]:
        return list(
            self.db.scalars(
                select(Company)
                .join(CompanyMember, CompanyMember.company_id == Company.id)
                .where(CompanyMember.profile_id == profile_id, CompanyMember.status == MembershipStatus.active)
                .order_by(Company.legal_name)
            )
        )

    def ensure_first_company(
        self,
        user_id: UUID,
        email: str | None,
        full_name: str | None,
        legal_name: str | None,
        trade_name: str | None = None,
        gstin: str | None = None,
    ) -> Company:
        companies = self.list_companies(user_id)
        if companies:
            self.ensure_launch_ai_ledgers(companies[0].id)
            return companies[0]
        self.ensure_user_identity(user_id, email, full_name)
        company_name = (legal_name or trade_name or "ANVRITAI Demo Company").strip() or "ANVRITAI Demo Company"
        company = Company(
            id=uuid4(),
            legal_name=company_name,
            trade_name=trade_name or company_name,
            gstin=gstin,
            created_by=user_id,
        )
        self.db.add(company)
        self.db.flush()
        owner_role = self.db.scalar(select(Role).where(Role.code == "owner"))
        if owner_role is None:
            owner_role = Role(id=uuid4(), code="owner", name="Owner", description="Company owner")
            self.db.add(owner_role)
            self.db.flush()
        self.db.add(
            CompanyMember(
                id=uuid4(),
                company_id=company.id,
                profile_id=user_id,
                role_id=owner_role.id,
                status=MembershipStatus.active,
            )
        )
        self.create_default_accounting_ledgers(company.id)
        self.db.commit()
        self.db.refresh(company)
        return company

    def create_company(
        self,
        user_id: UUID,
        email: str | None,
        full_name: str | None,
        legal_name: str,
        trade_name: str | None = None,
        gstin: str | None = None,
        state_code: str | None = None,
    ) -> Company:
        self.ensure_user_identity(user_id, email, full_name)
        company_name = legal_name.strip()
        company = Company(
            id=uuid4(),
            legal_name=company_name,
            trade_name=trade_name or company_name,
            gstin=gstin,
            state_code=state_code,
            created_by=user_id,
        )
        self.db.add(company)
        self.db.flush()
        owner_role = self.get_or_create_role("owner", "Owner", "Company owner")
        self.db.add(
            CompanyMember(
                id=uuid4(),
                company_id=company.id,
                profile_id=user_id,
                role_id=owner_role.id,
                status=MembershipStatus.active,
            )
        )
        self.create_default_accounting_ledgers(company.id)
        self.db.commit()
        self.db.refresh(company)
        return company

    def get_or_create_role(self, code: str, name: str, description: str) -> Role:
        role = self.db.scalar(select(Role).where(Role.code == code))
        if role is None:
            role = Role(id=uuid4(), code=code, name=name, description=description)
            self.db.add(role)
            self.db.flush()
        return role

    def request_company_access(
        self,
        company_id: UUID,
        requester_id: UUID,
        requester_email: str | None,
        requested_role: str,
        full_name: str | None = None,
    ) -> CompanyAccessRequest:
        self.ensure_user_identity(requester_id, requester_email, full_name)
        company = self.db.get(Company, company_id)
        if company is None:
            raise LookupError("Company not found.")
        if self.member_role_code(company_id, requester_id) is not None:
            raise ValueError("User already has access to this company.")
        request = self.db.scalar(
            select(CompanyAccessRequest).where(
                CompanyAccessRequest.company_id == company_id,
                CompanyAccessRequest.requester_profile_id == requester_id,
                CompanyAccessRequest.status == AccessRequestStatus.pending,
            )
        )
        if request is None:
            request = CompanyAccessRequest(
                id=uuid4(),
                company_id=company_id,
                requester_profile_id=requester_id,
                requester_email=requester_email,
                requested_role=requested_role,
                status=AccessRequestStatus.pending,
                decided_by=None,
                decided_at=None,
                created_at=datetime.now(timezone.utc),
            )
            self.db.add(request)
        else:
            request.requested_role = requested_role
            request.requester_email = requester_email
        self.db.commit()
        self.db.refresh(request)
        return request

    def list_access_requests(self, company_id: UUID, owner_id: UUID) -> list[CompanyAccessRequest]:
        self.ensure_owner(company_id, owner_id)
        return list(
            self.db.scalars(
                select(CompanyAccessRequest)
                .where(CompanyAccessRequest.company_id == company_id)
                .order_by(CompanyAccessRequest.created_at.desc())
            )
        )

    def decide_access_request(
        self,
        company_id: UUID,
        request_id: UUID,
        owner_id: UUID,
        decision: str,
        role_code: str,
    ) -> CompanyAccessRequest:
        self.ensure_owner(company_id, owner_id)
        request = self.db.scalar(
            select(CompanyAccessRequest).where(
                CompanyAccessRequest.company_id == company_id,
                CompanyAccessRequest.id == request_id,
            )
        )
        if request is None:
            raise LookupError("Access request not found.")
        if request.status != AccessRequestStatus.pending:
            raise ValueError("Access request is already decided.")
        request.decided_by = owner_id
        request.decided_at = datetime.now(timezone.utc)
        if decision == "approve":
            role_name = "Viewer" if role_code == "viewer" else "Accountant"
            role = self.get_or_create_role(role_code, role_name, f"{role_name} company access")
            member = self.db.scalar(
                select(CompanyMember).where(
                    CompanyMember.company_id == company_id,
                    CompanyMember.profile_id == request.requester_profile_id,
                )
            )
            if member is None:
                self.db.add(
                    CompanyMember(
                        id=uuid4(),
                        company_id=company_id,
                        profile_id=request.requester_profile_id,
                        role_id=role.id,
                        status=MembershipStatus.active,
                    )
                )
            else:
                member.role_id = role.id
                member.status = MembershipStatus.active
            request.status = AccessRequestStatus.approved
            request.requested_role = role_code
        else:
            request.status = AccessRequestStatus.rejected
        self.db.commit()
        self.db.refresh(request)
        return request

    def create_default_accounting_ledgers(self, company_id: UUID) -> None:
        groups = {
            AccountNature.asset: self.system_group(company_id, "Assets", AccountNature.asset),
            AccountNature.liability: self.system_group(company_id, "Liabilities", AccountNature.liability),
            AccountNature.income: self.system_group(company_id, "Income", AccountNature.income),
            AccountNature.expense: self.system_group(company_id, "Expenses", AccountNature.expense),
            AccountNature.equity: self.system_group(company_id, "Equity", AccountNature.equity),
        }
        defaults = [
            ("Cash", LedgerCategory.cash, AccountNature.asset),
            ("Bank", LedgerCategory.bank, AccountNature.asset),
            ("Sundry Debtors", LedgerCategory.sundry_debtor, AccountNature.asset),
            ("Input GST", LedgerCategory.input_gst, AccountNature.asset),
            ("Sundry Creditors", LedgerCategory.sundry_creditor, AccountNature.liability),
            ("Output GST", LedgerCategory.output_gst, AccountNature.liability),
            ("Sales", LedgerCategory.sales, AccountNature.income),
            ("Purchases", LedgerCategory.purchase, AccountNature.expense),
            ("Direct Expenses", LedgerCategory.direct_expense, AccountNature.expense),
            ("Indirect Expenses", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Mobile Recharge Expense", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Communication Expense", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Internet & Phone Expense", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Capital Account", LedgerCategory.capital, AccountNature.equity),
        ]
        for name, category, nature in defaults:
            self.db.add(
                Ledger(
                    id=uuid4(),
                    company_id=company_id,
                    ledger_group_id=groups[nature].id,
                    name=name,
                    category=category,
                    account_nature=nature,
                    opening_balance=Decimal("0.00"),
                    opening_balance_type="dr",
                    gstin=None,
                    state_code=None,
                    is_system=True,
                    is_active=True,
                )
            )

    def ensure_launch_ai_ledgers(self, company_id: UUID) -> None:
        expenses_group = self.db.scalar(
            select(LedgerGroup).where(
                LedgerGroup.company_id == company_id,
                LedgerGroup.name == "Expenses",
                LedgerGroup.account_nature == AccountNature.expense,
            )
        )
        if expenses_group is None:
            expenses_group = self.system_group(company_id, "Expenses", AccountNature.expense)
        for name in ["Mobile Recharge Expense", "Communication Expense", "Internet & Phone Expense"]:
            exists = self.db.scalar(select(Ledger).where(Ledger.company_id == company_id, Ledger.name == name))
            if exists is None:
                self.db.add(
                    Ledger(
                        id=uuid4(),
                        company_id=company_id,
                        ledger_group_id=expenses_group.id,
                        name=name,
                        category=LedgerCategory.indirect_expense,
                        account_nature=AccountNature.expense,
                        opening_balance=Decimal("0.00"),
                        opening_balance_type="dr",
                        gstin=None,
                        state_code=None,
                        is_system=True,
                        is_active=True,
                    )
                )
        self.db.commit()

    def system_group(self, company_id: UUID, name: str, nature: AccountNature) -> LedgerGroup:
        group = LedgerGroup(
            id=uuid4(),
            company_id=company_id,
            name=name,
            account_nature=nature,
            parent_id=None,
            is_system=True,
        )
        self.db.add(group)
        self.db.flush()
        return group

    def list_groups(self, company_id: UUID) -> list[LedgerGroup]:
        return list(
            self.db.scalars(
                select(LedgerGroup)
                .where(LedgerGroup.company_id == company_id)
                .order_by(LedgerGroup.account_nature, LedgerGroup.name)
            )
        )

    def create_group(self, company_id: UUID, name: str, nature: AccountNature) -> LedgerGroup:
        group = LedgerGroup(
            id=uuid4(),
            company_id=company_id,
            name=name,
            account_nature=nature,
            parent_id=None,
            is_system=False,
        )
        self.db.add(group)
        self.db.commit()
        self.db.refresh(group)
        return group

    def ledger_query(self, company_id: UUID) -> Select[tuple[Ledger, str]]:
        return (
            select(Ledger, LedgerGroup.name)
            .join(LedgerGroup, LedgerGroup.id == Ledger.ledger_group_id)
            .where(Ledger.company_id == company_id)
        )

    def list_ledgers(
        self,
        company_id: UUID,
        search: str | None,
        nature: AccountNature | None,
        category: LedgerCategory | None,
        include_inactive: bool,
    ) -> list[tuple[Ledger, str]]:
        query = self.ledger_query(company_id)
        if search:
            query = query.where(Ledger.name.ilike(f"%{search}%"))
        if nature:
            query = query.where(Ledger.account_nature == nature)
        if category:
            query = query.where(Ledger.category == category)
        if not include_inactive:
            query = query.where(Ledger.is_active.is_(True))
        return list(self.db.execute(query.order_by(Ledger.name)).all())

    def create_ledger(self, company_id: UUID, payload: LedgerCreate) -> Ledger:
        ledger = Ledger(
            id=uuid4(),
            company_id=company_id,
            ledger_group_id=payload.ledger_group_id,
            name=payload.name,
            category=payload.category,
            account_nature=payload.account_nature,
            opening_balance=money(payload.opening_balance),
            opening_balance_type=payload.opening_balance_type,
            gstin=payload.gstin,
            state_code=payload.state_code,
            is_system=False,
            is_active=True,
        )
        self.db.add(ledger)
        self.db.commit()
        self.db.refresh(ledger)
        return ledger

    def update_ledger(self, company_id: UUID, ledger_id: UUID, payload: LedgerUpdate) -> Ledger:
        ledger = self.db.scalar(
            select(Ledger).where(Ledger.company_id == company_id, Ledger.id == ledger_id)
        )
        if ledger is None:
            raise LookupError("Ledger not found.")
        for key, value in payload.model_dump(exclude_unset=True).items():
            setattr(ledger, key, value)
        self.db.commit()
        self.db.refresh(ledger)
        return ledger

    def delete_ledger(self, company_id: UUID, ledger_id: UUID) -> None:
        ledger = self.db.scalar(
            select(Ledger).where(Ledger.company_id == company_id, Ledger.id == ledger_id)
        )
        if ledger is None:
            raise LookupError("Ledger not found.")
        if ledger.is_system:
            raise ValueError("System ledgers cannot be deleted.")
        ledger.is_active = False
        self.db.commit()

    def create_voucher(self, company_id: UUID, user_id: UUID, payload: VoucherCreate) -> Voucher:
        lines = [
            PostingLine(
                ledger_id=line.ledger_id,
                debit=money(line.debit),
                credit=money(line.credit),
                narration=line.narration,
            )
            for line in payload.lines
        ]
        validate_double_entry(lines)

        number = self.next_voucher_number(company_id, payload.voucher_type.value)
        voucher = Voucher(
            id=uuid4(),
            company_id=company_id,
            voucher_number=number,
            voucher_type=payload.voucher_type,
            voucher_date=payload.voucher_date,
            status=VoucherStatus.posted,
            narration=payload.narration,
            source="manual",
            created_by=user_id,
            approved_by=user_id,
            posted_at=datetime.now(timezone.utc),
            created_at=datetime.now(timezone.utc),
        )
        self.db.add(voucher)
        self.db.flush()
        for index, line in enumerate(lines, start=1):
            self.db.add(
                __import__("app.models.accounting", fromlist=["JournalEntry"]).JournalEntry(
                    id=uuid4(),
                    company_id=company_id,
                    voucher_id=voucher.id,
                    ledger_id=line.ledger_id,
                    line_number=index,
                    debit=line.debit,
                    credit=line.credit,
                    narration=line.narration,
                )
            )
        self.db.add(
            VoucherAuditEvent(
                id=uuid4(),
                company_id=company_id,
                voucher_id=voucher.id,
                actor_id=user_id,
                event_type="voucher.posted",
                event_payload={"voucher_number": number, "voucher_type": payload.voucher_type.value},
                created_at=datetime.now(timezone.utc),
            )
        )
        self.db.commit()
        return self.get_voucher(company_id, voucher.id)

    def next_voucher_number(self, company_id: UUID, voucher_type: str) -> str:
        count = self.db.scalar(
            select(func.count(Voucher.id)).where(
                Voucher.company_id == company_id, Voucher.voucher_type == voucher_type
            )
        )
        return f"{voucher_type.upper()}-{(count or 0) + 1:06d}"

    def get_voucher(self, company_id: UUID, voucher_id: UUID) -> Voucher:
        voucher = self.db.scalar(
            select(Voucher).where(Voucher.company_id == company_id, Voucher.id == voucher_id)
        )
        if voucher is None:
            raise LookupError("Voucher not found.")
        return voucher

    def list_vouchers(self, company_id: UUID) -> list[Voucher]:
        return list(
            self.db.scalars(
                select(Voucher)
                .where(Voucher.company_id == company_id)
                .order_by(Voucher.voucher_date.desc(), Voucher.voucher_number.desc())
            )
        )

    def list_audit_events(self, company_id: UUID, limit: int = 20) -> list[VoucherAuditEvent]:
        return list(
            self.db.scalars(
                select(VoucherAuditEvent)
                .where(VoucherAuditEvent.company_id == company_id)
                .order_by(VoucherAuditEvent.created_at.desc())
                .limit(limit)
            )
        )

    def ledger_balances(self, company_id: UUID) -> list[tuple[Ledger, Decimal, Decimal]]:
        from app.models.accounting import JournalEntry

        rows = self.db.execute(
            select(
                Ledger,
                func.coalesce(func.sum(JournalEntry.debit), 0),
                func.coalesce(func.sum(JournalEntry.credit), 0),
            )
            .outerjoin(JournalEntry, JournalEntry.ledger_id == Ledger.id)
            .where(Ledger.company_id == company_id, Ledger.is_active.is_(True))
            .group_by(Ledger.id)
            .order_by(Ledger.name)
        ).all()
        return [(row[0], money(row[1]), money(row[2])) for row in rows]

    def create_invoice(self, company_id: UUID, user_id: UUID, payload: InvoiceCreate) -> Invoice:
        invoice = Invoice(
            id=uuid4(),
            company_id=company_id,
            invoice_type=payload.invoice_type,
            invoice_number=payload.invoice_number,
            invoice_date=payload.invoice_date,
            due_date=payload.due_date,
            party_ledger_id=payload.party_ledger_id,
            voucher_id=None,
            gst_supply_type=payload.gst_supply_type,
            taxable_value=Decimal("0.00"),
            cgst_amount=Decimal("0.00"),
            sgst_amount=Decimal("0.00"),
            igst_amount=Decimal("0.00"),
            total_amount=Decimal("0.00"),
            notes=payload.notes,
            created_by=user_id,
        )
        self.db.add(invoice)
        self.db.flush()
        tax_lines = []
        for index, line in enumerate(payload.lines, start=1):
            tax_line = calculate_invoice_tax_line(
                line.quantity,
                line.unit_price,
                line.discount_amount,
                line.gst_rate,
                payload.gst_supply_type.value,
            )
            tax_lines.append(tax_line)
            self.db.add(
                InvoiceLine(
                    id=uuid4(),
                    company_id=company_id,
                    invoice_id=invoice.id,
                    line_number=index,
                    description=line.description,
                    hsn_sac=line.hsn_sac,
                    quantity=line.quantity,
                    unit=line.unit,
                    unit_price=line.unit_price,
                    discount_amount=line.discount_amount,
                    gst_rate=line.gst_rate,
                    taxable_value=tax_line.taxable_value,
                    cgst_amount=tax_line.cgst_amount,
                    sgst_amount=tax_line.sgst_amount,
                    igst_amount=tax_line.igst_amount,
                    total_amount=tax_line.total_amount,
                )
            )
        totals = sum_invoice_totals(tax_lines)
        invoice.taxable_value = totals.taxable_value
        invoice.cgst_amount = totals.cgst_amount
        invoice.sgst_amount = totals.sgst_amount
        invoice.igst_amount = totals.igst_amount
        invoice.total_amount = totals.total_amount
        voucher = self.create_invoice_voucher(company_id, user_id, invoice, payload.invoice_type, totals)
        invoice.voucher_id = voucher.id
        self.db.commit()
        self.db.refresh(invoice)
        return invoice

    def create_invoice_voucher(
        self,
        company_id: UUID,
        user_id: UUID,
        invoice: Invoice,
        invoice_type,
        totals,
    ) -> Voucher:
        sales_or_purchase_category = (
            LedgerCategory.sales if invoice_type.value == "sales" else LedgerCategory.purchase
        )
        gst_category = LedgerCategory.output_gst if invoice_type.value == "sales" else LedgerCategory.input_gst
        main_ledger = self.get_required_ledger_by_category(company_id, sales_or_purchase_category)
        gst_ledger = self.get_required_ledger_by_category(company_id, gst_category)
        posting_lines = build_invoice_posting_lines(
            invoice_type,
            invoice.party_ledger_id,
            main_ledger.id,
            gst_ledger.id,
            totals,
        )
        validate_double_entry(
            [
                PostingLine(line.ledger_id, line.debit, line.credit, line.narration)
                for line in posting_lines
            ]
        )
        voucher = Voucher(
            id=uuid4(),
            company_id=company_id,
            voucher_number=self.next_voucher_number(company_id, invoice_type.value),
            voucher_type=VoucherType(invoice_type.value),
            voucher_date=invoice.invoice_date,
            status=VoucherStatus.posted,
            narration=f"Auto-posted from invoice {invoice.invoice_number}",
            source="invoice",
            created_by=user_id,
            approved_by=user_id,
            posted_at=datetime.now(timezone.utc),
            created_at=datetime.now(timezone.utc),
        )
        self.db.add(voucher)
        self.db.flush()
        for index, line in enumerate(posting_lines, start=1):
            self.db.add(
                JournalEntry(
                    id=uuid4(),
                    company_id=company_id,
                    voucher_id=voucher.id,
                    ledger_id=line.ledger_id,
                    line_number=index,
                    debit=line.debit,
                    credit=line.credit,
                    narration=line.narration,
                )
            )
        self.db.add(
            VoucherAuditEvent(
                id=uuid4(),
                company_id=company_id,
                voucher_id=voucher.id,
                actor_id=user_id,
                event_type="invoice.voucher.posted",
                event_payload={"invoice_number": invoice.invoice_number},
                created_at=datetime.now(timezone.utc),
            )
        )
        return voucher

    def get_required_ledger_by_category(self, company_id: UUID, category: LedgerCategory) -> Ledger:
        ledger = self.db.scalar(
            select(Ledger).where(
                Ledger.company_id == company_id,
                Ledger.category == category,
                Ledger.is_active.is_(True),
            )
        )
        if ledger is None:
            raise LookupError(f"Required ledger missing for category {category.value}.")
        return ledger

    def list_invoices(self, company_id: UUID) -> list[Invoice]:
        return list(
            self.db.scalars(
                select(Invoice)
                .where(Invoice.company_id == company_id)
                .order_by(Invoice.invoice_date.desc(), Invoice.invoice_number.desc())
            )
        )

    def get_invoice(self, company_id: UUID, invoice_id: UUID) -> Invoice:
        invoice = self.db.scalar(
            select(Invoice).where(Invoice.company_id == company_id, Invoice.id == invoice_id)
        )
        if invoice is None:
            raise LookupError("Invoice not found.")
        return invoice

    def gst_rates(self, company_id: UUID) -> list[GstRate]:
        return list(
            self.db.scalars(
                select(GstRate)
                .where((GstRate.company_id == company_id) | (GstRate.company_id.is_(None)))
                .order_by(GstRate.rate)
            )
        )

    def monthly_ledger_balances(
        self,
        company_id: UUID,
        starts_on,
        ends_on,
    ) -> list[IntelligenceLedgerBalance]:
        from app.models.accounting import JournalEntry

        rows = self.db.execute(
            select(
                Ledger.name,
                Ledger.account_nature,
                Ledger.category,
                func.coalesce(func.sum(JournalEntry.debit), 0),
                func.coalesce(func.sum(JournalEntry.credit), 0),
            )
            .join(JournalEntry, JournalEntry.ledger_id == Ledger.id)
            .join(Voucher, Voucher.id == JournalEntry.voucher_id)
            .where(
                Ledger.company_id == company_id,
                Voucher.company_id == company_id,
                Voucher.status == VoucherStatus.posted,
                Voucher.voucher_date >= starts_on,
                Voucher.voucher_date <= ends_on,
            )
            .group_by(Ledger.id)
            .order_by(Ledger.name)
        ).all()
        return [
            IntelligenceLedgerBalance(
                ledger_name=row[0],
                account_nature=row[1],
                category=row[2],
                debit=money(row[3]),
                credit=money(row[4]),
            )
            for row in rows
        ]

    def get_or_create_bank_account(
        self,
        company_id: UUID,
        bank_ledger_id: UUID | None,
        bank_name: str,
    ) -> BankAccount:
        ledger = None
        if bank_ledger_id:
            ledger = self.db.scalar(
                select(Ledger).where(
                    Ledger.company_id == company_id,
                    Ledger.id == bank_ledger_id,
                    Ledger.category == LedgerCategory.bank,
                )
            )
        if ledger is None:
            ledger = self.db.scalar(
                select(Ledger).where(
                    Ledger.company_id == company_id,
                    Ledger.category == LedgerCategory.bank,
                    Ledger.is_active.is_(True),
                )
            )
        if ledger is None:
            raise LookupError("No active bank ledger exists for this company.")

        account = self.db.scalar(
            select(BankAccount).where(BankAccount.company_id == company_id, BankAccount.ledger_id == ledger.id)
        )
        if account:
            return account
        account = BankAccount(
            id=uuid4(),
            company_id=company_id,
            ledger_id=ledger.id,
            bank_name=bank_name,
            account_number_last4=None,
            ifsc=None,
            is_active=True,
        )
        self.db.add(account)
        self.db.flush()
        return account

    def create_bank_statement(
        self,
        company_id: UUID,
        user_id: UUID,
        bank_account: BankAccount,
        filename: str,
        parsed_transactions,
    ) -> BankStatement:
        statement = BankStatement(
            id=uuid4(),
            company_id=company_id,
            bank_account_id=bank_account.id,
            file_path=f"local-upload://{filename}",
            statement_from=min((item.transaction_date for item in parsed_transactions), default=None),
            statement_to=max((item.transaction_date for item in parsed_transactions), default=None),
            uploaded_by=user_id,
            created_at=datetime.now(timezone.utc),
        )
        self.db.add(statement)
        self.db.flush()
        for item in parsed_transactions:
            self.db.add(
                BankTransaction(
                    id=uuid4(),
                    company_id=company_id,
                    bank_statement_id=statement.id,
                    transaction_date=item.transaction_date,
                    description=item.description,
                    reference_number=item.reference_number,
                    debit=item.debit,
                    credit=item.credit,
                    balance=item.balance,
                    reconciliation_status=ReconciliationStatus.unmatched,
                )
            )
        self.db.commit()
        self.db.refresh(statement)
        return statement

    def list_bank_transactions(self, company_id: UUID) -> list[BankTransaction]:
        return list(
            self.db.scalars(
                select(BankTransaction)
                .where(BankTransaction.company_id == company_id)
                .order_by(BankTransaction.transaction_date.desc(), BankTransaction.created_at.desc() if hasattr(BankTransaction, "created_at") else BankTransaction.id)
            )
        )

    def bank_match_candidates(self, company_id: UUID):
        from app.domain.banking.reconciliation import VoucherCandidate
        from app.models.accounting import JournalEntry

        rows = self.db.execute(
            select(
                Voucher.id,
                JournalEntry.id,
                Voucher.voucher_number,
                Voucher.voucher_date,
                Voucher.narration,
                Ledger.name,
                JournalEntry.debit,
                JournalEntry.credit,
            )
            .join(JournalEntry, JournalEntry.voucher_id == Voucher.id)
            .join(Ledger, Ledger.id == JournalEntry.ledger_id)
            .where(
                Voucher.company_id == company_id,
                Voucher.status == VoucherStatus.posted,
                Ledger.category == LedgerCategory.bank,
            )
        ).all()
        return [
            VoucherCandidate(
                voucher_id=row[0],
                journal_entry_id=row[1],
                voucher_number=row[2],
                voucher_date=row[3],
                narration=row[4] or "",
                ledger_name=row[5],
                debit=money(row[6]),
                credit=money(row[7]),
            )
            for row in rows
        ]

    def confirm_reconciliation_match(
        self,
        company_id: UUID,
        user_id: UUID,
        bank_transaction_id: UUID,
        journal_entry_id: UUID,
        confidence: Decimal,
    ) -> None:
        transaction = self.db.scalar(
            select(BankTransaction).where(
                BankTransaction.company_id == company_id,
                BankTransaction.id == bank_transaction_id,
            )
        )
        if transaction is None:
            raise LookupError("Bank transaction not found.")
        existing = self.db.scalar(
            select(ReconciliationMatch).where(
                ReconciliationMatch.company_id == company_id,
                ReconciliationMatch.bank_transaction_id == bank_transaction_id,
                ReconciliationMatch.journal_entry_id == journal_entry_id,
            )
        )
        if existing is None:
            self.db.add(
                ReconciliationMatch(
                    id=uuid4(),
                    company_id=company_id,
                    bank_transaction_id=bank_transaction_id,
                    journal_entry_id=journal_entry_id,
                    confidence=money(confidence),
                    matched_by=user_id,
                    matched_at=datetime.now(timezone.utc),
                )
            )
        transaction.reconciliation_status = ReconciliationStatus.matched
        self.db.commit()

    def update_bank_transaction_status(
        self,
        company_id: UUID,
        bank_transaction_id: UUID,
        status_value: str,
    ) -> None:
        transaction = self.db.scalar(
            select(BankTransaction).where(
                BankTransaction.company_id == company_id,
                BankTransaction.id == bank_transaction_id,
            )
        )
        if transaction is None:
            raise LookupError("Bank transaction not found.")
        transaction.reconciliation_status = ReconciliationStatus(status_value)
        self.db.commit()

    def create_demo_company(
        self,
        user_id: UUID,
        email: str | None = None,
        full_name: str | None = None,
    ) -> DemoSeedResult:
        self.ensure_user_identity(user_id, email, full_name)
        existing = self.db.scalar(
            select(Company)
            .join(CompanyMember, CompanyMember.company_id == Company.id)
            .where(
                CompanyMember.profile_id == user_id,
                Company.legal_name == DEMO_COMPANY_NAME,
                CompanyMember.status == MembershipStatus.active,
            )
        )
        if existing:
            self.ensure_launch_ai_ledgers(existing.id)
            return DemoSeedResult(existing.id, existing.legal_name, 0, 0, 0, 0)

        company = Company(
            id=uuid4(),
            legal_name=DEMO_COMPANY_NAME,
            trade_name=DEMO_COMPANY_NAME,
            gstin="27ABCDE1234F1Z5",
            created_by=user_id,
        )
        self.db.add(company)
        owner_role_id = self.db.scalar(select(Role.id).where(Role.code == "owner"))
        if owner_role_id is None:
            owner_role_id = uuid4()
            self.db.add(
                Role(
                    id=owner_role_id,
                    code="owner",
                    name="Owner",
                    description="Demo owner role",
                )
            )
        self.db.add(
            CompanyMember(
                id=uuid4(),
                company_id=company.id,
                profile_id=user_id,
                role_id=owner_role_id,
                status=MembershipStatus.active,
            )
        )
        groups = {
            "Assets": self.create_group(company.id, "Assets", AccountNature.asset),
            "Liabilities": self.create_group(company.id, "Liabilities", AccountNature.liability),
            "Income": self.create_group(company.id, "Income", AccountNature.income),
            "Expenses": self.create_group(company.id, "Expenses", AccountNature.expense),
            "Equity": self.create_group(company.id, "Equity", AccountNature.equity),
        }
        ledgers_to_create = [
            ("Cash", "Assets", LedgerCategory.cash, AccountNature.asset),
            ("Bank", "Assets", LedgerCategory.bank, AccountNature.asset),
            ("ABC Customers", "Assets", LedgerCategory.sundry_debtor, AccountNature.asset),
            ("Input GST", "Assets", LedgerCategory.input_gst, AccountNature.asset),
            ("XYZ Suppliers", "Liabilities", LedgerCategory.sundry_creditor, AccountNature.liability),
            ("Output GST", "Liabilities", LedgerCategory.output_gst, AccountNature.liability),
            ("Sales", "Income", LedgerCategory.sales, AccountNature.income),
            ("Purchases", "Expenses", LedgerCategory.purchase, AccountNature.expense),
            ("Office Rent", "Expenses", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Mobile Recharge Expense", "Expenses", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Communication Expense", "Expenses", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Internet & Phone Expense", "Expenses", LedgerCategory.indirect_expense, AccountNature.expense),
            ("Diesel Expense", "Expenses", LedgerCategory.direct_expense, AccountNature.expense),
            ("Capital Account", "Equity", LedgerCategory.capital, AccountNature.equity),
        ]
        ledgers = {}
        for name, group_name, category, nature in ledgers_to_create:
            ledger = self.create_ledger(
                company.id,
                LedgerCreate(
                    name=name,
                    ledger_group_id=groups[group_name].id,
                    category=category,
                    account_nature=nature,
                ),
            )
            ledgers[name] = ledger

        voucher_payloads = [
            VoucherCreate(
                voucher_type=VoucherType.receipt,
                voucher_date=DEMO_DATE,
                narration="Demo customer receipt",
                lines=[
                    VoucherLineCreate(ledger_id=ledgers["Bank"].id, debit=Decimal("60000.00")),
                    VoucherLineCreate(ledger_id=ledgers["ABC Customers"].id, credit=Decimal("60000.00")),
                ],
            ),
            VoucherCreate(
                voucher_type=VoucherType.payment,
                voucher_date=DEMO_DATE,
                narration="Demo office rent paid",
                lines=[
                    VoucherLineCreate(ledger_id=ledgers["Office Rent"].id, debit=Decimal("12000.00")),
                    VoucherLineCreate(ledger_id=ledgers["Cash"].id, credit=Decimal("12000.00")),
                ],
            ),
            VoucherCreate(
                voucher_type=VoucherType.payment,
                voucher_date=DEMO_DATE,
                narration="Demo diesel paid",
                lines=[
                    VoucherLineCreate(ledger_id=ledgers["Diesel Expense"].id, debit=Decimal("2500.00")),
                    VoucherLineCreate(ledger_id=ledgers["Cash"].id, credit=Decimal("2500.00")),
                ],
            ),
        ]
        for payload in voucher_payloads:
            self.create_voucher(company.id, user_id, payload)

        invoices = [
            InvoiceCreate(
                invoice_type=InvoiceType.sales,
                invoice_number="DEMO-SALES-001",
                invoice_date=DEMO_DATE,
                due_date=date(2026, 4, 20),
                party_ledger_id=ledgers["ABC Customers"].id,
                gst_supply_type=GstSupplyType.intra_state,
                notes="Demo sales invoice",
                lines=[
                    InvoiceLineCreate(
                        description="Consulting services",
                        hsn_sac="9983",
                        quantity=Decimal("1"),
                        unit="NOS",
                        unit_price=Decimal("100000.00"),
                        gst_rate=Decimal("18.00"),
                    )
                ],
            ),
            InvoiceCreate(
                invoice_type=InvoiceType.purchase,
                invoice_number="DEMO-PURCHASE-001",
                invoice_date=DEMO_DATE,
                due_date=date(2026, 4, 18),
                party_ledger_id=ledgers["XYZ Suppliers"].id,
                gst_supply_type=GstSupplyType.intra_state,
                notes="Demo purchase invoice",
                lines=[
                    InvoiceLineCreate(
                        description="Raw materials",
                        hsn_sac="7208",
                        quantity=Decimal("1"),
                        unit="NOS",
                        unit_price=Decimal("40000.00"),
                        gst_rate=Decimal("18.00"),
                    )
                ],
            ),
        ]
        for payload in invoices:
            self.create_invoice(company.id, user_id, payload)

        bank_account = self.get_or_create_bank_account(company.id, ledgers["Bank"].id, "Demo Bank")
        parsed = parse_bank_csv(DEMO_BANK_CSV)
        self.create_bank_statement(company.id, user_id, bank_account, "demo-bank.csv", parsed)
        return DemoSeedResult(company.id, company.legal_name, len(ledgers), len(voucher_payloads), len(invoices), len(parsed))

    def ensure_local_demo_identity(self, user_id: UUID) -> None:
        if user_id != UUID("00000000-0000-0000-0000-000000000001"):
            return
        self.ensure_user_identity(user_id, "demo@abhay.test", "Local Demo Owner")

    def ensure_user_identity(self, user_id: UUID, email: str | None, full_name: str | None) -> None:
        safe_email = email or f"{user_id}@abhay.test"
        safe_name = full_name or safe_email.split("@", 1)[0]
        try:
            self.insert_profile_identity(user_id, safe_name)
            self.db.commit()
            return
        except IntegrityError:
            self.db.rollback()
        self.db.execute(
            text(
                """
                insert into auth.users (id, email, raw_user_meta_data)
                values (:id, :email, jsonb_build_object('full_name', cast(:full_name as text)))
                on conflict (id) do nothing
                """
            ),
            {"id": str(user_id), "email": safe_email, "full_name": safe_name},
        )
        self.insert_profile_identity(user_id, safe_name)
        self.db.commit()

    def insert_profile_identity(self, user_id: UUID, full_name: str) -> None:
        self.db.execute(
            text(
                """
                insert into profiles (id, full_name)
                values (:id, :full_name)
                on conflict (id) do nothing
                """
            ),
            {"id": str(user_id), "full_name": full_name},
        )
