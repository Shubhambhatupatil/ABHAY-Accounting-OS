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


DEMO_DATE = date(2026, 6, 27)
DEMO_COMPANY_NAME = "ABHAY Client Demo Workspace"
DEMO_BANK_CSV = """date,description,debit,credit,balance,reference
2026-06-27,Client demo customer receipt,0,59000,59000,UTR-DEMO-001
2026-06-27,Client demo vendor payment,23600,0,35400,UTR-DEMO-002
"""
