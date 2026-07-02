package com.anvritai.abhay.service;

import com.anvritai.abhay.api.CompanyDtos.FinancialYearLockResponse;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialYearService {

    private final FinancialYearRepository financialYears;
    private final UserRepository users;
    private final CompanyAccessService access;
    private final AuditService auditService;

    public FinancialYearService(
            FinancialYearRepository financialYears,
            UserRepository users,
            CompanyAccessService access,
            AuditService auditService) {
        this.financialYears = financialYears;
        this.users = users;
        this.access = access;
        this.auditService = auditService;
    }

    @Transactional
    public FinancialYearLockResponse lock(UUID userId, UUID companyId, UUID financialYearId) {
        return setLock(userId, companyId, financialYearId, true);
    }

    @Transactional
    public FinancialYearLockResponse unlock(UUID userId, UUID companyId, UUID financialYearId) {
        return setLock(userId, companyId, financialYearId, false);
    }

    private FinancialYearLockResponse setLock(
            UUID userId,
            UUID companyId,
            UUID financialYearId,
            boolean locked) {
        access.requireRole(companyId, userId, RoleCode.OWNER, RoleCode.ADMIN);
        FinancialYear financialYear = financialYears.findByIdAndCompanyId(financialYearId, companyId)
                .orElseThrow(() -> new NotFoundException("Financial year not found."));
        User actor = users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
        financialYear.setLocked(locked);
        financialYear.setLockedAt(locked ? Instant.now() : null);
        financialYear.setLockedBy(locked ? actor : null);
        financialYears.save(financialYear);
        auditService.record(
                financialYear.getCompany(),
                actor,
                locked ? "FINANCIAL_YEAR_LOCKED" : "FINANCIAL_YEAR_UNLOCKED",
                "FINANCIAL_YEAR",
                financialYear.getId(),
                Map.of("label", financialYear.getLabel(), "locked", locked));
        return new FinancialYearLockResponse(
                financialYear.getId(), financialYear.isLocked(), financialYear.getLockedAt());
    }
}
