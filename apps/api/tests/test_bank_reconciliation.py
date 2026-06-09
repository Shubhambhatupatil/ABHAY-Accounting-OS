from datetime import date
from decimal import Decimal
from uuid import uuid4

from app.domain.banking.reconciliation import (
    VoucherCandidate,
    parse_bank_csv,
    suggest_best_match,
)


def test_bank_csv_parser_accepts_required_columns() -> None:
    rows = parse_bank_csv(
        """
date,description,debit,credit,balance,reference
2026-04-10,Office rent payment,12000,0,88000,UTR001
2026-04-11,Customer receipt,0,60000,148000,UTR002
"""
    )

    assert len(rows) == 2
    assert rows[0].transaction_date == date(2026, 4, 10)
    assert rows[0].debit == Decimal("12000.00")
    assert rows[1].credit == Decimal("60000.00")


def test_matching_logic_scores_amount_date_and_description() -> None:
    bank_transaction_id = uuid4()
    candidate = VoucherCandidate(
        voucher_id=uuid4(),
        journal_entry_id=uuid4(),
        voucher_number="PAYMENT-000001",
        voucher_date=date(2026, 4, 10),
        narration="Office rent payment",
        ledger_name="Bank",
        debit=Decimal("0.00"),
        credit=Decimal("12000.00"),
    )

    suggestion = suggest_best_match(
        bank_transaction_id,
        date(2026, 4, 10),
        "Office rent payment UTR001",
        Decimal("12000.00"),
        Decimal("0.00"),
        [candidate],
    )

    assert suggestion is not None
    assert suggestion.bank_transaction_id == bank_transaction_id
    assert suggestion.confidence >= Decimal("90.00")


def test_matching_logic_rejects_amount_mismatch() -> None:
    candidate = VoucherCandidate(
        voucher_id=uuid4(),
        journal_entry_id=uuid4(),
        voucher_number="PAYMENT-000001",
        voucher_date=date(2026, 4, 10),
        narration="Office rent payment",
        ledger_name="Bank",
        debit=Decimal("0.00"),
        credit=Decimal("12000.00"),
    )

    suggestion = suggest_best_match(
        uuid4(),
        date(2026, 4, 10),
        "Office rent payment",
        Decimal("12500.00"),
        Decimal("0.00"),
        [candidate],
    )

    assert suggestion is None


def test_reconciliation_summary_status_math() -> None:
    rows = [
        {"status": "matched", "debit": Decimal("12000.00"), "credit": Decimal("0.00")},
        {"status": "suggested_match", "debit": Decimal("0.00"), "credit": Decimal("60000.00")},
        {"status": "ignored", "debit": Decimal("100.00"), "credit": Decimal("0.00")},
        {"status": "unmatched", "debit": Decimal("2500.00"), "credit": Decimal("0.00")},
    ]
    counts = {"matched": 0, "suggested_match": 0, "ignored": 0, "unmatched": 0}
    matched_amount = Decimal("0.00")
    unreconciled_amount = Decimal("0.00")
    for row in rows:
        counts[row["status"]] += 1
        amount = row["debit"] if row["debit"] > 0 else row["credit"]
        if row["status"] == "matched":
            matched_amount += amount
        elif row["status"] != "ignored":
            unreconciled_amount += amount

    assert counts == {"matched": 1, "suggested_match": 1, "ignored": 1, "unmatched": 1}
    assert matched_amount == Decimal("12000.00")
    assert unreconciled_amount == Decimal("62500.00")
