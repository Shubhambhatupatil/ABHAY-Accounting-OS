package com.anvritai.abhay.service;

import com.anvritai.abhay.api.GstDtos.GstRateRequest;
import com.anvritai.abhay.api.GstDtos.GstRateResponse;
import com.anvritai.abhay.api.GstDtos.GstRuleRequest;
import com.anvritai.abhay.api.GstDtos.GstRuleResponse;
import com.anvritai.abhay.api.GstDtos.HsnSacRequest;
import com.anvritai.abhay.api.GstDtos.HsnSacResponse;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.gst.GstHsnSac;
import com.anvritai.abhay.domain.gst.GstRate;
import com.anvritai.abhay.domain.gst.GstRule;
import com.anvritai.abhay.repository.CompanyRepository;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.gst.GstHsnSacRepository;
import com.anvritai.abhay.repository.gst.GstRateRepository;
import com.anvritai.abhay.repository.gst.GstRuleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GstMasterService {
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};
    private final CompanyAccessService access;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final GstRateRepository rates;
    private final GstHsnSacRepository hsnSac;
    private final GstRuleRepository rules;
    private final GstSeedService seedService;
    private final AuditService audit;
    private final GstMemoryService memory;

    public GstMasterService(
            CompanyAccessService access, CompanyRepository companies, UserRepository users,
            GstRateRepository rates, GstHsnSacRepository hsnSac, GstRuleRepository rules,
            GstSeedService seedService, AuditService audit, GstMemoryService memory) {
        this.access = access;
        this.companies = companies;
        this.users = users;
        this.rates = rates;
        this.hsnSac = hsnSac;
        this.rules = rules;
        this.seedService = seedService;
        this.audit = audit;
        this.memory = memory;
    }

    @Transactional
    public List<GstRateResponse> listRates(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        seedService.seedCompany(company(companyId));
        return rates.findAllByCompanyIdOrderByRateAscCessRateAsc(companyId).stream().map(this::rateResponse).toList();
    }

    @Transactional
    public GstRateResponse createRate(UUID userId, UUID companyId, GstRateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        BigDecimal rate = percent(request.rate());
        BigDecimal cess = percent(request.cessRate());
        if (rates.existsByCompanyIdAndRateAndCessRate(companyId, rate, cess)) {
            throw new ConflictException("This GST and CESS rate combination already exists.");
        }
        GstRate entity = new GstRate();
        entity.setCompany(company(companyId));
        applyRate(entity, request);
        entity.setSystemRate(false);
        entity = rates.save(entity);
        record(userId, entity.getCompany(), "GST_RATE_CREATED", "GST_RATE", entity.getId(),
                Map.of("rate", entity.getRate(), "cessRate", entity.getCessRate()));
        return rateResponse(entity);
    }

    @Transactional
    public GstRateResponse updateRate(UUID userId, UUID companyId, UUID rateId, GstRateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstRate entity = rate(companyId, rateId);
        BigDecimal nextRate = percent(request.rate());
        BigDecimal nextCess = percent(request.cessRate());
        if ((entity.getRate().compareTo(nextRate) != 0 || entity.getCessRate().compareTo(nextCess) != 0)
                && rates.existsByCompanyIdAndRateAndCessRate(companyId, nextRate, nextCess)) {
            throw new ConflictException("This GST and CESS rate combination already exists.");
        }
        applyRate(entity, request);
        rates.save(entity);
        record(userId, entity.getCompany(), "GST_RATE_UPDATED", "GST_RATE", entity.getId(),
                Map.of("rate", entity.getRate(), "active", entity.isActive()));
        return rateResponse(entity);
    }

    @Transactional
    public void deactivateRate(UUID userId, UUID companyId, UUID rateId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstRate entity = rate(companyId, rateId);
        entity.setActive(false);
        rates.save(entity);
        record(userId, entity.getCompany(), "GST_RATE_DEACTIVATED", "GST_RATE", entity.getId(), Map.of());
    }

    @Transactional(readOnly = true)
    public List<HsnSacResponse> searchHsnSac(UUID userId, UUID companyId, String search) {
        access.requireMembership(companyId, userId);
        String normalized = search == null || search.isBlank() ? null : search.trim();
        return hsnSac.search(companyId, normalized).stream().map(this::hsnResponse).toList();
    }

    @Transactional
    public HsnSacResponse createHsnSac(UUID userId, UUID companyId, HsnSacRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (hsnSac.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code().trim())) {
            throw new ConflictException("This HSN/SAC code already exists.");
        }
        GstHsnSac entity = new GstHsnSac();
        entity.setCompany(company(companyId));
        applyHsn(entity, request);
        entity = hsnSac.save(entity);
        record(userId, entity.getCompany(), "GST_HSN_SAC_CREATED", "GST_HSN_SAC", entity.getId(),
                Map.of("code", entity.getCode(), "gstRate", entity.getGstRate()));
        return hsnResponse(entity);
    }

    @Transactional
    public HsnSacResponse updateHsnSac(
            UUID userId, UUID companyId, UUID codeId, HsnSacRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstHsnSac entity = hsn(companyId, codeId);
        if (!entity.getCode().equalsIgnoreCase(request.code().trim())
                && hsnSac.existsByCompanyIdAndCodeIgnoreCase(companyId, request.code().trim())) {
            throw new ConflictException("This HSN/SAC code already exists.");
        }
        applyHsn(entity, request);
        hsnSac.save(entity);
        record(userId, entity.getCompany(), "GST_HSN_SAC_UPDATED", "GST_HSN_SAC", entity.getId(),
                Map.of("code", entity.getCode(), "gstRate", entity.getGstRate()));
        return hsnResponse(entity);
    }

    @Transactional
    public void deactivateHsnSac(UUID userId, UUID companyId, UUID codeId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstHsnSac entity = hsn(companyId, codeId);
        entity.setActive(false);
        hsnSac.save(entity);
        record(userId, entity.getCompany(), "GST_HSN_SAC_DEACTIVATED", "GST_HSN_SAC", entity.getId(), Map.of());
    }

    @Transactional(readOnly = true)
    public List<GstRuleResponse> listRules(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return rules.findAllByCompanyIdOrderByName(companyId).stream().map(this::ruleResponse).toList();
    }

    @Transactional
    public GstRuleResponse createRule(UUID userId, UUID companyId, GstRuleRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (rules.existsByCompanyIdAndNameIgnoreCase(companyId, request.name().trim())) {
            throw new ConflictException("A GST rule with this name already exists.");
        }
        GstRule entity = new GstRule();
        entity.setCompany(company(companyId));
        applyRule(entity, request);
        entity = rules.save(entity);
        record(userId, entity.getCompany(), "GST_RULE_CREATED", "GST_RULE", entity.getId(),
                Map.of("treatment", entity.getGstTreatment().name(), "gstRate", entity.getGstRate()));
        return ruleResponse(entity);
    }

    @Transactional
    public GstRuleResponse updateRule(UUID userId, UUID companyId, UUID ruleId, GstRuleRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstRule entity = rule(companyId, ruleId);
        if (!entity.getName().equalsIgnoreCase(request.name().trim())
                && rules.existsByCompanyIdAndNameIgnoreCase(companyId, request.name().trim())) {
            throw new ConflictException("A GST rule with this name already exists.");
        }
        applyRule(entity, request);
        rules.save(entity);
        record(userId, entity.getCompany(), "GST_RULE_UPDATED", "GST_RULE", entity.getId(),
                Map.of("treatment", entity.getGstTreatment().name(), "active", entity.isActive()));
        return ruleResponse(entity);
    }

    @Transactional
    public void deactivateRule(UUID userId, UUID companyId, UUID ruleId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstRule entity = rule(companyId, ruleId);
        entity.setActive(false);
        rules.save(entity);
        record(userId, entity.getCompany(), "GST_RULE_DEACTIVATED", "GST_RULE", entity.getId(), Map.of());
    }

    private void applyRate(GstRate entity, GstRateRequest request) {
        entity.setName(request.name().trim());
        entity.setRate(percent(request.rate()));
        entity.setCessRate(percent(request.cessRate()));
        entity.setReverseChargeAllowed(request.reverseChargeAllowed() == null || request.reverseChargeAllowed());
        entity.setActive(request.active() == null || request.active());
    }

    private void applyHsn(GstHsnSac entity, HsnSacRequest request) {
        entity.setCode(request.code().trim());
        entity.setCodeType(request.codeType());
        entity.setDescription(request.description().trim());
        entity.setGstRate(percent(request.gstRate()));
        entity.setCessRate(percent(request.cessRate()));
        entity.setActive(request.active() == null || request.active());
    }

    private void applyRule(GstRule entity, GstRuleRequest request) {
        entity.setName(request.name().trim());
        entity.setGstTreatment(request.gstTreatment());
        entity.setHsnSacPrefix(blankToNull(request.hsnSacPrefix()));
        entity.setGstRate(percent(request.gstRate()));
        entity.setCessRate(percent(request.cessRate()));
        entity.setReverseCharge(request.reverseCharge() != null && request.reverseCharge());
        entity.setActive(request.active() == null || request.active());
    }

    private void record(
            UUID userId, Company company, String action, String entityType, UUID entityId,
            Map<String, Object> details) {
        User actor = users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
        audit.record(company, actor, action, entityType, entityId, details);
        memory.record(company, action, entityType, entityId, "GST master configuration changed.",
                new BigDecimal("1.0000"), details);
    }

    private GstRateResponse rateResponse(GstRate entity) {
        return new GstRateResponse(entity.getId(), entity.getName(), entity.getRate(), entity.getCessRate(),
                entity.isSystemRate(), entity.isReverseChargeAllowed(), entity.isActive(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private HsnSacResponse hsnResponse(GstHsnSac entity) {
        return new HsnSacResponse(entity.getId(), entity.getCode(), entity.getCodeType(), entity.getDescription(),
                entity.getGstRate(), entity.getCessRate(), entity.isActive(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private GstRuleResponse ruleResponse(GstRule entity) {
        return new GstRuleResponse(entity.getId(), entity.getName(), entity.getGstTreatment(),
                entity.getHsnSacPrefix(), entity.getGstRate(), entity.getCessRate(),
                entity.isReverseCharge(), entity.isActive(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
    private Company company(UUID companyId) {
        return companies.findById(companyId).orElseThrow(() -> new NotFoundException("Company not found."));
    }
    private GstRate rate(UUID companyId, UUID id) {
        return rates.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new NotFoundException("GST rate not found."));
    }
    private GstHsnSac hsn(UUID companyId, UUID id) {
        return hsnSac.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("HSN/SAC code not found."));
    }
    private GstRule rule(UUID companyId, UUID id) {
        return rules.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new NotFoundException("GST rule not found."));
    }
    private BigDecimal percent(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
