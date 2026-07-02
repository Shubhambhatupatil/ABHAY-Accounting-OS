package com.anvritai.abhay.api;

import com.anvritai.abhay.api.BankingDtos.*;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.AccountingRuleException;
import com.anvritai.abhay.service.BankingService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class BankingController {
    private final BankingService banking;

    public BankingController(BankingService banking) {
        this.banking = banking;
    }

    @GetMapping("/bank/accounts")
    public List<BankAccountResponse> accounts(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.accounts(principal.id(), companyId);
    }

    @PostMapping("/bank/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse createAccount(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody BankAccountRequest request) {
        return banking.createAccount(principal.id(), companyId, request);
    }

    @PatchMapping("/bank/accounts/{bankAccountId}")
    public BankAccountResponse updateAccount(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID bankAccountId, @Valid @RequestBody BankAccountRequest request) {
        return banking.updateAccount(principal.id(), companyId, bankAccountId, request);
    }

    @PostMapping(value = "/bank/accounts/{bankAccountId}/statement-imports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public StatementImportResponse importStatement(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID bankAccountId, @RequestPart("file") MultipartFile file) {
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
            throw new AccountingRuleException("Only CSV bank statements are supported.");
        }
        try {
            return banking.importStatement(principal.id(), companyId, bankAccountId,
                    file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new AccountingRuleException("Bank statement could not be read.");
        }
    }

    @GetMapping("/bank/accounts/{bankAccountId}/transactions")
    public List<BankTransactionResponse> transactions(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID bankAccountId) {
        return banking.transactions(principal.id(), companyId, bankAccountId);
    }

    @GetMapping("/bank/reconciliation/suggestions")
    public List<ReconciliationSuggestionResponse> suggestions(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.suggestions(principal.id(), companyId);
    }

    @PostMapping("/bank/reconciliation/{transactionId}/confirm")
    public ReconciliationActionResponse confirm(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID transactionId, @Valid @RequestBody ConfirmMatchRequest request) {
        return banking.confirm(principal.id(), companyId, transactionId, request);
    }

    @PostMapping("/bank/reconciliation/{transactionId}/ignore")
    public ReconciliationActionResponse ignore(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID transactionId) {
        return banking.ignore(principal.id(), companyId, transactionId);
    }

    @PostMapping("/bank/reconciliation/{transactionId}/unmatch")
    public ReconciliationActionResponse unmatch(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID transactionId) {
        return banking.unmatch(principal.id(), companyId, transactionId);
    }

    @GetMapping("/bank/reports/bank-book")
    public BankBookResponse bankBook(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.bankBook(principal.id(), companyId);
    }

    @GetMapping("/bank/reports/reconciliation")
    public ReconciliationReportResponse reconciliationReport(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.reconciliationReport(principal.id(), companyId);
    }

    @GetMapping("/bank/reports/unmatched")
    public List<BankTransactionResponse> unmatched(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.unmatched(principal.id(), companyId);
    }

    @GetMapping("/treasury/dashboard")
    public TreasuryDashboardResponse treasuryDashboard(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.treasuryDashboard(principal.id(), companyId);
    }

    @GetMapping("/treasury/cash-position")
    public CashPositionResponse cashPosition(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return banking.cashPosition(principal.id(), companyId);
    }
}
