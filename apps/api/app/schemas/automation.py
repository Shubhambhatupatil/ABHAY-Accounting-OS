from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, Field

from app.schemas.accounting import BalanceSheetResponse, CashFlowResponse, GstReportResponse, ProfitAndLossResponse, TrialBalanceRow
from app.schemas.financial_intelligence import OwnerInsight


class WhatsAppAccountingRequest(BaseModel):
    message: str = Field(min_length=2, max_length=800)


class BankAutoVoucherRequest(BaseModel):
    bank_transaction_id: UUID


class MonthEndClosePack(BaseModel):
    trial_balance: list[TrialBalanceRow]
    profit_and_loss: ProfitAndLossResponse
    balance_sheet: BalanceSheetResponse
    cash_flow: CashFlowResponse
    gst_summary: GstReportResponse


class AiCfoDashboard(BaseModel):
    profit_forecast: Decimal
    cash_runway_days: int | None
    expense_warnings: list[str]
    receivable_risk: str
    gst_risk: str
    business_health_score: int
    alerts: list[OwnerInsight]


class AutomationSummary(BaseModel):
    business_health_score: int
    open_ai_suggestions: int
    unreconciled_bank_transactions: int
    active_alerts: int
    cfo: AiCfoDashboard


class AutoCategorizationSuggestion(BaseModel):
    description: str
    suggested_ledger_name: str
    confidence: Decimal
    reason: str
