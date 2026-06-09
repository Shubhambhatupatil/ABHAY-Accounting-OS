from datetime import date
from decimal import Decimal

from app.domain.accounting.financial_intelligence import (
    CashFlowIntelligence,
    GstIntelligence,
    IntelligencePeriod,
    ProfitIntelligence,
)
from app.domain.automation.intelligence import automation_alerts, business_health_score, cash_runway_days


def period() -> IntelligencePeriod:
    return IntelligencePeriod(month=date(2026, 4, 1), starts_on=date(2026, 4, 1), ends_on=date(2026, 4, 30))


def test_business_health_score_rewards_profit_and_low_cash_risk() -> None:
    profit = ProfitIntelligence(
        period=period(),
        revenue=Decimal("100000"),
        expenses=Decimal("50000"),
        profit=Decimal("50000"),
        profit_margin=Decimal("50"),
        previous_month_profit=Decimal("30000"),
        profit_trend_percent=Decimal("66.67"),
    )
    cashflow = CashFlowIntelligence(
        period=period(),
        cash_position=Decimal("80000"),
        receivables=Decimal("10000"),
        payables=Decimal("5000"),
        net_cash_flow=Decimal("80000"),
        cash_risk_level="low",
        cash_risk_reason="Cash position is positive.",
    )
    gst = GstIntelligence(
        period=period(),
        gst_collected=Decimal("18000"),
        gst_input_paid=Decimal("7200"),
        current_gst_payable=Decimal("10800"),
        estimated_month_end_liability=Decimal("10800"),
    )

    assert business_health_score(profit, cashflow, gst) == 100


def test_cash_runway_uses_monthly_expenses() -> None:
    assert cash_runway_days(Decimal("30000"), Decimal("60000")) == 15


def test_automation_alerts_include_gst_and_receivable_risk() -> None:
    profit = ProfitIntelligence(
        period=period(),
        revenue=Decimal("100000"),
        expenses=Decimal("90000"),
        profit=Decimal("10000"),
        profit_margin=Decimal("10"),
        previous_month_profit=Decimal("0"),
        profit_trend_percent=None,
    )
    cashflow = CashFlowIntelligence(
        period=period(),
        cash_position=Decimal("5000"),
        receivables=Decimal("40000"),
        payables=Decimal("10000"),
        net_cash_flow=Decimal("5000"),
        cash_risk_level="medium",
        cash_risk_reason="Cash position is lower than payables.",
    )
    gst = GstIntelligence(
        period=period(),
        gst_collected=Decimal("18000"),
        gst_input_paid=Decimal("7200"),
        current_gst_payable=Decimal("10800"),
        estimated_month_end_liability=Decimal("10800"),
    )

    titles = {alert.title for alert in automation_alerts(profit, cashflow, gst)}

    assert "Low cash warning" in titles
    assert "GST due warning" in titles
    assert "Receivable overdue warning" in titles
