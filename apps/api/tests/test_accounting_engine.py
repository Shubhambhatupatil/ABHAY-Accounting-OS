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
    try:
        response = client.post(
            "/demo/company",
            headers={"Authorization": f"Bearer {LOCAL_DEMO_TOKEN}"},
        )
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 200
    body = response.json()
    assert body["company_id"]
    assert body["legal_name"] == "ABHAY Demo Traders"
    assert "ProgrammingError" not in response.text
