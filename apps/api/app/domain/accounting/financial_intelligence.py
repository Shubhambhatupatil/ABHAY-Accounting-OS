from __future__ import annotations

from calendar import monthrange
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

from app.domain.accounting.engine import money
from app.models.accounting import AccountNature, LedgerCategory
from app.schemas.financial_intelligence import (
    CashFlowIntelligence,
    FinancialIntelligenceSummary,
    GstIntelligence,
    IntelligencePeriod,
    OwnerInsight,
    ProfitIntelligence,
)


@dataclass(frozen=True)
class IntelligenceLedgerBalance:
    ledger_name: str
    account_nature: AccountNature
    category: LedgerCategory
    debit: Decimal
    credit: Decimal


def month_period(month: date | None) -> IntelligencePeriod:
    selected = month or date.today()
    starts_on = date(selected.year, selected.month, 1)
    ends_on = date(selected.year, selected.month, monthrange(selected.year, selected.month)[1])
    return IntelligencePeriod(month=starts_on, starts_on=starts_on, ends_on=ends_on)


def previous_month(month: date) -> date:
    if month.month == 1:
        return date(month.year - 1, 12, 1)
    return date(month.year, month.month - 1, 1)


def profit_intelligence(
    current_rows: list[IntelligenceLedgerBalance],
    previous_rows: list[IntelligenceLedgerBalance],
    period: IntelligencePeriod,
) -> ProfitIntelligence:
    revenue = money(
        sum(
            row.credit - row.debit
            for row in current_rows
            if row.account_nature == AccountNature.income
        )
    )
    expenses = money(
        sum(
            row.debit - row.credit
            for row in current_rows
            if row.account_nature == AccountNature.expense
        )
    )
    profit = money(revenue - expenses)
    previous_revenue = money(
        sum(
            row.credit - row.debit
            for row in previous_rows
            if row.account_nature == AccountNature.income
        )
    )
    previous_expenses = money(
        sum(
            row.debit - row.credit
            for row in previous_rows
            if row.account_nature == AccountNature.expense
        )
    )
    previous_profit = money(previous_revenue - previous_expenses)
    trend = None
    if previous_profit != 0:
        trend = money((profit - previous_profit) / abs(previous_profit) * Decimal("100"))
    return ProfitIntelligence(
        period=period,
        revenue=revenue,
        expenses=expenses,
        profit=profit,
        profit_margin=money((profit / revenue * Decimal("100")) if revenue else Decimal("0")),
        previous_month_profit=previous_profit,
        profit_trend_percent=trend,
    )


def cashflow_intelligence(
    current_rows: list[IntelligenceLedgerBalance],
    period: IntelligencePeriod,
) -> CashFlowIntelligence:
    cash_position = money(
        sum(
            row.debit - row.credit
            for row in current_rows
            if row.category in {LedgerCategory.cash, LedgerCategory.bank}
        )
    )
    receivables = money(
        sum(row.debit - row.credit for row in current_rows if row.category == LedgerCategory.sundry_debtor)
    )
    payables = money(
        sum(row.credit - row.debit for row in current_rows if row.category == LedgerCategory.sundry_creditor)
    )
    net_cash_flow = cash_position
    risk_level = "low"
    risk_reason = "Cash position is positive for the selected month."
    if cash_position < 0:
        risk_level = "high"
        risk_reason = "Cash balance is negative for the selected month."
    elif cash_position < payables:
        risk_level = "medium"
        risk_reason = "Cash position is lower than current payables."
    return CashFlowIntelligence(
        period=period,
        cash_position=cash_position,
        receivables=receivables,
        payables=payables,
        net_cash_flow=net_cash_flow,
        cash_risk_level=risk_level,
        cash_risk_reason=risk_reason,
    )


def gst_intelligence(
    current_rows: list[IntelligenceLedgerBalance],
    period: IntelligencePeriod,
) -> GstIntelligence:
    collected = money(
        sum(row.credit - row.debit for row in current_rows if row.category == LedgerCategory.output_gst)
    )
    input_paid = money(
        sum(row.debit - row.credit for row in current_rows if row.category == LedgerCategory.input_gst)
    )
    payable = money(collected - input_paid)
    today = date.today()
    if today.year == period.starts_on.year and today.month == period.starts_on.month and today.day > 0:
        elapsed = Decimal(today.day)
    else:
        elapsed = Decimal(period.ends_on.day)
    estimate = money(payable / elapsed * Decimal(period.ends_on.day)) if elapsed else payable
    return GstIntelligence(
        period=period,
        gst_collected=collected,
        gst_input_paid=input_paid,
        current_gst_payable=payable,
        estimated_month_end_liability=estimate,
    )


def generate_insights(
    profit: ProfitIntelligence,
    cashflow: CashFlowIntelligence,
    gst: GstIntelligence,
    current_rows: list[IntelligenceLedgerBalance],
) -> list[OwnerInsight]:
    insights: list[OwnerInsight] = []
    if profit.profit_trend_percent is None:
        insights.append(
            OwnerInsight(
                title="Profit trend unavailable",
                severity="info",
                message="Previous month profit is zero or unavailable, so trend cannot be calculated.",
            )
        )
    else:
        direction = "up" if profit.profit_trend_percent >= 0 else "down"
        severity = "positive" if profit.profit_trend_percent >= 0 else "warning"
        insights.append(
            OwnerInsight(
                title=f"Profit is {direction} compared to last month",
                severity=severity,
                message=f"Profit changed by {profit.profit_trend_percent}% versus the previous month.",
                metric_value=profit.profit_trend_percent,
            )
        )
    insights.append(
        OwnerInsight(
            title="Cash flow risk detected" if cashflow.cash_risk_level != "low" else "Cash flow risk is low",
            severity="warning" if cashflow.cash_risk_level != "low" else "positive",
            message=cashflow.cash_risk_reason,
            metric_value=cashflow.cash_position,
        )
    )
    insights.append(
        OwnerInsight(
            title="GST liability expected this month",
            severity="info" if gst.estimated_month_end_liability >= 0 else "positive",
            message=f"Estimated month-end GST payable is INR {gst.estimated_month_end_liability}.",
            metric_value=gst.estimated_month_end_liability,
        )
    )
    expense_rows = [
        (row.ledger_name, money(row.debit - row.credit))
        for row in current_rows
        if row.account_nature == AccountNature.expense and row.debit - row.credit > 0
    ]
    if expense_rows:
        top_name, top_amount = max(expense_rows, key=lambda item: item[1])
        insights.append(
            OwnerInsight(
                title="Top expense category",
                severity="info",
                message=f"{top_name} is the largest expense at INR {top_amount}.",
                metric_value=top_amount,
            )
        )
    else:
        insights.append(
            OwnerInsight(
                title="Top expense category",
                severity="info",
                message="No expenses are posted for the selected month.",
            )
        )
    receivable_severity = "warning" if cashflow.receivables > cashflow.cash_position else "positive"
    insights.append(
        OwnerInsight(
            title="Outstanding receivables risk",
            severity=receivable_severity,
            message=(
                "Receivables exceed current cash position."
                if receivable_severity == "warning"
                else "Receivables are within a manageable range."
            ),
            metric_value=cashflow.receivables,
        )
    )
    return insights


def summary(
    current_rows: list[IntelligenceLedgerBalance],
    previous_rows: list[IntelligenceLedgerBalance],
    period: IntelligencePeriod,
) -> FinancialIntelligenceSummary:
    profit = profit_intelligence(current_rows, previous_rows, period)
    cashflow = cashflow_intelligence(current_rows, period)
    gst = gst_intelligence(current_rows, period)
    return FinancialIntelligenceSummary(
        profit=profit,
        cashflow=cashflow,
        gst=gst,
        insights=generate_insights(profit, cashflow, gst, current_rows),
    )

