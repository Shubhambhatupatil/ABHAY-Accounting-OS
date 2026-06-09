from decimal import Decimal
from uuid import uuid4

from app.api.routes.accounting import make_multiline_pdf
from app.domain.accounting.invoices import (
    build_invoice_posting_lines,
    calculate_invoice_tax_line,
    sum_invoice_totals,
)
from app.models.accounting import InvoiceType


def test_gst_invoice_line_calculates_cgst_and_sgst() -> None:
    line = calculate_invoice_tax_line(
        quantity=Decimal("2"),
        unit_price=Decimal("1000.00"),
        discount_amount=Decimal("0.00"),
        gst_rate=Decimal("18.00"),
        supply_type="intra_state",
    )

    assert line.taxable_value == Decimal("2000.00")
    assert line.cgst_amount == Decimal("180.00")
    assert line.sgst_amount == Decimal("180.00")
    assert line.igst_amount == Decimal("0.00")
    assert line.total_amount == Decimal("2360.00")


def test_sales_invoice_posting_updates_receivables_and_output_gst() -> None:
    party_ledger_id = uuid4()
    sales_ledger_id = uuid4()
    output_gst_ledger_id = uuid4()
    totals = sum_invoice_totals(
        [
            calculate_invoice_tax_line(
                Decimal("1"),
                Decimal("100000.00"),
                Decimal("0.00"),
                Decimal("18.00"),
                "intra_state",
            )
        ]
    )

    lines = build_invoice_posting_lines(
        InvoiceType.sales,
        party_ledger_id,
        sales_ledger_id,
        output_gst_ledger_id,
        totals,
    )

    assert lines[0].ledger_id == party_ledger_id
    assert lines[0].debit == Decimal("118000.00")
    assert lines[1].ledger_id == sales_ledger_id
    assert lines[1].credit == Decimal("100000.00")
    assert lines[2].ledger_id == output_gst_ledger_id
    assert lines[2].credit == Decimal("18000.00")


def test_purchase_invoice_posting_updates_payables_and_input_gst() -> None:
    party_ledger_id = uuid4()
    purchase_ledger_id = uuid4()
    input_gst_ledger_id = uuid4()
    totals = sum_invoice_totals(
        [
            calculate_invoice_tax_line(
                Decimal("1"),
                Decimal("40000.00"),
                Decimal("0.00"),
                Decimal("18.00"),
                "intra_state",
            )
        ]
    )

    lines = build_invoice_posting_lines(
        InvoiceType.purchase,
        party_ledger_id,
        purchase_ledger_id,
        input_gst_ledger_id,
        totals,
    )

    assert lines[0].ledger_id == purchase_ledger_id
    assert lines[0].debit == Decimal("40000.00")
    assert lines[1].ledger_id == input_gst_ledger_id
    assert lines[1].debit == Decimal("7200.00")
    assert lines[2].ledger_id == party_ledger_id
    assert lines[2].credit == Decimal("47200.00")


def test_pdf_export_returns_pdf_bytes() -> None:
    pdf = make_multiline_pdf(["ANVRITAI", "ABHAY Accounting OS", "Invoice INV-1"])

    assert pdf.startswith(b"%PDF-1.4")
    assert b"ANVRITAI" in pdf
