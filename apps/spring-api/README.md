# ABHAY Accounting OS Spring API

Spring Boot 3 / Java 21 foundation for authentication, company workspaces, roles, financial years, and audit logs.

Sprint 2 adds the company-scoped accounting core: ledger groups, ledgers, voucher types and series,
draft vouchers, double-entry posting, immutable journal entries, reversals, account balances, audit logs,
and persisted AI memory events.

## Local setup

1. Create a PostgreSQL database and set the variables shown in `.env.example`.
2. Use a random `JWT_SECRET` of at least 32 bytes.
3. Run migrations and start the API:

```bash
./mvnw spring-boot:run
```

Flyway applies the schema automatically. Hibernate validates it and never creates production tables.

## Verification

```bash
./mvnw test
./mvnw package
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Accounting APIs

- `GET|POST /api/companies/{companyId}/ledger-groups`
- `GET|POST /api/companies/{companyId}/ledgers`
- `PATCH /api/companies/{companyId}/ledgers/{ledgerId}`
- `GET /api/companies/{companyId}/voucher-types`
- `GET|POST /api/companies/{companyId}/vouchers`
- `GET|PATCH /api/companies/{companyId}/vouchers/{voucherId}`
- `POST /api/companies/{companyId}/vouchers/{voucherId}/post`
- `POST /api/companies/{companyId}/vouchers/{voucherId}/reverse`
- `GET /api/companies/{companyId}/account-balances`

Voucher drafts can be edited. Posting requires equal, positive debit and credit totals. Posted vouchers are
immutable and can only be neutralized through a linked reversal voucher.

## Accounting reports and controls

- `PATCH /api/companies/{companyId}/ledgers/{ledgerId}/opening-balance`
- `GET /api/companies/{companyId}/ledgers/{ledgerId}/statement`
- `GET /api/companies/{companyId}/reports/day-book`
- `GET /api/companies/{companyId}/reports/cash-book`
- `GET /api/companies/{companyId}/reports/bank-book`
- `GET /api/companies/{companyId}/reports/trial-balance`
- `GET /api/companies/{companyId}/reports/profit-and-loss`
- `GET /api/companies/{companyId}/reports/balance-sheet`
- `GET /api/companies/{companyId}/reports/receivables`
- `GET /api/companies/{companyId}/reports/payables`
- `GET /api/companies/{companyId}/dashboard/accounting`
- `POST /api/companies/{companyId}/financial-years/{financialYearId}/lock`
- `POST /api/companies/{companyId}/financial-years/{financialYearId}/unlock`

Voucher listing supports `date_from`, `date_to`, `voucher_type`, `status`, `ledger_id`, and `search` filters.
Reports are calculated from persisted opening balances and journal entries. Locked financial years reject opening
balance changes and voucher create/edit/post/reversal operations.

## Environment

- `DATABASE_URL`: JDBC PostgreSQL URL
- `DATABASE_USERNAME`: PostgreSQL user
- `DATABASE_PASSWORD`: PostgreSQL password
- `JWT_SECRET`: random secret with at least 32 bytes
- `JWT_EXPIRATION_MINUTES`: token lifetime, default 60
- `CORS_ORIGINS`: comma-separated trusted frontend origins
