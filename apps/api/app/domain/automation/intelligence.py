from decimal import Decimal

from app.domain.accounting.engine import money
from app.schemas.financial_intelligence import CashFlowIntelligence, GstIntelligence, OwnerInsight, ProfitIntelligence


def business_health_score(
    profit: ProfitIntelligence,
    cashflow: CashFlowIntelligence,
    gst: GstIntelligence,
) -> int:
    score = Decimal("70")
    if profit.profit > 0:
        score += Decimal("10")
    else:
        score -= Decimal("20")
    if profit.profit_margin >= Decimal("20"):
        score += Decimal("10")
    elif profit.profit_margin < Decimal("5"):
        score -= Decimal("10")
    if cashflow.cash_risk_level == "low":
        score += Decimal("10")
    elif cashflow.cash_risk_level == "medium":
        score -= Decimal("10")
    else:
        score -= Decimal("25")
    if gst.current_gst_payable > cashflow.cash_position and gst.current_gst_payable > 0:
        score -= Decimal("10")
    return int(max(Decimal("0"), min(Decimal("100"), score)))


def cash_runway_days(cash_position: Decimal, expenses: Decimal) -> int | None:
    if expenses <= 0:
        return None
    daily_burn = money(expenses / Decimal("30"))
    if daily_burn <= 0:
        return None
    return int(cash_position / daily_burn)


def automation_alerts(
    profit: ProfitIntelligence,
    cashflow: CashFlowIntelligence,
    gst: GstIntelligence,
) -> list[OwnerInsight]:
    alerts: list[OwnerInsight] = []
    if cashflow.cash_risk_level != "low":
        alerts.append(
            OwnerInsight(
                title="Low cash warning",
                severity="warning",
                message=cashflow.cash_risk_reason,
                metric_value=cashflow.cash_position,
            )
        )
    if profit.expenses > profit.revenue * Decimal("0.80") and profit.revenue > 0:
        alerts.append(
            OwnerInsight(
                title="High expense warning",
                severity="warning",
                message="Expenses are above 80% of revenue for the selected month.",
                metric_value=profit.expenses,
            )
        )
    if gst.current_gst_payable > 0:
        alerts.append(
            OwnerInsight(
                title="GST due warning",
                severity="info",
                message=f"Current GST payable is INR {gst.current_gst_payable}.",
                metric_value=gst.current_gst_payable,
            )
        )
    if cashflow.receivables > cashflow.cash_position:
        alerts.append(
            OwnerInsight(
                title="Receivable overdue warning",
                severity="warning",
                message="Receivables exceed available cash. Follow up collections.",
                metric_value=cashflow.receivables,
            )
        )
    return alerts

