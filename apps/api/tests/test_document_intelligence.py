from sqlalchemy import create_engine
from fastapi.testclient import TestClient
from sqlalchemy import select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from uuid import UUID

import app.models.accounting  # noqa: F401
from app.api.routes.accounting import make_multiline_pdf
from app.core.database import Base, get_db
from app.core.security import LOCAL_DEMO_TOKEN
from app.main import app
from app.models.accounting import AuditLog, DocumentAiLog

client = TestClient(app)
AUTH_HEADERS = {"Authorization": f"Bearer {LOCAL_DEMO_TOKEN}"}


def with_isolated_database() -> sessionmaker:
    engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine, autoflush=False, autocommit=False)

    def override_db():
        db = session_factory()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_db
    return session_factory


def create_demo_company(client) -> str:
    response = client.post("/demo/company", headers=AUTH_HEADERS)
    assert response.status_code == 200
    return response.json()["company_id"]


def test_document_intelligence_extracts_text_pdf_invoice_fields() -> None:
    session_factory = with_isolated_database()
    try:
        company_id = create_demo_company(client)
        pdf = make_multiline_pdf(
            [
                "Tax Invoice",
                "Vendor: Bharat Telecom",
                "Customer: ANVRITAI",
                "Invoice Number: BT-42",
                "Invoice Date: 2026-04-10",
                "GSTIN: 27ABCDE1234F1Z5",
                "Taxable Value: INR 10000.00",
                "CGST: INR 900.00",
                "SGST: INR 900.00",
                "Total Amount: INR 11800.00",
            ]
        )

        response = client.post(
            f"/companies/{company_id}/document-intelligence/upload",
            headers={**AUTH_HEADERS, "Content-Type": "application/pdf", "X-File-Name": "invoice.pdf"},
            content=pdf,
        )

        assert response.status_code == 200
        data = response.json()
        assert data["document_type"] == "invoice"
        assert data["fields"]["vendor_name"] == "Bharat Telecom"
        assert data["fields"]["invoice_number"] == "BT-42"
        assert data["fields"]["gstin"] == "27ABCDE1234F1Z5"
        assert data["fields"]["subtotal"] == "10000.00"
        assert data["fields"]["gst_amount"] == "1800.00"
        assert data["fields"]["total_amount"] == "11800.00"
        assert data["human_approval_required"] is True
        assert data["draft_only"] is True

        counts = client.get(f"/companies/{company_id}/debug-counts", headers=AUTH_HEADERS).json()
        assert counts["audit_logs"] >= 1
        with session_factory() as db:
            assert db.scalar(select(DocumentAiLog).where(DocumentAiLog.company_id == UUID(company_id))) is not None
            audit = db.scalar(select(AuditLog).where(AuditLog.action_type == "document_ai_upload"))
            assert audit is not None
    finally:
        app.dependency_overrides.pop(get_db, None)


def test_document_intelligence_unknown_document_fallback() -> None:
    with_isolated_database()
    try:
        company_id = create_demo_company(client)
        pdf = make_multiline_pdf(["Random meeting note", "No accounting fields here"])

        response = client.post(
            f"/companies/{company_id}/document-intelligence/upload",
            headers={**AUTH_HEADERS, "Content-Type": "application/pdf", "X-File-Name": "note.pdf"},
            content=pdf,
        )

        assert response.status_code == 200
        data = response.json()
        assert data["document_type"] == "unknown"
        assert data["accounting_suggestion"]["suggested_voucher_type"] == "review_required"
        assert "Document type is unclear" in " ".join(data["warnings"])
    finally:
        app.dependency_overrides.pop(get_db, None)


def test_document_intelligence_rejects_unsupported_file_type() -> None:
    with_isolated_database()
    try:
        company_id = create_demo_company(client)

        response = client.post(
            f"/companies/{company_id}/document-intelligence/upload",
            headers={**AUTH_HEADERS, "Content-Type": "text/plain", "X-File-Name": "bill.txt"},
            content=b"Invoice Number: A-1",
        )

        assert response.status_code == 415
        assert response.json()["detail"] == "Upload a PDF, PNG, JPG, or JPEG document."
    finally:
        app.dependency_overrides.pop(get_db, None)
