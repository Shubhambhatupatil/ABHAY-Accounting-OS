package com.anvritai.abhay.api;

import com.anvritai.abhay.api.ReportDtos.AccountingDashboardResponse;
import com.anvritai.abhay.api.ReportDtos.BalanceSheetResponse;
import com.anvritai.abhay.api.ReportDtos.BookResponse;
import com.anvritai.abhay.api.ReportDtos.DayBookResponse;
import com.anvritai.abhay.api.ReportDtos.LedgerStatementResponse;
import com.anvritai.abhay.api.ReportDtos.OutstandingResponse;
import com.anvritai.abhay.api.ReportDtos.ProfitAndLossResponse;
import com.anvritai.abhay.api.ReportDtos.TrialBalanceResponse;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.AccountingReportService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class AccountingReportController {

    private final AccountingReportService reports;

    public AccountingReportController(AccountingReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/reports/day-book")
    public DayBookResponse dayBook(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.dayBook(principal.id(), companyId);
    }

    @GetMapping("/ledgers/{ledgerId}/statement")
    public LedgerStatementResponse ledgerStatement(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID ledgerId) {
        return reports.ledgerStatement(principal.id(), companyId, ledgerId);
    }

    @GetMapping("/reports/cash-book")
    public BookResponse cashBook(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.cashBook(principal.id(), companyId);
    }

    @GetMapping("/reports/bank-book")
    public BookResponse bankBook(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.bankBook(principal.id(), companyId);
    }

    @GetMapping("/reports/trial-balance")
    public TrialBalanceResponse trialBalance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.trialBalance(principal.id(), companyId);
    }

    @GetMapping("/reports/profit-and-loss")
    public ProfitAndLossResponse profitAndLoss(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.profitAndLoss(principal.id(), companyId);
    }

    @GetMapping("/reports/balance-sheet")
    public BalanceSheetResponse balanceSheet(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.balanceSheet(principal.id(), companyId);
    }

    @GetMapping("/reports/receivables")
    public OutstandingResponse receivables(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.receivables(principal.id(), companyId);
    }

    @GetMapping("/reports/payables")
    public OutstandingResponse payables(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.payables(principal.id(), companyId);
    }

    @GetMapping("/dashboard/accounting")
    public AccountingDashboardResponse dashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return reports.dashboard(principal.id(), companyId);
    }
}
