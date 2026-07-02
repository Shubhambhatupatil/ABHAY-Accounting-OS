package com.anvritai.abhay.service;

import com.anvritai.abhay.api.CompanyDtos.AuditLogResponse;
import com.anvritai.abhay.api.CompanyDtos.CompanyCreateRequest;
import com.anvritai.abhay.api.CompanyDtos.CompanyResponse;
import com.anvritai.abhay.api.CompanyDtos.CompanyUpdateRequest;
import com.anvritai.abhay.api.CompanyDtos.MemberCreateRequest;
import com.anvritai.abhay.api.CompanyDtos.MemberResponse;
import com.anvritai.abhay.domain.AuditLog;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.CompanyMember;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.Role;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.repository.AuditLogRepository;
import com.anvritai.abhay.repository.CompanyMemberRepository;
import com.anvritai.abhay.repository.CompanyRepository;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.RoleRepository;
import com.anvritai.abhay.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companies;
    private final CompanyMemberRepository members;
    private final UserRepository users;
    private final RoleRepository roles;
    private final FinancialYearRepository financialYears;
    private final AuditLogRepository auditLogs;
    private final CompanyAccessService access;
    private final AuditService auditService;
    private final AccountingSeedService accountingSeedService;
    private final GstSeedService gstSeedService;
    private final InventorySeedService inventorySeedService;

    public CompanyService(
            CompanyRepository companies,
            CompanyMemberRepository members,
            UserRepository users,
            RoleRepository roles,
            FinancialYearRepository financialYears,
            AuditLogRepository auditLogs,
            CompanyAccessService access,
            AuditService auditService,
            AccountingSeedService accountingSeedService,
            GstSeedService gstSeedService,
            InventorySeedService inventorySeedService) {
        this.companies = companies;
        this.members = members;
        this.users = users;
        this.roles = roles;
        this.financialYears = financialYears;
        this.auditLogs = auditLogs;
        this.access = access;
        this.auditService = auditService;
        this.accountingSeedService = accountingSeedService;
        this.gstSeedService = gstSeedService;
        this.inventorySeedService = inventorySeedService;
    }

    @Transactional
    public CompanyResponse create(UUID userId, CompanyCreateRequest request) {
        if (request.financialYearEnd().isBefore(request.financialYearStart())) {
            throw new IllegalArgumentException("Financial year end must not be before its start.");
        }
        User owner = requireUser(userId);
        Role ownerRole = requireRole(RoleCode.OWNER);
        Company company = new Company();
        apply(company, request.legalName(), request.tradeName(), request.gstin(), request.stateCode(), request.industry());
        company = companies.save(company);

        CompanyMember membership = new CompanyMember();
        membership.setCompany(company);
        membership.setUser(owner);
        membership.setRole(ownerRole);
        members.save(membership);

        FinancialYear financialYear = new FinancialYear();
        financialYear.setCompany(company);
        financialYear.setStartsOn(request.financialYearStart());
        financialYear.setEndsOn(request.financialYearEnd());
        financialYear.setLabel(financialYearLabel(request.financialYearStart(), request.financialYearEnd()));
        financialYear.setActive(true);
        financialYears.save(financialYear);
        accountingSeedService.seedCompany(company);
        gstSeedService.seedCompany(company);
        inventorySeedService.seedCompany(company);

        if (owner.getSelectedCompany() == null) {
            owner.setSelectedCompany(company);
            users.save(owner);
        }
        auditService.record(
                company,
                owner,
                "COMPANY_CREATED",
                "COMPANY",
                company.getId(),
                Map.of("legalName", company.getLegalName(), "ownerUserId", owner.getId().toString()));
        return response(company, membership, financialYear);
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list(UUID userId) {
        return companies.findAllForUser(userId).stream()
                .map(company -> response(
                        company,
                        access.requireMembership(company.getId(), userId),
                        activeFinancialYear(company.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID userId, UUID companyId) {
        CompanyMember member = access.requireMembership(companyId, userId);
        return response(requireCompany(companyId), member, activeFinancialYear(companyId));
    }

    @Transactional
    public CompanyResponse update(UUID userId, UUID companyId, CompanyUpdateRequest request) {
        CompanyMember member = access.requireRole(companyId, userId, RoleCode.OWNER, RoleCode.ADMIN);
        Company company = requireCompany(companyId);
        if (request.legalName() != null) {
            company.setLegalName(request.legalName().trim());
        }
        if (request.tradeName() != null) {
            company.setTradeName(blankToNull(request.tradeName()));
        }
        if (request.gstin() != null) {
            company.setGstin(upperOrNull(request.gstin()));
        }
        if (request.stateCode() != null) {
            company.setStateCode(blankToNull(request.stateCode()));
        }
        if (request.industry() != null) {
            company.setIndustry(blankToNull(request.industry()));
        }
        companies.save(company);
        User actor = requireUser(userId);
        auditService.record(
                company,
                actor,
                "COMPANY_UPDATED",
                "COMPANY",
                company.getId(),
                Map.of("legalName", company.getLegalName()));
        return response(company, member, activeFinancialYear(companyId));
    }

    @Transactional
    public UUID select(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        User user = requireUser(userId);
        user.setSelectedCompany(requireCompany(companyId));
        users.save(user);
        return companyId;
    }

    @Transactional
    public MemberResponse addMember(UUID actorId, UUID companyId, MemberCreateRequest request) {
        access.requireRole(companyId, actorId, RoleCode.OWNER, RoleCode.ADMIN);
        if (request.role() == RoleCode.OWNER) {
            throw new IllegalArgumentException("Owner role cannot be assigned through member add.");
        }
        User target = users.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("No ABHAY account exists for this email."));
        if (members.existsByCompanyIdAndUserId(companyId, target.getId())) {
            throw new ConflictException("This user is already a company member.");
        }
        Company company = requireCompany(companyId);
        CompanyMember member = new CompanyMember();
        member.setCompany(company);
        member.setUser(target);
        member.setRole(requireRole(request.role()));
        member = members.save(member);
        auditService.record(
                company,
                requireUser(actorId),
                "MEMBER_ADDED",
                "COMPANY_MEMBER",
                member.getId(),
                Map.of("memberUserId", target.getId().toString(), "role", request.role().name()));
        return memberResponse(member);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return members.findAllByCompanyIdOrderByCreatedAt(companyId).stream()
                .map(this::memberResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> auditLogs(UUID userId, UUID companyId) {
        access.requireRole(companyId, userId, RoleCode.OWNER, RoleCode.ADMIN, RoleCode.AUDITOR);
        return auditLogs.findAllByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, 100)).stream()
                .map(this::auditResponse)
                .toList();
    }

    private CompanyResponse response(Company company, CompanyMember member, FinancialYear financialYear) {
        return new CompanyResponse(
                company.getId(),
                company.getLegalName(),
                company.getTradeName(),
                company.getGstin(),
                company.getStateCode(),
                company.getIndustry(),
                RoleCode.valueOf(member.getRole().getCode()),
                financialYear == null ? null : financialYear.getId(),
                financialYear == null ? null : financialYear.getLabel(),
                company.getCreatedAt(),
                company.getUpdatedAt());
    }

    private MemberResponse memberResponse(CompanyMember member) {
        return new MemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getDisplayName(),
                member.getUser().getEmail(),
                RoleCode.valueOf(member.getRole().getCode()),
                member.getCreatedAt());
    }

    private AuditLogResponse auditResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getActor() == null ? null : log.getActor().getId(),
                log.getDetailsJson(),
                log.getCreatedAt());
    }

    private FinancialYear activeFinancialYear(UUID companyId) {
        return financialYears.findAllByCompanyIdOrderByStartsOnDesc(companyId).stream()
                .filter(FinancialYear::isActive)
                .findFirst()
                .orElse(null);
    }

    private Company requireCompany(UUID id) {
        return companies.findById(id).orElseThrow(() -> new NotFoundException("Company not found."));
    }

    private User requireUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Account not found."));
    }

    private Role requireRole(RoleCode code) {
        return roles.findByCode(code).orElseThrow(() -> new IllegalStateException("Required role is missing."));
    }

    private void apply(
            Company company,
            String legalName,
            String tradeName,
            String gstin,
            String stateCode,
            String industry) {
        company.setLegalName(legalName.trim());
        company.setTradeName(blankToNull(tradeName));
        company.setGstin(upperOrNull(gstin));
        company.setStateCode(blankToNull(stateCode));
        company.setIndustry(blankToNull(industry));
    }

    private String financialYearLabel(LocalDate start, LocalDate end) {
        return "FY " + start.getYear() + "-" + String.valueOf(end.getYear()).substring(2);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String upperOrNull(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
