import re
from decimal import Decimal
from pathlib import Path
from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

import app.models.accounting  # noqa: F401
from app.core.database import Base, get_db
from app.core.config import Settings, get_settings
from app.domain.accounting.engine import AccountingValidationError, PostingLine, gst_split, validate_double_entry
from app.main import app
from app.core.security import LOCAL_DEMO_TOKEN
from app.models.accounting import JournalEntry, Voucher


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


def test_database_stabilization_migration_is_supabase_safe() -> None:
    migration = (
        Path(__file__).resolve().parents[1]
        / "migrations"
        / "20260620_database_stabilization.sql"
    ).read_text(encoding="utf-8")
    required_tables = [
        "companies",
        "company_members",
        "profiles",
        "subscriptions",
        "payments",
        "ledger_groups",
        "ledgers",
        "vouchers",
        "voucher_lines",
        "accounting_entries",
        "invoices",
        "invoice_items",
        "gst_rates",
        "bank_transactions",
        "bank_matches",
        "ai_logs",
        "document_ai_logs",
        "audit_logs",
        "inventory_items",
        "site_visits",
    ]

    for table in required_tables:
        assert f"create table if not exists {table}" in migration
    assert "enable row level security" in migration
    assert "abhay_user_has_company_access" in migration
    assert "abhay_validate_posted_voucher_balance" in migration
    assert "roles_code_unique_idx" in migration
    assert "payments_razorpay_payment_id_unique_idx" in migration
    assert "on conflict" not in migration.lower()
    assert "drop table" not in migration.lower()
    assert "delete from" not in migration.lower()


def test_database_stabilization_adds_created_at_before_created_at_indexes() -> None:
    migration = (
        Path(__file__).resolve().parents[1]
        / "migrations"
        / "20260620_database_stabilization.sql"
    ).read_text(encoding="utf-8")
    normalized = re.sub(r"\s+", " ", migration.lower())
    for match in re.finditer(r"create index if not exists \w+ on (\w+)\s*\(\s*created_at", normalized):
        table_name = match.group(1)
        prior_sql = normalized[: match.start()]
        assert (
            f"alter table {table_name} add column if not exists created_at" in prior_sql
        ), f"{table_name}.created_at must be added before its created_at index"


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
    assert body["legal_name"] == "ABHAY Client Demo Workspace"
    assert "ProgrammingError" not in response.text


def test_client_demo_company_seeds_exact_demo_accounting_flow() -> None:
    with_isolated_database()
    try:
        company_id = client.post("/demo/company", headers=AUTH_HEADERS).json()["company_id"]
        dashboard = client.get(f"/companies/{company_id}/reports/dashboard", headers=AUTH_HEADERS).json()
        pnl = client.get(f"/companies/{company_id}/reports/profit-and-loss", headers=AUTH_HEADERS).json()
        balance_sheet = client.get(f"/companies/{company_id}/reports/balance-sheet", headers=AUTH_HEADERS).json()
        trial_balance = client.get(f"/companies/{company_id}/reports/trial-balance", headers=AUTH_HEADERS).json()
        invoices = client.get(f"/companies/{company_id}/invoices", headers=AUTH_HEADERS).json()
        vouchers = client.get(f"/companies/{company_id}/vouchers", headers=AUTH_HEADERS).json()
        companies_after_reload = client.get("/companies", headers=AUTH_HEADERS).json()
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert dashboard["revenue"] == "50000.00"
    assert dashboard["expenses"] == "20000.00"
    assert dashboard["profit"] == "30000.00"
    assert dashboard["cash_position"] == "35400.00"
    assert dashboard["receivables"] == "0.00"
    assert dashboard["payables"] == "0.00"
    assert pnl == {"revenue": "50000.00", "expenses": "20000.00", "profit": "30000.00"}
    assert balance_sheet == {
        "assets": "39000.00",
        "liabilities": "9000.00",
        "equity": "30000.00",
        "check_difference": "0.00",
    }
    assert sum(Decimal(row["debit"]) for row in trial_balance) == Decimal("59000.00")
    assert sum(Decimal(row["credit"]) for row in trial_balance) == Decimal("59000.00")
    assert {invoice["invoice_number"] for invoice in invoices} == {
        "CLIENT-DEMO-SALES-001",
        "CLIENT-DEMO-PURCHASE-001",
    }
    assert len(vouchers) == 4
    assert companies_after_reload[0]["legal_name"] == "ABHAY Client Demo Workspace"


def test_client_demo_workspace_route_returns_200_when_enabled() -> None:
    with_isolated_database()
    app.dependency_overrides[get_settings] = lambda: Settings(
        app_env="production",
        client_demo_mode=True,
        supabase_url="https://example.supabase.co",
        database_url="sqlite://",
    )
    try:
        response = client.post("/api/demo/client-workspace")
        body = response.json()
        repeated_response = client.post("/api/demo/client-workspace")
        repeated_body = repeated_response.json()
        dashboard = client.get(
            f"/companies/{body['company_id']}/reports/dashboard",
            headers=AUTH_HEADERS,
        ).json()
    finally:
        app.dependency_overrides.pop(get_db, None)
        app.dependency_overrides.pop(get_settings, None)

    assert response.status_code == 200
    assert body["success"] is True
    assert body["mode"] == "client_demo"
    assert body["company_name"] == "ABHAY Client Demo Workspace"
    assert body["seeded"] is True
    assert body["reused"] is False
    assert body["user"] == {
        "name": "Client Demo User",
        "email": "demo@abhay.local",
        "role": "owner",
    }
    assert repeated_response.status_code == 200
    assert repeated_body["success"] is True
    assert repeated_body["company_id"] == body["company_id"]
    assert repeated_body["seeded"] is False
    assert repeated_body["reused"] is True
    assert dashboard["revenue"] == "50000.00"
    assert dashboard["expenses"] == "20000.00"
    assert dashboard["cash_position"] == "35400.00"


def test_client_demo_workspace_route_returns_403_when_disabled() -> None:
    with_isolated_database()
    app.dependency_overrides[get_settings] = lambda: Settings(
        app_env="production",
        client_demo_mode=False,
        alpha_demo_mode=False,
        supabase_url="https://example.supabase.co",
        database_url="sqlite://",
    )
    try:
        response = client.post("/api/demo/client-workspace")
    finally:
        app.dependency_overrides.pop(get_db, None)
        app.dependency_overrides.pop(get_settings, None)

    assert response.status_code == 403
    assert response.json()["detail"] == "Client Demo Mode is disabled."


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
    assert after["invoice_items"] == before["invoice_items"] + 1


def test_fresh_company_invoice_voucher_flow_updates_reports_and_persists_after_reload() -> None:
    with_isolated_database()
    try:
        company_id = client.post(
            "/companies",
            headers=AUTH_HEADERS,
            json={"legal_name": "ABHAY Fresh Flow Pvt Ltd"},
        ).json()["id"]
        ledgers = client.get(f"/companies/{company_id}/ledgers", headers=AUTH_HEADERS).json()
        bank = next(ledger for ledger in ledgers if ledger["category"] == "bank")
        customer = next(ledger for ledger in ledgers if ledger["category"] == "sundry_debtor")
        supplier = next(ledger for ledger in ledgers if ledger["category"] == "sundry_creditor")

        sales_invoice = client.post(
            f"/companies/{company_id}/invoices",
            headers=AUTH_HEADERS,
            json={
                "invoice_type": "sales",
                "invoice_number": "FLOW-SALES-001",
                "invoice_date": "2026-06-27",
                "due_date": "2026-07-07",
                "party_ledger_id": customer["id"],
                "gst_supply_type": "intra_state",
                "notes": "Fresh flow sales invoice",
                "lines": [
                    {
                        "description": "Accounting services",
                        "hsn_sac": "9983",
                        "quantity": "1",
                        "unit": "NOS",
                        "unit_price": "50000.00",
                        "gst_rate": "18.00",
                    }
                ],
            },
        )
        purchase_invoice = client.post(
            f"/companies/{company_id}/invoices",
            headers=AUTH_HEADERS,
            json={
                "invoice_type": "purchase",
                "invoice_number": "FLOW-PURCHASE-001",
                "invoice_date": "2026-06-27",
                "due_date": "2026-07-07",
                "party_ledger_id": supplier["id"],
                "gst_supply_type": "intra_state",
                "notes": "Fresh flow purchase invoice",
                "lines": [
                    {
                        "description": "Business purchase",
                        "hsn_sac": "9983",
                        "quantity": "1",
                        "unit": "NOS",
                        "unit_price": "20000.00",
                        "gst_rate": "18.00",
                    }
                ],
            },
        )
        receipt = client.post(
            f"/companies/{company_id}/vouchers",
            headers=AUTH_HEADERS,
            json={
                "voucher_type": "receipt",
                "voucher_date": "2026-06-27",
                "narration": "Receipt against FLOW-SALES-001",
                "lines": [
                    {"ledger_id": bank["id"], "debit": "59000.00", "credit": "0.00"},
                    {"ledger_id": customer["id"], "debit": "0.00", "credit": "59000.00"},
                ],
            },
        )
        payment = client.post(
            f"/companies/{company_id}/vouchers",
            headers=AUTH_HEADERS,
            json={
                "voucher_type": "payment",
                "voucher_date": "2026-06-27",
                "narration": "Payment against FLOW-PURCHASE-001",
                "lines": [
                    {"ledger_id": supplier["id"], "debit": "23600.00", "credit": "0.00"},
                    {"ledger_id": bank["id"], "debit": "0.00", "credit": "23600.00"},
                ],
            },
        )

        trial_balance = client.get(f"/companies/{company_id}/reports/trial-balance", headers=AUTH_HEADERS).json()
        pnl = client.get(f"/companies/{company_id}/reports/profit-and-loss", headers=AUTH_HEADERS).json()
        balance_sheet = client.get(f"/companies/{company_id}/reports/balance-sheet", headers=AUTH_HEADERS).json()
        dashboard = client.get(f"/companies/{company_id}/reports/dashboard", headers=AUTH_HEADERS).json()
        vouchers_after_reload = client.get(f"/companies/{company_id}/vouchers", headers=AUTH_HEADERS).json()
        invoices_after_reload = client.get(f"/companies/{company_id}/invoices", headers=AUTH_HEADERS).json()
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert sales_invoice.status_code == 200
    assert sales_invoice.json()["taxable_value"] == "50000.00"
    assert sales_invoice.json()["total_amount"] == "59000.00"
    assert purchase_invoice.status_code == 200
    assert purchase_invoice.json()["taxable_value"] == "20000.00"
    assert purchase_invoice.json()["total_amount"] == "23600.00"
    assert receipt.status_code == 200
    assert payment.status_code == 200

    debit_total = sum(Decimal(row["debit"]) for row in trial_balance)
    credit_total = sum(Decimal(row["credit"]) for row in trial_balance)
    assert debit_total == Decimal("59000.00")
    assert credit_total == Decimal("59000.00")
    assert pnl == {"revenue": "50000.00", "expenses": "20000.00", "profit": "30000.00"}
    assert balance_sheet == {
        "assets": "39000.00",
        "liabilities": "9000.00",
        "equity": "30000.00",
        "check_difference": "0.00",
    }
    assert dashboard["revenue"] == "50000.00"
    assert dashboard["expenses"] == "20000.00"
    assert dashboard["profit"] == "30000.00"
    assert dashboard["cash_position"] == "35400.00"
    assert dashboard["receivables"] == "0.00"
    assert dashboard["payables"] == "0.00"
    assert len(vouchers_after_reload) == 4
    assert len(invoices_after_reload) == 2
    assert {invoice["invoice_number"] for invoice in invoices_after_reload} == {
        "FLOW-SALES-001",
        "FLOW-PURCHASE-001",
    }


def test_sample_data_creates_non_zero_counts_and_balanced_vouchers() -> None:
    session_factory = with_isolated_database()
    try:
        company_id = client.post(
            "/companies",
            headers=AUTH_HEADERS,
            json={"legal_name": "ABHAY Sample Data Test"},
        ).json()["id"]

        response = client.post(f"/companies/{company_id}/sample-data", headers=AUTH_HEADERS)
        counts = response.json()
        with session_factory() as db:
            company_uuid = UUID(company_id)
            vouchers = db.scalars(select(Voucher).where(Voucher.company_id == company_uuid)).all()
            totals = []
            for voucher in vouchers:
                entries = db.scalars(
                    select(JournalEntry).where(JournalEntry.voucher_id == voucher.id)
                ).all()
                if entries:
                    totals.append(
                        (
                            voucher.id,
                            sum((entry.debit for entry in entries), Decimal("0")),
                            sum((entry.credit for entry in entries), Decimal("0")),
                        )
                    )
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert response.status_code == 200
    assert counts["ledgers"] >= 10
    assert counts["vouchers"] >= 3
    assert counts["voucher_lines"] >= 6
    assert counts["accounting_entries"] >= 6
    assert counts["invoices"] >= 2
    assert counts["invoice_items"] >= 2
    assert counts["audit_logs"] >= 1
    assert counts["inventory_items"] >= 2
    assert totals
    for _voucher_id, debit_total, credit_total in totals:
        assert debit_total == credit_total


def test_sample_data_is_company_scoped() -> None:
    with_isolated_database()
    try:
        company_one = client.post(
            "/companies",
            headers=AUTH_HEADERS,
            json={"legal_name": "ABHAY Scope One"},
        ).json()["id"]
        company_two = client.post(
            "/companies",
            headers=AUTH_HEADERS,
            json={"legal_name": "ABHAY Scope Two"},
        ).json()["id"]

        client.post(f"/companies/{company_one}/sample-data", headers=AUTH_HEADERS)
        first_counts = client.get(f"/companies/{company_one}/debug-counts", headers=AUTH_HEADERS).json()
        second_counts = client.get(f"/companies/{company_two}/debug-counts", headers=AUTH_HEADERS).json()
    finally:
        app.dependency_overrides.pop(get_db, None)

    assert first_counts["vouchers"] > second_counts["vouchers"]
    assert first_counts["invoices"] > second_counts["invoices"]
    assert first_counts["inventory_items"] > second_counts["inventory_items"]
