package com.anvritai.abhay.api;

import com.anvritai.abhay.api.AccountingDtos.AccountBalanceResponse;
import com.anvritai.abhay.api.AccountingDtos.LedgerCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.LedgerGroupCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.LedgerGroupResponse;
import com.anvritai.abhay.api.AccountingDtos.LedgerResponse;
import com.anvritai.abhay.api.AccountingDtos.LedgerUpdateRequest;
import com.anvritai.abhay.api.AccountingDtos.OpeningBalanceRequest;
import com.anvritai.abhay.api.AccountingDtos.OpeningBalanceResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.VoucherResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherTypeResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherUpdateRequest;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.AccountingService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.time.LocalDate;
import java.util.Locale;
import com.anvritai.abhay.domain.accounting.VoucherStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}")
public class AccountingController {

    private final AccountingService accountingService;

    public AccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping("/ledger-groups")
    public List<LedgerGroupResponse> ledgerGroups(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return accountingService.listLedgerGroups(principal.id(), companyId);
    }

    @PostMapping("/ledger-groups")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerGroupResponse createLedgerGroup(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody LedgerGroupCreateRequest request) {
        return accountingService.createLedgerGroup(principal.id(), companyId, request);
    }

    @GetMapping("/ledgers")
    public List<LedgerResponse> ledgers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return accountingService.listLedgers(principal.id(), companyId);
    }

    @PostMapping("/ledgers")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerResponse createLedger(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody LedgerCreateRequest request) {
        return accountingService.createLedger(principal.id(), companyId, request);
    }

    @PatchMapping("/ledgers/{ledgerId}")
    public LedgerResponse updateLedger(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID ledgerId,
            @Valid @RequestBody LedgerUpdateRequest request) {
        return accountingService.updateLedger(principal.id(), companyId, ledgerId, request);
    }

    @PatchMapping("/ledgers/{ledgerId}/opening-balance")
    public OpeningBalanceResponse setOpeningBalance(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID ledgerId,
            @Valid @RequestBody OpeningBalanceRequest request) {
        return accountingService.setOpeningBalance(principal.id(), companyId, ledgerId, request);
    }

    @GetMapping("/voucher-types")
    public List<VoucherTypeResponse> voucherTypes(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return accountingService.listVoucherTypes(principal.id(), companyId);
    }

    @PostMapping("/vouchers")
    @ResponseStatus(HttpStatus.CREATED)
    public VoucherResponse createVoucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody VoucherCreateRequest request) {
        return accountingService.createVoucher(principal.id(), companyId, request);
    }

    @GetMapping("/vouchers")
    public List<VoucherResponse> vouchers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @RequestParam(name = "date_from", required = false) LocalDate dateFrom,
            @RequestParam(name = "date_to", required = false) LocalDate dateTo,
            @RequestParam(name = "voucher_type", required = false) String voucherType,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "ledger_id", required = false) UUID ledgerId,
            @RequestParam(name = "search", required = false) String search) {
        VoucherStatus voucherStatus = status == null || status.isBlank()
                ? null
                : VoucherStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return accountingService.listVouchers(
                principal.id(), companyId, dateFrom, dateTo, voucherType, voucherStatus, ledgerId, search);
    }

    @GetMapping("/vouchers/{voucherId}")
    public VoucherResponse voucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID voucherId) {
        return accountingService.getVoucher(principal.id(), companyId, voucherId);
    }

    @PatchMapping("/vouchers/{voucherId}")
    public VoucherResponse updateVoucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID voucherId,
            @Valid @RequestBody VoucherUpdateRequest request) {
        return accountingService.updateVoucher(principal.id(), companyId, voucherId, request);
    }

    @PostMapping("/vouchers/{voucherId}/post")
    public VoucherResponse postVoucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID voucherId) {
        return accountingService.postVoucher(principal.id(), companyId, voucherId);
    }

    @PostMapping("/vouchers/{voucherId}/reverse")
    public VoucherResponse reverseVoucher(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID voucherId) {
        return accountingService.reverseVoucher(principal.id(), companyId, voucherId);
    }

    @GetMapping("/account-balances")
    public List<AccountBalanceResponse> accountBalances(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return accountingService.accountBalances(principal.id(), companyId);
    }
}
