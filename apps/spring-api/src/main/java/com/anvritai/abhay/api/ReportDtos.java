package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.accounting.AccountNature;
import com.anvritai.abhay.domain.accounting.VoucherStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record DayBookRow(
            UUID voucherId,
            String voucherNumber,
            String voucherType,
            LocalDate date,
            String narration,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            VoucherStatus status) {
    }

    public record DayBookResponse(
            UUID financialYearId,
            List<DayBookRow> vouchers,
            BigDecimal totalDebit,
            BigDecimal totalCredit) {
    }

    public record LedgerMovement(
            LocalDate date,
            UUID voucherId,
            String voucherNumber,
            String voucherType,
            String narration,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningDebit,
            BigDecimal runningCredit) {
    }

    public record LedgerStatementResponse(
            UUID financialYearId,
            UUID ledgerId,
            String ledgerName,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            List<LedgerMovement> entries,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {
    }

    public record BookLedger(
            UUID ledgerId,
            String ledgerName,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            List<LedgerMovement> entries,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {
    }

    public record BookResponse(
            UUID financialYearId,
            String bookType,
            List<BookLedger> ledgers,
            BigDecimal totalClosingDebit,
            BigDecimal totalClosingCredit) {
    }

    public record TrialBalanceRow(
            UUID ledgerId,
            String ledgerName,
            String groupName,
            AccountNature accountNature,
            BigDecimal debitBalance,
            BigDecimal creditBalance) {
    }

    public record TrialBalanceResponse(
            UUID financialYearId,
            List<TrialBalanceRow> rows,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal difference) {
    }

    public record ReportLedgerRow(UUID ledgerId, String ledgerName, String groupName, BigDecimal amount) {
    }

    public record ProfitAndLossResponse(
            UUID financialYearId,
            List<ReportLedgerRow> income,
            List<ReportLedgerRow> expenses,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal grossProfit,
            BigDecimal netProfit) {
    }

    public record BalanceSheetResponse(
            UUID financialYearId,
            List<ReportLedgerRow> assets,
            List<ReportLedgerRow> liabilities,
            List<ReportLedgerRow> equity,
            BigDecimal currentProfit,
            BigDecimal totalAssets,
            BigDecimal totalLiabilitiesAndEquity,
            BigDecimal difference) {
    }

    public record OutstandingRow(
            UUID ledgerId,
            String ledgerName,
            BigDecimal amount) {
    }

    public record OutstandingResponse(
            UUID financialYearId,
            String type,
            List<OutstandingRow> rows,
            BigDecimal total) {
    }

    public record AccountingDashboardResponse(
            UUID financialYearId,
            long totalVouchers,
            long postedVouchers,
            long draftVouchers,
            BigDecimal cashBalance,
            BigDecimal bankBalance,
            BigDecimal receivables,
            BigDecimal payables,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal profit) {
    }
}
