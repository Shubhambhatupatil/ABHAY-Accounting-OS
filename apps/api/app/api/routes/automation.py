from datetime import date
from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.routes.accounting import balance_sheet, cash_flow, gst_report, profit_and_loss, repo_for_company, trial_balance
from app.api.routes.ai_accountant import create_ai_suggestion
from app.api.routes.financial_intelligence import load_rows
from app.core.config import Settings, get_settings
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.financial_intelligence import cashflow_intelligence, gst_intelligence, profit_intelligence
from app.domain.automation.intelligence import automation_alerts, business_health_score, cash_runway_days
from app.models.accounting import AiSuggestion, AiSuggestionStatus, BankTransaction, ReconciliationStatus
from app.repositories.accounting import AccountingRepository
from app.schemas.ai_accountant import AiVoucherSuggestionResponse, NaturalLanguageEntryRequest
from app.schemas.automation import (
    AiCfoDashboard,
    AutoCategorizationSuggestion,
    AutomationSummary,
    BankAutoVoucherRequest,
    MonthEndClosePack,
    WhatsAppAccountingRequest,
)

router = APIRouter(prefix="/companies/{company_id}/automation", tags=["automation"])


@router.post("/bank-auto-voucher", response_model=AiVoucherSuggestionResponse)
async def bank_auto_voucher(
    company_id: UUID,
    payload: BankAutoVoucherRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiVoucherSuggestionResponse:
    repo = repo_for_company(company_id, user, db)
    transaction = db.scalar(
        select(BankTransaction).where(
            BankTransaction.company_id == company_id,
            BankTransaction.id == payload.bank_transaction_id,
        )
    )
    if transaction is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Bank transaction not found.")
    direction = "Paid" if transaction.debit > 0 else "Received"
    amount = transaction.debit if transaction.debit > 0 else transaction.credit
    request = NaturalLanguageEntryRequest(
        text=f"{direction} {transaction.description} INR {amount} bank",
        transaction_date=transaction.transaction_date,
        language="bank_statement",
    )
    return await create_ai_suggestion(company_id, request, user, db, repo, settings)


@router.post("/whatsapp-entry", response_model=AiVoucherSuggestionResponse)
async def whatsapp_accounting(
    company_id: UUID,
    payload: WhatsAppAccountingRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiVoucherSuggestionResponse:
    repo = repo_for_company(company_id, user, db)
    request = NaturalLanguageEntryRequest(text=payload.message, language="whatsapp")
    return await create_ai_suggestion(company_id, request, user, db, repo, settings)


@router.get("/month-end-close", response_model=MonthEndClosePack)
def month_end_close(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> MonthEndClosePack:
    repo_for_company(company_id, user, db)
    return MonthEndClosePack(
        trial_balance=trial_balance(company_id, user, db),
        profit_and_loss=profit_and_loss(company_id, user, db),
        balance_sheet=balance_sheet(company_id, user, db),
        cash_flow=cash_flow(company_id, user, db),
        gst_summary=gst_report(company_id, user, db),
    )


@router.get("/cfo-dashboard", response_model=AiCfoDashboard)
def cfo_dashboard(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiCfoDashboard:
    period, current_rows, previous_rows = load_rows(company_id, month, user, db)
    profit = profit_intelligence(current_rows, previous_rows, period)
    cashflow = cashflow_intelligence(current_rows, period)
    gst = gst_intelligence(current_rows, period)
    alerts = automation_alerts(profit, cashflow, gst)
    score = business_health_score(profit, cashflow, gst)
    runway = cash_runway_days(cashflow.cash_position, profit.expenses)
    expense_warnings = [alert.message for alert in alerts if "expense" in alert.title.lower()]
    return AiCfoDashboard(
        profit_forecast=profit.profit,
        cash_runway_days=runway,
        expense_warnings=expense_warnings,
        receivable_risk="high" if cashflow.receivables > cashflow.cash_position else "low",
        gst_risk="high" if gst.current_gst_payable > cashflow.cash_position and gst.current_gst_payable > 0 else "low",
        business_health_score=score,
        alerts=alerts,
    )


@router.get("/alerts", response_model=list)
def ai_alerts(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
):
    return cfo_dashboard(company_id, month, user, db).alerts


@router.get("/summary", response_model=AutomationSummary)
def automation_summary(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AutomationSummary:
    repo_for_company(company_id, user, db)
    cfo = cfo_dashboard(company_id, month, user, db)
    open_suggestions = db.scalar(
        select(func.count(AiSuggestion.id)).where(
            AiSuggestion.company_id == company_id,
            AiSuggestion.status == AiSuggestionStatus.needs_review,
        )
    ) or 0
    unreconciled = db.scalar(
        select(func.count(BankTransaction.id)).where(
            BankTransaction.company_id == company_id,
            BankTransaction.reconciliation_status.in_(
                [ReconciliationStatus.unmatched, ReconciliationStatus.suggested_match]
            ),
        )
    ) or 0
    return AutomationSummary(
        business_health_score=cfo.business_health_score,
        open_ai_suggestions=open_suggestions,
        unreconciled_bank_transactions=unreconciled,
        active_alerts=len(cfo.alerts),
        cfo=cfo,
    )


@router.get("/auto-categorization", response_model=list[AutoCategorizationSuggestion])
def auto_categorization(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[AutoCategorizationSuggestion]:
    repo: AccountingRepository = repo_for_company(company_id, user, db)
    ledgers = [row[0] for row in repo.list_ledgers(company_id, None, None, None, False)]
    transactions = [
        item
        for item in repo.list_bank_transactions(company_id)
        if item.reconciliation_status in {ReconciliationStatus.unmatched, ReconciliationStatus.suggested_match}
    ]
    suggestions: list[AutoCategorizationSuggestion] = []
    for transaction in transactions[:20]:
        description = transaction.description.lower()
        matched = next((ledger for ledger in ledgers if ledger.name.lower() in description), None)
        if matched is None and "diesel" in description:
            matched = next((ledger for ledger in ledgers if "diesel" in ledger.name.lower()), None)
        if matched is None:
            matched = next((ledger for ledger in ledgers if ledger.category.value in description), None)
        if matched:
            suggestions.append(
                AutoCategorizationSuggestion(
                    description=transaction.description,
                    suggested_ledger_name=matched.name,
                    confidence=Decimal("0.78"),
                    reason="Matched from previous ledger names and transaction description.",
                )
            )
    return suggestions
