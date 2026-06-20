from decimal import Decimal
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models.accounting  # noqa: F401
from app.core.database import Base, get_db
from app.domain.accounting.engine import AccountingValidationError, PostingLine, gst_split, validate_double_entry
from app.main import app
from app.core.security import LOCAL_DEMO_TOKEN


client = TestClient(app)
AUTH_HEADERS = {"Authorization": f"Bearer {LOCAL_DEMO_TOKEN}"}


def with_isolated_database() -> sessionmaker:
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
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


def test_launch_pack_calculators_return_structured_results() -> None:
    tds = client.post("/calculators/tds", json={"amount": "100000", "rate_percent": "10"})
    pf = client.post(
        "/calculators/pf",
        json={
            "monthly_basic_wage": "20000",
            "employee_rate_percent": "12",
            "employer_rate_percent": "12",
            "wage_ceiling": "15000",
        },
    )
    esic = client.post(
        "/calculators/esic",
        json={
            "monthly_gross_wage": "21000",
            "employee_rate_percent": "0.75",
            "employer_rate_percent": "3.25",
            "wage_limit": "21000",
        },
    )

    assert tds.status_code == 200
    assert tds.json()["tds_amount"] == "10000.00"
    assert pf.status_code == 200
    assert pf.json()["total_contribution"] == "3600.00"
    assert esic.status_code == 200
    assert esic.json()["eligible"] is True


def test_launch_pack_gstr_csv_routes_are_registered() -> None:
    response = client.get("/routes")

    assert response.status_code == 200
    paths = {route["path"] for route in response.json()}
    assert "/companies/{company_id}/reports/gstr1.csv" in paths
    assert "/companies/{company_id}/reports/gstr3b.csv" in paths


def test_demo_company_creation_returns_company_id() -> None:
    with_isolated_database()
    try:
        response = client.post(
            "/demo/company",
            headers=AUTH_HEADERS,
        )
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 200
    body = response.json()
    assert body["company_id"]
    assert body["legal_name"] == "ABHAY Demo Traders"
    assert "ProgrammingError" not in response.text


def test_voucher_create_persists_posting_rows() -> None:
    with_isolated_database()
    try:
        company_id = client.post("/demo/company", headers=AUTH_HEADERS).json()["company_id"]
        ledgers = client.get(f"/companies/{company_id}/ledgers", headers=AUTH_HEADERS).json()
        bank = next(ledger for ledger in ledgers if ledger["category"] == "bank")
        customer = next(ledger for ledger in ledgers if ledger["category"] == "sundry_debtor")
        before = client.get(f"/companies/{company_id}/debug-counts", headers=AUTH_HEADERS).json()

        response = client.post(
            f"/companies/{company_id}/vouchers",
            headers=AUTH_HEADERS,
            json={
                "voucher_type": "receipt",
                "voucher_date": "2026-03-15",
                "narration": "Test receipt",
                "lines": [
                    {"ledger_id": bank["id"], "debit": "1000.00", "credit": "0.00"},
                    {"ledger_id": customer["id"], "debit": "0.00", "credit": "1000.00"},
                ],
            },
        )
        after = client.get(f"/companies/{company_id}/debug-counts", headers=AUTH_HEADERS).json()
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 200
    assert after["vouchers"] == before["vouchers"] + 1
    assert after["voucher_lines"] == before["voucher_lines"] + 2
    assert after["accounting_entries"] >= before["accounting_entries"] + 2
    assert after["audit_logs"] >= before["audit_logs"] + 1


def test_invoice_create_persists_invoice_and_audit_rows() -> None:
    with_isolated_database()
    try:
        company_id = client.post("/demo/company", headers=AUTH_HEADERS).json()["company_id"]
        ledgers = client.get(f"/companies/{company_id}/ledgers", headers=AUTH_HEADERS).json()
        customer = next(ledger for ledger in ledgers if ledger["category"] == "sundry_debtor")
        before = client.get(f"/companies/{company_id}/debug-counts", headers=AUTH_HEADERS).json()

        response = client.post(
            f"/companies/{company_id}/invoices",
            headers=AUTH_HEADERS,
            json={
                "invoice_type": "sales",
                "invoice_number": "TEST-SALES-001",
                "invoice_date": "2026-03-16",
                "due_date": "2026-03-31",
                "party_ledger_id": customer["id"],
                "gst_supply_type": "intra_state",
                "notes": "Test invoice",
                "lines": [
                    {
                        "description": "Test service",
                        "hsn_sac": "9983",
                        "quantity": "1",
                        "unit": "NOS",
                        "unit_price": "5000.00",
                        "gst_rate": "18.00",
                    }
                ],
            },
        )
        after = client.get(f"/companies/{company_id}/debug-counts", headers=AUTH_HEADERS).json()
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 200
    assert after["invoices"] == before["invoices"] + 1
    assert after["vouchers"] == before["vouchers"] + 1
    assert after["accounting_entries"] >= before["accounting_entries"] + 3
    assert after["audit_logs"] >= before["audit_logs"] + 2
