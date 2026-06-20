import csv
import logging
from decimal import Decimal
from io import StringIO
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import func, inspect, select, text
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.engine import AccountingValidationError, money
from app.models.accounting import (
    AccountingEntry,
    AccountNature,
    AiLog,
    AuditLog,
    Invoice,
    InvoiceType,
    JournalEntry,
    LedgerCategory,
    Voucher,
)
from app.repositories.accounting import AccountingRepository
from app.schemas.accounting import (
    AccessRequestCreate,
    AccessRequestDecision,
    AccessRequestResponse,
    AuditEventResponse,
    BalanceSheetResponse,
    CashFlowResponse,
    CompanyCreate,
    CompanyResponse,
    DashboardMetrics,
    DebugCountsResponse,
    DemoCompanyResponse,
    GstRateResponse,
    GstReportResponse,
    EsicCalculatorRequest,
    EsicCalculatorResponse,
    InvoiceCreate,
    InvoiceGstSummaryRow,
    InvoiceLineResponse,
    InvoiceResponse,
    LedgerCreate,
    LedgerGroupCreate,
    LedgerGroupResponse,
    LedgerResponse,
    LedgerUpdate,
    LedgerScrutinyIssue,
    LedgerScrutinyResponse,
    PfCalculatorRequest,
    PfCalculatorResponse,
    ProfitAndLossResponse,
    TdsCalculatorRequest,
    TdsCalculatorResponse,
    TrialBalanceRow,
    VoucherCreate,
    VoucherLineResponse,
    VoucherResponse,
)

router = APIRouter()
logger = logging.getLogger(__name__)


def user_uuid(user: AuthenticatedUser) -> UUID:
    if user.id == "local-demo-owner":
        return UUID("00000000-0000-0000-0000-000000000001")
    return UUID(user.id)


def repo_for_company(
    company_id: UUID,
    user: AuthenticatedUser,
    db: Session,
) -> AccountingRepository:
    repo = AccountingRepository(db)
    try:
        repo.ensure_member(company_id, user_uuid(user))
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    return repo


def ledger_response(row: tuple) -> LedgerResponse:
    ledger, group_name = row
    return LedgerResponse(
        id=ledger.id,
        name=ledger.name,
        ledger_group_id=ledger.ledger_group_id,
        group_name=group_name,
        category=ledger.category,
        account_nature=ledger.account_nature,
        opening_balance=ledger.opening_balance,
        opening_balance_type=ledger.opening_balance_type,
        gstin=ledger.gstin,
        state_code=ledger.state_code,
        is_system=ledger.is_system,
        is_active=ledger.is_active,
    )


def voucher_response(voucher) -> VoucherResponse:
    return VoucherResponse(
        id=voucher.id,
        voucher_number=voucher.voucher_number,
        voucher_type=voucher.voucher_type,
        voucher_date=voucher.voucher_date,
        status=voucher.status.value,
        narration=voucher.narration,
        posted_at=voucher.posted_at,
        lines=[
            VoucherLineResponse(
                id=line.id,
                ledger_id=line.ledger_id,
                ledger_name=line.ledger.name,
                debit=line.debit,
                credit=line.credit,
                narration=line.narration,
            )
            for line in sorted(voucher.entries, key=lambda item: item.line_number)
        ],
    )


def invoice_response(invoice) -> InvoiceResponse:
    party_name = invoice.party_ledger.name if getattr(invoice, "party_ledger", None) else None
    return InvoiceResponse(
        id=invoice.id,
        invoice_type=invoice.invoice_type,
        invoice_number=invoice.invoice_number,
        invoice_date=invoice.invoice_date,
        due_date=invoice.due_date,
        party_ledger_id=invoice.party_ledger_id,
        party_ledger_name=party_name,
        voucher_id=invoice.voucher_id,
        taxable_value=invoice.taxable_value,
        cgst_amount=invoice.cgst_amount,
        sgst_amount=invoice.sgst_amount,
        igst_amount=invoice.igst_amount,
        total_amount=invoice.total_amount,
        notes=invoice.notes,
        lines=[
            InvoiceLineResponse(
                id=line.id,
                description=line.description,
                hsn_sac=line.hsn_sac,
                quantity=line.quantity,
                unit=line.unit,
                unit_price=line.unit_price,
                gst_rate=line.gst_rate,
                taxable_value=line.taxable_value,
                cgst_amount=line.cgst_amount,
                sgst_amount=line.sgst_amount,
                igst_amount=line.igst_amount,
                total_amount=line.total_amount,
            )
            for line in sorted(invoice.lines, key=lambda item: item.line_number)
        ],
    )


def access_request_response(request) -> AccessRequestResponse:
    company_name = request.company.legal_name if getattr(request, "company", None) else "Company"
    return AccessRequestResponse(
        id=request.id,
        company_id=request.company_id,
        company_legal_name=company_name,
        requester_profile_id=request.requester_profile_id,
        requester_email=request.requester_email,
        requested_role=request.requested_role,
        status=request.status.value,
        created_at=request.created_at,
        decided_at=request.decided_at,
    )


@router.get("/companies", response_model=list[CompanyResponse])
def list_companies(
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[CompanyResponse]:
    repo = AccountingRepository(db)
    try:
        companies = repo.list_companies(user_uuid(user))
        if not companies:
            if user.id == "local-demo-owner":
                result = repo.create_demo_company(user_uuid(user), user.email, "Local Demo Owner")
                companies = repo.list_companies(user_uuid(user))
                if not companies:
                    raise RuntimeError(f"Demo company seed failed for {result.company_id}")
            else:
                metadata = user.claims.get("user_metadata") or user.claims.get("raw_user_meta_data") or {}
                company = repo.ensure_first_company(
                    user_uuid(user),
                    user.email,
                    metadata.get("full_name"),
                    metadata.get("initial_company_name") or metadata.get("company_name"),
                )
                companies = [company]
        else:
            for company in companies:
                repo.ensure_launch_ai_ledgers(company.id)
    except SQLAlchemyError as exc:
        logger.warning("Database unavailable while loading companies", exc_info=exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Unable to load companies. Please try again.",
        ) from exc
    return [CompanyResponse.model_validate(company) for company in companies]


@router.post("/companies", response_model=CompanyResponse)
def create_first_company(
    payload: CompanyCreate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> CompanyResponse:
    metadata = user.claims.get("user_metadata") or user.claims.get("raw_user_meta_data") or {}
    try:
        company = AccountingRepository(db).create_company(
            user_uuid(user),
            user.email,
            metadata.get("full_name"),
            payload.legal_name,
            payload.trade_name,
            payload.gstin,
            payload.state_code,
        )
    except SQLAlchemyError as exc:
        logger.warning("Database unavailable while creating company", exc_info=exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Unable to create company. Please try again.",
        ) from exc
    return CompanyResponse.model_validate(company)


@router.post("/companies/{company_id}/access-requests", response_model=AccessRequestResponse)
def request_company_access(
    company_id: UUID,
    payload: AccessRequestCreate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AccessRequestResponse:
    metadata = user.claims.get("user_metadata") or user.claims.get("raw_user_meta_data") or {}
    try:
        request = AccountingRepository(db).request_company_access(
            company_id,
            user_uuid(user),
            user.email,
            payload.requested_role,
            metadata.get("full_name"),
        )
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    return access_request_response(request)


@router.get("/companies/{company_id}/access-requests", response_model=list[AccessRequestResponse])
def list_company_access_requests(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[AccessRequestResponse]:
    try:
        requests = AccountingRepository(db).list_access_requests(company_id, user_uuid(user))
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    return [access_request_response(request) for request in requests]


@router.patch("/companies/{company_id}/access-requests/{request_id}", response_model=AccessRequestResponse)
def decide_company_access_request(
    company_id: UUID,
    request_id: UUID,
    payload: AccessRequestDecision,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AccessRequestResponse:
    try:
        request = AccountingRepository(db).decide_access_request(
            company_id,
            request_id,
            user_uuid(user),
            payload.decision,
            payload.role,
        )
    except PermissionError as exc:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(exc)) from exc
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    return access_request_response(request)


@router.post("/demo/company", response_model=DemoCompanyResponse)
def create_demo_company(
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> DemoCompanyResponse:
    metadata = user.claims.get("user_metadata") or user.claims.get("raw_user_meta_data") or {}
    try:
        result = AccountingRepository(db).create_demo_company(
            user_uuid(user),
            user.email,
            metadata.get("full_name"),
        )
    except SQLAlchemyError as exc:
        logger.warning("Database unavailable while creating demo company", exc_info=exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Unable to create company. Please try again.",
        ) from exc
    return DemoCompanyResponse(
        company_id=result.company_id,
        legal_name=result.legal_name,
        seeded_ledgers=result.seeded_ledgers,
        seeded_vouchers=result.seeded_vouchers,
        seeded_invoices=result.seeded_invoices,
        seeded_bank_transactions=result.seeded_bank_transactions,
    )


@router.get("/companies/{company_id}/ledger-groups", response_model=list[LedgerGroupResponse])
def list_ledger_groups(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[LedgerGroupResponse]:
    repo = repo_for_company(company_id, user, db)
    return [LedgerGroupResponse.model_validate(group) for group in repo.list_groups(company_id)]


@router.get("/companies/{company_id}/debug-counts", response_model=DebugCountsResponse)
def debug_counts(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> DebugCountsResponse:
    repo_for_company(company_id, user, db)
    journal_line_count = db.scalar(
        select(func.count(JournalEntry.id)).where(JournalEntry.company_id == company_id)
    ) or 0
    physical_voucher_lines = 0
    if inspect(db.bind).has_table("voucher_lines"):
        physical_voucher_lines = db.execute(
            text("select count(*) from voucher_lines where company_id = :company_id"),
            {"company_id": str(company_id)},
        ).scalar_one()

    return DebugCountsResponse(
        vouchers=db.scalar(select(func.count(Voucher.id)).where(Voucher.company_id == company_id)) or 0,
        voucher_lines=int(journal_line_count) + int(physical_voucher_lines),
        invoices=db.scalar(select(func.count(Invoice.id)).where(Invoice.company_id == company_id)) or 0,
        accounting_entries=db.scalar(
            select(func.count(AccountingEntry.id)).where(AccountingEntry.company_id == company_id)
        ) or 0,
        audit_logs=db.scalar(select(func.count(AuditLog.id)).where(AuditLog.company_id == company_id)) or 0,
        ai_logs=db.scalar(select(func.count(AiLog.id)).where(AiLog.company_id == company_id)) or 0,
    )


@router.post("/companies/{company_id}/ledger-groups", response_model=LedgerGroupResponse)
def create_ledger_group(
    company_id: UUID,
    payload: LedgerGroupCreate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> LedgerGroupResponse:
    repo = repo_for_company(company_id, user, db)
    return LedgerGroupResponse.model_validate(
        repo.create_group(company_id, user_uuid(user), payload.name, payload.account_nature)
    )


@router.get("/companies/{company_id}/ledgers", response_model=list[LedgerResponse])
def list_ledgers(
    company_id: UUID,
    search: str | None = None,
    nature: AccountNature | None = None,
    category: LedgerCategory | None = None,
    include_inactive: bool = False,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[LedgerResponse]:
    repo = repo_for_company(company_id, user, db)
    return [
        ledger_response(row)
        for row in repo.list_ledgers(company_id, search, nature, category, include_inactive)
    ]


@router.post("/companies/{company_id}/ledgers", response_model=LedgerResponse)
def create_ledger(
    company_id: UUID,
    payload: LedgerCreate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> LedgerResponse:
    repo = repo_for_company(company_id, user, db)
    ledger = repo.create_ledger(company_id, user_uuid(user), payload)
    row = db.execute(repo.ledger_query(company_id).where(ledger.__class__.id == ledger.id)).one()
    return ledger_response(row)


@router.patch("/companies/{company_id}/ledgers/{ledger_id}", response_model=LedgerResponse)
def update_ledger(
    company_id: UUID,
    ledger_id: UUID,
    payload: LedgerUpdate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> LedgerResponse:
    repo = repo_for_company(company_id, user, db)
    try:
        ledger = repo.update_ledger(company_id, user_uuid(user), ledger_id, payload)
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    row = db.execute(repo.ledger_query(company_id).where(ledger.__class__.id == ledger.id)).one()
    return ledger_response(row)


@router.delete("/companies/{company_id}/ledgers/{ledger_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_ledger(
    company_id: UUID,
    ledger_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> Response:
    repo = repo_for_company(company_id, user, db)
    try:
        repo.delete_ledger(company_id, user_uuid(user), ledger_id)
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/companies/{company_id}/vouchers", response_model=list[VoucherResponse])
def list_vouchers(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[VoucherResponse]:
    repo = repo_for_company(company_id, user, db)
    return [voucher_response(voucher) for voucher in repo.list_vouchers(company_id)]


@router.post("/companies/{company_id}/vouchers", response_model=VoucherResponse)
def create_voucher(
    company_id: UUID,
    payload: VoucherCreate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> VoucherResponse:
    repo = repo_for_company(company_id, user, db)
    try:
        voucher = repo.create_voucher(company_id, user_uuid(user), payload)
    except AccountingValidationError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except SQLAlchemyError as exc:
        logger.warning("Database unavailable while creating voucher", exc_info=exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Voucher could not be created. Please try again.",
        ) from exc
    return voucher_response(voucher)


@router.get("/companies/{company_id}/audit-events", response_model=list[AuditEventResponse])
def list_audit_events(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[AuditEventResponse]:
    repo = repo_for_company(company_id, user, db)
    return [
        AuditEventResponse(
            id=event.id,
            created_by=event.actor_id,
            updated_by=event.actor_id,
            created_at=event.created_at,
            updated_at=None,
            action_type=event.event_type,
            entity_type="voucher",
            entity_id=event.voucher_id,
            summary=str(event.event_payload.get("voucher_number", event.event_type)),
        )
        for event in repo.list_audit_events(company_id)
    ]


@router.get("/companies/{company_id}/reports/trial-balance", response_model=list[TrialBalanceRow])
def trial_balance(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[TrialBalanceRow]:
    repo = repo_for_company(company_id, user, db)
    rows: list[TrialBalanceRow] = []
    for ledger, debit, credit in repo.ledger_balances(company_id):
        opening = money(ledger.opening_balance)
        debit_total = debit + (opening if ledger.opening_balance_type == "dr" else Decimal("0.00"))
        credit_total = credit + (opening if ledger.opening_balance_type == "cr" else Decimal("0.00"))
        balance = money(debit_total - credit_total)
        rows.append(
            TrialBalanceRow(
                ledger_id=ledger.id,
                ledger_name=ledger.name,
                account_nature=ledger.account_nature,
                category=ledger.category,
                debit=balance if balance > 0 else Decimal("0.00"),
                credit=abs(balance) if balance < 0 else Decimal("0.00"),
            )
        )
    return rows


@router.get("/companies/{company_id}/reports/profit-and-loss", response_model=ProfitAndLossResponse)
def profit_and_loss(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> ProfitAndLossResponse:
    rows = trial_balance(company_id, user, db)
    revenue = money(sum((row.credit - row.debit for row in rows if row.account_nature == AccountNature.income), Decimal("0.00")))
    expenses = money(sum((row.debit - row.credit for row in rows if row.account_nature == AccountNature.expense), Decimal("0.00")))
    return ProfitAndLossResponse(revenue=revenue, expenses=expenses, profit=money(revenue - expenses))


@router.get("/companies/{company_id}/reports/balance-sheet", response_model=BalanceSheetResponse)
def balance_sheet(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> BalanceSheetResponse:
    rows = trial_balance(company_id, user, db)
    pnl = profit_and_loss(company_id, user, db)
    assets = money(sum((row.debit - row.credit for row in rows if row.account_nature == AccountNature.asset), Decimal("0.00")))
    liabilities = money(sum((row.credit - row.debit for row in rows if row.account_nature == AccountNature.liability), Decimal("0.00")))
    equity = money(sum((row.credit - row.debit for row in rows if row.account_nature == AccountNature.equity), Decimal("0.00")) + pnl.profit)
    return BalanceSheetResponse(
        assets=assets,
        liabilities=liabilities,
        equity=equity,
        check_difference=money(assets - liabilities - equity),
    )


@router.get("/companies/{company_id}/reports/cash-flow", response_model=CashFlowResponse)
def cash_flow(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> CashFlowResponse:
    rows = trial_balance(company_id, user, db)
    cash = money(
        sum(
            (
                row.debit - row.credit
                for row in rows
                if row.category in {LedgerCategory.cash, LedgerCategory.bank}
            ),
            Decimal("0.00"),
        )
    )
    return CashFlowResponse(
        operating_cash_flow=cash,
        investing_cash_flow=Decimal("0.00"),
        financing_cash_flow=Decimal("0.00"),
        net_cash_flow=cash,
    )


@router.get("/companies/{company_id}/reports/dashboard", response_model=DashboardMetrics)
def dashboard_metrics(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> DashboardMetrics:
    rows = trial_balance(company_id, user, db)
    pnl = profit_and_loss(company_id, user, db)
    cash_position = money(
        sum(
            (
                row.debit - row.credit
                for row in rows
                if row.category in {LedgerCategory.cash, LedgerCategory.bank}
            ),
            Decimal("0.00"),
        )
    )
    receivables = money(
        sum(
            (row.debit - row.credit for row in rows if row.category == LedgerCategory.sundry_debtor),
            Decimal("0.00"),
        )
    )
    payables = money(
        sum(
            (
                row.credit - row.debit
                for row in rows
                if row.category == LedgerCategory.sundry_creditor
            ),
            Decimal("0.00"),
        )
    )
    return DashboardMetrics(
        revenue=pnl.revenue,
        expenses=pnl.expenses,
        profit=pnl.profit,
        cash_position=cash_position,
        receivables=receivables,
        payables=payables,
    )


@router.post("/companies/{company_id}/invoices", response_model=InvoiceResponse)
def create_invoice(
    company_id: UUID,
    payload: InvoiceCreate,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> InvoiceResponse:
    repo = repo_for_company(company_id, user, db)
    try:
        return invoice_response(repo.create_invoice(company_id, user_uuid(user), payload))
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    except SQLAlchemyError as exc:
        logger.warning("Database unavailable while creating invoice", exc_info=exc)
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Invoice could not be created. Please try again.",
        ) from exc


@router.get("/companies/{company_id}/invoices", response_model=list[InvoiceResponse])
def list_invoices(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[InvoiceResponse]:
    repo = repo_for_company(company_id, user, db)
    return [invoice_response(invoice) for invoice in repo.list_invoices(company_id)]


@router.get("/companies/{company_id}/invoices/{invoice_id}", response_model=InvoiceResponse)
def get_invoice(
    company_id: UUID,
    invoice_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> InvoiceResponse:
    repo = repo_for_company(company_id, user, db)
    try:
        return invoice_response(repo.get_invoice(company_id, invoice_id))
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.get("/companies/{company_id}/invoices/{invoice_id}/pdf")
def invoice_pdf(
    company_id: UUID,
    invoice_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> Response:
    repo = repo_for_company(company_id, user, db)
    try:
        invoice = repo.get_invoice(company_id, invoice_id)
    except LookupError:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Invoice not found.")
    pdf = make_invoice_pdf(invoice)
    return Response(
        content=pdf,
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{invoice.invoice_number}.pdf"'},
    )


@router.get("/companies/{company_id}/gst/rates", response_model=list[GstRateResponse])
def gst_rates(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[GstRateResponse]:
    repo = repo_for_company(company_id, user, db)
    rates = repo.gst_rates(company_id)
    if not rates:
        return [
            GstRateResponse(label="GST 0%", rate=Decimal("0.00")),
            GstRateResponse(label="GST 5%", rate=Decimal("5.00")),
            GstRateResponse(label="GST 12%", rate=Decimal("12.00")),
            GstRateResponse(label="GST 18%", rate=Decimal("18.00")),
            GstRateResponse(label="GST 28%", rate=Decimal("28.00")),
        ]
    return [GstRateResponse(label=rate.label, rate=rate.rate) for rate in rates]


@router.get("/companies/{company_id}/reports/gst", response_model=GstReportResponse)
def gst_report(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> GstReportResponse:
    rows = trial_balance(company_id, user, db)
    input_gst = money(
        sum(
            (row.debit - row.credit for row in rows if row.category == LedgerCategory.input_gst),
            Decimal("0.00"),
        )
    )
    output_gst = money(
        sum(
            (row.credit - row.debit for row in rows if row.category == LedgerCategory.output_gst),
            Decimal("0.00"),
        )
    )
    return GstReportResponse(input_gst=input_gst, output_gst=output_gst, net_payable=money(output_gst - input_gst))


@router.get("/companies/{company_id}/reports/gst/invoices", response_model=list[InvoiceGstSummaryRow])
def invoice_wise_gst_report(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[InvoiceGstSummaryRow]:
    repo = repo_for_company(company_id, user, db)
    rows: list[InvoiceGstSummaryRow] = []
    for invoice in repo.list_invoices(company_id):
        rows.append(
            InvoiceGstSummaryRow(
                invoice_id=invoice.id,
                invoice_number=invoice.invoice_number,
                invoice_type=invoice.invoice_type,
                invoice_date=invoice.invoice_date,
                party_ledger_name=invoice.party_ledger.name if invoice.party_ledger else "Party ledger",
                taxable_value=invoice.taxable_value,
                cgst_amount=invoice.cgst_amount,
                sgst_amount=invoice.sgst_amount,
                igst_amount=invoice.igst_amount,
                total_amount=invoice.total_amount,
            )
        )
    return rows


@router.get("/companies/{company_id}/reports/gstr-1.csv")
@router.get("/companies/{company_id}/reports/gstr1.csv")
def gstr_1_draft_csv(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> Response:
    repo = repo_for_company(company_id, user, db)
    output = StringIO()
    writer = csv.writer(output)
    writer.writerow(
        [
            "GSTIN/UIN of Recipient",
            "Receiver Name",
            "Invoice Number",
            "Invoice Date",
            "Invoice Value",
            "Place Of Supply",
            "Reverse Charge",
            "Invoice Type",
            "Rate",
            "Taxable Value",
            "CGST",
            "SGST",
            "IGST",
        ]
    )
    for invoice in repo.list_invoices(company_id):
        if invoice.invoice_type != InvoiceType.sales:
            continue
        party_name = invoice.party_ledger.name if invoice.party_ledger else "Customer"
        for line in sorted(invoice.lines, key=lambda item: item.line_number):
            writer.writerow(
                [
                    getattr(invoice.party_ledger, "gstin", None) or "",
                    party_name,
                    invoice.invoice_number,
                    invoice.invoice_date.isoformat(),
                    invoice.total_amount,
                    getattr(invoice.party_ledger, "state_code", None) or "",
                    "N",
                    "Regular",
                    line.gst_rate,
                    line.taxable_value,
                    line.cgst_amount,
                    line.sgst_amount,
                    line.igst_amount,
                ]
            )
    return csv_response(output.getvalue(), "gstr1-draft.csv")


@router.get("/companies/{company_id}/reports/gstr-3b.csv")
@router.get("/companies/{company_id}/reports/gstr3b.csv")
def gstr_3b_draft_csv(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> Response:
    repo = repo_for_company(company_id, user, db)
    sales_taxable = Decimal("0.00")
    purchase_taxable = Decimal("0.00")
    output_cgst = Decimal("0.00")
    output_sgst = Decimal("0.00")
    output_igst = Decimal("0.00")
    input_cgst = Decimal("0.00")
    input_sgst = Decimal("0.00")
    input_igst = Decimal("0.00")
    for invoice in repo.list_invoices(company_id):
        if invoice.invoice_type == InvoiceType.sales:
            sales_taxable += money(invoice.taxable_value)
            output_cgst += money(invoice.cgst_amount)
            output_sgst += money(invoice.sgst_amount)
            output_igst += money(invoice.igst_amount)
        elif invoice.invoice_type == InvoiceType.purchase:
            purchase_taxable += money(invoice.taxable_value)
            input_cgst += money(invoice.cgst_amount)
            input_sgst += money(invoice.sgst_amount)
            input_igst += money(invoice.igst_amount)
    output = StringIO()
    writer = csv.writer(output)
    writer.writerow(["Section", "Description", "Taxable Value", "CGST", "SGST", "IGST", "Total Tax"])
    writer.writerow(
        [
            "3.1(a)",
            "Outward taxable supplies",
            money(sales_taxable),
            money(output_cgst),
            money(output_sgst),
            money(output_igst),
            money(output_cgst + output_sgst + output_igst),
        ]
    )
    writer.writerow(
        [
            "4(A)",
            "Eligible ITC from inward supplies",
            money(purchase_taxable),
            money(input_cgst),
            money(input_sgst),
            money(input_igst),
            money(input_cgst + input_sgst + input_igst),
        ]
    )
    writer.writerow(
        [
            "Net",
            "Draft net GST payable",
            money(sales_taxable - purchase_taxable),
            money(output_cgst - input_cgst),
            money(output_sgst - input_sgst),
            money(output_igst - input_igst),
            money((output_cgst + output_sgst + output_igst) - (input_cgst + input_sgst + input_igst)),
        ]
    )
    return csv_response(output.getvalue(), "gstr3b-draft.csv")


@router.post("/calculators/tds", response_model=TdsCalculatorResponse)
def tds_calculator(payload: TdsCalculatorRequest) -> TdsCalculatorResponse:
    tds_amount = money(payload.amount * payload.rate_percent / Decimal("100"))
    return TdsCalculatorResponse(
        taxable_amount=money(payload.amount),
        rate_percent=payload.rate_percent,
        tds_amount=tds_amount,
        net_payable=money(payload.amount - tds_amount),
    )


@router.post("/calculators/pf", response_model=PfCalculatorResponse)
def pf_calculator(payload: PfCalculatorRequest) -> PfCalculatorResponse:
    eligible_wage = money(min(payload.monthly_basic_wage, payload.wage_ceiling))
    employee = money(eligible_wage * payload.employee_rate_percent / Decimal("100"))
    employer = money(eligible_wage * payload.employer_rate_percent / Decimal("100"))
    return PfCalculatorResponse(
        eligible_wage=eligible_wage,
        employee_contribution=employee,
        employer_contribution=employer,
        total_contribution=money(employee + employer),
    )


@router.post("/calculators/esic", response_model=EsicCalculatorResponse)
def esic_calculator(payload: EsicCalculatorRequest) -> EsicCalculatorResponse:
    eligible = payload.monthly_gross_wage <= payload.wage_limit
    eligible_wage = money(payload.monthly_gross_wage if eligible else Decimal("0.00"))
    employee = money(eligible_wage * payload.employee_rate_percent / Decimal("100"))
    employer = money(eligible_wage * payload.employer_rate_percent / Decimal("100"))
    return EsicCalculatorResponse(
        eligible=eligible,
        eligible_wage=eligible_wage,
        employee_contribution=employee,
        employer_contribution=employer,
        total_contribution=money(employee + employer),
    )


@router.get("/companies/{company_id}/reports/ledger-scrutiny", response_model=LedgerScrutinyResponse)
def ledger_scrutiny(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> LedgerScrutinyResponse:
    repo = repo_for_company(company_id, user, db)
    issues: list[LedgerScrutinyIssue] = []
    for voucher in repo.list_vouchers(company_id):
        debit = money(sum((line.debit for line in voucher.entries), Decimal("0.00")))
        credit = money(sum((line.credit for line in voucher.entries), Decimal("0.00")))
        if debit != credit:
            issues.append(
                LedgerScrutinyIssue(
                    severity="high",
                    title="Unbalanced voucher",
                    detail=f"{voucher.voucher_number} has debit {debit} and credit {credit}.",
                    amount=money(abs(debit - credit)),
                )
            )
        if len(voucher.entries) < 2:
            issues.append(
                LedgerScrutinyIssue(
                    severity="high",
                    title="Voucher has fewer than two lines",
                    detail=f"{voucher.voucher_number} needs complete double-entry lines.",
                    amount=None,
                )
            )
    seen_invoices: dict[tuple[str, str, Decimal], str] = {}
    for invoice in repo.list_invoices(company_id):
        party_name = invoice.party_ledger.name if invoice.party_ledger else "Party"
        duplicate_key = (invoice.invoice_number.strip().lower(), party_name.lower(), money(invoice.total_amount))
        if duplicate_key in seen_invoices:
            issues.append(
                LedgerScrutinyIssue(
                    severity="warning",
                    title="Possible duplicate invoice",
                    detail=f"{invoice.invoice_number} matches {seen_invoices[duplicate_key]} for {party_name}.",
                    amount=money(invoice.total_amount),
                )
            )
        else:
            seen_invoices[duplicate_key] = invoice.invoice_number
        if money(invoice.cgst_amount + invoice.sgst_amount + invoice.igst_amount) == 0 and money(invoice.taxable_value) > 0:
            issues.append(
                LedgerScrutinyIssue(
                    severity="warning",
                    title="Invoice has no GST amount",
                    detail=f"{invoice.invoice_number} has taxable value but no GST. Verify exemption or GST rate.",
                    amount=money(invoice.taxable_value),
                )
            )
    for ledger, debit, credit in repo.ledger_balances(company_id):
        balance = money(debit - credit)
        if ledger.category in {LedgerCategory.cash, LedgerCategory.bank} and balance < 0:
            issues.append(
                LedgerScrutinyIssue(
                    severity="high",
                    title="Negative cash or bank balance",
                    detail=f"{ledger.name} shows negative balance.",
                    amount=abs(balance),
                )
            )
        if debit == 0 and credit == 0 and not ledger.is_system:
            issues.append(
                LedgerScrutinyIssue(
                    severity="info",
                    title="Ledger has no activity",
                    detail=f"{ledger.name} has no debit or credit movement.",
                    amount=None,
                )
            )
    unreconciled = [
        item for item in repo.list_bank_transactions(company_id)
        if item.reconciliation_status.value in {"unmatched", "suggested_match"}
    ]
    if unreconciled:
        issues.append(
            LedgerScrutinyIssue(
                severity="warning",
                title="Unreconciled bank transactions",
                detail=f"{len(unreconciled)} bank transactions still need reconciliation.",
                amount=money(sum((item.debit if item.debit > 0 else item.credit for item in unreconciled), Decimal("0.00"))),
            )
        )
    return LedgerScrutinyResponse(
        issue_count=len(issues),
        high_risk_count=sum(1 for issue in issues if issue.severity == "high"),
        warning_count=sum(1 for issue in issues if issue.severity == "warning"),
        issues=issues,
    )


def csv_response(content: str, filename: str) -> Response:
    return Response(
        content=content,
        media_type="text/csv",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


def make_simple_pdf(text: str) -> bytes:
    safe_text = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    stream = f"BT /F1 12 Tf 72 760 Td ({safe_text}) Tj ET"
    objects = [
        "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj",
        "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj",
        "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj",
        "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj",
        f"5 0 obj << /Length {len(stream)} >> stream\n{stream}\nendstream endobj",
    ]
    body = "%PDF-1.4\n" + "\n".join(objects) + "\n"
    offsets = [0]
    cursor = len("%PDF-1.4\n")
    for obj in objects:
        offsets.append(cursor)
        cursor += len(obj) + 1
    xref_at = len(body)
    xref = "xref\n0 6\n0000000000 65535 f \n" + "".join(
        f"{offset:010d} 00000 n \n" for offset in offsets[1:]
    )
    trailer = f"trailer << /Size 6 /Root 1 0 R >>\nstartxref\n{xref_at}\n%%EOF"
    return (body + xref + trailer).encode("latin-1")


def make_invoice_pdf(invoice: Invoice) -> bytes:
    party_name = invoice.party_ledger.name if invoice.party_ledger else "Customer / Vendor"
    lines = [
        "ANVRITAI",
        "ABHAY Accounting OS",
        "Professional GST Invoice",
        f"Invoice Number: {invoice.invoice_number}",
        f"Invoice Date: {invoice.invoice_date}",
        f"Due Date: {invoice.due_date or '-'}",
        f"Party: {party_name}",
        f"Voucher ID: {invoice.voucher_id or '-'}",
        "",
        "Items",
    ]
    for item in sorted(invoice.lines, key=lambda entry: entry.line_number):
        lines.append(
            f"{item.description} | HSN/SAC {item.hsn_sac or '-'} | Qty {item.quantity} {item.unit} | "
            f"Rate INR {item.unit_price} | GST {item.gst_rate}% | Total INR {item.total_amount}"
        )
    lines.extend(
        [
            "",
            f"Taxable Value: INR {invoice.taxable_value}",
            f"CGST: INR {invoice.cgst_amount}",
            f"SGST: INR {invoice.sgst_amount}",
            f"IGST: INR {invoice.igst_amount}",
            f"Total Amount: INR {invoice.total_amount}",
        ]
    )
    return make_multiline_pdf(lines)


def make_multiline_pdf(lines: list[str]) -> bytes:
    commands = ["BT /F1 11 Tf 50 790 Td"]
    for index, line in enumerate(lines):
        safe = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
        if index == 0:
            commands.append(f"({safe}) Tj")
        else:
            commands.append(f"0 -18 Td ({safe}) Tj")
    commands.append("ET")
    stream = "\n".join(commands)
    objects = [
        "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj",
        "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj",
        "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj",
        "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj",
        f"5 0 obj << /Length {len(stream)} >> stream\n{stream}\nendstream endobj",
    ]
    body = "%PDF-1.4\n" + "\n".join(objects) + "\n"
    offsets = [0]
    cursor = len("%PDF-1.4\n")
    for obj in objects:
        offsets.append(cursor)
        cursor += len(obj) + 1
    xref_at = len(body)
    xref = "xref\n0 6\n0000000000 65535 f \n" + "".join(
        f"{offset:010d} 00000 n \n" for offset in offsets[1:]
    )
    trailer = f"trailer << /Size 6 /Root 1 0 R >>\nstartxref\n{xref_at}\n%%EOF"
    return (body + xref + trailer).encode("latin-1", errors="replace")
