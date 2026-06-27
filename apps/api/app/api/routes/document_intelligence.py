import logging
import re
from datetime import datetime, timezone
from decimal import Decimal
from io import BytesIO
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app.api.routes.accounting import repo_for_company, user_uuid
from app.api.routes.ai_entry import extract_text_from_pdf_bytes
from app.core.database import get_db
from app.core.security import AuthenticatedUser, require_user
from app.domain.accounting.engine import money
from app.models.accounting import DocumentAiLog
from app.repositories.accounting import json_safe
from app.schemas.document_intelligence import (
    DocumentAccountingSuggestion,
    DocumentExtractedFields,
    DocumentIntelligenceResponse,
    DocumentLineItem,
)

router = APIRouter(prefix="/companies/{company_id}/document-intelligence", tags=["document-intelligence"])
logger = logging.getLogger(__name__)

MAX_UPLOAD_BYTES = 10 * 1024 * 1024
MAX_PDF_PAGES = 20
SUPPORTED_CONTENT_TYPES = {"application/pdf", "image/png", "image/jpeg", "image/jpg"}
OCR_UNAVAILABLE_MESSAGE = "Scanned OCR is not available yet, but text PDFs are supported."


@router.post("/upload", response_model=DocumentIntelligenceResponse)
async def upload_document(
    company_id: UUID,
    request: Request,
    user: AuthenticatedUser = Depends(require_user),
    db: Session = Depends(get_db),
    x_file_name: str | None = Header(default=None, alias="X-File-Name"),
) -> DocumentIntelligenceResponse:
    repo = repo_for_company(company_id, user, db)
    content_type = (request.headers.get("content-type") or "").split(";", 1)[0].lower()
    if content_type not in SUPPORTED_CONTENT_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Upload a PDF, PNG, JPG, or JPEG document.",
        )

    file_bytes = await request.body()
    if not file_bytes:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Uploaded file is empty.")
    if len(file_bytes) > MAX_UPLOAD_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail="File too large for Alpha. Upload a document up to 10MB.",
        )

    file_name = safe_file_name(x_file_name)
    extracted_text, extraction_warnings = extract_document_text(file_bytes, content_type)
    document_type = classify_document(extracted_text, file_name)
    fields = parse_document_fields(extracted_text, document_type)
    suggestion = build_accounting_suggestion(document_type, fields, extracted_text)
    warnings = [*extraction_warnings, *suggestion.warnings]
    confidence = fields.confidence_score

    response_payload = DocumentIntelligenceResponse(
        id=uuid4(),
        file_name=file_name,
        document_type=document_type,
        extracted_text_summary=summarize_text(extracted_text),
        extracted_text_available=bool(extracted_text.strip()),
        fields=fields,
        accounting_suggestion=suggestion,
        confidence_score=confidence,
        warnings=list(dict.fromkeys(warnings)),
        human_approval_required=True,
        draft_only=True,
    )

    try:
        log = DocumentAiLog(
            id=response_payload.id,
            company_id=company_id,
            user_id=user_uuid(user),
            file_name=file_name,
            document_type=document_type,
            extracted_text=extracted_text[:20000],
            extracted_json=json_safe(response_payload.model_dump(mode="json")),
            confidence_score=confidence,
            created_at=datetime.now(timezone.utc),
        )
        db.add(log)
        repo.add_audit_log(
            company_id,
            user_uuid(user),
            "document_ai_upload",
            "document_ai_log",
            log.id,
            {
                "file_name": file_name,
                "document_type": document_type,
                "confidence_score": confidence,
                "draft_only": True,
            },
        )
        db.commit()
    except SQLAlchemyError as exc:
        db.rollback()
        logger.warning("Document AI log persistence failed: %s", exc)

    return response_payload


def safe_file_name(value: str | None) -> str:
    candidate = (value or "uploaded-document").strip().replace("\\", "/").split("/")[-1]
    return candidate[:180] or "uploaded-document"


def extract_document_text(file_bytes: bytes, content_type: str) -> tuple[str, list[str]]:
    if content_type == "application/pdf":
        text, warnings = extract_pdf_text(file_bytes)
        if text.strip():
            return text, warnings
        ocr_text, ocr_warning = ocr_pdf(file_bytes)
        return ocr_text, [*warnings, ocr_warning] if ocr_warning else warnings
    text, warning = ocr_image(file_bytes)
    return text, [warning] if warning else []


def extract_pdf_text(file_bytes: bytes) -> tuple[str, list[str]]:
    warnings: list[str] = []
    pymupdf_text = extract_pdf_with_pymupdf(file_bytes, warnings)
    if pymupdf_text.strip():
        return pymupdf_text, warnings

    pdfplumber_text = extract_pdf_with_pdfplumber(file_bytes, warnings)
    if pdfplumber_text.strip():
        return pdfplumber_text, warnings

    fallback = extract_text_from_pdf_bytes(file_bytes)
    if fallback.strip():
        return fallback, warnings
    return "", warnings


def extract_pdf_with_pymupdf(file_bytes: bytes, warnings: list[str]) -> str:
    try:
        import fitz  # type: ignore[import-not-found]
    except ImportError:
        return ""
    try:
        with fitz.open(stream=file_bytes, filetype="pdf") as document:
            if document.page_count > MAX_PDF_PAGES:
                warnings.append("Alpha analyzed the first 20 pages only.")
            pages = [document.load_page(index).get_text("text") for index in range(min(document.page_count, MAX_PDF_PAGES))]
        return "\n".join(pages).strip()
    except Exception as exc:  # pragma: no cover - optional dependency edge
        logger.warning("PyMuPDF extraction failed: %s", exc)
        return ""


def extract_pdf_with_pdfplumber(file_bytes: bytes, warnings: list[str]) -> str:
    try:
        import pdfplumber  # type: ignore[import-not-found]
    except ImportError:
        return ""
    try:
        with pdfplumber.open(BytesIO(file_bytes)) as document:
            if len(document.pages) > MAX_PDF_PAGES:
                warnings.append("Alpha analyzed the first 20 pages only.")
            pages = [(page.extract_text() or "") for page in document.pages[:MAX_PDF_PAGES]]
        return "\n".join(pages).strip()
    except Exception as exc:  # pragma: no cover - optional dependency edge
        logger.warning("pdfplumber extraction failed: %s", exc)
        return ""


def ocr_pdf(file_bytes: bytes) -> tuple[str, str | None]:
    try:
        import fitz  # type: ignore[import-not-found]
        import pytesseract  # type: ignore[import-not-found]
        from PIL import Image  # type: ignore[import-not-found]
    except ImportError:
        return "", OCR_UNAVAILABLE_MESSAGE
    try:
        text_parts: list[str] = []
        with fitz.open(stream=file_bytes, filetype="pdf") as document:
            page_count = min(document.page_count, MAX_PDF_PAGES)
            for index in range(page_count):
                pixmap = document.load_page(index).get_pixmap(dpi=180)
                image = Image.open(BytesIO(pixmap.tobytes("png")))
                text_parts.append(pytesseract.image_to_string(image))
            warning = "Alpha analyzed the first 20 pages only." if document.page_count > MAX_PDF_PAGES else None
        return "\n".join(text_parts).strip(), warning
    except Exception as exc:  # pragma: no cover - OCR runtime depends on host binary
        logger.warning("PDF OCR failed: %s", exc)
        return "", OCR_UNAVAILABLE_MESSAGE


def ocr_image(file_bytes: bytes) -> tuple[str, str | None]:
    try:
        import pytesseract  # type: ignore[import-not-found]
        from PIL import Image  # type: ignore[import-not-found]
    except ImportError:
        return "", "Image/scanned OCR is coming soon. Use text PDF or one-line entry for now."
    try:
        image = Image.open(BytesIO(file_bytes))
        return pytesseract.image_to_string(image).strip(), None
    except Exception as exc:  # pragma: no cover - OCR runtime depends on host binary
        logger.warning("Image OCR failed: %s", exc)
        return "", "Image/scanned OCR is coming soon. Use text PDF or one-line entry for now."


def classify_document(text: str, file_name: str) -> str:
    normalized = f"{file_name} {text[:50000]}".lower()
    if any(word in normalized for word in ["bank statement", "ifsc", "withdrawal", "deposit", "closing balance"]):
        return "bank_statement"
    if any(word in normalized for word in ["gstr", "gst return", "3b", "tax period"]):
        return "gst_return"
    if any(word in normalized for word in ["ledger", "opening balance", "closing balance", "dr", "cr"]):
        return "ledger"
    if any(word in normalized for word in ["tax invoice", "invoice number", "invoice no", "gstin"]):
        return "invoice"
    if any(word in normalized for word in ["bill", "receipt", "expense", "paid"]):
        return "expense_bill"
    return "unknown"


def parse_document_fields(text: str, document_type: str) -> DocumentExtractedFields:
    normalized = " ".join(text[:50000].split())
    subtotal = find_money(normalized, [r"taxable(?: value| amount)?[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)", r"subtotal[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)"])
    cgst = find_money(normalized, [r"cgst(?:\s*@?\s*[0-9.]+%)?[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)"])
    sgst = find_money(normalized, [r"sgst(?:\s*@?\s*[0-9.]+%)?[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)"])
    igst = find_money(normalized, [r"igst(?:\s*@?\s*[0-9.]+%)?[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)"])
    gst_amount = sum((amount for amount in [cgst, sgst, igst] if amount is not None), Decimal("0.00"))
    total = find_money(normalized, [r"total amount[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)", r"grand total[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)", r"total[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)"])
    gst_rate = find_gst_rate(normalized, subtotal, gst_amount)
    fields_found = sum(
        value is not None
        for value in [
            subtotal,
            total,
            gst_rate,
            find_invoice_number(normalized),
            find_gstin(normalized),
            find_date(normalized),
            find_party(normalized, "vendor"),
        ]
    )
    confidence = min(Decimal("0.92"), Decimal("0.25") + Decimal(fields_found) * Decimal("0.09"))
    if not text.strip():
        confidence = Decimal("0.10")

    return DocumentExtractedFields(
        document_type=document_type,
        vendor_name=find_party(normalized, "vendor") or find_party(normalized, "supplier"),
        customer_name=find_party(normalized, "customer") or find_party(normalized, "buyer"),
        gstin=find_gstin(normalized),
        invoice_number=find_invoice_number(normalized),
        invoice_date=find_date(normalized),
        subtotal=subtotal,
        gst_rate=gst_rate,
        gst_amount=money(gst_amount) if gst_amount else None,
        total_amount=total,
        line_items=parse_line_items(normalized),
        confidence_score=money(confidence),
    )


def build_accounting_suggestion(
    document_type: str,
    fields: DocumentExtractedFields,
    text: str,
) -> DocumentAccountingSuggestion:
    warnings: list[str] = []
    if not text.strip():
        warnings.append(OCR_UNAVAILABLE_MESSAGE)
    if document_type == "invoice":
        if fields.gst_amount and not fields.gstin:
            warnings.append("GST amount found but GSTIN is missing. Verify before posting.")
        return DocumentAccountingSuggestion(
            suggested_voucher_type="purchase",
            debit_ledger="Purchases / Expense Ledger",
            credit_ledger=fields.vendor_name or "Sundry Creditors",
            gst_treatment=gst_treatment(fields),
            summary="ABHAY detected an invoice. Create a draft purchase invoice or voucher for human review.",
            warnings=warnings,
        )
    if document_type == "expense_bill":
        return DocumentAccountingSuggestion(
            suggested_voucher_type="payment",
            debit_ledger="Relevant Expense Ledger",
            credit_ledger="Cash / Bank",
            gst_treatment=gst_treatment(fields),
            summary="ABHAY detected an expense bill. Review ledger and payment mode before posting.",
            warnings=warnings,
        )
    if document_type == "bank_statement":
        return DocumentAccountingSuggestion(
            suggested_voucher_type="contra / receipt / payment",
            debit_ledger="Bank / Party Ledger",
            credit_ledger="Counterparty Ledger",
            gst_treatment="Usually not GST-applicable directly; reconcile transactions before voucher posting.",
            summary="ABHAY detected a bank statement. Use Bank Reconciliation for transaction matching.",
            warnings=warnings,
        )
    if document_type == "gst_return":
        return DocumentAccountingSuggestion(
            suggested_voucher_type="journal",
            debit_ledger="Input GST / GST Payable",
            credit_ledger="Output GST / GST Payable",
            gst_treatment="GST assistance only. Verify with CA before filing.",
            summary="ABHAY detected a GST document. Review GST summary against books before adjustment entries.",
            warnings=warnings,
        )
    if document_type == "ledger":
        return DocumentAccountingSuggestion(
            suggested_voucher_type="journal",
            debit_ledger="Ledger under review",
            credit_ledger="Counter ledger",
            gst_treatment="Depends on source voucher; verify supporting documents.",
            summary="ABHAY detected ledger-style data. Use it for scrutiny or import review.",
            warnings=warnings,
        )
    warnings.append("Document type is unclear. Please review manually before creating a draft.")
    return DocumentAccountingSuggestion(
        suggested_voucher_type="review_required",
        debit_ledger="Needs review",
        credit_ledger="Needs review",
        gst_treatment="Unknown",
        summary="ABHAY could not confidently classify this document.",
        warnings=warnings,
    )


def gst_treatment(fields: DocumentExtractedFields) -> str:
    if fields.gst_amount and fields.gst_amount > 0:
        rate = f" at {fields.gst_rate}%" if fields.gst_rate is not None else ""
        return f"GST detected{rate}. Needs CA/user verification before filing."
    return "No GST amount confidently detected."


def find_text(text: str, patterns: list[str]) -> str | None:
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return match.group(1).strip(" :-")
    return None


def find_money(text: str, patterns: list[str]) -> Decimal | None:
    found = find_text(text, patterns)
    return money(found.replace(",", "")) if found else None


def find_gstin(text: str) -> str | None:
    return find_text(text, [r"\b([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z])\b"])


def find_invoice_number(text: str) -> str | None:
    return find_text(
        text,
        [
            r"invoice(?: number| no| #)[:\s]+([A-Za-z0-9/-]{2,40})",
            r"bill(?: number| no| #)[:\s]+([A-Za-z0-9/-]{2,40})",
        ],
    )


def find_party(text: str, label: str) -> str | None:
    return find_text(
        text,
        [
            rf"{label}[:\s]+([A-Za-z0-9 &.,'-]{{3,80}}?)(?=\s+(?:vendor|supplier|customer|buyer|invoice|bill|gstin|taxable|subtotal|cgst|sgst|igst|total)\b|$)"
        ],
    )


def find_date(text: str) -> str | None:
    value = find_text(text, [r"\b([0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4}|[0-9]{2}-[0-9]{2}-[0-9]{4})\b"])
    return value


def find_gst_rate(text: str, subtotal: Decimal | None, gst_amount: Decimal) -> Decimal | None:
    rate_text = find_text(text, [r"gst\s*(?:rate)?\s*@?\s*([0-9]{1,2}(?:\.[0-9]+)?)\s*%", r"igst\s*@?\s*([0-9]{1,2}(?:\.[0-9]+)?)\s*%", r"cgst\s*@?\s*([0-9]{1,2}(?:\.[0-9]+)?)\s*%"])
    if rate_text:
        rate = money(rate_text)
        if "cgst" in text.lower() and "sgst" in text.lower() and rate in {Decimal("2.50"), Decimal("6.00"), Decimal("9.00"), Decimal("14.00")}:
            return money(rate * 2)
        return rate
    if subtotal and gst_amount:
        return money(gst_amount * Decimal("100.00") / subtotal)
    return None


def parse_line_items(text: str) -> list[DocumentLineItem]:
    items: list[DocumentLineItem] = []
    for match in re.finditer(r"(?:item|description)[:\s]+([A-Za-z0-9 &.,'-]{3,80})\s+(?:amount|total)[:\s]+(?:inr|rs\.?|₹)?\s*([0-9,.]+)", text, flags=re.IGNORECASE):
        items.append(DocumentLineItem(description=match.group(1).strip(), amount=money(match.group(2).replace(",", ""))))
        if len(items) >= 8:
            break
    return items


def summarize_text(text: str) -> str:
    compact = " ".join(text.split())
    if not compact:
        return "No extractable text found."
    return compact[:600]
