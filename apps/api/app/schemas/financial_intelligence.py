from datetime import date
from decimal import Decimal

from pydantic import BaseModel


class IntelligencePeriod(BaseModel):
    month: date
    starts_on: date
    ends_on: date


class ProfitIntelligence(BaseModel):
    period: IntelligencePeriod
    revenue: Decimal
    expenses: Decimal
    profit: Decimal
    profit_margin: Decimal
    previous_month_profit: Decimal
    profit_trend_percent: Decimal | None


class CashFlowIntelligence(BaseModel):
    period: IntelligencePeriod
    cash_position: Decimal
    receivables: Decimal
    payables: Decimal
    net_cash_flow: Decimal
    cash_risk_level: str
    cash_risk_reason: str


class GstIntelligence(BaseModel):
    period: IntelligencePeriod
    gst_collected: Decimal
    gst_input_paid: Decimal
    current_gst_payable: Decimal
    estimated_month_end_liability: Decimal


class OwnerInsight(BaseModel):
    title: str
    severity: str
    message: str
    metric_value: Decimal | None = None


class FinancialIntelligenceSummary(BaseModel):
    profit: ProfitIntelligence
    cashflow: CashFlowIntelligence
    gst: GstIntelligence
    insights: list[OwnerInsight]

