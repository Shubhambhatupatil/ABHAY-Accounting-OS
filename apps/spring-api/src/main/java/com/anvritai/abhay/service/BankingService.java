package com.anvritai.abhay.service;

import com.anvritai.abhay.api.BankingDtos.*;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.accounting.*;
import com.anvritai.abhay.domain.banking.*;
import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoicePayment;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.repository.CompanyRepository;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.accounting.AccountBalanceRepository;
import com.anvritai.abhay.repository.accounting.LedgerRepository;
import com.anvritai.abhay.repository.accounting.VoucherRepository;
import com.anvritai.abhay.repository.banking.*;
import com.anvritai.abhay.repository.sales.InvoicePaymentRepository;
import com.anvritai.abhay.repository.sales.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankingService {
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final long MAX_IMPORT_SIZE = 10L * 1024 * 1024;
    private final CompanyAccessService access;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final LedgerRepository ledgers;
    private final BankAccountRepository accounts;
    private final BankStatementImportRepository imports;
    private final BankTransactionRepository transactions;
    private final BankReconciliationSuggestionRepository suggestions;
    private final BankReconciliationMatchRepository matches;
    private final VoucherRepository vouchers;
    private final InvoicePaymentRepository payments;
    private final InvoiceRepository invoices;
    private final AccountBalanceRepository balances;
    private final CashFlowSnapshotRepository snapshots;
    private final TreasuryAlertRepository alerts;
    private final AuditService audit;
    private final GstMemoryService memory;

    public BankingService(
            CompanyAccessService access, CompanyRepository companies, UserRepository users,
            LedgerRepository ledgers, BankAccountRepository accounts,
            BankStatementImportRepository imports, BankTransactionRepository transactions,
            BankReconciliationSuggestionRepository suggestions,
            BankReconciliationMatchRepository matches, VoucherRepository vouchers,
            InvoicePaymentRepository payments, InvoiceRepository invoices,
            AccountBalanceRepository balances, CashFlowSnapshotRepository snapshots,
            TreasuryAlertRepository alerts, AuditService audit, GstMemoryService memory) {
        this.access = access;
        this.companies = companies;
        this.users = users;
        this.ledgers = ledgers;
        this.accounts = accounts;
        this.imports = imports;
        this.transactions = transactions;
        this.suggestions = suggestions;
        this.matches = matches;
        this.vouchers = vouchers;
        this.payments = payments;
        this.invoices = invoices;
        this.balances = balances;
        this.snapshots = snapshots;
        this.alerts = alerts;
        this.audit = audit;
        this.memory = memory;
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> accounts(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return accounts.findAllByCompanyIdOrderByBankNameAscAccountNameAsc(companyId).stream()
                .map(this::accountResponse).toList();
    }

    @Transactional
    public BankAccountResponse createAccount(UUID userId, UUID companyId, BankAccountRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Ledger ledger = requireEligibleLedger(companyId, request.ledgerId());
        if (accounts.existsByCompanyIdAndLedgerId(companyId, ledger.getId())) {
            throw new ConflictException("This ledger is already linked to a bank account.");
        }
        BankAccount account = new BankAccount();
        account.setCompany(company(companyId));
        account.setLedger(ledger);
        applyAccount(account, request);
        account = accounts.save(account);
        record(userId, account.getCompany(), "BANK_ACCOUNT_CREATED", "BANK_ACCOUNT", account.getId(),
                Map.of("ledgerId", ledger.getId(), "accountType", account.getAccountType().name()));
        return accountResponse(account);
    }

    @Transactional
    public BankAccountResponse updateAccount(
            UUID userId, UUID companyId, UUID bankAccountId, BankAccountRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        BankAccount account = requireAccount(companyId, bankAccountId);
        Ledger ledger = requireEligibleLedger(companyId, request.ledgerId());
        if (accounts.existsByCompanyIdAndLedgerIdAndIdNot(companyId, ledger.getId(), bankAccountId)) {
            throw new ConflictException("This ledger is already linked to another bank account.");
        }
        account.setLedger(ledger);
        applyAccount(account, request);
        accounts.save(account);
        record(userId, account.getCompany(), "BANK_ACCOUNT_UPDATED", "BANK_ACCOUNT", account.getId(),
                Map.of("ledgerId", ledger.getId(), "active", account.isActive()));
        return accountResponse(account);
    }

    @Transactional
    public StatementImportResponse importStatement(
            UUID userId, UUID companyId, UUID bankAccountId, String fileName, byte[] content) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (content.length == 0 || content.length > MAX_IMPORT_SIZE) {
            throw new AccountingRuleException("Bank statement CSV must be between 1 byte and 10 MB.");
        }
        BankAccount account = requireAccount(companyId, bankAccountId);
        String fileHash = sha256(content);
        Optional<BankStatementImport> existing = imports.findByCompanyIdAndBankAccountIdAndFileHash(
                companyId, bankAccountId, fileHash);
        if (existing.isPresent()) {
            return importResponse(existing.get());
        }
        List<CsvTransaction> rows = parseCsv(content);
        BankStatementImport statementImport = new BankStatementImport();
        statementImport.setCompany(account.getCompany());
        statementImport.setBankAccount(account);
        statementImport.setFileName(cleanFileName(fileName));
        statementImport.setFileSize(content.length);
        statementImport.setFileHash(fileHash);
        statementImport.setStatus("PROCESSING");
        statementImport.setImportedBy(user(userId));
        statementImport = imports.save(statementImport);
        int imported = 0;
        int duplicates = 0;
        for (CsvTransaction row : rows) {
            String rawHash = transactionHash(row);
            if (transactions.existsByCompanyIdAndBankAccountIdAndRawHash(companyId, bankAccountId, rawHash)) {
                duplicates++;
                continue;
            }
            BankTransaction transaction = new BankTransaction();
            transaction.setCompany(account.getCompany());
            transaction.setBankAccount(account);
            transaction.setStatementImport(statementImport);
            transaction.setTransactionDate(row.date());
            transaction.setDescription(row.description());
            transaction.setReference(row.reference());
            transaction.setDebit(row.debit());
            transaction.setCredit(row.credit());
            transaction.setBalance(row.balance());
            transaction.setCounterparty(row.counterparty());
            transaction.setRawHash(rawHash);
            transactions.save(transaction);
            imported++;
        }
        statementImport.setImportedRows(imported);
        statementImport.setDuplicateRows(duplicates);
        statementImport.setStatus("COMPLETED");
        imports.save(statementImport);
        record(userId, account.getCompany(), "BANK_STATEMENT_IMPORTED", "BANK_STATEMENT_IMPORT",
                statementImport.getId(), Map.of("importedRows", imported, "duplicateRows", duplicates));
        return importResponse(statementImport);
    }

    @Transactional(readOnly = true)
    public List<BankTransactionResponse> transactions(UUID userId, UUID companyId, UUID bankAccountId) {
        access.requireMembership(companyId, userId);
        requireAccount(companyId, bankAccountId);
        return transactions.findAllByCompanyIdAndBankAccountIdOrderByTransactionDateAscCreatedAtAsc(
                companyId, bankAccountId).stream().map(this::transactionResponse).toList();
    }

    @Transactional
    public List<ReconciliationSuggestionResponse> suggestions(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        List<Voucher> companyVouchers = vouchers.findAllByCompanyIdOrderByVoucherDateDescVoucherNumberDesc(companyId)
                .stream().filter(v -> v.getStatus() == VoucherStatus.POSTED).toList();
        List<InvoicePayment> companyPayments = payments.findAllByCompanyIdOrderByPaymentDateDescCreatedAtDesc(companyId);
        for (BankTransaction transaction : transactions.findAllByCompanyIdAndReconciliationStatusOrderByTransactionDateDesc(
                companyId, ReconciliationStatus.UNMATCHED)) {
            rebuildSuggestions(transaction, companyVouchers, companyPayments);
        }
        return suggestions.findAllByCompanyIdAndActiveTrueOrderByConfidenceDescCreatedAtDesc(companyId)
                .stream().map(this::suggestionResponse).toList();
    }

    @Transactional
    public ReconciliationActionResponse confirm(
            UUID userId, UUID companyId, UUID transactionId, ConfirmMatchRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        BankTransaction transaction = lockTransaction(companyId, transactionId);
        if (transaction.getReconciliationStatus() == ReconciliationStatus.IGNORED) {
            throw new AccountingRuleException("Ignored transactions must be unignored before matching.");
        }
        BankReconciliationMatch match = matches.findByCompanyIdAndBankTransactionId(companyId, transactionId)
                .orElseGet(BankReconciliationMatch::new);
        if (match.isActive() && match.getId() != null) {
            throw new ConflictException("This bank transaction is already reconciled.");
        }
        Target target = requireTarget(companyId, request.targetType(), request.targetId());
        boolean targetAlreadyMatched = request.targetType() == ReconciliationTargetType.VOUCHER
                ? matches.existsByCompanyIdAndVoucherIdAndActiveTrue(companyId, request.targetId())
                : matches.existsByCompanyIdAndInvoicePaymentIdAndActiveTrue(companyId, request.targetId());
        if (targetAlreadyMatched) {
            throw new ConflictException("This accounting record is already reconciled to another bank transaction.");
        }
        match.setCompany(transaction.getCompany());
        match.setBankTransaction(transaction);
        match.setVoucher(target.voucher());
        match.setInvoicePayment(target.payment());
        match.setMatchType(target.confidence().compareTo(new BigDecimal("0.9000")) >= 0 ? "EXACT" : "MANUAL");
        match.setConfidence(target.confidence());
        match.setActive(true);
        match.setConfirmedBy(user(userId));
        match.setConfirmedAt(Instant.now());
        match.setUnmatchedAt(null);
        match = matches.save(match);
        transaction.setReconciliationStatus(ReconciliationStatus.MATCHED);
        transactions.save(transaction);
        suggestions.deleteAllByCompanyIdAndBankTransactionId(companyId, transactionId);
        Map<String, Object> matchMemory = new LinkedHashMap<>();
        matchMemory.put("targetType", request.targetType().name());
        matchMemory.put("targetId", request.targetId());
        matchMemory.put("confidence", target.confidence());
        Voucher matchedVoucher = target.voucher() != null ? target.voucher() : target.payment().getLinkedVoucher();
        matchedVoucher.getLines().stream()
                .filter(line -> !line.getLedger().getId().equals(transaction.getBankAccount().getLedger().getId()))
                .findFirst().ifPresent(line -> {
                    matchMemory.put("targetLedgerId", line.getLedger().getId());
                    matchMemory.put("targetLedgerName", line.getLedger().getName());
                });
        recordReconciliation(userId, transaction, "BANK_TRANSACTION_MATCHED", matchMemory);
        return new ReconciliationActionResponse(transactionId, ReconciliationStatus.MATCHED, match.getId(),
                request.targetType(), request.targetId());
    }

    @Transactional
    public ReconciliationActionResponse ignore(UUID userId, UUID companyId, UUID transactionId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        BankTransaction transaction = lockTransaction(companyId, transactionId);
        if (transaction.getReconciliationStatus() == ReconciliationStatus.MATCHED) {
            throw new AccountingRuleException("Matched transactions must be unmatched before they can be ignored.");
        }
        transaction.setReconciliationStatus(ReconciliationStatus.IGNORED);
        transactions.save(transaction);
        suggestions.deleteAllByCompanyIdAndBankTransactionId(companyId, transactionId);
        recordReconciliation(userId, transaction, "BANK_TRANSACTION_IGNORED", Map.of());
        return new ReconciliationActionResponse(transactionId, ReconciliationStatus.IGNORED, null, null, null);
    }

    @Transactional
    public ReconciliationActionResponse unmatch(UUID userId, UUID companyId, UUID transactionId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        BankTransaction transaction = lockTransaction(companyId, transactionId);
        BankReconciliationMatch match = matches.findByCompanyIdAndBankTransactionId(companyId, transactionId)
                .orElse(null);
        if (match != null && match.isActive()) {
            match.setActive(false);
            match.setUnmatchedAt(Instant.now());
            matches.save(match);
        }
        transaction.setReconciliationStatus(ReconciliationStatus.UNMATCHED);
        transactions.save(transaction);
        recordReconciliation(userId, transaction, "BANK_TRANSACTION_UNMATCHED", Map.of());
        return new ReconciliationActionResponse(transactionId, ReconciliationStatus.UNMATCHED,
                match == null ? null : match.getId(), null, null);
    }

    @Transactional(readOnly = true)
    public BankBookResponse bankBook(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        List<BankBookAccount> result = new ArrayList<>();
        BigDecimal total = ZERO;
        for (BankAccount account : accounts.findAllByCompanyIdOrderByBankNameAscAccountNameAsc(companyId)) {
            BigDecimal running = money(account.getOpeningBalance());
            List<BankBookRow> rows = new ArrayList<>();
            for (BankTransaction transaction : transactions
                    .findAllByCompanyIdAndBankAccountIdOrderByTransactionDateAscCreatedAtAsc(companyId, account.getId())) {
                running = running.add(transaction.getCredit()).subtract(transaction.getDebit());
                rows.add(new BankBookRow(transaction.getId(), transaction.getTransactionDate(),
                        transaction.getDescription(), transaction.getReference(), transaction.getDebit(),
                        transaction.getCredit(), money(running), transaction.getReconciliationStatus()));
            }
            running = money(running);
            total = total.add(running);
            result.add(new BankBookAccount(account.getId(), account.getAccountName(), account.getBankName(),
                    account.getAccountNumberMasked(), account.getOpeningBalance(), rows, running));
        }
        return new BankBookResponse(result, money(total));
    }

    @Transactional(readOnly = true)
    public ReconciliationReportResponse reconciliationReport(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        Map<UUID, BankReconciliationMatch> matchByTransaction = new HashMap<>();
        matches.findAllByCompanyIdOrderByConfirmedAtDesc(companyId).stream().filter(BankReconciliationMatch::isActive)
                .forEach(match -> matchByTransaction.put(match.getBankTransaction().getId(), match));
        List<ReconciliationReportRow> rows = transactions.findAllByCompanyIdOrderByTransactionDateDescCreatedAtDesc(companyId)
                .stream().map(transaction -> reportRow(transaction, matchByTransaction.get(transaction.getId()))).toList();
        return new ReconciliationReportResponse(
                count(rows, ReconciliationStatus.MATCHED), count(rows, ReconciliationStatus.UNMATCHED),
                count(rows, ReconciliationStatus.IGNORED), count(rows, ReconciliationStatus.SUGGESTED), rows);
    }

    @Transactional(readOnly = true)
    public List<BankTransactionResponse> unmatched(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return transactions.findAllByCompanyIdOrderByTransactionDateDescCreatedAtDesc(companyId).stream()
                .filter(transaction -> transaction.getReconciliationStatus() == ReconciliationStatus.UNMATCHED
                        || transaction.getReconciliationStatus() == ReconciliationStatus.SUGGESTED)
                .map(this::transactionResponse).toList();
    }

    @Transactional
    public CashPositionResponse cashPosition(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        CashPositionResponse position = calculateCashPosition(companyId);
        saveSnapshot(companyId, position);
        return position;
    }

    @Transactional
    public TreasuryDashboardResponse treasuryDashboard(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        CashPositionResponse position = calculateCashPosition(companyId);
        List<Invoice> open = invoices.findAllByCompanyIdAndStatusInOrderByDueDate(
                companyId, List.of(InvoiceStatus.APPROVED, InvoiceStatus.POSTED));
        BigDecimal receivables = ZERO;
        BigDecimal payables = ZERO;
        for (Invoice invoice : open) {
            BigDecimal outstanding = invoice.getTotal().subtract(payments.totalPaid(companyId, invoice.getId())).max(ZERO);
            if (invoice.getInvoiceType() == InvoiceType.SALES) receivables = receivables.add(outstanding);
            else payables = payables.add(outstanding);
        }
        long unreconciled = transactions.countByCompanyIdAndReconciliationStatus(companyId, ReconciliationStatus.UNMATCHED)
                + transactions.countByCompanyIdAndReconciliationStatus(companyId, ReconciliationStatus.SUGGESTED);
        refreshTreasuryAlert(companyId, unreconciled);
        saveSnapshot(companyId, position);
        List<TreasuryAlertResponse> activeAlerts = alerts.findAllByCompanyIdAndResolvedFalseOrderByCreatedAtDesc(companyId)
                .stream().map(a -> new TreasuryAlertResponse(a.getId(), a.getAlertType(), a.getSeverity(),
                        a.getMessage(), a.getAmount(), a.getCreatedAt())).toList();
        return new TreasuryDashboardResponse(position.bankLedgerBalance(), position.cashLedgerBalance(),
                position.totalLiquidity(), unreconciled, money(receivables), money(payables),
                activeAlerts, LocalDate.now());
    }

    private void rebuildSuggestions(
            BankTransaction transaction, List<Voucher> companyVouchers, List<InvoicePayment> companyPayments) {
        suggestions.deleteAllByCompanyIdAndBankTransactionId(transaction.getCompany().getId(), transaction.getId());
        List<Candidate> candidates = new ArrayList<>();
        for (InvoicePayment payment : companyPayments) {
            BigDecimal confidence = score(transaction, payment.getAmount(), payment.getPaymentDate(),
                    payment.getReference(), payment.getInvoice().getInvoiceNumber());
            if (confidence.compareTo(new BigDecimal("0.5500")) >= 0) {
                candidates.add(new Candidate(ReconciliationTargetType.INVOICE_PAYMENT, payment.getId(), null,
                        payment, confidence, "Amount, payment date and reference similarity"));
            }
        }
        Set<UUID> paymentVoucherIds = companyPayments.stream().map(p -> p.getLinkedVoucher().getId())
                .collect(java.util.stream.Collectors.toSet());
        for (Voucher voucher : companyVouchers) {
            if (paymentVoucherIds.contains(voucher.getId())) continue;
            BigDecimal confidence = score(transaction, voucherAmount(voucher), voucher.getVoucherDate(),
                    voucher.getVoucherNumber(), voucher.getNarration());
            if (confidence.compareTo(new BigDecimal("0.5500")) >= 0) {
                candidates.add(new Candidate(ReconciliationTargetType.VOUCHER, voucher.getId(), voucher,
                        null, confidence, "Amount, voucher date and narration similarity"));
            }
        }
        candidates.stream().sorted(Comparator.comparing(Candidate::confidence).reversed()).limit(5).forEach(candidate -> {
            BankReconciliationSuggestion suggestion = new BankReconciliationSuggestion();
            suggestion.setCompany(transaction.getCompany());
            suggestion.setBankTransaction(transaction);
            suggestion.setTargetType(candidate.type());
            suggestion.setVoucher(candidate.voucher());
            suggestion.setInvoicePayment(candidate.payment());
            suggestion.setConfidence(candidate.confidence());
            suggestion.setReason(candidate.reason());
            suggestions.save(suggestion);
        });
        if (!candidates.isEmpty()) {
            transaction.setReconciliationStatus(ReconciliationStatus.SUGGESTED);
            transactions.save(transaction);
        }
    }

    private Target requireTarget(UUID companyId, ReconciliationTargetType type, UUID id) {
        if (type == ReconciliationTargetType.VOUCHER) {
            Voucher voucher = vouchers.findByIdAndCompanyId(id, companyId)
                    .orElseThrow(() -> new NotFoundException("Voucher was not found."));
            if (voucher.getStatus() != VoucherStatus.POSTED) {
                throw new AccountingRuleException("Only posted vouchers can be reconciled.");
            }
            return new Target(voucher, null, new BigDecimal("0.5000"));
        }
        InvoicePayment payment = payments.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Invoice payment was not found."));
        return new Target(null, payment, new BigDecimal("0.5000"));
    }

    private CashPositionResponse calculateCashPosition(UUID companyId) {
        Map<UUID, BigDecimal> movementByLedger = new HashMap<>();
        for (AccountBalance balance : balances.findAllByCompanyIdOrderByLedgerName(companyId)) {
            movementByLedger.merge(balance.getLedger().getId(),
                    balance.getDebitTotal().subtract(balance.getCreditTotal()), BigDecimal::add);
        }
        BigDecimal bank = ZERO;
        Set<UUID> bankLedgers = new HashSet<>();
        for (BankAccount account : accounts.findAllByCompanyIdOrderByBankNameAscAccountNameAsc(companyId)) {
            if (!account.isActive()) continue;
            bankLedgers.add(account.getLedger().getId());
            bank = bank.add(account.getOpeningBalance())
                    .add(movementByLedger.getOrDefault(account.getLedger().getId(), ZERO));
        }
        BigDecimal cash = ledgers.findAllByCompanyIdAndLedgerTypeInAndActiveTrueOrderByName(
                        companyId, List.of(LedgerType.CASH)).stream()
                .filter(ledger -> !bankLedgers.contains(ledger.getId()))
                .map(ledger -> ledger.getOpeningDebit().subtract(ledger.getOpeningCredit())
                        .add(movementByLedger.getOrDefault(ledger.getId(), ZERO)))
                .reduce(ZERO, BigDecimal::add);
        List<BankTransaction> unmatched = transactions.findAllByCompanyIdOrderByTransactionDateDescCreatedAtDesc(companyId)
                .stream().filter(t -> t.getReconciliationStatus() == ReconciliationStatus.UNMATCHED
                        || t.getReconciliationStatus() == ReconciliationStatus.SUGGESTED).toList();
        BigDecimal credits = unmatched.stream().map(BankTransaction::getCredit).reduce(ZERO, BigDecimal::add);
        BigDecimal debits = unmatched.stream().map(BankTransaction::getDebit).reduce(ZERO, BigDecimal::add);
        BigDecimal liquidity = bank.add(cash);
        return new CashPositionResponse(money(bank), money(cash), money(liquidity), money(credits), money(debits),
                money(liquidity.add(credits).subtract(debits)));
    }

    private void saveSnapshot(UUID companyId, CashPositionResponse position) {
        CashFlowSnapshot snapshot = snapshots.findByCompanyIdAndSnapshotDate(companyId, LocalDate.now())
                .orElseGet(CashFlowSnapshot::new);
        snapshot.setCompany(company(companyId));
        snapshot.setSnapshotDate(LocalDate.now());
        snapshot.setBankBalance(position.bankLedgerBalance());
        snapshot.setCashBalance(position.cashLedgerBalance());
        snapshot.setTotalLiquidity(position.totalLiquidity());
        snapshot.setUnreconciledNet(position.unreconciledCredits().subtract(position.unreconciledDebits()));
        snapshots.save(snapshot);
    }

    private void refreshTreasuryAlert(UUID companyId, long unreconciled) {
        Optional<TreasuryAlert> existing = alerts.findByCompanyIdAndAlertTypeAndResolvedFalse(
                companyId, "UNRECONCILED_TRANSACTIONS");
        if (unreconciled == 0) {
            existing.ifPresent(alert -> { alert.setResolved(true); alerts.save(alert); });
            return;
        }
        TreasuryAlert alert = existing.orElseGet(TreasuryAlert::new);
        alert.setCompany(company(companyId));
        alert.setAlertType("UNRECONCILED_TRANSACTIONS");
        alert.setSeverity(unreconciled > 20 ? "HIGH" : "MEDIUM");
        alert.setMessage(unreconciled + " bank transactions require reconciliation review.");
        alert.setAmount(BigDecimal.valueOf(unreconciled));
        alerts.save(alert);
    }

    private void recordReconciliation(UUID userId, BankTransaction transaction, String action, Map<String, Object> details) {
        Map<String, Object> enriched = new LinkedHashMap<>(details);
        enriched.put("description", transaction.getDescription());
        enriched.put("amount", transaction.amount());
        if (transaction.getCounterparty() != null) enriched.put("counterparty", transaction.getCounterparty());
        if (transaction.getReference() != null) enriched.put("reference", transaction.getReference());
        record(userId, transaction.getCompany(), action, "BANK_TRANSACTION", transaction.getId(), enriched);
    }

    private void record(UUID userId, Company company, String action, String entityType, UUID entityId,
            Map<String, Object> details) {
        audit.record(company, user(userId), action, entityType, entityId, details);
        memory.record(company, action, entityType, entityId, "Banking and treasury activity",
                new BigDecimal("1.0000"), details);
    }

    private BigDecimal score(BankTransaction transaction, BigDecimal amount, LocalDate date,
            String reference, String description) {
        if (transaction.amount().compareTo(amount) != 0) return ZERO.setScale(4);
        BigDecimal score = new BigDecimal("0.6000");
        long days = Math.abs(ChronoUnit.DAYS.between(transaction.getTransactionDate(), date));
        if (days <= 3) score = score.add(BigDecimal.valueOf(3 - days).multiply(new BigDecimal("0.0500")));
        String haystack = normalize(transaction.getDescription() + " " + nullToEmpty(transaction.getReference())
                + " " + nullToEmpty(transaction.getCounterparty()));
        if (!normalize(reference).isBlank() && haystack.contains(normalize(reference))) score = score.add(new BigDecimal("0.2000"));
        else if (tokenSimilarity(haystack, normalize(description)) >= 0.4) score = score.add(new BigDecimal("0.1000"));
        return score.min(new BigDecimal("0.9900")).setScale(4, RoundingMode.HALF_UP);
    }

    private double tokenSimilarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0;
        Set<String> a = new HashSet<>(Arrays.asList(left.split("\\s+")));
        Set<String> b = new HashSet<>(Arrays.asList(right.split("\\s+")));
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    private List<CsvTransaction> parseCsv(byte[] content) {
        String csv = new String(content, StandardCharsets.UTF_8).replace("\uFEFF", "");
        List<String> lines = csv.lines().filter(line -> !line.isBlank()).toList();
        if (lines.size() < 2) throw new AccountingRuleException("CSV must include a header and at least one transaction.");
        List<String> headers = csvFields(lines.get(0)).stream().map(this::normalizeHeader).toList();
        int date = requiredColumn(headers, "date", "transactiondate");
        int description = requiredColumn(headers, "description", "narration");
        int reference = optionalColumn(headers, "reference", "ref");
        int debit = requiredColumn(headers, "debit", "withdrawal");
        int credit = requiredColumn(headers, "credit", "deposit");
        int balance = optionalColumn(headers, "balance", "closingbalance");
        int counterparty = optionalColumn(headers, "counterparty", "party");
        List<CsvTransaction> result = new ArrayList<>();
        for (int lineNumber = 1; lineNumber < lines.size(); lineNumber++) {
            List<String> fields = csvFields(lines.get(lineNumber));
            try {
                BigDecimal debitValue = decimal(value(fields, debit));
                BigDecimal creditValue = decimal(value(fields, credit));
                if ((debitValue.signum() > 0) == (creditValue.signum() > 0)) {
                    throw new AccountingRuleException("Exactly one of debit or credit must be positive.");
                }
                result.add(new CsvTransaction(parseDate(value(fields, date)), value(fields, description),
                        optionalValue(fields, reference), debitValue, creditValue,
                        balance < 0 || optionalValue(fields, balance).isBlank() ? null : decimal(value(fields, balance)),
                        optionalValue(fields, counterparty)));
            } catch (RuntimeException exception) {
                throw new AccountingRuleException("Invalid bank statement row " + (lineNumber + 1) + ": "
                        + exception.getMessage());
            }
        }
        return result;
    }

    private List<String> csvFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { current.append('"'); i++; }
                else quoted = !quoted;
            } else if (c == ',' && !quoted) { fields.add(current.toString().trim()); current.setLength(0); }
            else current.append(c);
        }
        if (quoted) throw new AccountingRuleException("CSV contains an unterminated quoted value.");
        fields.add(current.toString().trim());
        return fields;
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/uuuu"), DateTimeFormatter.ofPattern("d-M-uuuu"))) {
            try { return LocalDate.parse(value.trim(), formatter); } catch (DateTimeParseException ignored) { }
        }
        throw new AccountingRuleException("Date must use YYYY-MM-DD, DD/MM/YYYY, or DD-MM-YYYY.");
    }

    private void applyAccount(BankAccount account, BankAccountRequest request) {
        String masked = request.accountNumberMasked().trim();
        if (!masked.contains("*") && masked.replaceAll("\\D", "").length() > 4) {
            throw new AccountingRuleException("Store only a masked account number, for example ********1234.");
        }
        account.setBankName(request.bankName().trim());
        account.setAccountName(request.accountName().trim());
        account.setAccountNumberMasked(masked);
        account.setAccountType(request.accountType());
        account.setIfsc(blankToNull(request.ifsc()) == null ? null : request.ifsc().trim().toUpperCase(Locale.ROOT));
        account.setBranch(blankToNull(request.branch()));
        account.setCurrency(request.currency() == null ? "INR" : request.currency().trim().toUpperCase(Locale.ROOT));
        account.setOpeningBalance(money(request.openingBalance() == null ? ZERO : request.openingBalance()));
        account.setActive(request.active() == null || request.active());
    }

    private Ledger requireEligibleLedger(UUID companyId, UUID ledgerId) {
        Ledger ledger = ledgers.findByIdAndCompanyId(ledgerId, companyId)
                .orElseThrow(() -> new NotFoundException("Ledger was not found."));
        if (ledger.getLedgerType() != LedgerType.BANK && ledger.getLedgerType() != LedgerType.CASH
                && ledger.getLedgerGroup().getAccountNature() != AccountNature.ASSET) {
            throw new AccountingRuleException("Bank accounts can link only to asset, cash, or bank ledgers.");
        }
        return ledger;
    }

    private BankAccount requireAccount(UUID companyId, UUID accountId) {
        return accounts.findByIdAndCompanyId(accountId, companyId)
                .orElseThrow(() -> new NotFoundException("Bank account was not found."));
    }
    private BankTransaction lockTransaction(UUID companyId, UUID id) {
        return transactions.lockByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Bank transaction was not found."));
    }
    private Company company(UUID id) { return companies.findById(id).orElseThrow(() -> new NotFoundException("Company was not found.")); }
    private User user(UUID id) { return users.findById(id).orElseThrow(() -> new NotFoundException("User was not found.")); }
    private BigDecimal voucherAmount(Voucher voucher) { return voucher.getLines().stream().map(VoucherLine::getDebit).reduce(ZERO, BigDecimal::add); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private long count(List<ReconciliationReportRow> rows, ReconciliationStatus status) { return rows.stream().filter(r -> r.status() == status).count(); }
    private String normalize(String value) { return nullToEmpty(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private String normalizeHeader(String value) { return normalize(value).replace(" ", ""); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private BigDecimal decimal(String value) { return value == null || value.isBlank() ? ZERO : new BigDecimal(value.replace(",", "").trim()).abs().setScale(2, RoundingMode.HALF_UP); }
    private String value(List<String> fields, int index) { if (index < 0 || index >= fields.size()) throw new AccountingRuleException("Required column value is missing."); return fields.get(index).trim(); }
    private String optionalValue(List<String> fields, int index) { return index < 0 || index >= fields.size() ? "" : fields.get(index).trim(); }
    private int requiredColumn(List<String> headers, String... names) { int value = optionalColumn(headers, names); if (value < 0) throw new AccountingRuleException("Required CSV column is missing: " + names[0]); return value; }
    private int optionalColumn(List<String> headers, String... names) { for (String name : names) { int index = headers.indexOf(name); if (index >= 0) return index; } return -1; }
    private String cleanFileName(String value) { String file = value == null ? "statement.csv" : value.replace("\\", "/"); return file.substring(file.lastIndexOf('/') + 1); }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable.", e); } }
    private String transactionHash(CsvTransaction row) { return sha256((row.date() + "|" + normalize(row.description()) + "|" + normalize(row.reference()) + "|" + row.debit() + "|" + row.credit() + "|" + row.balance()).getBytes(StandardCharsets.UTF_8)); }

    private BankAccountResponse accountResponse(BankAccount a) { return new BankAccountResponse(a.getId(), a.getLedger().getId(), a.getLedger().getName(), a.getBankName(), a.getAccountName(), a.getAccountNumberMasked(), a.getAccountType(), a.getIfsc(), a.getBranch(), a.getCurrency(), a.getOpeningBalance(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt()); }
    private StatementImportResponse importResponse(BankStatementImport i) { return new StatementImportResponse(i.getId(), i.getFileName(), i.getFileSize(), i.getImportedRows(), i.getDuplicateRows(), i.getStatus(), i.getCreatedAt()); }
    private BankTransactionResponse transactionResponse(BankTransaction t) { return new BankTransactionResponse(t.getId(), t.getBankAccount().getId(), t.getBankAccount().getAccountName(), t.getTransactionDate(), t.getDescription(), t.getReference(), t.getDebit(), t.getCredit(), t.getBalance(), t.getCounterparty(), t.getReconciliationStatus(), t.getCreatedAt()); }
    private ReconciliationSuggestionResponse suggestionResponse(BankReconciliationSuggestion s) { UUID targetId = s.getTargetType() == ReconciliationTargetType.VOUCHER ? s.getVoucher().getId() : s.getInvoicePayment().getId(); String reference = s.getTargetType() == ReconciliationTargetType.VOUCHER ? s.getVoucher().getVoucherNumber() : nullToEmpty(s.getInvoicePayment().getReference()); return new ReconciliationSuggestionResponse(s.getId(), s.getBankTransaction().getId(), s.getTargetType(), targetId, reference, s.getBankTransaction().amount(), s.getConfidence(), s.getReason()); }
    private ReconciliationReportRow reportRow(BankTransaction t, BankReconciliationMatch m) { String type = null; UUID id = null; String ref = null; BigDecimal confidence = null; if (m != null) { confidence = m.getConfidence(); if (m.getVoucher() != null) { type = "VOUCHER"; id = m.getVoucher().getId(); ref = m.getVoucher().getVoucherNumber(); } else { type = "INVOICE_PAYMENT"; id = m.getInvoicePayment().getId(); ref = m.getInvoicePayment().getReference(); } } return new ReconciliationReportRow(t.getId(), t.getTransactionDate(), t.getDescription(), t.amount(), t.getReconciliationStatus(), confidence, type, id, ref); }

    private record CsvTransaction(LocalDate date, String description, String reference, BigDecimal debit,
                                  BigDecimal credit, BigDecimal balance, String counterparty) { }
    private record Candidate(ReconciliationTargetType type, UUID id, Voucher voucher, InvoicePayment payment,
                             BigDecimal confidence, String reason) { }
    private record Target(Voucher voucher, InvoicePayment payment, BigDecimal confidence) { }
}
