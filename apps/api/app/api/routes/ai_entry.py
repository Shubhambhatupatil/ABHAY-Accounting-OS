import base64
import re
from datetime import datetime, timezone
from decimal import Decimal
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.api.routes.accounting import repo_for_company, user_uuid, voucher_response
from app.api.routes.ai_accountant import create_ai_suggestion, get_suggestion_or_404, suggestion_response
from app.core.config import Settings, get_settings
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.engine import AccountingValidationError, money
from app.domain.accounting.financial_intelligence import cashflow_intelligence, gst_intelligence, month_period, profit_intelligence
from app.models.accounting import (
    AiFeedbackExample,
    AiSuggestion,
    AiSuggestionStatus,
    BankTransaction,
    Invoice,
    Ledger,
    ReconciliationStatus,
)
from app.schemas.accounting import VoucherCreate, VoucherLineCreate
from app.schemas.ai_accountant import NaturalLanguageEntryRequest
from app.schemas.ai_entry import (
    AiAccuracyDashboardResponse,
    AiCorrectionResponse,
    AiEntryApproveRequest,
    AiEntryApproveResponse,
    AiEntryPdfRequest,
    AiEntryRejectRequest,
    AiEntrySuggestionListItem,
    AiEntrySuggestionResponse,
    AiEntryTextRequest,
    AiExtractedInvoiceFields,
    AiMonthEndReadinessResponse,
    AiOwnerReportResponse,
)

router = APIRouter(prefix="/companies/{company_id}/ai-entry", tags=["ai-entry"])


@router.post("/parse-text", response_model=AiEntrySuggestionResponse)
async def parse_text_entry(
    company_id: UUID,
    payload: AiEntryTextRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiEntrySuggestionResponse:
    repo = repo_for_company(company_id, user, db)
    suggestion = await create_ai_suggestion(
        company_id,
        NaturalLanguageEntryRequest(
            text=payload.text,
            transaction_date=payload.transaction_date,
            language=payload.language,
        ),
        user,
        db,
        repo,
        settings,
    )
    suggestion = apply_smart_ledger_memory(db, company_id, user_uuid(user), payload.text, suggestion)
    return enriched_workbench_response(db, company_id, suggestion)


@router.post("/upload-pdf", response_model=AiEntrySuggestionResponse)
async def upload_pdf_entry(
    company_id: UUID,
    payload: AiEntryPdfRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiEntrySuggestionResponse:
    repo = repo_for_company(company_id, user, db)
    try:
        pdf_bytes = base64.b64decode(payload.file_base64, validate=True)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Invalid base64 PDF content.") from exc

    extracted_text = extract_text_from_pdf_bytes(pdf_bytes)
    invoice_fields = extract_invoice_fields(extracted_text)
    if not extracted_text.strip():
        invoice_fields.extraction_warning = "This appears to be a scanned-image PDF. Text OCR is required before AI posting."
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=invoice_fields.extraction_warning,
        )

    suggestion = await create_ai_suggestion(
        company_id,
        NaturalLanguageEntryRequest(
            text=invoice_text_for_parser(extracted_text, invoice_fields),
            transaction_date=payload.transaction_date or invoice_fields.invoice_date,
            language=payload.language,
        ),
        user,
        db,
        repo,
        settings,
    )
    suggestion = apply_smart_ledger_memory(db, company_id, user_uuid(user), extracted_text, suggestion)
    return enriched_workbench_response(db, company_id, suggestion, extracted_invoice=invoice_fields)


@router.get("/suggestions", response_model=list[AiEntrySuggestionListItem])
def list_suggestions(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[AiEntrySuggestionListItem]:
    repo_for_company(company_id, user, db)
    rows = db.scalars(
        select(AiSuggestion)
        .where(AiSuggestion.company_id == company_id, AiSuggestion.requested_by == user_uuid(user))
        .order_by(AiSuggestion.created_at.desc())
        .limit(50)
    ).all()
    return [
        AiEntrySuggestionListItem(
            suggestion_id=row.id,
            input_text=row.input_text,
            status=row.status,
            confidence=row.confidence,
            voucher_type=row.proposed_payload["voucher_type"],
            amount=money(row.proposed_payload["amount"]),
            created_at=row.created_at.isoformat(),
            decided_at=row.decided_at.isoformat() if row.decided_at else None,
        )
        for row in rows
    ]


@router.post("/inbox", response_model=AiEntrySuggestionResponse)
async def autopilot_inbox(
    company_id: UUID,
    payload: AiEntryTextRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    settings: Settings = Depends(get_settings),
) -> AiEntrySuggestionResponse:
    return await parse_text_entry(company_id, payload, user, db, settings)


@router.post("/approve", response_model=AiEntryApproveResponse)
def approve_suggestion(
    company_id: UUID,
    payload: AiEntryApproveRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiEntryApproveResponse:
    repo = repo_for_company(company_id, user, db)
    suggestion = get_suggestion_or_404(db, company_id, payload.suggestion_id, user_uuid(user))
    if suggestion.status != AiSuggestionStatus.needs_review:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Only reviewable AI suggestions can be approved.")

    proposed = payload.corrected_payload or suggestion.proposed_payload
    lines = proposed.get("lines", [])
    if not lines or any(line.get("ledger_id") is None for line in lines):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="All posting lines must map to ledgers.")

    voucher_payload = VoucherCreate(
        voucher_type=proposed["voucher_type"],
        voucher_date=proposed["voucher_date"],
        narration=f"ABHAY AI Workbench approved: {suggestion.input_text}",
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

    correction_recorded = payload.corrected_payload is not None
    if correction_recorded:
        db.add(
            AiFeedbackExample(
                company_id=company_id,
                ai_suggestion_id=suggestion.id,
                corrected_payload={
                    "source_text": suggestion.input_text,
                    "correction_note": payload.correction_note,
                    "payload": payload.corrected_payload,
                },
                created_by=user_uuid(user),
                created_at=datetime.now(timezone.utc),
            )
        )
        suggestion.proposed_payload = payload.corrected_payload

    suggestion.status = AiSuggestionStatus.approved
    suggestion.approved_voucher_id = voucher.id
    suggestion.decided_at = datetime.now(timezone.utc)
    suggestion.decided_by = user_uuid(user)
    db.commit()
    return AiEntryApproveResponse(
        suggestion_id=suggestion.id,
        voucher=voucher_response(voucher),
        correction_recorded=correction_recorded,
    )


@router.post("/reject", response_model=AiEntrySuggestionResponse)
def reject_suggestion(
    company_id: UUID,
    payload: AiEntryRejectRequest,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiEntrySuggestionResponse:
    repo_for_company(company_id, user, db)
    suggestion = get_suggestion_or_404(db, company_id, payload.suggestion_id, user_uuid(user))
    suggestion.status = AiSuggestionStatus.rejected
    suggestion.validation_errors = [*suggestion.validation_errors, f"Rejected: {payload.reason}"]
    suggestion.decided_at = datetime.now(timezone.utc)
    suggestion.decided_by = user_uuid(user)
    db.commit()
    return enriched_workbench_response(db, company_id, suggestion_response(suggestion))


@router.get("/corrections", response_model=list[AiCorrectionResponse])
def list_corrections(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> list[AiCorrectionResponse]:
    repo_for_company(company_id, user, db)
    rows = db.scalars(
        select(AiFeedbackExample)
        .where(AiFeedbackExample.company_id == company_id, AiFeedbackExample.created_by == user_uuid(user))
        .order_by(AiFeedbackExample.created_at.desc())
        .limit(25)
    ).all()
    return [
        AiCorrectionResponse(
            id=row.id,
            suggestion_id=row.ai_suggestion_id,
            corrected_payload=row.corrected_payload,
            created_at=row.created_at.isoformat(),
        )
        for row in rows
    ]


@router.get("/accuracy-dashboard", response_model=AiAccuracyDashboardResponse)
def accuracy_dashboard(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiAccuracyDashboardResponse:
    repo_for_company(company_id, user, db)
    filters = [AiSuggestion.company_id == company_id, AiSuggestion.requested_by == user_uuid(user)]
    total = db.scalar(select(func.count(AiSuggestion.id)).where(*filters)) or 0
    approved = db.scalar(
        select(func.count(AiSuggestion.id)).where(*filters, AiSuggestion.status == AiSuggestionStatus.approved)
    ) or 0
    rejected = db.scalar(
        select(func.count(AiSuggestion.id)).where(*filters, AiSuggestion.status == AiSuggestionStatus.rejected)
    ) or 0
    corrected = db.scalar(
        select(func.count()).where(AiFeedbackExample.company_id == company_id, AiFeedbackExample.created_by == user_uuid(user))
    ) or 0
    approved_without_edit = max(approved - corrected, 0)
    average_confidence = db.scalar(select(func.coalesce(func.avg(AiSuggestion.confidence), 0)).where(*filters)) or 0
    return AiAccuracyDashboardResponse(
        total_suggestions=total,
        approved_suggestions=approved,
        approved_without_edit=approved_without_edit,
        rejected_suggestions=rejected,
        corrected_suggestions=corrected,
        approval_rate=ratio(approved, total),
        correction_rate=ratio(corrected, total),
        estimated_accuracy=ratio(approved_without_edit, total),
        average_confidence=money(average_confidence),
    )


@router.get("/month-end-readiness", response_model=AiMonthEndReadinessResponse)
def month_end_readiness(
    company_id: UUID,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiMonthEndReadinessResponse:
    repo_for_company(company_id, user, db)
    pending = db.scalar(
        select(func.count(AiSuggestion.id)).where(
            AiSuggestion.company_id == company_id,
            AiSuggestion.status == AiSuggestionStatus.needs_review,
        )
    ) or 0
    unreconciled = db.scalar(
        select(func.count(BankTransaction.id)).where(
            BankTransaction.company_id == company_id,
            BankTransaction.reconciliation_status.in_([ReconciliationStatus.unmatched, ReconciliationStatus.suggested_match]),
        )
    ) or 0
    gst_risk_count = db.scalar(
        select(func.count(AiSuggestion.id)).where(
            AiSuggestion.company_id == company_id,
            AiSuggestion.proposed_payload["gst_applicable"].as_boolean().is_(True),
            func.jsonb_array_length(AiSuggestion.validation_errors) > 0,
        )
    ) or 0
    missing_bills = db.scalar(
        select(func.count(AiSuggestion.id)).where(
            AiSuggestion.company_id == company_id,
            AiSuggestion.input_text.ilike("%bill%"),
            AiSuggestion.status != AiSuggestionStatus.approved,
        )
    ) or 0
    deductions = (pending * 8) + (unreconciled * 5) + (gst_risk_count * 10) + (missing_bills * 6)
    score = max(Decimal("0.00"), Decimal("100.00") - Decimal(deductions))
    return AiMonthEndReadinessResponse(
        books_completion_percent=score,
        pending_vouchers=pending,
        unreconciled_bank_entries=unreconciled,
        gst_risk_count=gst_risk_count,
        missing_bill_count=missing_bills,
        readiness_score=score,
    )


@router.get("/owner-report", response_model=AiOwnerReportResponse)
def owner_report(
    company_id: UUID,
    month: datetime | None = None,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
) -> AiOwnerReportResponse:
    repo = repo_for_company(company_id, user, db)
    period = month_period(month.date() if month else None)
    rows = repo.monthly_ledger_balances(company_id, period.starts_on, period.ends_on)
    profit = profit_intelligence(rows, [], period)
    cashflow = cashflow_intelligence(rows, period)
    gst = gst_intelligence(rows, period)
    summary = (
        f"Is mahine profit INR {profit.profit} hai, cash risk "
        f"{cashflow.cash_risk_level} hai, GST payable INR {gst.current_gst_payable} hai."
    )
    return AiOwnerReportResponse(
        month=period.starts_on.strftime("%Y-%m"),
        summary=summary,
        profit=profit.profit,
        cash_position=cashflow.cash_position,
        gst_payable=gst.current_gst_payable,
    )


def enriched_workbench_response(
    db: Session,
    company_id: UUID,
    suggestion,
    extracted_invoice: AiExtractedInvoiceFields | None = None,
) -> AiEntrySuggestionResponse:
    response = workbench_response(suggestion, extracted_invoice)
    response.doctor_findings = voucher_doctor_findings(suggestion)
    response.gst_risks = gst_risk_findings(suggestion, extracted_invoice)
    response.duplicate_warnings = duplicate_bill_warnings(db, company_id, suggestion, extracted_invoice)
    return response


def workbench_response(suggestion, extracted_invoice: AiExtractedInvoiceFields | None = None) -> AiEntrySuggestionResponse:
    confidence = money(suggestion.confidence)
    questions: list[str] = []
    if confidence < Decimal("0.70"):
        questions.append(smart_clarification_question(suggestion.input_text))
    elif suggestion.validation_errors:
        questions.append("Please select or create the missing ledger before approval.")
    return AiEntrySuggestionResponse(
        suggestion=suggestion,
        workflow_state=workflow_state(confidence, suggestion.validation_errors),
        confidence_band=confidence_band(confidence),
        clarification_questions=questions[:1],
        extracted_invoice=extracted_invoice,
    )


def confidence_band(confidence: Decimal) -> str:
    if confidence >= Decimal("0.90"):
        return "high"
    if confidence >= Decimal("0.70"):
        return "medium"
    return "low"


def workflow_state(confidence: Decimal, errors: list[str]) -> str:
    if confidence >= Decimal("0.90") and not errors:
        return "ready_for_approval"
    if confidence >= Decimal("0.70"):
        return "needs_review"
    return "needs_clarification"


def ratio(part: int, total: int) -> Decimal:
    if total == 0:
        return Decimal("0.00")
    return money(Decimal(part) * Decimal("100.00") / Decimal(total))


def extract_text_from_pdf_bytes(pdf_bytes: bytes) -> str:
    raw = pdf_bytes.decode("latin-1", errors="ignore")
    strings = re.findall(r"\((.*?)\)", raw, flags=re.DOTALL)
    text = "\n".join(clean_pdf_text(item) for item in strings)
    return text.strip()


def clean_pdf_text(value: str) -> str:
    return value.replace(r"\(", "(").replace(r"\)", ")").replace(r"\\", "\\").strip()


def extract_invoice_fields(text: str) -> AiExtractedInvoiceFields:
    normalized = " ".join(text.split())
    total = find_money(normalized, [r"total amount[:\s]+(?:inr\s*)?([0-9,.]+)", r"grand total[:\s]+(?:inr\s*)?([0-9,.]+)"])
    taxable = find_money(normalized, [r"taxable(?: value| amount)?[:\s]+(?:inr\s*)?([0-9,.]+)"])
    cgst = find_money(normalized, [r"cgst[:\s]+(?:inr\s*)?([0-9,.]+)"])
    sgst = find_money(normalized, [r"sgst[:\s]+(?:inr\s*)?([0-9,.]+)"])
    igst = find_money(normalized, [r"igst[:\s]+(?:inr\s*)?([0-9,.]+)"])
    return AiExtractedInvoiceFields(
        vendor_or_customer_name=find_text(normalized, [r"(?:vendor|supplier|customer|party)[:\s]+([A-Za-z0-9 &.-]{3,80})"]),
        invoice_number=find_text(normalized, [r"invoice(?: number| no| #)?[:\s]+([A-Za-z0-9/-]{2,40})"]),
        invoice_date=find_date(normalized),
        gstin=find_text(normalized, [r"\b([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z])\b"]),
        taxable_amount=taxable,
        cgst_amount=cgst,
        sgst_amount=sgst,
        igst_amount=igst,
        total_amount=total,
        source_text_available=bool(text.strip()),
    )


def invoice_text_for_parser(text: str, fields: AiExtractedInvoiceFields) -> str:
    amount = fields.taxable_amount or fields.total_amount
    gst_bits = []
    if any([fields.cgst_amount, fields.sgst_amount, fields.igst_amount]):
        gst_bits.append("with GST")
    if fields.invoice_number:
        gst_bits.append(f"invoice {fields.invoice_number}")
    return f"Purchase bill {amount or ''} {' '.join(gst_bits)} {text[:600]}".strip()


def find_text(text: str, patterns: list[str]) -> str | None:
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return match.group(1).strip()
    return None


def find_money(text: str, patterns: list[str]) -> Decimal | None:
    found = find_text(text, patterns)
    return money(found.replace(",", "")) if found else None


def find_date(text: str):
    match = re.search(r"\b([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4})\b", text)
    if not match:
        return None
    value = match.group(1)
    try:
        if "-" in value:
            return datetime.strptime(value, "%Y-%m-%d").date()
        return datetime.strptime(value, "%d/%m/%Y").date()
    except ValueError:
        return None


def voucher_doctor_findings(suggestion) -> list[str]:
    findings: list[str] = []
    lines = suggestion.lines
    debit_total = sum((money(line.debit) for line in lines), Decimal("0.00"))
    credit_total = sum((money(line.credit) for line in lines), Decimal("0.00"))
    if debit_total != credit_total:
        findings.append("Debit and credit totals do not match.")
    if any(line.ledger_id is None for line in lines):
        findings.append("One or more posting lines have no mapped ledger.")
    if suggestion.gst_applicable and not any("gst" in line.ledger_name.lower() for line in lines):
        findings.append("GST is detected but no GST ledger line is present.")
    if suggestion.voucher_type == "payment" and not any(
        line.credit > 0 and line.ledger_name.lower() in {"cash", "bank"} for line in lines
    ):
        findings.append("Payment voucher should normally credit Cash or Bank.")
    if money(suggestion.amount) >= Decimal("100000.00") and suggestion.confidence < Decimal("0.90"):
        findings.append("Suspicious high-value entry needs careful review.")
    findings.extend(suggestion.validation_errors)
    return list(dict.fromkeys(findings))


def gst_risk_findings(suggestion, extracted_invoice: AiExtractedInvoiceFields | None) -> list[str]:
    risks: list[str] = []
    lines = suggestion.lines
    gst_lines = [line for line in lines if "gst" in line.ledger_name.lower()]
    if any(word in suggestion.input_text.lower() for word in ["invoice", "bill", "purchase", "sales"]) and not suggestion.gst_applicable:
        risks.append("GST may be missing for an invoice/bill-like entry.")
    if suggestion.gst_applicable and not gst_lines:
        risks.append("GST applicable but GST ledger line is missing.")
    if suggestion.gst_applicable and suggestion.suggested_gst_rate not in {Decimal("0.00"), Decimal("5.00"), Decimal("12.00"), Decimal("18.00"), Decimal("28.00")}:
        risks.append("GST rate is outside common GST slabs.")
    if extracted_invoice:
        cgst = extracted_invoice.cgst_amount or Decimal("0.00")
        sgst = extracted_invoice.sgst_amount or Decimal("0.00")
        igst = extracted_invoice.igst_amount or Decimal("0.00")
        if (cgst > 0 or sgst > 0) and cgst != sgst:
            risks.append("CGST and SGST amounts do not match.")
        if igst > 0 and (cgst > 0 or sgst > 0):
            risks.append("Both IGST and CGST/SGST are present; verify supply type.")
        if extracted_invoice.gstin is None and suggestion.gst_applicable:
            risks.append("GSTIN is missing on a GST invoice.")
        taxable = extracted_invoice.taxable_amount or Decimal("0.00")
        rate = suggestion.suggested_gst_rate or Decimal("0.00")
        expected_tax = money(taxable * rate / Decimal("100.00")) if taxable and rate else Decimal("0.00")
        actual_tax = money(cgst + sgst + igst)
        if expected_tax and actual_tax and abs(expected_tax - actual_tax) > Decimal("1.00"):
            risks.append("Tax amount does not match taxable value and suggested GST rate.")
    return risks


def duplicate_bill_warnings(
    db: Session,
    company_id: UUID,
    suggestion,
    extracted_invoice: AiExtractedInvoiceFields | None,
) -> list[str]:
    warnings: list[str] = []
    invoice_number = extracted_invoice.invoice_number if extracted_invoice else find_text(
        suggestion.input_text,
        [r"invoice(?: number| no| #)?[:\s]+([A-Za-z0-9/-]{2,40})"],
    )
    if invoice_number:
        existing_invoice = db.scalar(
            select(Invoice).where(Invoice.company_id == company_id, Invoice.invoice_number == invoice_number)
        )
        if existing_invoice:
            warnings.append(f"Possible duplicate bill: invoice number {invoice_number} already exists.")
    amount = money(suggestion.amount)
    if amount > 0:
        similar = db.scalars(
            select(AiSuggestion)
            .where(
                AiSuggestion.company_id == company_id,
                AiSuggestion.id != suggestion.suggestion_id,
                AiSuggestion.proposed_payload["amount"].as_string() == str(amount),
                AiSuggestion.input_text.ilike(f"%{invoice_number}%") if invoice_number else AiSuggestion.input_text.ilike("%bill%"),
            )
            .limit(1)
        ).first()
        if similar:
            warnings.append("Similar AI bill suggestion already exists with matching amount/context.")
    return warnings


def smart_clarification_question(text: str) -> str:
    normalized = text.lower()
    if "recharge" in normalized:
        return "Was this office mobile/internet expense or personal?"
    if "cash" not in normalized and "bank" not in normalized and "upi" not in normalized:
        return "Was this paid by cash, bank, UPI, or kept payable/receivable?"
    if not re.search(r"\d", normalized):
        return "What is the transaction amount?"
    return "Which ledger should ABHAY use for this entry?"


def apply_smart_ledger_memory(db: Session, company_id: UUID, user_id: UUID, text: str, suggestion):
    corrections = db.scalars(
        select(AiFeedbackExample)
        .where(AiFeedbackExample.company_id == company_id, AiFeedbackExample.created_by == user_id)
        .order_by(AiFeedbackExample.created_at.desc())
        .limit(25)
    ).all()
    row = db.scalar(select(AiSuggestion).where(AiSuggestion.id == suggestion.suggestion_id))
    if row is None:
        return suggestion
    payload = row.proposed_payload
    for correction in corrections:
        source_text = str(correction.corrected_payload.get("source_text", "")).lower()
        remembered_payload = correction.corrected_payload.get("payload", {})
        if not isinstance(remembered_payload, dict) or not source_text:
            continue
        matched_keyword = next((word for word in source_text.split() if len(word) >= 5 and word in text.lower()), None)
        if not matched_keyword:
            continue
        remembered_lines = remembered_payload.get("lines", [])
        if not remembered_lines:
            continue
        for index, line in enumerate(payload.get("lines", [])):
            if money(line.get("debit", "0")) > 0 and index < len(remembered_lines):
                memory_line = remembered_lines[index]
                ledger_id = memory_line.get("ledger_id")
                if ledger_id:
                    ledger = db.scalar(select(Ledger).where(Ledger.company_id == company_id, Ledger.id == UUID(ledger_id)))
                    if ledger:
                        line["ledger_id"] = str(ledger.id)
                        line["ledger_name"] = ledger.name
                        line["category"] = ledger.category.value
                        line["reason"] = f"Debit {ledger.name}: remembered from previous correction"
                        row.confidence = max(row.confidence, Decimal("0.90"))
                        db.commit()
                        db.refresh(row)
                        return suggestion_response(row)
    return suggestion
