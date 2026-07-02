package com.anvritai.abhay.service;

import com.anvritai.abhay.api.ReportDtos.AccountingDashboardResponse;
import com.anvritai.abhay.api.ReportDtos.BalanceSheetResponse;
import com.anvritai.abhay.api.ReportDtos.BookLedger;
import com.anvritai.abhay.api.ReportDtos.BookResponse;
import com.anvritai.abhay.api.ReportDtos.DayBookResponse;
import com.anvritai.abhay.api.ReportDtos.DayBookRow;
import com.anvritai.abhay.api.ReportDtos.LedgerMovement;
import com.anvritai.abhay.api.ReportDtos.LedgerStatementResponse;
import com.anvritai.abhay.api.ReportDtos.OutstandingResponse;
import com.anvritai.abhay.api.ReportDtos.OutstandingRow;
import com.anvritai.abhay.api.ReportDtos.ProfitAndLossResponse;
import com.anvritai.abhay.api.ReportDtos.ReportLedgerRow;
import com.anvritai.abhay.api.ReportDtos.TrialBalanceResponse;
import com.anvritai.abhay.api.ReportDtos.TrialBalanceRow;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.accounting.AccountNature;
import com.anvritai.abhay.domain.accounting.JournalEntryLine;
import com.anvritai.abhay.domain.accounting.Ledger;
import com.anvritai.abhay.domain.accounting.LedgerOpeningBalance;
import com.anvritai.abhay.domain.accounting.LedgerType;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.accounting.VoucherLine;
import com.anvritai.abhay.domain.accounting.VoucherStatus;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.accounting.JournalEntryLineRepository;
import com.anvritai.abhay.repository.accounting.LedgerOpeningBalanceRepository;
import com.anvritai.abhay.repository.accounting.LedgerRepository;
import com.anvritai.abhay.repository.accounting.VoucherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingReportService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CompanyAccessService access;
    private final FinancialYearRepository financialYears;
    private final LedgerRepository ledgers;
    private final LedgerOpeningBalanceRepository openingBalances;
    private final JournalEntryLineRepository journalLines;
    private final VoucherRepository vouchers;

    public AccountingReportService(
            CompanyAccessService access,
            FinancialYearRepository financialYears,
            LedgerRepository ledgers,
            LedgerOpeningBalanceRepository openingBalances,
            JournalEntryLineRepository journalLines,
            VoucherRepository vouchers) {
        this.access = access;
        this.financialYears = financialYears;
        this.ledgers = ledgers;
        this.openingBalances = openingBalances;
        this.journalLines = journalLines;
        this.vouchers = vouchers;
    }

    @Transactional(readOnly = true)
    public DayBookResponse dayBook(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        List<DayBookRow> rows = vouchers.findAllByCompanyIdOrderByVoucherDateDescVoucherNumberDesc(companyId).stream()
                .filter(voucher -> voucher.getFinancialYear().getId().equals(financialYear.getId()))
                .sorted(Comparator.comparing(Voucher::getVoucherDate).thenComparing(Voucher::getVoucherNumber))
                .map(voucher -> new DayBookRow(
                        voucher.getId(),
                        voucher.getVoucherNumber(),
                        voucher.getVoucherType().getCode(),
                        voucher.getVoucherDate(),
                        voucher.getNarration(),
                        totalDebit(voucher),
                        totalCredit(voucher),
                        voucher.getStatus()))
                .toList();
        return new DayBookResponse(
                financialYear.getId(),
                rows,
                sum(rows.stream().map(DayBookRow::totalDebit).toList()),
                sum(rows.stream().map(DayBookRow::totalCredit).toList()));
    }

    @Transactional(readOnly = true)
    public LedgerStatementResponse ledgerStatement(UUID userId, UUID companyId, UUID ledgerId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        Ledger ledger = ledgers.findByIdAndCompanyId(ledgerId, companyId)
                .orElseThrow(() -> new NotFoundException("Ledger not found."));
        Opening opening = opening(companyId, financialYear, ledger);
        List<JournalEntryLine> lines = journalLines.findLedgerStatementLines(
                companyId, financialYear.getId(), ledgerId);
        BigDecimal running = money(opening.debit().subtract(opening.credit()));
        List<LedgerMovement> movements = new ArrayList<>();
        for (JournalEntryLine line : lines) {
            running = money(running.add(line.getDebit()).subtract(line.getCredit()));
            movements.add(new LedgerMovement(
                    line.getJournalEntry().getEntryDate(),
                    line.getJournalEntry().getVoucher().getId(),
                    line.getJournalEntry().getVoucher().getVoucherNumber(),
                    line.getJournalEntry().getVoucher().getVoucherType().getCode(),
                    line.getNarration(),
                    money(line.getDebit()),
                    money(line.getCredit()),
                    running.signum() >= 0 ? running : ZERO,
                    running.signum() < 0 ? running.abs() : ZERO));
        }
        return new LedgerStatementResponse(
                financialYear.getId(),
                ledger.getId(),
                ledger.getName(),
                opening.debit(),
                opening.credit(),
                movements,
                running.signum() >= 0 ? running : ZERO,
                running.signum() < 0 ? running.abs() : ZERO);
    }

    @Transactional(readOnly = true)
    public BookResponse cashBook(UUID userId, UUID companyId) {
        return book(userId, companyId, LedgerType.CASH, "CASH");
    }

    @Transactional(readOnly = true)
    public BookResponse bankBook(UUID userId, UUID companyId) {
        return book(userId, companyId, LedgerType.BANK, "BANK");
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        List<TrialBalanceRow> rows = snapshots(companyId, financialYear).values().stream()
                .filter(snapshot -> snapshot.ledger().isActive())
                .sorted(Comparator.comparing(snapshot -> snapshot.ledger().getName()))
                .map(snapshot -> {
                    BigDecimal net = snapshot.net();
                    return new TrialBalanceRow(
                            snapshot.ledger().getId(),
                            snapshot.ledger().getName(),
                            snapshot.ledger().getLedgerGroup().getName(),
                            snapshot.ledger().getLedgerGroup().getAccountNature(),
                            net.signum() >= 0 ? net : ZERO,
                            net.signum() < 0 ? net.abs() : ZERO);
                })
                .toList();
        BigDecimal totalDebit = sum(rows.stream().map(TrialBalanceRow::debitBalance).toList());
        BigDecimal totalCredit = sum(rows.stream().map(TrialBalanceRow::creditBalance).toList());
        return new TrialBalanceResponse(
                financialYear.getId(), rows, totalDebit, totalCredit, money(totalDebit.subtract(totalCredit)));
    }

    @Transactional(readOnly = true)
    public ProfitAndLossResponse profitAndLoss(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        return profitAndLoss(financialYear, snapshots(companyId, financialYear));
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        Map<UUID, Snapshot> snapshots = snapshots(companyId, financialYear);
        ProfitAndLossResponse pnl = profitAndLoss(financialYear, snapshots);
        List<ReportLedgerRow> assets = reportRows(snapshots, AccountNature.ASSET, true);
        List<ReportLedgerRow> liabilities = reportRows(snapshots, AccountNature.LIABILITY, false);
        List<ReportLedgerRow> equity = reportRows(snapshots, AccountNature.EQUITY, false);
        BigDecimal totalAssets = sum(assets.stream().map(ReportLedgerRow::amount).toList());
        BigDecimal totalLiabilitiesAndEquity = money(
                sum(liabilities.stream().map(ReportLedgerRow::amount).toList())
                        .add(sum(equity.stream().map(ReportLedgerRow::amount).toList()))
                        .add(pnl.netProfit()));
        return new BalanceSheetResponse(
                financialYear.getId(),
                assets,
                liabilities,
                equity,
                pnl.netProfit(),
                totalAssets,
                totalLiabilitiesAndEquity,
                money(totalAssets.subtract(totalLiabilitiesAndEquity)));
    }

    @Transactional(readOnly = true)
    public OutstandingResponse receivables(UUID userId, UUID companyId) {
        return outstanding(userId, companyId, LedgerType.CUSTOMER, true, "RECEIVABLES");
    }

    @Transactional(readOnly = true)
    public OutstandingResponse payables(UUID userId, UUID companyId) {
        return outstanding(userId, companyId, LedgerType.VENDOR, false, "PAYABLES");
    }

    @Transactional(readOnly = true)
    public AccountingDashboardResponse dashboard(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        Map<UUID, Snapshot> snapshots = snapshots(companyId, financialYear);
        List<Voucher> yearVouchers = vouchers.findAllByCompanyIdOrderByVoucherDateDescVoucherNumberDesc(companyId)
                .stream()
                .filter(voucher -> voucher.getFinancialYear().getId().equals(financialYear.getId()))
                .toList();
        ProfitAndLossResponse pnl = profitAndLoss(financialYear, snapshots);
        BigDecimal cash = typeBalance(snapshots, LedgerType.CASH, true);
        BigDecimal bank = typeBalance(snapshots, LedgerType.BANK, true);
        BigDecimal receivables = typeBalance(snapshots, LedgerType.CUSTOMER, true);
        BigDecimal payables = typeBalance(snapshots, LedgerType.VENDOR, false);
        return new AccountingDashboardResponse(
                financialYear.getId(),
                yearVouchers.size(),
                yearVouchers.stream().filter(voucher -> voucher.getStatus() != VoucherStatus.DRAFT).count(),
                yearVouchers.stream().filter(voucher -> voucher.getStatus() == VoucherStatus.DRAFT).count(),
                cash,
                bank,
                receivables,
                payables,
                pnl.totalIncome(),
                pnl.totalExpenses(),
                pnl.netProfit());
    }

    private BookResponse book(UUID userId, UUID companyId, LedgerType type, String label) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        List<BookLedger> books = ledgers.findAllByCompanyIdAndLedgerTypeInAndActiveTrueOrderByName(
                        companyId, List.of(type)).stream()
                .map(ledger -> {
                    LedgerStatementResponse statement = statement(companyId, financialYear, ledger);
                    return new BookLedger(
                            ledger.getId(),
                            ledger.getName(),
                            statement.openingDebit(),
                            statement.openingCredit(),
                            statement.entries(),
                            statement.closingDebit(),
                            statement.closingCredit());
                })
                .toList();
        return new BookResponse(
                financialYear.getId(),
                label,
                books,
                sum(books.stream().map(BookLedger::closingDebit).toList()),
                sum(books.stream().map(BookLedger::closingCredit).toList()));
    }

    private LedgerStatementResponse statement(UUID companyId, FinancialYear financialYear, Ledger ledger) {
        Opening opening = opening(companyId, financialYear, ledger);
        BigDecimal running = money(opening.debit().subtract(opening.credit()));
        List<LedgerMovement> movements = new ArrayList<>();
        for (JournalEntryLine line : journalLines.findLedgerStatementLines(
                companyId, financialYear.getId(), ledger.getId())) {
            running = money(running.add(line.getDebit()).subtract(line.getCredit()));
            movements.add(new LedgerMovement(
                    line.getJournalEntry().getEntryDate(),
                    line.getJournalEntry().getVoucher().getId(),
                    line.getJournalEntry().getVoucher().getVoucherNumber(),
                    line.getJournalEntry().getVoucher().getVoucherType().getCode(),
                    line.getNarration(),
                    money(line.getDebit()),
                    money(line.getCredit()),
                    running.signum() >= 0 ? running : ZERO,
                    running.signum() < 0 ? running.abs() : ZERO));
        }
        return new LedgerStatementResponse(
                financialYear.getId(), ledger.getId(), ledger.getName(), opening.debit(), opening.credit(), movements,
                running.signum() >= 0 ? running : ZERO,
                running.signum() < 0 ? running.abs() : ZERO);
    }

    private OutstandingResponse outstanding(
            UUID userId,
            UUID companyId,
            LedgerType type,
            boolean debitNature,
            String label) {
        access.requireMembership(companyId, userId);
        FinancialYear financialYear = activeFinancialYear(companyId);
        Map<UUID, Snapshot> snapshots = snapshots(companyId, financialYear);
        List<OutstandingRow> rows = snapshots.values().stream()
                .filter(snapshot -> snapshot.ledger().getLedgerType() == type)
                .map(snapshot -> new OutstandingRow(
                        snapshot.ledger().getId(),
                        snapshot.ledger().getName(),
                        debitNature ? snapshot.net().max(ZERO) : snapshot.net().negate().max(ZERO)))
                .filter(row -> row.amount().signum() > 0)
                .sorted(Comparator.comparing(OutstandingRow::ledgerName))
                .toList();
        return new OutstandingResponse(
                financialYear.getId(), label, rows, sum(rows.stream().map(OutstandingRow::amount).toList()));
    }

    private ProfitAndLossResponse profitAndLoss(FinancialYear financialYear, Map<UUID, Snapshot> snapshots) {
        List<ReportLedgerRow> income = reportRows(snapshots, AccountNature.INCOME, false);
        List<ReportLedgerRow> expenses = reportRows(snapshots, AccountNature.EXPENSE, true);
        BigDecimal totalIncome = sum(income.stream().map(ReportLedgerRow::amount).toList());
        BigDecimal totalExpenses = sum(expenses.stream().map(ReportLedgerRow::amount).toList());
        BigDecimal directExpenses = sum(expenses.stream()
                .filter(row -> row.groupName().equalsIgnoreCase("Direct Expenses")
                        || row.groupName().equalsIgnoreCase("Purchase Accounts"))
                .map(ReportLedgerRow::amount)
                .toList());
        return new ProfitAndLossResponse(
                financialYear.getId(),
                income,
                expenses,
                totalIncome,
                totalExpenses,
                money(totalIncome.subtract(directExpenses)),
                money(totalIncome.subtract(totalExpenses)));
    }

    private List<ReportLedgerRow> reportRows(
            Map<UUID, Snapshot> snapshots,
            AccountNature nature,
            boolean debitNature) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.ledger().getLedgerGroup().getAccountNature() == nature)
                .map(snapshot -> new ReportLedgerRow(
                        snapshot.ledger().getId(),
                        snapshot.ledger().getName(),
                        snapshot.ledger().getLedgerGroup().getName(),
                        debitNature ? snapshot.net() : snapshot.net().negate()))
                .filter(row -> row.amount().signum() != 0)
                .sorted(Comparator.comparing(ReportLedgerRow::ledgerName))
                .toList();
    }

    private Map<UUID, Snapshot> snapshots(UUID companyId, FinancialYear financialYear) {
        Map<UUID, Opening> openings = new HashMap<>();
        for (LedgerOpeningBalance opening : openingBalances.findAllByCompanyIdAndFinancialYearId(
                companyId, financialYear.getId())) {
            openings.put(opening.getLedger().getId(), new Opening(
                    money(opening.getOpeningDebit()), money(opening.getOpeningCredit())));
        }
        Map<UUID, Snapshot> snapshots = new HashMap<>();
        for (Ledger ledger : ledgers.findAllByCompanyIdOrderByName(companyId)) {
            Opening opening = openings.getOrDefault(
                    ledger.getId(), new Opening(money(ledger.getOpeningDebit()), money(ledger.getOpeningCredit())));
            snapshots.put(ledger.getId(), new Snapshot(ledger, opening.debit(), opening.credit()));
        }
        for (JournalEntryLine line : journalLines.findReportLines(companyId, financialYear.getId())) {
            Snapshot snapshot = snapshots.get(line.getLedger().getId());
            if (snapshot != null) {
                snapshot.add(money(line.getDebit()), money(line.getCredit()));
            }
        }
        return snapshots;
    }

    private Opening opening(UUID companyId, FinancialYear financialYear, Ledger ledger) {
        return openingBalances.findByCompanyIdAndFinancialYearIdAndLedgerId(
                        companyId, financialYear.getId(), ledger.getId())
                .map(value -> new Opening(money(value.getOpeningDebit()), money(value.getOpeningCredit())))
                .orElseGet(() -> new Opening(money(ledger.getOpeningDebit()), money(ledger.getOpeningCredit())));
    }

    private BigDecimal typeBalance(Map<UUID, Snapshot> snapshots, LedgerType type, boolean debitNature) {
        return money(snapshots.values().stream()
                .filter(snapshot -> snapshot.ledger().getLedgerType() == type)
                .map(snapshot -> debitNature ? snapshot.net() : snapshot.net().negate())
                .filter(value -> value.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private FinancialYear activeFinancialYear(UUID companyId) {
        return financialYears.findFirstByCompanyIdAndActiveTrueOrderByStartsOnDesc(companyId)
                .orElseThrow(() -> new AccountingRuleException("No active financial year exists for this company."));
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

    private BigDecimal sum(List<BigDecimal> values) {
        return money(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record Opening(BigDecimal debit, BigDecimal credit) {
    }

    private static final class Snapshot {
        private final Ledger ledger;
        private final BigDecimal openingDebit;
        private final BigDecimal openingCredit;
        private BigDecimal movementDebit = ZERO;
        private BigDecimal movementCredit = ZERO;

        private Snapshot(Ledger ledger, BigDecimal openingDebit, BigDecimal openingCredit) {
            this.ledger = ledger;
            this.openingDebit = openingDebit;
            this.openingCredit = openingCredit;
        }

        private void add(BigDecimal debit, BigDecimal credit) {
            movementDebit = movementDebit.add(debit);
            movementCredit = movementCredit.add(credit);
        }

        private Ledger ledger() {
            return ledger;
        }

        private BigDecimal net() {
            return openingDebit.add(movementDebit).subtract(openingCredit).subtract(movementCredit)
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}
