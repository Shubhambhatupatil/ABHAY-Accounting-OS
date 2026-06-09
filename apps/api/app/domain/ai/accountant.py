from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import date
from decimal import Decimal

import httpx

from app.domain.accounting.engine import AccountingValidationError, PostingLine, money, validate_double_entry
from app.models.accounting import Ledger, LedgerCategory, VoucherType


@dataclass(frozen=True)
class ParsedAiSuggestion:
    voucher_type: VoucherType
    voucher_date: date
    amount: Decimal
    confidence: Decimal
    gst_applicable: bool
    suggested_gst_rate: Decimal | None
    debit_ledger_name: str
    debit_category: LedgerCategory
    credit_ledger_name: str
    credit_category: LedgerCategory
    explanation: str
    model_name: str


def parse_with_rules(text: str, transaction_date: date, ledgers: list[Ledger]) -> ParsedAiSuggestion:
    normalized = text.lower()
    amount = extract_amount(normalized)
    voucher_type = detect_voucher_type(normalized)
    gst_applicable, gst_rate = detect_gst(normalized, voucher_type)

    debit_name, debit_category, credit_name, credit_category = infer_posting_ledgers(
        normalized, voucher_type, ledgers
    )
    confidence = score_rule_confidence(normalized, amount)

    return ParsedAiSuggestion(
        voucher_type=voucher_type,
        voucher_date=transaction_date,
        amount=amount,
        confidence=confidence,
        gst_applicable=gst_applicable,
        suggested_gst_rate=gst_rate,
        debit_ledger_name=debit_name,
        debit_category=debit_category,
        credit_ledger_name=credit_name,
        credit_category=credit_category,
        explanation=explain(voucher_type, debit_name, credit_name, normalized),
        model_name="rule-based-fallback",
    )


async def parse_with_openai(
    text: str,
    transaction_date: date,
    ledgers: list[Ledger],
    api_key: str | None,
) -> ParsedAiSuggestion:
    if not api_key:
        return parse_with_rules(text, transaction_date, ledgers)

    ledger_catalog = [
        {"name": ledger.name, "category": ledger.category.value, "nature": ledger.account_nature.value}
        for ledger in ledgers
    ]
    prompt = {
        "task": "Parse Indian accounting transaction into a double-entry voucher suggestion.",
        "input": text,
        "transaction_date": transaction_date.isoformat(),
        "allowed_voucher_types": [item.value for item in VoucherType],
        "ledger_catalog": ledger_catalog,
        "required_json_keys": [
            "voucher_type",
            "amount",
            "gst_applicable",
            "suggested_gst_rate",
            "debit_ledger_name",
            "debit_category",
            "credit_ledger_name",
            "credit_category",
            "confidence",
            "explanation",
        ],
    }
    try:
        async with httpx.AsyncClient(timeout=12) as client:
            response = await client.post(
                "https://api.openai.com/v1/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json={
                    "model": "gpt-4o-mini",
                    "messages": [
                        {
                            "role": "system",
                            "content": "Return only strict JSON. Never post accounting entries.",
                        },
                        {"role": "user", "content": json.dumps(prompt)},
                    ],
                    "temperature": 0.1,
                    "response_format": {"type": "json_object"},
                },
            )
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            payload = json.loads(content)
    except (httpx.HTTPError, KeyError, TypeError, ValueError, json.JSONDecodeError):
        return parse_with_rules(text, transaction_date, ledgers)

    try:
        return ParsedAiSuggestion(
            voucher_type=VoucherType(payload["voucher_type"]),
            voucher_date=transaction_date,
            amount=money(payload["amount"]),
            confidence=Decimal(str(payload.get("confidence", "0.70"))),
            gst_applicable=bool(payload.get("gst_applicable", False)),
            suggested_gst_rate=(
                money(payload["suggested_gst_rate"])
                if payload.get("suggested_gst_rate") is not None
                else None
            ),
            debit_ledger_name=str(payload["debit_ledger_name"]),
            debit_category=LedgerCategory(payload["debit_category"]),
            credit_ledger_name=str(payload["credit_ledger_name"]),
            credit_category=LedgerCategory(payload["credit_category"]),
            explanation=str(payload["explanation"]),
            model_name="openai:gpt-4o-mini",
        )
    except (KeyError, ValueError, TypeError, ArithmeticError):
        return parse_with_rules(text, transaction_date, ledgers)


def extract_amount(text: str) -> Decimal:
    currency_match = re.search(
        r"(?:₹|rs\.?|inr)\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)",
        text,
    )
    if currency_match:
        return money(currency_match.group(1).replace(",", ""))

    candidates: list[Decimal] = []
    for match in re.finditer(r"\b([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)\b", text):
        after = text[match.end() : match.end() + 16]
        if re.match(r"\s*(people|person|staff|employees|users|qty|quantity|pcs|nos)\b", after):
            continue
        candidates.append(money(match.group(1).replace(",", "")))
    if not candidates:
        return Decimal("0.00")
    return candidates[0]


def score_rule_confidence(text: str, amount: Decimal) -> Decimal:
    if amount <= 0:
        return Decimal("0.35")
    score = Decimal("0.58")
    if any(word in text for word in ["paid", "received", "purchase", "sold", "sales", "rent", "diesel", "salary"]):
        score += Decimal("0.18")
    if any(word in text for word in ["cash", "bank", "upi", "neft"]):
        score += Decimal("0.10")
    if any(word in text for word in ["gst", "invoice", "bill"]):
        score += Decimal("0.08")
    if is_communication_expense(text):
        score += Decimal("0.25")
    return min(score, Decimal("0.93"))


def detect_voucher_type(text: str) -> VoucherType:
    if any(word in text for word in ["received", "receipt", "customer paid", "cash mila", "paise mile"]):
        return VoucherType.receipt
    if any(word in text for word in ["transfer", "withdraw", "deposit", "contra"]):
        return VoucherType.contra
    if any(word in text for word in ["sold", "sales", "sale invoice", "becha"]):
        return VoucherType.sales
    if any(word in text for word in ["purchase", "bought", "supplier bill", "kharida"]):
        return VoucherType.purchase
    if any(word in text for word in ["paid", "payment", "diya", "pay kiya", "cash diya"]) or is_communication_expense(text):
        return VoucherType.payment
    return VoucherType.journal


def detect_gst(text: str, voucher_type: VoucherType) -> tuple[bool, Decimal | None]:
    explicit_rate = re.search(r"(\d{1,2})(?:\.\d{1,2})?\s*%?\s*gst", text)
    if explicit_rate:
        return True, money(explicit_rate.group(1))
    if "gst" in text:
        return True, Decimal("18.00")
    if voucher_type in {VoucherType.sales, VoucherType.purchase}:
        return True, Decimal("18.00")
    return False, None


def infer_posting_ledgers(
    text: str,
    voucher_type: VoucherType,
    ledgers: list[Ledger],
) -> tuple[str, LedgerCategory, str, LedgerCategory]:
    cash_or_bank = "Bank" if "bank" in text or "upi" in text or "neft" in text else "Cash"
    if voucher_type == VoucherType.receipt:
        return cash_or_bank, LedgerCategory.bank if cash_or_bank == "Bank" else LedgerCategory.cash, "Sundry Debtors", LedgerCategory.sundry_debtor
    if voucher_type == VoucherType.contra:
        if "withdraw" in text:
            return "Cash", LedgerCategory.cash, "Bank", LedgerCategory.bank
        return "Bank", LedgerCategory.bank, "Cash", LedgerCategory.cash
    if voucher_type == VoucherType.sales:
        return "Sundry Debtors", LedgerCategory.sundry_debtor, "Sales", LedgerCategory.sales
    if voucher_type == VoucherType.purchase:
        return "Purchases", LedgerCategory.purchase, "Sundry Creditors", LedgerCategory.sundry_creditor
    if voucher_type == VoucherType.payment:
        expense_name = infer_expense_ledger_name(text, ledgers)
        return expense_name, infer_expense_category(text), cash_or_bank, LedgerCategory.bank if cash_or_bank == "Bank" else LedgerCategory.cash
    return "Direct Expenses", LedgerCategory.direct_expense, cash_or_bank, LedgerCategory.bank if cash_or_bank == "Bank" else LedgerCategory.cash


def infer_expense_ledger_name(text: str, ledgers: list[Ledger]) -> str:
    if is_communication_expense(text):
        existing_names = {ledger.name.lower(): ledger.name for ledger in ledgers}
        if any(word in text for word in ["internet", "wifi", "data pack"]):
            return existing_names.get("internet & phone expense", "Internet & Phone Expense")
        if any(word in text for word in ["recharge", "mobile", "smartphone", "phone", "sim"]):
            return existing_names.get("mobile recharge expense", "Mobile Recharge Expense")
        return existing_names.get("communication expense", "Communication Expense")

    keyword_map = {
        "diesel": "Diesel Expense",
        "fuel": "Diesel Expense",
        "mobile": "Mobile Recharge Expense",
        "phone": "Mobile Recharge Expense",
        "recharge": "Mobile Recharge Expense",
        "smartphone": "Mobile Recharge Expense",
        "rent": "Office Rent",
        "office rent": "Office Rent",
        "salary": "Salary Expense",
        "electricity": "Electricity Expense",
    }
    existing_names = {ledger.name.lower(): ledger.name for ledger in ledgers}
    for keyword, ledger_name in keyword_map.items():
        if keyword in text:
            return existing_names.get(ledger_name.lower(), ledger_name)
    return existing_names.get("direct expenses", "Direct Expenses")


def infer_expense_category(text: str) -> LedgerCategory:
    if any(word in text for word in ["diesel", "fuel", "freight", "transport"]):
        return LedgerCategory.direct_expense
    return LedgerCategory.indirect_expense


def is_communication_expense(text: str) -> bool:
    return any(
        keyword in text
        for keyword in ["recharge", "mobile", "smartphone", "phone", "sim", "internet", "wifi", "data pack"]
    )


def explain(voucher_type: VoucherType, debit_name: str, credit_name: str, text: str) -> str:
    if voucher_type == VoucherType.payment:
        return (
            f"This is a payment voucher because the transaction says money was paid. "
            f"Debit {debit_name} because the expense increases. Credit {credit_name} because cash or bank reduces."
        )
    if voucher_type == VoucherType.receipt:
        return (
            f"This is a receipt voucher because money was received. Debit {debit_name} because cash or bank increases. "
            f"Credit {credit_name} because receivable reduces or income is recognized."
        )
    if voucher_type == VoucherType.contra:
        return f"This is a contra voucher because funds move between cash and bank. Debit {debit_name}; credit {credit_name}."
    if voucher_type == VoucherType.sales:
        return f"This is a sales voucher. Debit {debit_name} for the customer receivable and credit {credit_name} for revenue."
    if voucher_type == VoucherType.purchase:
        return f"This is a purchase voucher. Debit {debit_name} for purchase cost and credit {credit_name} for supplier payable."
    return f"This is a journal voucher inferred from: {text}. Debit {debit_name}; credit {credit_name}."


def validate_suggested_lines(lines: list[PostingLine]) -> list[str]:
    try:
        validate_double_entry(lines)
    except AccountingValidationError as exc:
        return [str(exc)]
    return []
