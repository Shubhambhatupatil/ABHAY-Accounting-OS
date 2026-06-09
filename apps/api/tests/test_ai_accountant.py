from datetime import date
from decimal import Decimal
from uuid import uuid4

from app.api.routes.ai_accountant import build_proposed_lines
from app.api.routes.ai_entry import (
    confidence_band,
    extract_invoice_fields,
    extract_text_from_pdf_bytes,
    gst_risk_findings,
    voucher_doctor_findings,
    workbench_response,
)
from app.api.routes.accounting import make_multiline_pdf
from app.domain.ai.accountant import parse_with_rules
from app.models.accounting import AccountNature, Ledger, LedgerCategory, VoucherType
from app.schemas.ai_accountant import AiVoucherSuggestionResponse


def test_rule_parser_creates_payment_suggestion_for_diesel_cash() -> None:
    suggestion = parse_with_rules("Paid diesel expense ₹2500 cash", date(2026, 4, 10), [])

    assert suggestion.voucher_type == VoucherType.payment
    assert suggestion.amount == Decimal("2500.00")
    assert suggestion.debit_ledger_name == "Diesel Expense"
    assert suggestion.debit_category == LedgerCategory.direct_expense
    assert suggestion.credit_ledger_name == "Cash"
    assert suggestion.credit_category == LedgerCategory.cash
    assert suggestion.gst_applicable is False


def test_rule_parser_detects_purchase_gst() -> None:
    suggestion = parse_with_rules("Purchase goods 10000 plus gst", date(2026, 4, 10), [])

    assert suggestion.voucher_type == VoucherType.purchase
    assert suggestion.gst_applicable is True
    assert suggestion.suggested_gst_rate == Decimal("18.00")


def test_one_line_recharge_entry_uses_first_amount_not_people_count() -> None:
    suggestion = parse_with_rules("400 recharge smartphone back office 30 people", date(2026, 4, 10), [])

    assert suggestion.amount == Decimal("400.00")
    assert suggestion.debit_ledger_name == "Mobile Recharge Expense"
    assert suggestion.debit_category == LedgerCategory.indirect_expense
    assert suggestion.confidence > Decimal("0.80")


def test_recharge_expense_does_not_map_to_office_rent_when_office_is_present() -> None:
    suggestion = parse_with_rules("400 recharge smartphone back office 30 people", date(2026, 4, 10), communication_ledgers())
    lines = build_proposed_lines(suggestion, communication_ledgers())

    assert lines[0]["ledger_name"] == "Mobile Recharge Expense"
    assert lines[0]["debit"] == "400.00"
    assert lines[1]["ledger_name"] == "Cash"
    assert lines[1]["credit"] == "400.00"
    assert suggestion.confidence > Decimal("0.80")


def test_mobile_recharge_cash_maps_to_mobile_recharge_expense() -> None:
    suggestion = parse_with_rules("mobile recharge 500 cash", date(2026, 4, 10), communication_ledgers())
    lines = build_proposed_lines(suggestion, communication_ledgers())

    assert lines[0]["ledger_name"] == "Mobile Recharge Expense"
    assert lines[0]["debit"] == "500.00"
    assert lines[1]["ledger_name"] == "Cash"
    assert lines[1]["credit"] == "500.00"


def test_office_internet_bill_maps_to_internet_phone_expense() -> None:
    suggestion = parse_with_rules("office internet bill 1200 paid", date(2026, 4, 10), communication_ledgers())
    lines = build_proposed_lines(suggestion, communication_ledgers())

    assert lines[0]["ledger_name"] == "Internet & Phone Expense"
    assert lines[0]["debit"] == "1200.00"
    assert lines[1]["ledger_name"] == "Cash"
    assert lines[1]["credit"] == "1200.00"


def test_gst_purchase_suggestion_builds_balanced_three_line_entry() -> None:
    suggestion = parse_with_rules("Purchase goods 10000 plus GST from supplier", date(2026, 4, 10), [])
    lines = build_proposed_lines(
        suggestion,
        [
            ledger("Purchases", LedgerCategory.purchase, AccountNature.expense),
            ledger("Input GST", LedgerCategory.input_gst, AccountNature.asset),
            ledger("Sundry Creditors", LedgerCategory.sundry_creditor, AccountNature.liability),
        ],
    )

    assert [line["ledger_name"] for line in lines] == ["Purchases", "Input GST", "Sundry Creditors"]
    assert sum(Decimal(str(line["debit"])) for line in lines) == Decimal("11800.00")
    assert sum(Decimal(str(line["credit"])) for line in lines) == Decimal("11800.00")


def test_low_confidence_suggestion_asks_clarification() -> None:
    response = AiVoucherSuggestionResponse(
        suggestion_id=uuid4(),
        input_text="unclear entry",
        voucher_type=VoucherType.journal,
        voucher_date=date(2026, 4, 10),
        amount=Decimal("0.00"),
        confidence=Decimal("0.35"),
        gst_applicable=False,
        suggested_gst_rate=None,
        suggested_ledgers=[],
        lines=[],
        explanation="Unable to infer enough accounting context.",
        validation_errors=[],
        can_post=False,
        model_name="rule-based-fallback",
    )

    workbench = workbench_response(response)

    assert workbench.confidence_band == "low"
    assert len(workbench.clarification_questions) == 1


def test_pdf_text_extraction_and_invoice_fields_from_text_pdf() -> None:
    pdf = make_multiline_pdf(
        [
            "Vendor: Bharat Telecom",
            "Invoice Number: BT-42",
            "Invoice Date: 2026-04-10",
            "GSTIN: 27ABCDE1234F1Z5",
            "Taxable Value: INR 10000.00",
            "CGST: INR 900.00",
            "SGST: INR 900.00",
            "Total Amount: INR 11800.00",
        ]
    )

    text = extract_text_from_pdf_bytes(pdf)
    fields = extract_invoice_fields(text)

    assert "Bharat Telecom" in text
    assert fields.invoice_number == "BT-42"
    assert fields.gstin == "27ABCDE1234F1Z5"
    assert fields.taxable_amount == Decimal("10000.00")
    assert fields.total_amount == Decimal("11800.00")


def test_confidence_band_does_not_claim_accuracy() -> None:
    assert confidence_band(Decimal("0.90")) == "high"
    assert confidence_band(Decimal("0.70")) == "medium"
    assert confidence_band(Decimal("0.40")) == "low"


def test_voucher_doctor_flags_unbalanced_entry() -> None:
    response = AiVoucherSuggestionResponse(
        suggestion_id=uuid4(),
        input_text="bad entry",
        voucher_type=VoucherType.payment,
        voucher_date=date(2026, 4, 10),
        amount=Decimal("100.00"),
        confidence=Decimal("0.80"),
        gst_applicable=False,
        suggested_gst_rate=None,
        suggested_ledgers=[],
        lines=[
            posting_line("Expense", Decimal("100.00"), Decimal("0.00")),
            posting_line("Cash", Decimal("0.00"), Decimal("90.00")),
        ],
        explanation="",
        validation_errors=[],
        can_post=False,
        model_name="rule-based-fallback",
    )

    assert "Debit and credit totals do not match." in voucher_doctor_findings(response)


def test_gst_risk_flags_missing_gst_line() -> None:
    response = AiVoucherSuggestionResponse(
        suggestion_id=uuid4(),
        input_text="Purchase bill 1000 GST",
        voucher_type=VoucherType.purchase,
        voucher_date=date(2026, 4, 10),
        amount=Decimal("1000.00"),
        confidence=Decimal("0.80"),
        gst_applicable=True,
        suggested_gst_rate=Decimal("18.00"),
        suggested_ledgers=[],
        lines=[
            posting_line("Purchases", Decimal("1000.00"), Decimal("0.00")),
            posting_line("Sundry Creditors", Decimal("0.00"), Decimal("1000.00")),
        ],
        explanation="",
        validation_errors=[],
        can_post=False,
        model_name="rule-based-fallback",
    )

    assert "GST applicable but GST ledger line is missing." in gst_risk_findings(response, None)


def ledger(name: str, category: LedgerCategory, nature: AccountNature) -> Ledger:
    return Ledger(
        id=uuid4(),
        company_id=uuid4(),
        ledger_group_id=uuid4(),
        name=name,
        category=category,
        account_nature=nature,
        opening_balance=Decimal("0.00"),
        opening_balance_type="dr",
        gstin=None,
        state_code=None,
        is_system=True,
        is_active=True,
    )


def communication_ledgers() -> list[Ledger]:
    return [
        ledger("Office Rent", LedgerCategory.indirect_expense, AccountNature.expense),
        ledger("Mobile Recharge Expense", LedgerCategory.indirect_expense, AccountNature.expense),
        ledger("Communication Expense", LedgerCategory.indirect_expense, AccountNature.expense),
        ledger("Internet & Phone Expense", LedgerCategory.indirect_expense, AccountNature.expense),
        ledger("Cash", LedgerCategory.cash, AccountNature.asset),
        ledger("Bank", LedgerCategory.bank, AccountNature.asset),
    ]


def posting_line(ledger_name: str, debit: Decimal, credit: Decimal):
    from app.schemas.ai_accountant import SuggestedPostingLine

    return SuggestedPostingLine(
        ledger_id=uuid4(),
        ledger_name=ledger_name,
        debit=debit,
        credit=credit,
        reason="test",
    )
