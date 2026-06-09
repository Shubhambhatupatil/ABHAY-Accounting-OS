from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.routes.accounting import repo_for_company, user_uuid, voucher_response
from app.core.config import Settings, get_settings
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.engine import AccountingValidationError, PostingLine, money
from app.domain.ai.accountant import parse_with_openai, validate_suggested_lines
from app.models.accounting import AiSuggestion, AiSuggestionStatus, Ledger, LedgerCategory, VoucherType
from app.repositories.accounting import AccountingRepository
from app.schemas.accounting import VoucherCreate, VoucherLineCreate
from app.schemas.ai_accountant import (
    AiVoucherSuggestionResponse,
    ConfirmAiPostingRequest,
    ConfirmAiPostingResponse,
    NaturalLanguageEntryRequest,
    RejectAiSuggestionRequest,
    SuggestedLedger,
    SuggestedPostingLine,
)

router = APIRouter(prefix="/companies/{company_id}/ai-accountant", tags=["ai-accountant"])


@router.post("/parse", response_model=AiVoucherSuggestionResponse)
async def parse_natural_language_transaction(
    company_id: UUID,
    payload: NaturalLanguageEntryRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiVoucherSuggestionResponse:
    repo = repo_for_company(company_id, user, db)
    return await create_ai_suggestion(company_id, payload, user, db, repo, settings)


@router.post("/suggest-voucher", response_model=AiVoucherSuggestionResponse)
async def suggest_voucher(
    company_id: UUID,
    payload: NaturalLanguageEntryRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiVoucherSuggestionResponse:
    repo = repo_for_company(company_id, user, db)
    return await create_ai_suggestion(company_id, payload, user, db, repo, settings)


@router.post("/confirm", response_model=ConfirmAiPostingResponse)
def confirm_posting(
    company_id: UUID,
    payload: ConfirmAiPostingRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> ConfirmAiPostingResponse:
    repo = repo_for_company(company_id, user, db)
    suggestion = get_suggestion_or_404(db, company_id, payload.suggestion_id, user_uuid(user))
    if suggestion.status != AiSuggestionStatus.needs_review:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Only reviewable AI suggestions can be posted.",
        )

    proposed = suggestion.proposed_payload
    lines = proposed.get("lines", [])
    if not lines or any(line.get("ledger_id") is None for line in lines):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="AI suggestion has missing ledgers. Create/select ledgers before posting.",
        )

    voucher_payload = VoucherCreate(
        voucher_type=proposed["voucher_type"],
        voucher_date=proposed["voucher_date"],
        narration=f"ABHAY AI approved: {suggestion.input_text}",
        lines=[
            VoucherLineCreate(
                ledger_id=UUID(line["ledger_id"]),
                debit=money(line["debit"]),
                credit=money(line["credit"]),
                narration=line.get("reason"),
            )
            for line in lines
        ],
    )
    try:
        voucher = repo.create_voucher(company_id, user_uuid(user), voucher_payload)
    except AccountingValidationError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc

    suggestion.status = AiSuggestionStatus.approved
    suggestion.approved_voucher_id = voucher.id
    suggestion.decided_at = datetime.now(timezone.utc)
    suggestion.decided_by = user_uuid(user)
    db.commit()
    return ConfirmAiPostingResponse(
        suggestion_id=suggestion.id,
        voucher=voucher_response(voucher),
    )


@router.post("/suggestions/{suggestion_id}/reject", response_model=AiVoucherSuggestionResponse)
def reject_suggestion(
    company_id: UUID,
    suggestion_id: UUID,
    payload: RejectAiSuggestionRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiVoucherSuggestionResponse:
    repo_for_company(company_id, user, db)
    suggestion = get_suggestion_or_404(db, company_id, suggestion_id, user_uuid(user))
    suggestion.status = AiSuggestionStatus.rejected
    suggestion.decided_at = datetime.now(timezone.utc)
    suggestion.decided_by = user_uuid(user)
    suggestion.validation_errors = [*suggestion.validation_errors, f"Rejected: {payload.reason}"]
    db.commit()
    return suggestion_response(suggestion)


async def create_ai_suggestion(
    company_id: UUID,
    payload: NaturalLanguageEntryRequest,
    user: AuthenticatedUser,
    db: Session,
    repo: AccountingRepository,
    settings: Settings,
) -> AiVoucherSuggestionResponse:
    transaction_date = payload.transaction_date or datetime.now(timezone.utc).date()
    ledgers = [row[0] for row in repo.list_ledgers(company_id, None, None, None, False)]
    parsed = await parse_with_openai(payload.text, transaction_date, ledgers, settings.openai_api_key)

    proposed_lines = build_proposed_lines(parsed, ledgers)
    validation_errors = []
    missing_ledgers = [line["ledger_name"] for line in proposed_lines if line["ledger_id"] is None]
    validation_errors.extend(f"Ledger not found: {ledger_name}" for ledger_name in missing_ledgers)
    if not missing_ledgers:
        validation_errors.extend(
            validate_suggested_lines(
                [
                    PostingLine(UUID(line["ledger_id"]), money(line["debit"]), money(line["credit"]))
                    for line in proposed_lines
                ]
            )
        )

    proposed_payload = {
        "voucher_type": parsed.voucher_type.value,
        "voucher_date": parsed.voucher_date.isoformat(),
        "amount": str(parsed.amount),
        "gst_applicable": parsed.gst_applicable,
        "suggested_gst_rate": str(parsed.suggested_gst_rate) if parsed.suggested_gst_rate else None,
        "explanation": parsed.explanation,
        "lines": proposed_lines,
    }
    suggestion = AiSuggestion(
        company_id=company_id,
        requested_by=user_uuid(user),
        input_text=payload.text,
        input_language=payload.language,
        intent=parsed.voucher_type.value,
        status=AiSuggestionStatus.needs_review,
        confidence=parsed.confidence,
        proposed_payload=proposed_payload,
        validation_errors=validation_errors,
        approved_voucher_id=None,
        model_name=parsed.model_name,
        created_at=datetime.now(timezone.utc),
        decided_at=None,
        decided_by=None,
    )
    db.add(suggestion)
    db.commit()
    db.refresh(suggestion)
    return suggestion_response(suggestion)


def build_proposed_lines(parsed, ledgers: list[Ledger]) -> list[dict[str, str | None]]:
    amount = money(parsed.amount)
    gst_rate = money(parsed.suggested_gst_rate or Decimal("0.00"))
    gst_amount = money(amount * gst_rate / Decimal("100.00")) if parsed.gst_applicable and gst_rate > 0 else Decimal("0.00")
    total_amount = money(amount + gst_amount)

    if parsed.gst_applicable and parsed.voucher_type == VoucherType.purchase:
        return [
            line_payload(ledgers, parsed.debit_ledger_name, parsed.debit_category, amount, Decimal("0.00")),
            line_payload(ledgers, "Input GST", LedgerCategory.input_gst, gst_amount, Decimal("0.00")),
            line_payload(ledgers, parsed.credit_ledger_name, parsed.credit_category, Decimal("0.00"), total_amount),
        ]
    if parsed.gst_applicable and parsed.voucher_type == VoucherType.sales:
        return [
            line_payload(ledgers, parsed.debit_ledger_name, parsed.debit_category, total_amount, Decimal("0.00")),
            line_payload(ledgers, parsed.credit_ledger_name, parsed.credit_category, Decimal("0.00"), amount),
            line_payload(ledgers, "Output GST", LedgerCategory.output_gst, Decimal("0.00"), gst_amount),
        ]
    return [
        line_payload(ledgers, parsed.debit_ledger_name, parsed.debit_category, amount, Decimal("0.00")),
        line_payload(ledgers, parsed.credit_ledger_name, parsed.credit_category, Decimal("0.00"), amount),
    ]


def line_payload(
    ledgers: list[Ledger],
    ledger_name: str,
    category: LedgerCategory,
    debit: Decimal,
    credit: Decimal,
) -> dict[str, str | None]:
    ledger = find_ledger(ledgers, ledger_name, category)
    side = "Debit" if debit > 0 else "Credit"
    reason = debit_reason(category) if debit > 0 else credit_reason(category)
    return {
        "ledger_id": str(ledger.id) if ledger else None,
        "ledger_name": ledger.name if ledger else ledger_name,
        "category": category.value,
        "debit": str(money(debit)),
        "credit": str(money(credit)),
        "reason": f"{side} {ledger.name if ledger else ledger_name}: {reason}",
    }


def get_suggestion_or_404(
    db: Session,
    company_id: UUID,
    suggestion_id: UUID,
    user_id: UUID,
) -> AiSuggestion:
    suggestion = db.scalar(
        select(AiSuggestion).where(
            AiSuggestion.company_id == company_id,
            AiSuggestion.id == suggestion_id,
            AiSuggestion.requested_by == user_id,
        )
    )
    if suggestion is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="AI suggestion not found.")
    return suggestion


def find_ledger(
    ledgers: list[Ledger],
    target_name: str,
    target_category: LedgerCategory,
) -> Ledger | None:
    normalized_target = target_name.lower()
    for ledger in ledgers:
        if ledger.name.lower() == normalized_target and ledger.category == target_category:
            return ledger
    if normalized_target in {"mobile recharge expense", "communication expense", "internet & phone expense"}:
        communication_names = ["mobile recharge expense", "communication expense", "internet & phone expense"]
        for name in communication_names:
            for ledger in ledgers:
                if ledger.name.lower() == name and ledger.category == target_category:
                    return ledger
        return None
    for ledger in ledgers:
        if ledger.category == target_category:
            return ledger
    for ledger in ledgers:
        if ledger.name.lower() == normalized_target:
            return ledger
    return None


def suggestion_response(suggestion: AiSuggestion) -> AiVoucherSuggestionResponse:
    payload = suggestion.proposed_payload
    lines = payload["lines"]
    return AiVoucherSuggestionResponse(
        suggestion_id=suggestion.id,
        input_text=suggestion.input_text,
        voucher_type=payload["voucher_type"],
        voucher_date=payload["voucher_date"],
        amount=money(payload["amount"]),
        confidence=suggestion.confidence,
        gst_applicable=bool(payload["gst_applicable"]),
        suggested_gst_rate=(
            money(payload["suggested_gst_rate"]) if payload["suggested_gst_rate"] is not None else None
        ),
        suggested_ledgers=[
            SuggestedLedger(
                ledger_id=UUID(line["ledger_id"]) if line["ledger_id"] else None,
                ledger_name=line["ledger_name"],
                category=line["category"],
                reason=line["reason"],
                should_create=line["ledger_id"] is None,
            )
            for line in lines
        ],
        lines=[
            SuggestedPostingLine(
                ledger_id=UUID(line["ledger_id"]) if line["ledger_id"] else None,
                ledger_name=line["ledger_name"],
                debit=money(line["debit"]),
                credit=money(line["credit"]),
                reason=line["reason"],
            )
            for line in lines
        ],
        explanation=payload["explanation"],
        validation_errors=suggestion.validation_errors,
        can_post=not suggestion.validation_errors,
        model_name=suggestion.model_name,
    )


def debit_reason(category: LedgerCategory) -> str:
    if category in {LedgerCategory.direct_expense, LedgerCategory.indirect_expense, LedgerCategory.purchase}:
        return "expense or purchase increases"
    if category in {LedgerCategory.cash, LedgerCategory.bank, LedgerCategory.sundry_debtor}:
        return "asset or receivable increases"
    return "debit side is required for this accounting event"


def credit_reason(category: LedgerCategory) -> str:
    if category in {LedgerCategory.cash, LedgerCategory.bank}:
        return "cash or bank decreases"
    if category in {LedgerCategory.sales, LedgerCategory.direct_income, LedgerCategory.indirect_income}:
        return "income increases"
    if category == LedgerCategory.sundry_creditor:
        return "supplier payable increases"
    return "credit side is required for this accounting event"
