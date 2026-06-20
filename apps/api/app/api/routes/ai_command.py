import ast
import logging
import operator
import re
from typing import Any
from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.models.accounting import AiLog, AuditLog, Company

router = APIRouter(prefix="/ai", tags=["ai-command"])
logger = logging.getLogger(__name__)


class AiCommandRequest(BaseModel):
    command: str = Field(min_length=1, max_length=1000)
    context: dict[str, Any] | None = None


class AiCommandResponse(BaseModel):
    ok: bool
    summary: str
    actions: list[str]
    confidence: float
    calculation: str | None = None
    base_amount: float | None = None
    gst_rate: float | None = None
    gst_amount: float | None = None
    total: float | None = None


@router.post("/command", response_model=AiCommandResponse)
def run_ai_command(
    payload: AiCommandRequest,
    db: Session = Depends(get_db),
) -> AiCommandResponse:
    command = payload.command.strip().lower()
    calculation = calculate_command_amount(command)
    if calculation:
        response = build_calculation_response(command, calculation)
        persist_ai_command_log_safely(db, payload, response)
        return response

    if "invoice" in command or "bill" in command:
        response = AiCommandResponse(
            ok=True,
            summary="ABHAY can route this through the invoice upload and extraction workflow for human review.",
            actions=[
                "Upload a text PDF invoice or bill.",
                "Review extracted party, GSTIN, taxable amount and tax split.",
                "Approve the suggested voucher only after checking the debit and credit lines.",
            ],
            confidence=0.86,
        )
        persist_ai_command_log_safely(db, payload, response)
        return response

    if "gst" in command:
        response = AiCommandResponse(
            ok=True,
            summary="ABHAY can prepare GST-ready assistance, but filing values still need CA review.",
            actions=[
                "Review output GST, input GST and net payable.",
                "Check GSTIN, place of supply and CGST/SGST versus IGST treatment.",
                "Resolve missing GST rates before using the report for filing decisions.",
            ],
            confidence=0.84,
        )
        persist_ai_command_log_safely(db, payload, response)
        return response

    if "ledger" in command:
        response = AiCommandResponse(
            ok=True,
            summary="ABHAY can help map this to the right ledger group and remember corrections for future suggestions.",
            actions=[
                "Search existing ledgers for a close match.",
                "Suggest a new ledger only if no suitable ledger exists.",
                "Store user correction as Smart Ledger Memory after review.",
            ],
            confidence=0.82,
        )
        persist_ai_command_log_safely(db, payload, response)
        return response

    response = AiCommandResponse(
        ok=True,
        summary="ABHAY Alpha can analyze this accounting request and suggest the safest next review workflow.",
        actions=[
            "Convert the instruction into a reviewable accounting task.",
            "Check whether a voucher, ledger, GST review or invoice workflow is needed.",
            "Ask for human approval before any accounting entry is posted.",
        ],
        confidence=0.78,
    )
    persist_ai_command_log_safely(db, payload, response)
    return response


class CommandCalculation(BaseModel):
    calculation: str
    base_amount: float
    gst_rate: float | None = None
    gst_amount: float | None = None
    total: float | None = None


def build_calculation_response(command: str, calculation: CommandCalculation) -> AiCommandResponse:
    actions = [
        "Review ledger mapping and voucher type before posting.",
        "Keep the accounting entry in suggestion preview until a human approves it.",
    ]
    if calculation.gst_rate is None:
        actions.insert(0, "Which GST rate applies: 0%, 5%, 12%, 18% or 28%?")
        summary = (
            f"Base amount calculated as {format_amount(calculation.base_amount)}. "
            "GST rate is not specified, so ABHAY needs the applicable GST rate before finalizing tax and total."
        )
        confidence = 0.8
    else:
        summary = (
            f"Base amount {format_amount(calculation.base_amount)}, GST "
            f"({format_amount(calculation.gst_rate)}%) {format_amount(calculation.gst_amount or 0)}, "
            f"total {format_amount(calculation.total or calculation.base_amount)}."
        )
        actions.insert(0, "Use the calculated base, GST and total in the voucher preview.")
        confidence = 0.9

    if "invoice" in command or "bill" in command:
        actions.append("Route attached bill or invoice details through the invoice review workflow.")
    if "gst" in command:
        actions.append("Verify GST treatment with CA review before filing.")
    if "ledger" in command or any(word in command for word in ("recharge", "phone", "internet", "expense")):
        actions.append("Suggest the closest expense or party ledger and remember user corrections.")

    return AiCommandResponse(
        ok=True,
        summary=summary,
        actions=actions,
        confidence=confidence,
        calculation=calculation.calculation,
        base_amount=round(calculation.base_amount, 2),
        gst_rate=calculation.gst_rate,
        gst_amount=round(calculation.gst_amount, 2) if calculation.gst_amount is not None else None,
        total=round(calculation.total, 2) if calculation.total is not None else None,
    )


def calculate_command_amount(command: str) -> CommandCalculation | None:
    gst_rate = extract_gst_rate(command)
    amount_text = remove_gst_rate(command)
    expression_result = calculate_explicit_expression(amount_text)
    if expression_result:
        calculation_text, base_amount = expression_result
    else:
        implicit_result = calculate_implicit_amount(amount_text)
        if not implicit_result:
            return None
        calculation_text, base_amount = implicit_result

    if gst_rate is None:
        return CommandCalculation(calculation=calculation_text, base_amount=base_amount)

    gst_amount = base_amount * gst_rate / 100
    total = base_amount + gst_amount
    return CommandCalculation(
        calculation=calculation_text,
        base_amount=base_amount,
        gst_rate=gst_rate,
        gst_amount=gst_amount,
        total=total,
    )


def extract_gst_rate(command: str) -> float | None:
    patterns = [
        r"\bgst\s*(?:rate)?\s*(?:@|at|is|of)?\s*(\d+(?:\.\d+)?)\s*%",
        r"(\d+(?:\.\d+)?)\s*%\s*gst\b",
    ]
    for pattern in patterns:
        match = re.search(pattern, command, flags=re.IGNORECASE)
        if match:
            return float(match.group(1))
    return None


def remove_gst_rate(command: str) -> str:
    without_gst = re.sub(
        r"\bgst\s*(?:rate)?\s*(?:@|at|is|of)?\s*\d+(?:\.\d+)?\s*%",
        "",
        command,
        flags=re.IGNORECASE,
    )
    return re.sub(r"\d+(?:\.\d+)?\s*%\s*gst\b", "", without_gst, flags=re.IGNORECASE)


def calculate_explicit_expression(command: str) -> tuple[str, float] | None:
    normalized = normalize_math_text(command)
    percentage_result = calculate_percentage_of(normalized)
    if percentage_result:
        return percentage_result

    expression_match = re.search(
        r"(\d+(?:\.\d+)?(?:\s*[+\-*/]\s*\d+(?:\.\d+)?)+)",
        normalized,
    )
    if not expression_match:
        return None
    expression = expression_match.group(1)
    try:
        value = evaluate_arithmetic_expression(expression)
    except (ValueError, ZeroDivisionError):
        return None
    return expression, value


def calculate_percentage_of(command: str) -> tuple[str, float] | None:
    match = re.search(r"(\d+(?:\.\d+)?)\s*%\s*(?:of|on)\s*(\d+(?:\.\d+)?)", command)
    if not match:
        return None
    percent = float(match.group(1))
    amount = float(match.group(2))
    return f"{percent}% of {amount}", amount * percent / 100


def calculate_implicit_amount(command: str) -> tuple[str, float] | None:
    numbers = [float(value) for value in re.findall(r"\d+(?:\.\d+)?", command)]
    if not numbers:
        return None
    has_quantity_language = any(
        word in command
        for word in ("people", "person", "persons", "qty", "quantity", "units", "each", "per")
    )
    has_multiply_signal = any(symbol in command for symbol in ("*", "x", "×"))
    if len(numbers) >= 2 and (has_quantity_language or has_multiply_signal):
        quantity, rate = infer_quantity_and_rate(command, numbers)
        return f"{format_amount(quantity)} x {format_amount(rate)}", quantity * rate
    if len(numbers) == 1:
        return format_amount(numbers[0]), numbers[0]
    return None


def infer_quantity_and_rate(command: str, numbers: list[float]) -> tuple[float, float]:
    quantity_match = re.search(
        r"(\d+(?:\.\d+)?)\s*(?:people|person|persons|qty|quantity|units)",
        command,
        flags=re.IGNORECASE,
    )
    if quantity_match:
        quantity = float(quantity_match.group(1))
        rate = next((number for number in numbers if number != quantity), numbers[0])
        return quantity, rate
    return numbers[0], numbers[1]


def normalize_math_text(command: str) -> str:
    return (
        command.replace("×", "*")
        .replace("x", "*")
        .replace("÷", "/")
        .replace("plus", "+")
        .replace("minus", "-")
        .replace("multiplied by", "*")
        .replace("divided by", "/")
    )


ALLOWED_ARITHMETIC_OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
}


def evaluate_arithmetic_expression(expression: str) -> float:
    node = ast.parse(expression, mode="eval")
    return float(evaluate_arithmetic_node(node.body))


def evaluate_arithmetic_node(node: ast.AST) -> float:
    if isinstance(node, ast.Constant) and isinstance(node.value, int | float):
        return float(node.value)
    if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
        return -evaluate_arithmetic_node(node.operand)
    if isinstance(node, ast.BinOp) and type(node.op) in ALLOWED_ARITHMETIC_OPERATORS:
        left = evaluate_arithmetic_node(node.left)
        right = evaluate_arithmetic_node(node.right)
        return ALLOWED_ARITHMETIC_OPERATORS[type(node.op)](left, right)
    raise ValueError("Unsupported arithmetic expression")


def format_amount(value: float) -> str:
    if value.is_integer():
        return str(int(value))
    return f"{value:.2f}".rstrip("0").rstrip(".")


def persist_ai_command_log_safely(
    db: Session,
    payload: AiCommandRequest,
    response: AiCommandResponse,
) -> None:
    try:
        persist_ai_command_log(db, payload, response)
    except SQLAlchemyError as exc:
        db.rollback()
        logger.warning("ABHAY AI command log persistence failed: %s", exc.__class__.__name__)


def persist_ai_command_log(db: Session, payload: AiCommandRequest, response: AiCommandResponse) -> None:
    context = payload.context or {}
    company_id = parse_uuid(context.get("companyId") or context.get("company_id"))
    profile_id = parse_uuid(context.get("profileId") or context.get("profile_id"))
    if company_id and db.get(Company, company_id) is None:
        company_id = None
    now = datetime.now(timezone.utc)
    db.add(
        AiLog(
            id=uuid4(),
            company_id=company_id,
            profile_id=profile_id,
            action_type="ai.command",
            input_payload={"command": payload.command, "context": context},
            output_payload=response.model_dump(),
            confidence=Decimal(str(response.confidence)),
            created_at=now,
        )
    )
    db.add(
        AuditLog(
            id=uuid4(),
            company_id=company_id,
            actor_id=profile_id,
            action_type="ai_command.created",
            entity_type="ai_log",
            entity_id=None,
            event_payload={"command": payload.command, "summary": response.summary},
            created_at=now,
        )
    )
    db.commit()


def parse_uuid(value: Any) -> UUID | None:
    if not value:
        return None
    try:
        return UUID(str(value))
    except ValueError:
        return None
