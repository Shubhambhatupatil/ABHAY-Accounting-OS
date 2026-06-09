from dataclasses import dataclass
from decimal import Decimal
from uuid import UUID

from app.domain.accounting.engine import gst_split, money
from app.models.accounting import InvoiceType


@dataclass(frozen=True)
class InvoiceTaxLine:
    taxable_value: Decimal
    cgst_amount: Decimal
    sgst_amount: Decimal
    igst_amount: Decimal
    total_amount: Decimal


@dataclass(frozen=True)
class InvoiceTotals:
    taxable_value: Decimal
    cgst_amount: Decimal
    sgst_amount: Decimal
    igst_amount: Decimal
    total_amount: Decimal


@dataclass(frozen=True)
class InvoicePostingLine:
    ledger_id: UUID
    debit: Decimal
    credit: Decimal
    narration: str


def calculate_invoice_tax_line(
    quantity: Decimal,
    unit_price: Decimal,
    discount_amount: Decimal,
    gst_rate: Decimal,
    supply_type: str,
) -> InvoiceTaxLine:
    taxable = money(quantity * unit_price - discount_amount)
    cgst, sgst, igst = gst_split(taxable, gst_rate, supply_type)
    total = money(taxable + cgst + sgst + igst)
    return InvoiceTaxLine(
        taxable_value=taxable,
        cgst_amount=cgst,
        sgst_amount=sgst,
        igst_amount=igst,
        total_amount=total,
    )


def sum_invoice_totals(lines: list[InvoiceTaxLine]) -> InvoiceTotals:
    return InvoiceTotals(
        taxable_value=money(sum((line.taxable_value for line in lines), Decimal("0.00"))),
        cgst_amount=money(sum((line.cgst_amount for line in lines), Decimal("0.00"))),
        sgst_amount=money(sum((line.sgst_amount for line in lines), Decimal("0.00"))),
        igst_amount=money(sum((line.igst_amount for line in lines), Decimal("0.00"))),
        total_amount=money(sum((line.total_amount for line in lines), Decimal("0.00"))),
    )


def build_invoice_posting_lines(
    invoice_type: InvoiceType,
    party_ledger_id: UUID,
    revenue_or_purchase_ledger_id: UUID,
    gst_ledger_id: UUID,
    totals: InvoiceTotals,
) -> list[InvoicePostingLine]:
    gst_total = money(totals.cgst_amount + totals.sgst_amount + totals.igst_amount)
    if invoice_type == InvoiceType.sales:
        lines = [
            InvoicePostingLine(
                ledger_id=party_ledger_id,
                debit=totals.total_amount,
                credit=Decimal("0.00"),
                narration="Customer receivable for GST sales invoice",
            ),
            InvoicePostingLine(
                ledger_id=revenue_or_purchase_ledger_id,
                debit=Decimal("0.00"),
                credit=totals.taxable_value,
                narration="Sales revenue from GST invoice",
            ),
        ]
        if gst_total > 0:
            lines.append(
                InvoicePostingLine(
                    ledger_id=gst_ledger_id,
                    debit=Decimal("0.00"),
                    credit=gst_total,
                    narration="Output GST liability from sales invoice",
                )
            )
        return lines

    lines = [
        InvoicePostingLine(
            ledger_id=revenue_or_purchase_ledger_id,
            debit=totals.taxable_value,
            credit=Decimal("0.00"),
            narration="Purchase cost from GST purchase invoice",
        )
    ]
    if gst_total > 0:
        lines.append(
            InvoicePostingLine(
                ledger_id=gst_ledger_id,
                debit=gst_total,
                credit=Decimal("0.00"),
                narration="Input GST credit from purchase invoice",
            )
        )
    lines.append(
        InvoicePostingLine(
            ledger_id=party_ledger_id,
            debit=Decimal("0.00"),
            credit=totals.total_amount,
            narration="Vendor payable for GST purchase invoice",
        )
    )
    return lines
