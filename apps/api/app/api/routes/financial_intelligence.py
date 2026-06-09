from datetime import date
from uuid import UUID

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.api.routes.accounting import repo_for_company
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.financial_intelligence import (
    cashflow_intelligence,
    generate_insights,
    gst_intelligence,
    month_period,
    previous_month,
    profit_intelligence,
    summary,
)
from app.schemas.financial_intelligence import (
    CashFlowIntelligence,
    FinancialIntelligenceSummary,
    GstIntelligence,
    OwnerInsight,
    ProfitIntelligence,
)

router = APIRouter(
    prefix="/companies/{company_id}/financial-intelligence",
    tags=["financial-intelligence"],
)


def load_rows(company_id: UUID, selected_month: date | None, user: AuthenticatedUser, db: Session):
    repo = repo_for_company(company_id, user, db)
    period = month_period(selected_month)
    previous_period = month_period(previous_month(period.starts_on))
    current_rows = repo.monthly_ledger_balances(company_id, period.starts_on, period.ends_on)
    previous_rows = repo.monthly_ledger_balances(
        company_id,
        previous_period.starts_on,
        previous_period.ends_on,
    )
    return period, current_rows, previous_rows


@router.get("/summary", response_model=FinancialIntelligenceSummary)
def financial_summary(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> FinancialIntelligenceSummary:
    period, current_rows, previous_rows = load_rows(company_id, month, user, db)
    return summary(current_rows, previous_rows, period)


@router.get("/profit", response_model=ProfitIntelligence)
def profit(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> ProfitIntelligence:
    period, current_rows, previous_rows = load_rows(company_id, month, user, db)
    return profit_intelligence(current_rows, previous_rows, period)


@router.get("/cashflow", response_model=CashFlowIntelligence)
def cashflow(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> CashFlowIntelligence:
    period, current_rows, _ = load_rows(company_id, month, user, db)
    return cashflow_intelligence(current_rows, period)


@router.get("/gst", response_model=GstIntelligence)
def gst(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> GstIntelligence:
    period, current_rows, _ = load_rows(company_id, month, user, db)
    return gst_intelligence(current_rows, period)


@router.get("/insights", response_model=list[OwnerInsight])
def insights(
    company_id: UUID,
    month: date | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[OwnerInsight]:
    period, current_rows, previous_rows = load_rows(company_id, month, user, db)
    profit_result = profit_intelligence(current_rows, previous_rows, period)
    cashflow_result = cashflow_intelligence(current_rows, period)
    gst_result = gst_intelligence(current_rows, period)
    return generate_insights(profit_result, cashflow_result, gst_result, current_rows)
