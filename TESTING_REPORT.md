# ABHAY Accounting OS Testing Report

## Scope

- Local PostgreSQL setup created with Docker.
- Supabase-compatible local auth bootstrap applied.
- All project migrations applied.
- Demo company, ledgers, vouchers, and GST data seeded.
- Accounting calculations verified against real PostgreSQL data.

## Migrations Applied

- 0001_initial_accounting_schema.sql
- 0002_gst_rates.sql
- 0003_bank_reconciliation_status.sql

## Demo Data

- Demo user: `15e1f524-9fc4-4c0e-9956-2d435a213e35`
- Demo company: `ABHAY Demo Traders`
- Demo company id: `6cad217b-b721-42b5-a068-9c7f7b9203e1`
- Demo vouchers:
  - Sales invoice posting: INR 100,000 revenue + INR 18,000 output GST
  - Customer receipt: INR 60,000 into bank
  - Purchase posting: INR 40,000 purchase + INR 7,200 input GST
  - Office rent cash payment: INR 12,000
  - Diesel cash payment: INR 2,500
  - Contra bank-to-cash transfer: INR 10,000
  - Supplier journal/payment adjustment: INR 20,000

## Verification Results

| Check | Result |
|---|---:|
| Trial Balance debit total | INR 149700.00 |
| Trial Balance credit total | INR 149700.00 |
| Revenue | INR 100000.00 |
| Expenses | INR 54500.00 |
| Profit | INR 45500.00 |
| Assets | INR 90700.00 |
| Liabilities | INR 45200.00 |
| Equity / retained profit | INR 45500.00 |
| Balance Sheet difference | INR 0.00 |
| Cash position / net cash flow | INR 25500.00 |
| Input GST | INR 7200.00 |
| Output GST | INR 18000.00 |
| Net GST payable | INR 10800.00 |

## Discovered Bugs

- [Fixed] Dashboard receivables and payables could be calculated from ledger display names instead of ledger categories, causing party ledgers such as `ABC Customers` and `XYZ Suppliers` to be missed.
- [Fixed] Cash flow and GST report classification also depended on ledger names in some paths; these now use ledger categories.
- [Fixed] The first verification harness expected cash position was INR 17,500.00, but real ledger movement produced INR 25,500.00. The harness expectation was corrected.

## Active Bugs

- No active accounting calculation bugs remain after fixes.

## Fixed During Verification

- Replaced name-based report classification with category-based classification for receivables, payables, cash position, cash flow, and GST reports.
- Corrected the verification harness cash-flow expectation after recalculating the seeded cash and bank ledger movements against PostgreSQL data.

## Status

Accounting calculations are verified with real PostgreSQL data. AI feature work remains blocked until this report stays clean after any future accounting changes.
