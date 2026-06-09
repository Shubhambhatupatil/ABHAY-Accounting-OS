from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from uuid import UUID


@dataclass(frozen=True)
class DemoSeedResult:
    company_id: UUID
    legal_name: str
    seeded_ledgers: int
    seeded_vouchers: int
    seeded_invoices: int
    seeded_bank_transactions: int


DEMO_DATE = date(2026, 4, 10)
DEMO_COMPANY_NAME = "ABHAY Demo Traders"
DEMO_BANK_CSV = """date,description,debit,credit,balance,reference
2026-04-10,Customer receipt ABC Customers,0,60000,60000,UTR-DEMO-001
2026-04-10,Supplier payment XYZ Suppliers,20000,0,40000,UTR-DEMO-002
2026-04-10,Cash withdrawal,10000,0,30000,UTR-DEMO-003
"""
