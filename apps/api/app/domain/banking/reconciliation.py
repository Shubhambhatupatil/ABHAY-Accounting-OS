from __future__ import annotations

import csv
from dataclasses import dataclass
from datetime import date, datetime
from decimal import Decimal
from difflib import SequenceMatcher
from io import StringIO
from uuid import UUID

from app.domain.accounting.engine import money


REQUIRED_COLUMNS = {"date", "description", "debit", "credit", "balance", "reference"}


@dataclass(frozen=True)
class ParsedBankTransaction:
    transaction_date: date
    description: str
    debit: Decimal
    credit: Decimal
    balance: Decimal | None
    reference_number: str | None


@dataclass(frozen=True)
class VoucherCandidate:
    voucher_id: UUID
    journal_entry_id: UUID
    voucher_number: str
    voucher_date: date
    narration: str
    ledger_name: str
    debit: Decimal
    credit: Decimal


@dataclass(frozen=True)
class SuggestedMatch:
    bank_transaction_id: UUID
    voucher_id: UUID
    journal_entry_id: UUID
    voucher_number: str
    confidence: Decimal
    reason: str


def parse_bank_csv(csv_content: str) -> list[ParsedBankTransaction]:
    reader = csv.DictReader(StringIO(csv_content.strip()))
    if reader.fieldnames is None:
        raise ValueError("CSV has no header row.")
    normalized = {name.strip().lower(): name for name in reader.fieldnames}
    missing = REQUIRED_COLUMNS - set(normalized)
    if missing:
        raise ValueError(f"CSV missing required columns: {', '.join(sorted(missing))}")

    transactions: list[ParsedBankTransaction] = []
    for index, row in enumerate(reader, start=2):
        try:
            debit = parse_amount(row[normalized["debit"]])
            credit = parse_amount(row[normalized["credit"]])
            if (debit > 0 and credit > 0) or (debit == 0 and credit == 0):
                raise ValueError("Exactly one of debit or credit must be greater than zero.")
            transactions.append(
                ParsedBankTransaction(
                    transaction_date=parse_date(row[normalized["date"]]),
                    description=row[normalized["description"]].strip(),
                    debit=debit,
                    credit=credit,
                    balance=parse_optional_amount(row[normalized["balance"]]),
                    reference_number=row[normalized["reference"]].strip() or None,
                )
            )
        except (KeyError, ValueError, ArithmeticError) as exc:
            raise ValueError(f"Invalid CSV row {index}: {exc}") from exc
    return transactions


def parse_date(value: str) -> date:
    cleaned = value.strip()
    for fmt in ("%Y-%m-%d", "%d-%m-%Y", "%d/%m/%Y"):
        try:
            return datetime.strptime(cleaned, fmt).date()
        except ValueError:
            pass
    raise ValueError(f"Unsupported date format {value!r}")


def parse_amount(value: str | None) -> Decimal:
    if value is None or not value.strip():
        return Decimal("0.00")
    return money(value.replace(",", "").strip())


def parse_optional_amount(value: str | None) -> Decimal | None:
    if value is None or not value.strip():
        return None
    return parse_amount(value)


def score_candidate(
    transaction_date: date,
    description: str,
    debit: Decimal,
    credit: Decimal,
    candidate: VoucherCandidate,
) -> tuple[Decimal, str]:
    bank_amount = debit if debit > 0 else credit
    entry_amount = candidate.credit if debit > 0 else candidate.debit
    if bank_amount != entry_amount:
        return Decimal("0.00"), "Amount does not match."

    score = Decimal("55.00")
    reasons = ["amount matched"]
    date_gap = abs((transaction_date - candidate.voucher_date).days)
    if date_gap == 0:
        score += Decimal("20.00")
        reasons.append("same date")
    elif date_gap <= 3:
        score += Decimal("12.00")
        reasons.append("date within 3 days")
    elif date_gap <= 7:
        score += Decimal("6.00")
        reasons.append("date within 7 days")

    narration = f"{candidate.narration} {candidate.voucher_number} {candidate.ledger_name}"
    similarity = Decimal(str(SequenceMatcher(None, description.lower(), narration.lower()).ratio()))
    score += money(similarity * Decimal("25.00"))
    if similarity >= Decimal("0.35"):
        reasons.append("description is similar")

    return min(score, Decimal("100.00")), ", ".join(reasons)


def suggest_best_match(
    bank_transaction_id: UUID,
    transaction_date: date,
    description: str,
    debit: Decimal,
    credit: Decimal,
    candidates: list[VoucherCandidate],
) -> SuggestedMatch | None:
    scored = [
        (score, reason, candidate)
        for candidate in candidates
        for score, reason in [score_candidate(transaction_date, description, debit, credit, candidate)]
        if score >= Decimal("70.00")
    ]
    if not scored:
        return None
    score, reason, candidate = max(scored, key=lambda item: item[0])
    return SuggestedMatch(
        bank_transaction_id=bank_transaction_id,
        voucher_id=candidate.voucher_id,
        journal_entry_id=candidate.journal_entry_id,
        voucher_number=candidate.voucher_number,
        confidence=money(score),
        reason=reason,
    )

