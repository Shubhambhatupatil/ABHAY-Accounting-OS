from decimal import Decimal
from uuid import uuid4

import pytest

from app.domain.accounting.engine import AccountingValidationError, PostingLine, gst_split, validate_double_entry


def test_balanced_voucher_is_valid() -> None:
    validate_double_entry(
        [
            PostingLine(ledger_id=uuid4(), debit=Decimal("2500.00"), credit=Decimal("0.00")),
            PostingLine(ledger_id=uuid4(), debit=Decimal("0.00"), credit=Decimal("2500.00")),
        ]
    )


def test_unbalanced_voucher_is_rejected() -> None:
    with pytest.raises(AccountingValidationError):
        validate_double_entry(
            [
                PostingLine(ledger_id=uuid4(), debit=Decimal("2500.00"), credit=Decimal("0.00")),
                PostingLine(ledger_id=uuid4(), debit=Decimal("0.00"), credit=Decimal("2400.00")),
            ]
        )


def test_intra_state_gst_splits_into_cgst_and_sgst() -> None:
    assert gst_split(Decimal("1000.00"), Decimal("18.00"), "intra_state") == (
        Decimal("90.00"),
        Decimal("90.00"),
        Decimal("0.00"),
    )


def test_inter_state_gst_uses_igst() -> None:
    assert gst_split(Decimal("1000.00"), Decimal("18.00"), "inter_state") == (
        Decimal("0.00"),
        Decimal("0.00"),
        Decimal("180.00"),
    )
