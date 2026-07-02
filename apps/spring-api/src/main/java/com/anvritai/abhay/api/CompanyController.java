package com.anvritai.abhay.api;

import com.anvritai.abhay.api.CompanyDtos.AuditLogResponse;
import com.anvritai.abhay.api.CompanyDtos.CompanyCreateRequest;
import com.anvritai.abhay.api.CompanyDtos.CompanyResponse;
import com.anvritai.abhay.api.CompanyDtos.CompanyUpdateRequest;
import com.anvritai.abhay.api.CompanyDtos.MemberCreateRequest;
import com.anvritai.abhay.api.CompanyDtos.MemberResponse;
import com.anvritai.abhay.api.CompanyDtos.SelectionResponse;
import com.anvritai.abhay.api.CompanyDtos.FinancialYearLockResponse;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.CompanyService;
import com.anvritai.abhay.service.FinancialYearService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final FinancialYearService financialYearService;

    public CompanyController(CompanyService companyService, FinancialYearService financialYearService) {
        this.companyService = companyService;
        this.financialYearService = financialYearService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CompanyCreateRequest request) {
        return companyService.create(principal.id(), request);
    }

    @GetMapping
    public List<CompanyResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return companyService.list(principal.id());
    }

    @GetMapping("/{companyId}")
    public CompanyResponse get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return companyService.get(principal.id(), companyId);
    }

    @PatchMapping("/{companyId}")
    public CompanyResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody CompanyUpdateRequest request) {
        return companyService.update(principal.id(), companyId, request);
    }

    @PostMapping("/{companyId}/select")
    public SelectionResponse select(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return new SelectionResponse(companyService.select(principal.id(), companyId));
    }

    @PostMapping("/{companyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @Valid @RequestBody MemberCreateRequest request) {
        return companyService.addMember(principal.id(), companyId, request);
    }

    @GetMapping("/{companyId}/members")
    public List<MemberResponse> members(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return companyService.members(principal.id(), companyId);
    }

    @GetMapping("/{companyId}/audit-logs")
    public List<AuditLogResponse> auditLogs(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId) {
        return companyService.auditLogs(principal.id(), companyId);
    }

    @PostMapping("/{companyId}/financial-years/{financialYearId}/lock")
    public FinancialYearLockResponse lockFinancialYear(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID financialYearId) {
        return financialYearService.lock(principal.id(), companyId, financialYearId);
    }

    @PostMapping("/{companyId}/financial-years/{financialYearId}/unlock")
    public FinancialYearLockResponse unlockFinancialYear(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID companyId,
            @PathVariable UUID financialYearId) {
        return financialYearService.unlock(principal.id(), companyId, financialYearId);
    }
}
