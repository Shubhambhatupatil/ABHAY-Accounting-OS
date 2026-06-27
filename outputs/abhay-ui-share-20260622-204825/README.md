# ABHAY Accounting OS by ANVRITAI

AI-first Accounting Operating System for Indian businesses.

ABHAY focuses only on accounting, finance, taxation, GST, ledgers, invoices, bank reconciliation, reporting, and AI accounting automation.

## MVP Modules

- Authentication and multi-company access
- Chart of accounts, ledgers, vouchers, journal entries
- Trial balance, P&L, balance sheet, cash flow
- ABHAY AI Accountant with confirmation-only posting
- Real-time financial intelligence
- Bank reconciliation
- GST invoices and PDF export
- Demo company mode

## Local Setup

1. Copy `apps/api/.env.example` to `apps/api/.env`.
2. Copy `apps/web/.env.example` to `apps/web/.env.local`.
3. Fill Supabase values and database URL.
4. Install dependencies:

```powershell
npm.cmd install
C:\Users\Shubham\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m pip install fastapi "uvicorn[standard]" pydantic-settings "PyJWT[crypto]" httpx sqlalchemy "psycopg[binary]" pytest ruff email-validator
```

## Database Migrations

Apply migrations in order from `supabase/migrations`:

1. `0001_initial_accounting_schema.sql`
2. `0002_gst_rates.sql`
3. `0003_bank_reconciliation_status.sql`

For local verification with Docker PostgreSQL:

```powershell
docker run --name abhay-postgres-test -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=abhay_accounting_test -p 55432:5432 -d postgres:16
$env:DATABASE_URL='postgresql://postgres:postgres@localhost:55432/abhay_accounting_test'
C:\Users\Shubham\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe work\verify_accounting_db.py
```

## Run Apps

```powershell
npm.cmd run dev:web
cd apps/api
C:\Users\Shubham\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m uvicorn app.main:app --reload
```

## Demo Mode

Sign in, open any protected module, and click `Create demo` in the sidebar. This creates `ABHAY Demo Traders` with ledgers, vouchers, invoices, GST data, and bank transactions.

## Production Deployment

- Web: deploy `apps/web` to Vercel.
- API: deploy `apps/api` as a Dockerized FastAPI service.
- Database/Auth/Storage: Supabase.
- Set all environment variables from `apps/web/.env.example` and `apps/api/.env.example`.
- Apply migrations before routing production traffic.

## Verification

```powershell
C:\Users\Shubham\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m ruff check apps/api
C:\Users\Shubham\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m pytest apps/api
npm.cmd --workspace apps/web run lint
npm.cmd run test
npm.cmd run build
```
