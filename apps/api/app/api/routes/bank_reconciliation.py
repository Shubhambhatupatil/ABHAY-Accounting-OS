from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.api.routes.accounting import repo_for_company, user_uuid
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.engine import money
from app.domain.banking.reconciliation import parse_bank_csv, suggest_best_match
from app.models.accounting import ReconciliationStatus
from app.schemas.bank_reconciliation import (
    BankStatementUploadRequest,
    BankStatementUploadResponse,
    BankTransactionResponse,
    ConfirmMatchRequest,
    IgnoreTransactionRequest,
    ReconciliationSummary,
    SuggestedMatchResponse,
)

router = APIRouter(
    prefix="/companies/{company_id}/bank-reconciliation",
    tags=["bank-reconciliation"],
)


@router.post("/upload", response_model=BankStatementUploadResponse)
def upload_bank_statement(
    company_id: UUID,
    payload: BankStatementUploadRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> BankStatementUploadResponse:
    repo = repo_for_company(company_id, user, db)
    try:
        parsed = parse_bank_csv(payload.csv_content)
        bank_account = repo.get_or_create_bank_account(company_id, payload.bank_ledger_id, payload.bank_name)
        statement = repo.create_bank_statement(
            company_id,
            user_uuid(user),
            bank_account,
            payload.filename,
            parsed,
        )
    except (LookupError, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
    return BankStatementUploadResponse(statement_id=statement.id, imported_count=len(parsed))


@router.get("/transactions", response_model=list[BankTransactionResponse])
def list_transactions(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[BankTransactionResponse]:
    repo = repo_for_company(company_id, user, db)
    return [transaction_response(item) for item in repo.list_bank_transactions(company_id)]


@router.get("/suggest-matches", response_model=list[SuggestedMatchResponse])
def suggest_matches(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[SuggestedMatchResponse]:
    repo = repo_for_company(company_id, user, db)
    candidates = repo.bank_match_candidates(company_id)
    suggestions: list[SuggestedMatchResponse] = []
    for transaction in repo.list_bank_transactions(company_id):
        if transaction.reconciliation_status not in {
            ReconciliationStatus.unmatched,
            ReconciliationStatus.suggested_match,
        }:
            continue
        suggestion = suggest_best_match(
            transaction.id,
            transaction.transaction_date,
            transaction.description,
            money(transaction.debit),
            money(transaction.credit),
            candidates,
        )
        if suggestion is None:
            continue
        transaction.reconciliation_status = ReconciliationStatus.suggested_match
        suggestions.append(
            SuggestedMatchResponse(
                bank_transaction_id=suggestion.bank_transaction_id,
                voucher_id=suggestion.voucher_id,
                journal_entry_id=suggestion.journal_entry_id,
                voucher_number=suggestion.voucher_number,
                confidence=suggestion.confidence,
                reason=suggestion.reason,
            )
        )
    db.commit()
    return suggestions


@router.post("/confirm-match", status_code=status.HTTP_204_NO_CONTENT)
def confirm_match(
    company_id: UUID,
    payload: ConfirmMatchRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> None:
    repo = repo_for_company(company_id, user, db)
    try:
        repo.confirm_reconciliation_match(
            company_id,
            user_uuid(user),
            payload.bank_transaction_id,
            payload.journal_entry_id,
            payload.confidence,
        )
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.post("/ignore", status_code=status.HTTP_204_NO_CONTENT)
def ignore_transaction(
    company_id: UUID,
    payload: IgnoreTransactionRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> None:
    repo = repo_for_company(company_id, user, db)
    try:
        repo.update_bank_transaction_status(company_id, payload.bank_transaction_id, "ignored")
    except LookupError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc


@router.get("/summary", response_model=ReconciliationSummary)
def reconciliation_summary(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> ReconciliationSummary:
    repo = repo_for_company(company_id, user, db)
    transactions = repo.list_bank_transactions(company_id)
    counts = {"matched": 0, "unmatched": 0, "suggested_match": 0, "ignored": 0}
    matched_amount = Decimal("0.00")
    unreconciled_amount = Decimal("0.00")
    for item in transactions:
        status_value = item.reconciliation_status.value
        counts[status_value] = counts.get(status_value, 0) + 1
        amount = money(item.debit if item.debit > 0 else item.credit)
        if status_value == "matched":
            matched_amount += amount
        elif status_value != "ignored":
            unreconciled_amount += amount
    return ReconciliationSummary(
        total_transactions=len(transactions),
        matched=counts["matched"],
        unmatched=counts["unmatched"],
        suggested_match=counts["suggested_match"],
        ignored=counts["ignored"],
        matched_amount=money(matched_amount),
        unreconciled_amount=money(unreconciled_amount),
    )


def transaction_response(item) -> BankTransactionResponse:
    return BankTransactionResponse(
        id=item.id,
        transaction_date=item.transaction_date,
        description=item.description,
        reference_number=item.reference_number,
        debit=item.debit,
        credit=item.credit,
        balance=item.balance,
        reconciliation_status=item.reconciliation_status.value,
    )
