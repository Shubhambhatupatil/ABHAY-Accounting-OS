package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.accounting.AccountNature;
import com.anvritai.abhay.domain.accounting.NormalBalance;
import com.anvritai.abhay.domain.accounting.LedgerType;
import com.anvritai.abhay.domain.accounting.VoucherStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AccountingDtos {
    private AccountingDtos() {
    }

    public record LedgerGroupCreateRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull AccountNature accountNature,
            UUID parentId) {
    }

    public record LedgerGroupResponse(
            UUID id,
            String name,
            AccountNature accountNature,
            UUID parentId,
            boolean systemGroup,
            Instant createdAt) {
    }

    public record LedgerCreateRequest(
            @NotNull UUID ledgerGroupId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 40) String code,
            @NotNull NormalBalance normalBalance,
            LedgerType ledgerType,
            @DecimalMin("0.00") BigDecimal openingDebit,
            @DecimalMin("0.00") BigDecimal openingCredit) {
    }

    public record LedgerUpdateRequest(
            @Size(min = 1, max = 200) String name,
            @Size(max = 40) String code,
            NormalBalance normalBalance,
            LedgerType ledgerType,
            Boolean active) {
    }

    public record LedgerResponse(
            UUID id,
            UUID ledgerGroupId,
            String ledgerGroupName,
            String name,
            String code,
            NormalBalance normalBalance,
            LedgerType ledgerType,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record OpeningBalanceRequest(
            UUID financialYearId,
            @DecimalMin("0.00") BigDecimal openingDebit,
            @DecimalMin("0.00") BigDecimal openingCredit) {
    }

    public record OpeningBalanceResponse(
            UUID financialYearId,
            UUID ledgerId,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            Instant updatedAt) {
    }

    public record VoucherTypeResponse(UUID id, String code, String name, boolean systemType) {
    }

    public record VoucherLineRequest(
            @NotNull UUID ledgerId,
            @DecimalMin("0.00") BigDecimal debit,
            @DecimalMin("0.00") BigDecimal credit,
            @Size(max = 500) String narration) {
    }

    public record VoucherCreateRequest(
            @NotBlank @Size(max = 30) String voucherTypeCode,
            @NotNull LocalDate voucherDate,
            @Size(max = 1000) String narration,
            @NotEmpty @Size(min = 2, max = 100) List<@Valid VoucherLineRequest> lines) {
    }

    public record VoucherUpdateRequest(
            @NotNull LocalDate voucherDate,
            @Size(max = 1000) String narration,
            @NotEmpty @Size(min = 2, max = 100) List<@Valid VoucherLineRequest> lines) {
    }

    public record VoucherLineResponse(
            UUID id,
            int lineNumber,
            UUID ledgerId,
            String ledgerName,
            BigDecimal debit,
            BigDecimal credit,
            String narration) {
    }

    public record VoucherResponse(
            UUID id,
            UUID financialYearId,
            String voucherTypeCode,
            String voucherNumber,
            LocalDate voucherDate,
            VoucherStatus status,
            String narration,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            UUID reversalVoucherId,
            Instant postedAt,
            Instant reversedAt,
            List<VoucherLineResponse> lines,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record AccountBalanceResponse(
            UUID ledgerId,
            String ledgerName,
            String ledgerGroupName,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            BigDecimal periodDebit,
            BigDecimal periodCredit,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {
    }
}
