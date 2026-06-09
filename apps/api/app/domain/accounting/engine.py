from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from uuid import UUID


MONEY_QUANT = Decimal("0.01")


def money(value: Decimal | int | str) -> Decimal:
    return Decimal(value).quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)


@dataclass(frozen=True)
class PostingLine:
    ledger_id: UUID
    debit: Decimal
    credit: Decimal
    narration: str | None = None


class AccountingValidationError(ValueError):
    pass


def validate_double_entry(lines: list[PostingLine]) -> None:
    if len(lines) < 2:
        raise AccountingValidationError("A voucher requires at least two posting lines.")

    debit_total = money(sum((line.debit for line in lines), Decimal("0.00")))
    credit_total = money(sum((line.credit for line in lines), Decimal("0.00")))

    for line in lines:
        debit = money(line.debit)
        credit = money(line.credit)
        if debit < 0 or credit < 0:
            raise AccountingValidationError("Debit and credit amounts cannot be negative.")
        if (debit > 0 and credit > 0) or (debit == 0 and credit == 0):
            raise AccountingValidationError("Each journal line must contain either debit or credit.")

    if debit_total <= 0:
        raise AccountingValidationError("Voucher total must be greater than zero.")
    if debit_total != credit_total:
        raise AccountingValidationError(
            f"Voucher is not balanced. Debit {debit_total} must equal credit {credit_total}."
        )


def gst_split(taxable_value: Decimal, gst_rate: Decimal, supply_type: str) -> tuple[Decimal, Decimal, Decimal]:
    tax = money(taxable_value * gst_rate / Decimal("100.00"))
    if supply_type == "inter_state":
        return Decimal("0.00"), Decimal("0.00"), tax
    return money(tax / 2), money(tax / 2), Decimal("0.00")
