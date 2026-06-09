from datetime import date
from decimal import Decimal

from app.domain.accounting.financial_intelligence import (
    IntelligenceLedgerBalance,
    cashflow_intelligence,
    generate_insights,
    gst_intelligence,
    month_period,
    profit_intelligence,
)
from app.models.accounting import AccountNature, LedgerCategory


def row(
    name: str,
    nature: AccountNature,
    category: LedgerCategory,
    debit: str,
    credit: str,
) -> IntelligenceLedgerBalance:
    return IntelligenceLedgerBalance(
        ledger_name=name,
        account_nature=nature,
        category=category,
        debit=Decimal(debit),
        credit=Decimal(credit),
    )


def test_profit_intelligence_calculates_margin_and_trend() -> None:
    period = month_period(date(2026, 4, 1))
    current = [
        row("Sales", AccountNature.income, LedgerCategory.sales, "0", "100000"),
        row("Rent", AccountNature.expense, LedgerCategory.indirect_expense, "25000", "0"),
    ]
    previous = [
        row("Sales", AccountNature.income, LedgerCategory.sales, "0", "80000"),
        row("Rent", AccountNature.expense, LedgerCategory.indirect_expense, "30000", "0"),
    ]

    result = profit_intelligence(current, previous, period)

    assert result.revenue == Decimal("100000.00")
    assert result.expenses == Decimal("25000.00")
    assert result.profit == Decimal("75000.00")
    assert result.profit_margin == Decimal("75.00")
    assert result.previous_month_profit == Decimal("50000.00")
    assert result.profit_trend_percent == Decimal("50.00")


def test_cashflow_intelligence_flags_medium_risk_when_payables_exceed_cash() -> None:
    period = month_period(date(2026, 4, 1))
    result = cashflow_intelligence(
        [
            row("Bank", AccountNature.asset, LedgerCategory.bank, "10000", "0"),
            row("Creditors", AccountNature.liability, LedgerCategory.sundry_creditor, "0", "15000"),
            row("Customers", AccountNature.asset, LedgerCategory.sundry_debtor, "20000", "0"),
        ],
        period,
    )

    assert result.cash_position == Decimal("10000.00")
    assert result.payables == Decimal("15000.00")
    assert result.receivables == Decimal("20000.00")
    assert result.cash_risk_level == "medium"


def test_gst_intelligence_calculates_current_payable() -> None:
    period = month_period(date(2026, 4, 1))
    result = gst_intelligence(
        [
            row("Output GST", AccountNature.liability, LedgerCategory.output_gst, "0", "18000"),
            row("Input GST", AccountNature.asset, LedgerCategory.input_gst, "7200", "0"),
        ],
        period,
    )

    assert result.gst_collected == Decimal("18000.00")
    assert result.gst_input_paid == Decimal("7200.00")
    assert result.current_gst_payable == Decimal("10800.00")


def test_insight_generation_includes_required_owner_cards() -> None:
    period = month_period(date(2026, 4, 1))
    current = [
        row("Sales", AccountNature.income, LedgerCategory.sales, "0", "100000"),
        row("Diesel Expense", AccountNature.expense, LedgerCategory.direct_expense, "12000", "0"),
        row("Bank", AccountNature.asset, LedgerCategory.bank, "5000", "0"),
        row("Creditors", AccountNature.liability, LedgerCategory.sundry_creditor, "0", "10000"),
    ]
    previous = [row("Sales", AccountNature.income, LedgerCategory.sales, "0", "90000")]
    profit = profit_intelligence(current, previous, period)
    cashflow = cashflow_intelligence(current, period)
    gst = gst_intelligence(current, period)

    titles = {insight.title for insight in generate_insights(profit, cashflow, gst, current)}

    assert "Cash flow risk detected" in titles
    assert "GST liability expected this month" in titles
    assert "Top expense category" in titles
    assert "Outstanding receivables risk" in titles
