package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.banking.BankAccountType;
import com.anvritai.abhay.domain.banking.ReconciliationStatus;
import com.anvritai.abhay.domain.banking.ReconciliationTargetType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BankingDtos {
    private BankingDtos() {
    }

    public record BankAccountRequest(
            @NotNull UUID ledgerId,
            @NotBlank @Size(max = 160) String bankName,
            @NotBlank @Size(max = 160) String accountName,
            @NotBlank @Size(min = 4, max = 40) String accountNumberMasked,
            @NotNull BankAccountType accountType,
            @Pattern(regexp = "(^$)|(^[A-Z]{4}0[A-Z0-9]{6}$)") String ifsc,
            @Size(max = 160) String branch,
            @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @DecimalMin("0.00") BigDecimal openingBalance,
            Boolean active) {
    }

    public record BankAccountResponse(
            UUID id, UUID ledgerId, String ledgerName, String bankName, String accountName,
            String accountNumberMasked, BankAccountType accountType, String ifsc, String branch,
            String currency, BigDecimal openingBalance, boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record StatementImportResponse(
            UUID id, String fileName, long fileSize, int importedRows, int duplicateRows,
            String status, Instant createdAt) {
    }

    public record BankTransactionResponse(
            UUID id, UUID bankAccountId, String bankAccountName, LocalDate transactionDate,
            String description, String reference, BigDecimal debit, BigDecimal credit,
            BigDecimal balance, String counterparty, ReconciliationStatus status, Instant createdAt) {
    }

    public record ConfirmMatchRequest(
            @NotNull ReconciliationTargetType targetType,
            @NotNull UUID targetId) {
    }

    public record ReconciliationSuggestionResponse(
            UUID id, UUID transactionId, ReconciliationTargetType targetType, UUID targetId,
            String targetReference, BigDecimal amount, BigDecimal confidence, String reason) {
    }

    public record ReconciliationActionResponse(
            UUID transactionId, ReconciliationStatus status, UUID matchId,
            ReconciliationTargetType targetType, UUID targetId) {
    }

    public record BankBookRow(
            UUID transactionId, LocalDate date, String description, String reference,
            BigDecimal debit, BigDecimal credit, BigDecimal runningBalance,
            ReconciliationStatus status) {
    }

    public record BankBookAccount(
            UUID bankAccountId, String accountName, String bankName, String maskedNumber,
            BigDecimal openingBalance, List<BankBookRow> transactions, BigDecimal closingBalance) {
    }

    public record BankBookResponse(List<BankBookAccount> accounts, BigDecimal totalClosingBalance) {
    }

    public record ReconciliationReportRow(
            UUID transactionId, LocalDate date, String description, BigDecimal amount,
            ReconciliationStatus status, BigDecimal confidence, String linkedType,
            UUID linkedId, String linkedReference) {
    }

    public record ReconciliationReportResponse(
            long matched, long unmatched, long ignored, long suggested,
            List<ReconciliationReportRow> transactions) {
    }

    public record CashPositionResponse(
            BigDecimal bankLedgerBalance, BigDecimal cashLedgerBalance,
            BigDecimal totalLiquidity, BigDecimal unreconciledCredits,
            BigDecimal unreconciledDebits, BigDecimal projectedLiquidity) {
    }

    public record TreasuryAlertResponse(
            UUID id, String type, String severity, String message, BigDecimal amount, Instant createdAt) {
    }

    public record TreasuryDashboardResponse(
            BigDecimal totalBankBalance, BigDecimal totalCashBalance, BigDecimal totalLiquidity,
            long unreconciledCount, BigDecimal upcomingReceivables, BigDecimal upcomingPayables,
            List<TreasuryAlertResponse> alerts, LocalDate asOfDate) {
    }
}
