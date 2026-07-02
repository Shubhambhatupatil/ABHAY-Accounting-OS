package com.anvritai.abhay.service;

import com.anvritai.abhay.api.AccountingDtos.AccountBalanceResponse;
import com.anvritai.abhay.api.AccountingDtos.LedgerCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.LedgerGroupCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.LedgerGroupResponse;
import com.anvritai.abhay.api.AccountingDtos.LedgerResponse;
import com.anvritai.abhay.api.AccountingDtos.LedgerUpdateRequest;
import com.anvritai.abhay.api.AccountingDtos.OpeningBalanceRequest;
import com.anvritai.abhay.api.AccountingDtos.OpeningBalanceResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherCreateRequest;
import com.anvritai.abhay.api.AccountingDtos.VoucherLineRequest;
import com.anvritai.abhay.api.AccountingDtos.VoucherLineResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherTypeResponse;
import com.anvritai.abhay.api.AccountingDtos.VoucherUpdateRequest;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.accounting.AccountBalance;
import com.anvritai.abhay.domain.accounting.AiMemoryEvent;
import com.anvritai.abhay.domain.accounting.JournalEntry;
import com.anvritai.abhay.domain.accounting.JournalEntryLine;
import com.anvritai.abhay.domain.accounting.Ledger;
import com.anvritai.abhay.domain.accounting.LedgerGroup;
import com.anvritai.abhay.domain.accounting.LedgerOpeningBalance;
import com.anvritai.abhay.domain.accounting.LedgerType;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.accounting.VoucherLine;
import com.anvritai.abhay.domain.accounting.VoucherSeries;
import com.anvritai.abhay.domain.accounting.VoucherStatus;
import com.anvritai.abhay.domain.accounting.VoucherType;
import com.anvritai.abhay.repository.CompanyRepository;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.accounting.AccountBalanceRepository;
import com.anvritai.abhay.repository.accounting.AiMemoryEventRepository;
import com.anvritai.abhay.repository.accounting.JournalEntryRepository;
import com.anvritai.abhay.repository.accounting.LedgerGroupRepository;
import com.anvritai.abhay.repository.accounting.LedgerOpeningBalanceRepository;
import com.anvritai.abhay.repository.accounting.LedgerRepository;
import com.anvritai.abhay.repository.accounting.VoucherRepository;
import com.anvritai.abhay.repository.accounting.VoucherSeriesRepository;
import com.anvritai.abhay.repository.accounting.VoucherTypeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.JoinType;

@Service
public class AccountingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};

    private final CompanyAccessService access;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final FinancialYearRepository financialYears;
    private final LedgerGroupRepository ledgerGroups;
    private final LedgerRepository ledgers;
    private final LedgerOpeningBalanceRepository openingBalances;
    private final VoucherTypeRepository voucherTypes;
    private final VoucherSeriesRepository voucherSeries;
    private final VoucherRepository vouchers;
    private final JournalEntryRepository journalEntries;
    private final AccountBalanceRepository accountBalances;
    private final AiMemoryEventRepository aiMemoryEvents;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final MemoryEventCaptureService memoryEvents;

    public AccountingService(
            CompanyAccessService access,
            CompanyRepository companies,
            UserRepository users,
            FinancialYearRepository financialYears,
            LedgerGroupRepository ledgerGroups,
            LedgerRepository ledgers,
            LedgerOpeningBalanceRepository openingBalances,
            VoucherTypeRepository voucherTypes,
            VoucherSeriesRepository voucherSeries,
            VoucherRepository vouchers,
            JournalEntryRepository journalEntries,
            AccountBalanceRepository accountBalances,
            AiMemoryEventRepository aiMemoryEvents,
            AuditService auditService,
            ObjectMapper objectMapper,
            MemoryEventCaptureService memoryEvents) {
        this.access = access;
        this.companies = companies;
        this.users = users;
        this.financialYears = financialYears;
        this.ledgerGroups = ledgerGroups;
        this.ledgers = ledgers;
        this.openingBalances = openingBalances;
        this.voucherTypes = voucherTypes;
        this.voucherSeries = voucherSeries;
        this.vouchers = vouchers;
        this.journalEntries = journalEntries;
        this.accountBalances = accountBalances;
        this.aiMemoryEvents = aiMemoryEvents;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.memoryEvents = memoryEvents;
    }

    @Transactional(readOnly = true)
    public List<LedgerGroupResponse> listLedgerGroups(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return ledgerGroups.findAllByCompanyIdOrderByName(companyId).stream().map(this::groupResponse).toList();
    }

    @Transactional
    public LedgerGroupResponse createLedgerGroup(UUID userId, UUID companyId, LedgerGroupCreateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (ledgerGroups.existsByCompanyIdAndNameIgnoreCase(companyId, request.name().trim())) {
            throw new ConflictException("A ledger group with this name already exists.");
        }
        Company company = requireCompany(companyId);
        LedgerGroup parent = null;
        if (request.parentId() != null) {
            parent = ledgerGroups.findByIdAndCompanyId(request.parentId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Parent ledger group not found."));
            if (parent.getAccountNature() != request.accountNature()) {
                throw new AccountingRuleException("Parent and child ledger groups must have the same account nature.");
            }
        }
        LedgerGroup group = new LedgerGroup();
        group.setCompany(company);
        group.setName(request.name().trim());
        group.setAccountNature(request.accountNature());
        group.setParent(parent);
        group.setSystemGroup(false);
        group = ledgerGroups.save(group);
        auditService.record(
                company,
                requireUser(userId),
                "LEDGER_GROUP_CREATED",
                "LEDGER_GROUP",
                group.getId(),
                Map.of("name", group.getName(), "accountNature", group.getAccountNature().name()));
        return groupResponse(group);
    }

    @Transactional(readOnly = true)
    public List<LedgerResponse> listLedgers(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return ledgers.findAllByCompanyIdOrderByName(companyId).stream().map(this::ledgerResponse).toList();
    }

    @Transactional
    public LedgerResponse createLedger(UUID userId, UUID companyId, LedgerCreateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        String name = request.name().trim();
        String code = blankToNull(request.code());
        if (ledgers.existsByCompanyIdAndNameIgnoreCase(companyId, name)) {
            throw new ConflictException("A ledger with this name already exists.");
        }
        if (code != null && ledgers.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
            throw new ConflictException("A ledger with this code already exists.");
        }
        LedgerGroup group = ledgerGroups.findByIdAndCompanyId(request.ledgerGroupId(), companyId)
                .orElseThrow(() -> new NotFoundException("Ledger group not found."));
        BigDecimal openingDebit = money(request.openingDebit());
        BigDecimal openingCredit = money(request.openingCredit());
        validateOneSide(openingDebit, openingCredit, true);
        FinancialYear financialYear = activeFinancialYear(companyId);
        if (openingDebit.signum() > 0 || openingCredit.signum() > 0) {
            requireUnlocked(financialYear);
        }
        Ledger ledger = new Ledger();
        ledger.setCompany(requireCompany(companyId));
        ledger.setLedgerGroup(group);
        ledger.setName(name);
        ledger.setCode(code);
        ledger.setNormalBalance(request.normalBalance());
        ledger.setLedgerType(request.ledgerType() == null ? LedgerType.GENERAL : request.ledgerType());
        ledger.setOpeningDebit(openingDebit);
        ledger.setOpeningCredit(openingCredit);
        ledger.setActive(true);
        ledger = ledgers.save(ledger);
        saveOpeningBalance(ledger, financialYear, openingDebit, openingCredit);
        auditService.record(
                ledger.getCompany(),
                requireUser(userId),
                "LEDGER_CREATED",
                "LEDGER",
                ledger.getId(),
                Map.of("name", ledger.getName(), "groupId", group.getId().toString()));
        return ledgerResponse(ledger);
    }

    @Transactional
    public LedgerResponse updateLedger(
            UUID userId,
            UUID companyId,
            UUID ledgerId,
            LedgerUpdateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Ledger ledger = requireLedger(companyId, ledgerId);
        if (request.name() != null && !request.name().trim().equalsIgnoreCase(ledger.getName())) {
            if (ledgers.existsByCompanyIdAndNameIgnoreCase(companyId, request.name().trim())) {
                throw new ConflictException("A ledger with this name already exists.");
            }
            ledger.setName(request.name().trim());
        }
        if (request.code() != null) {
            String code = blankToNull(request.code());
            if (code != null
                    && (ledger.getCode() == null || !code.equalsIgnoreCase(ledger.getCode()))
                    && ledgers.existsByCompanyIdAndCodeIgnoreCase(companyId, code)) {
                throw new ConflictException("A ledger with this code already exists.");
            }
            ledger.setCode(code);
        }
        if (request.normalBalance() != null) {
            ledger.setNormalBalance(request.normalBalance());
        }
        if (request.ledgerType() != null) {
            ledger.setLedgerType(request.ledgerType());
        }
        if (request.active() != null) {
            ledger.setActive(request.active());
        }
        ledgers.save(ledger);
        auditService.record(
                ledger.getCompany(),
                requireUser(userId),
                "LEDGER_UPDATED",
                "LEDGER",
                ledger.getId(),
                Map.of("name", ledger.getName(), "active", ledger.isActive()));
        return ledgerResponse(ledger);
    }

    @Transactional
    public OpeningBalanceResponse setOpeningBalance(
            UUID userId,
            UUID companyId,
            UUID ledgerId,
            OpeningBalanceRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Ledger ledger = requireLedger(companyId, ledgerId);
        FinancialYear financialYear = request.financialYearId() == null
                ? activeFinancialYear(companyId)
                : financialYears.findByIdAndCompanyId(request.financialYearId(), companyId)
                        .orElseThrow(() -> new NotFoundException("Financial year not found."));
        requireUnlocked(financialYear);
        BigDecimal debit = money(request.openingDebit());
        BigDecimal credit = money(request.openingCredit());
        validateOneSide(debit, credit, true);
        LedgerOpeningBalance opening = saveOpeningBalance(ledger, financialYear, debit, credit);
        if (financialYear.isActive()) {
            ledger.setOpeningDebit(debit);
            ledger.setOpeningCredit(credit);
            ledgers.save(ledger);
        }
        auditService.record(
                ledger.getCompany(),
                requireUser(userId),
                "LEDGER_OPENING_BALANCE_UPDATED",
                "LEDGER",
                ledger.getId(),
                Map.of(
                        "financialYearId", financialYear.getId().toString(),
                        "openingDebit", debit.toPlainString(),
                        "openingCredit", credit.toPlainString()));
        return new OpeningBalanceResponse(
                financialYear.getId(), ledger.getId(), debit, credit, opening.getUpdatedAt());
    }

    @Transactional(readOnly = true)
    public List<VoucherTypeResponse> listVoucherTypes(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return voucherTypes.findAllByActiveTrueOrderByName().stream()
                .map(type -> new VoucherTypeResponse(type.getId(), type.getCode(), type.getName(), type.isSystemType()))
                .toList();
    }

    @Transactional
    public VoucherResponse createVoucher(UUID userId, UUID companyId, VoucherCreateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Company company = requireCompany(companyId);
        User actor = requireUser(userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        requireUnlocked(financialYear);
        validateVoucherDate(financialYear, request.voucherDate());
        VoucherType voucherType = requireVoucherType(request.voucherTypeCode());
        VoucherSeries series = requireSeries(company, financialYear, voucherType);

        Voucher voucher = new Voucher();
        voucher.setCompany(company);
        voucher.setFinancialYear(financialYear);
        voucher.setVoucherType(voucherType);
        voucher.setVoucherSeries(series);
        voucher.setVoucherNumber(nextVoucherNumber(series));
        voucher.setVoucherDate(request.voucherDate());
        voucher.setStatus(VoucherStatus.DRAFT);
        voucher.setNarration(blankToNull(request.narration()));
        voucher.setCreatedBy(actor);
        addVoucherLines(voucher, companyId, request.lines());
        voucher = vouchers.save(voucher);
        auditService.record(
                company,
                actor,
                "VOUCHER_DRAFT_CREATED",
                "VOUCHER",
                voucher.getId(),
                Map.of("voucherNumber", voucher.getVoucherNumber(), "voucherType", voucherType.getCode()));
        return voucherResponse(voucher);
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> listVouchers(
            UUID userId,
            UUID companyId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String voucherType,
            VoucherStatus status,
            UUID ledgerId,
            String search) {
        access.requireMembership(companyId, userId);
        Specification<Voucher> specification = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("company").get("id"), companyId));
            if (dateFrom != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("voucherDate"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("voucherDate"), dateTo));
            }
            if (voucherType != null && !voucherType.isBlank()) {
                predicates.add(builder.equal(
                        root.join("voucherType", JoinType.INNER).get("code"),
                        voucherType.trim().toUpperCase(Locale.ROOT)));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (ledgerId != null) {
                predicates.add(builder.equal(root.join("lines", JoinType.INNER).get("ledger").get("id"), ledgerId));
                query.distinct(true);
            }
            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("voucherNumber")), term),
                        builder.like(builder.lower(root.get("narration")), term)));
            }
            return builder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return vouchers.findAll(
                        specification,
                        Sort.by(Sort.Order.desc("voucherDate"), Sort.Order.desc("voucherNumber"))).stream()
                .map(this::voucherResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public VoucherResponse getVoucher(UUID userId, UUID companyId, UUID voucherId) {
        access.requireMembership(companyId, userId);
        return voucherResponse(requireVoucher(companyId, voucherId));
    }

    @Transactional
    public VoucherResponse updateVoucher(
            UUID userId,
            UUID companyId,
            UUID voucherId,
            VoucherUpdateRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Voucher voucher = lockVoucher(companyId, voucherId);
        if (voucher.getStatus() != VoucherStatus.DRAFT) {
            throw new AccountingRuleException("Posted or reversed vouchers cannot be edited.");
        }
        requireUnlocked(voucher.getFinancialYear());
        validateVoucherDate(voucher.getFinancialYear(), request.voucherDate());
        voucher.setVoucherDate(request.voucherDate());
        voucher.setNarration(blankToNull(request.narration()));
        voucher.clearLines();
        vouchers.flush();
        addVoucherLines(voucher, companyId, request.lines());
        vouchers.save(voucher);
        auditService.record(
                voucher.getCompany(),
                requireUser(userId),
                "VOUCHER_DRAFT_UPDATED",
                "VOUCHER",
                voucher.getId(),
                Map.of("voucherNumber", voucher.getVoucherNumber()));
        return voucherResponse(voucher);
    }

    @Transactional
    public VoucherResponse postVoucher(UUID userId, UUID companyId, UUID voucherId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Voucher voucher = lockVoucher(companyId, voucherId);
        if (voucher.getStatus() != VoucherStatus.DRAFT) {
            throw new AccountingRuleException("Only draft vouchers can be posted.");
        }
        requireUnlocked(voucher.getFinancialYear());
        postInternal(voucher, requireUser(userId), false);
        return voucherResponse(voucher);
    }

    @Transactional
    public VoucherResponse reverseVoucher(UUID userId, UUID companyId, UUID voucherId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Voucher original = lockVoucher(companyId, voucherId);
        if (original.getStatus() != VoucherStatus.POSTED) {
            throw new AccountingRuleException("Only posted vouchers can be reversed.");
        }
        requireUnlocked(original.getFinancialYear());
        User actor = requireUser(userId);
        VoucherSeries series = requireSeries(original.getCompany(), original.getFinancialYear(), original.getVoucherType());
        Voucher reversal = new Voucher();
        reversal.setCompany(original.getCompany());
        reversal.setFinancialYear(original.getFinancialYear());
        reversal.setVoucherType(original.getVoucherType());
        reversal.setVoucherSeries(series);
        reversal.setVoucherNumber(nextVoucherNumber(series));
        reversal.setVoucherDate(original.getVoucherDate());
        reversal.setStatus(VoucherStatus.DRAFT);
        reversal.setNarration("Reversal of " + original.getVoucherNumber());
        reversal.setCreatedBy(actor);
        reversal.setReversalOf(original);
        int lineNumber = 1;
        for (VoucherLine originalLine : original.getLines()) {
            VoucherLine line = new VoucherLine();
            line.setLineNumber(lineNumber++);
            line.setLedger(originalLine.getLedger());
            line.setDebit(money(originalLine.getCredit()));
            line.setCredit(money(originalLine.getDebit()));
            line.setNarration("Reversal of " + original.getVoucherNumber());
            reversal.addLine(line);
        }
        reversal = vouchers.save(reversal);
        postInternal(reversal, actor, true);
        original.setStatus(VoucherStatus.REVERSED);
        original.setReversedBy(actor);
        original.setReversedAt(Instant.now());
        original.setReversalVoucher(reversal);
        vouchers.save(original);
        auditService.record(
                original.getCompany(),
                actor,
                "VOUCHER_REVERSED",
                "VOUCHER",
                original.getId(),
                Map.of(
                        "voucherNumber", original.getVoucherNumber(),
                        "reversalVoucherId", reversal.getId().toString(),
                        "reversalVoucherNumber", reversal.getVoucherNumber()));
        return voucherResponse(original);
    }

    @Transactional(readOnly = true)
    public List<AccountBalanceResponse> accountBalances(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        Map<UUID, AccountBalance> movements = new HashMap<>();
        for (AccountBalance balance : accountBalances.findAllByCompanyIdAndFinancialYearIdOrderByLedgerName(
                companyId, financialYear.getId())) {
            movements.put(balance.getLedger().getId(), balance);
        }
        Map<UUID, LedgerOpeningBalance> openings = new HashMap<>();
        for (LedgerOpeningBalance opening : openingBalances.findAllByCompanyIdAndFinancialYearId(
                companyId, financialYear.getId())) {
            openings.put(opening.getLedger().getId(), opening);
        }
        return ledgers.findAllByCompanyIdOrderByName(companyId).stream()
                .map(ledger -> balanceResponse(ledger, movements.get(ledger.getId()), openings.get(ledger.getId())))
                .toList();
    }

    private void postInternal(Voucher voucher, User actor, boolean reversal) {
        validateBalanced(voucher);
        if (journalEntries.existsByVoucherId(voucher.getId())) {
            throw new AccountingRuleException("This voucher already has a journal entry.");
        }
        JournalEntry journal = new JournalEntry();
        journal.setCompany(voucher.getCompany());
        journal.setFinancialYear(voucher.getFinancialYear());
        journal.setVoucher(voucher);
        journal.setEntryDate(voucher.getVoucherDate());
        journal.setDescription(voucher.getNarration());
        journal.setReversal(reversal);
        for (VoucherLine source : voucher.getLines()) {
            JournalEntryLine line = new JournalEntryLine();
            line.setLedger(source.getLedger());
            line.setLineNumber(source.getLineNumber());
            line.setDebit(money(source.getDebit()));
            line.setCredit(money(source.getCredit()));
            line.setNarration(source.getNarration());
            journal.addLine(line);
            applyBalance(voucher, source);
        }
        journalEntries.save(journal);
        voucher.setStatus(VoucherStatus.POSTED);
        voucher.setPostedBy(actor);
        voucher.setPostedAt(Instant.now());
        vouchers.save(voucher);
        auditService.record(
                voucher.getCompany(),
                actor,
                "VOUCHER_POSTED",
                "VOUCHER",
                voucher.getId(),
                Map.of(
                        "voucherNumber", voucher.getVoucherNumber(),
                        "totalDebit", totalDebit(voucher).toPlainString(),
                        "totalCredit", totalCredit(voucher).toPlainString(),
                        "reversal", reversal));
        createAiMemoryEvent(voucher);
    }

    private void applyBalance(Voucher voucher, VoucherLine line) {
        AccountBalance balance = accountBalances.lockByScope(
                        voucher.getCompany().getId(),
                        voucher.getFinancialYear().getId(),
                        line.getLedger().getId())
                .orElseGet(() -> {
                    AccountBalance created = new AccountBalance();
                    created.setCompany(voucher.getCompany());
                    created.setFinancialYear(voucher.getFinancialYear());
                    created.setLedger(line.getLedger());
                    created.setDebitTotal(ZERO);
                    created.setCreditTotal(ZERO);
                    return created;
                });
        balance.setDebitTotal(money(balance.getDebitTotal().add(line.getDebit())));
        balance.setCreditTotal(money(balance.getCreditTotal().add(line.getCredit())));
        accountBalances.save(balance);
    }

    private void createAiMemoryEvent(Voucher voucher) {
        List<Map<String, Object>> ledgerMappings = voucher.getLines().stream().map(line -> Map.<String, Object>of(
                "ledgerId", line.getLedger().getId(), "ledgerName", line.getLedger().getName(),
                "debit", line.getDebit(), "credit", line.getCredit())).toList();
        Map<String, Object> details = new HashMap<>();
        details.put("voucherNumber", voucher.getVoucherNumber());
        details.put("voucherType", voucher.getVoucherType().getCode());
        details.put("totalDebit", totalDebit(voucher).toPlainString());
        details.put("totalCredit", totalCredit(voucher).toPlainString());
        details.put("narration", voucher.getNarration() == null ? voucher.getVoucherType().getName() : voucher.getNarration());
        details.put("ledgerMappings", ledgerMappings);
        AiMemoryEvent event = new AiMemoryEvent();
        event.setCompany(voucher.getCompany());
        event.setEventType("VOUCHER_POSTED");
        event.setEntityType("VOUCHER");
        event.setEntityId(voucher.getId());
        event.setProcessingStatus("PENDING");
        try {
            event.setPayloadJson(objectMapper.writeValueAsString(details));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI memory event could not be serialized.", exception);
        }
        aiMemoryEvents.save(event);
        memoryEvents.record(voucher.getCompany(), "VOUCHER_POSTED", "VOUCHER", voucher.getId(), details);
    }

    private void validateBalanced(Voucher voucher) {
        if (voucher.getLines().size() < 2) {
            throw new AccountingRuleException("A voucher requires at least two lines.");
        }
        BigDecimal debit = totalDebit(voucher);
        BigDecimal credit = totalCredit(voucher);
        if (debit.signum() <= 0 || debit.compareTo(credit) != 0) {
            throw new AccountingRuleException(
                    "Voucher debit and credit totals must be equal and greater than zero before posting.");
        }
    }

    private void addVoucherLines(Voucher voucher, UUID companyId, List<VoucherLineRequest> requests) {
        int lineNumber = 1;
        for (VoucherLineRequest request : requests) {
            Ledger ledger = requireLedger(companyId, request.ledgerId());
            if (!ledger.isActive()) {
                throw new AccountingRuleException("Inactive ledgers cannot be used in vouchers.");
            }
            BigDecimal debit = money(request.debit());
            BigDecimal credit = money(request.credit());
            validateOneSide(debit, credit, false);
            VoucherLine line = new VoucherLine();
            line.setLedger(ledger);
            line.setLineNumber(lineNumber++);
            line.setDebit(debit);
            line.setCredit(credit);
            line.setNarration(blankToNull(request.narration()));
            voucher.addLine(line);
        }
    }

    private void validateOneSide(BigDecimal debit, BigDecimal credit, boolean allowZero) {
        if (debit.signum() < 0 || credit.signum() < 0) {
            throw new AccountingRuleException("Debit and credit amounts cannot be negative.");
        }
        boolean debitOnly = debit.signum() > 0 && credit.signum() == 0;
        boolean creditOnly = credit.signum() > 0 && debit.signum() == 0;
        if (!allowZero && !debitOnly && !creditOnly) {
            throw new AccountingRuleException("Each voucher line must contain either a debit or a credit amount.");
        }
        if (allowZero && debit.signum() > 0 && credit.signum() > 0) {
            throw new AccountingRuleException("Opening balance must be entered on only one side.");
        }
    }

    private VoucherSeries requireSeries(Company company, FinancialYear financialYear, VoucherType type) {
        return voucherSeries.lockByScope(company.getId(), financialYear.getId(), type.getId())
                .orElseGet(() -> {
                    VoucherSeries series = new VoucherSeries();
                    series.setCompany(company);
                    series.setFinancialYear(financialYear);
                    series.setVoucherType(type);
                    series.setPrefix(type.getCode() + "-");
                    series.setNextNumber(1);
                    series.setPadding(6);
                    return voucherSeries.saveAndFlush(series);
                });
    }

    private String nextVoucherNumber(VoucherSeries series) {
        long number = series.getNextNumber();
        series.setNextNumber(number + 1);
        voucherSeries.save(series);
        return series.getPrefix() + String.format(Locale.ROOT, "%0" + series.getPadding() + "d", number);
    }

    private void validateVoucherDate(FinancialYear financialYear, java.time.LocalDate voucherDate) {
        if (voucherDate.isBefore(financialYear.getStartsOn()) || voucherDate.isAfter(financialYear.getEndsOn())) {
            throw new AccountingRuleException("Voucher date must fall inside the active financial year.");
        }
    }

    private FinancialYear activeFinancialYear(UUID companyId) {
        return financialYears.findFirstByCompanyIdAndActiveTrueOrderByStartsOnDesc(companyId)
                .orElseThrow(() -> new AccountingRuleException("No active financial year exists for this company."));
    }

    private void requireUnlocked(FinancialYear financialYear) {
        if (financialYear.isLocked()) {
            throw new AccountingRuleException("This financial year is locked. Unlock it before changing books.");
        }
    }

    private LedgerOpeningBalance saveOpeningBalance(
            Ledger ledger,
            FinancialYear financialYear,
            BigDecimal openingDebit,
            BigDecimal openingCredit) {
        LedgerOpeningBalance opening = openingBalances.findByCompanyIdAndFinancialYearIdAndLedgerId(
                        ledger.getCompany().getId(), financialYear.getId(), ledger.getId())
                .orElseGet(() -> {
                    LedgerOpeningBalance created = new LedgerOpeningBalance();
                    created.setCompany(ledger.getCompany());
                    created.setFinancialYear(financialYear);
                    created.setLedger(ledger);
                    return created;
                });
        opening.setOpeningDebit(openingDebit);
        opening.setOpeningCredit(openingCredit);
        return openingBalances.save(opening);
    }

    private VoucherType requireVoucherType(String code) {
        return voucherTypes.findByCodeAndActiveTrue(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("Voucher type not found."));
    }

    private Voucher requireVoucher(UUID companyId, UUID voucherId) {
        return vouchers.findByIdAndCompanyId(voucherId, companyId)
                .orElseThrow(() -> new NotFoundException("Voucher not found."));
    }

    private Voucher lockVoucher(UUID companyId, UUID voucherId) {
        return vouchers.lockByIdAndCompanyId(voucherId, companyId)
                .orElseThrow(() -> new NotFoundException("Voucher not found."));
    }

    private Ledger requireLedger(UUID companyId, UUID ledgerId) {
        return ledgers.findByIdAndCompanyId(ledgerId, companyId)
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
    }

    private Company requireCompany(UUID companyId) {
        return companies.findById(companyId).orElseThrow(() -> new NotFoundException("Company not found."));
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
    }

    private LedgerGroupResponse groupResponse(LedgerGroup group) {
        return new LedgerGroupResponse(
                group.getId(),
                group.getName(),
                group.getAccountNature(),
                group.getParent() == null ? null : group.getParent().getId(),
                group.isSystemGroup(),
                group.getCreatedAt());
    }

    private LedgerResponse ledgerResponse(Ledger ledger) {
        return new LedgerResponse(
                ledger.getId(),
                ledger.getLedgerGroup().getId(),
                ledger.getLedgerGroup().getName(),
                ledger.getName(),
                ledger.getCode(),
                ledger.getNormalBalance(),
                ledger.getLedgerType(),
                money(ledger.getOpeningDebit()),
                money(ledger.getOpeningCredit()),
                ledger.isActive(),
                ledger.getCreatedAt(),
                ledger.getUpdatedAt());
    }

    private VoucherResponse voucherResponse(Voucher voucher) {
        List<VoucherLineResponse> lines = voucher.getLines().stream()
                .map(line -> new VoucherLineResponse(
                        line.getId(),
                        line.getLineNumber(),
                        line.getLedger().getId(),
                        line.getLedger().getName(),
                        money(line.getDebit()),
                        money(line.getCredit()),
                        line.getNarration()))
                .toList();
        return new VoucherResponse(
                voucher.getId(),
                voucher.getFinancialYear().getId(),
                voucher.getVoucherType().getCode(),
                voucher.getVoucherNumber(),
                voucher.getVoucherDate(),
                voucher.getStatus(),
                voucher.getNarration(),
                totalDebit(voucher),
                totalCredit(voucher),
                voucher.getReversalVoucher() == null ? null : voucher.getReversalVoucher().getId(),
                voucher.getPostedAt(),
                voucher.getReversedAt(),
                lines,
                voucher.getCreatedAt(),
                voucher.getUpdatedAt());
    }

    private AccountBalanceResponse balanceResponse(
            Ledger ledger,
            AccountBalance balance,
            LedgerOpeningBalance opening) {
        BigDecimal openingDebit = opening == null ? money(ledger.getOpeningDebit()) : money(opening.getOpeningDebit());
        BigDecimal openingCredit = opening == null ? money(ledger.getOpeningCredit()) : money(opening.getOpeningCredit());
        BigDecimal periodDebit = balance == null ? ZERO : money(balance.getDebitTotal());
        BigDecimal periodCredit = balance == null ? ZERO : money(balance.getCreditTotal());
        BigDecimal net = money(openingDebit.add(periodDebit).subtract(openingCredit).subtract(periodCredit));
        return new AccountBalanceResponse(
                ledger.getId(),
                ledger.getName(),
                ledger.getLedgerGroup().getName(),
                openingDebit,
                openingCredit,
                periodDebit,
                periodCredit,
                net.signum() >= 0 ? net : ZERO,
                net.signum() < 0 ? net.abs() : ZERO);
    }

    private BigDecimal totalDebit(Voucher voucher) {
        return money(voucher.getLines().stream()
                .map(VoucherLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal totalCredit(Voucher voucher) {
        return money(voucher.getLines().stream()
                .map(VoucherLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
