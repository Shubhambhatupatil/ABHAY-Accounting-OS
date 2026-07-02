package com.anvritai.abhay.service;

import com.anvritai.abhay.api.GstDtos.GstAlertResponse;
import com.anvritai.abhay.api.GstDtos.GstDashboardResponse;
import com.anvritai.abhay.api.GstDtos.GstPeriodSummary;
import com.anvritai.abhay.api.GstDtos.GstRegisterResponse;
import com.anvritai.abhay.api.GstDtos.GstRegisterRow;
import com.anvritai.abhay.api.GstDtos.GstSummaryResponse;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.gst.GstAlert;
import com.anvritai.abhay.domain.gst.GstLiability;
import com.anvritai.abhay.domain.gst.GstTreatment;
import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.gst.GstAlertRepository;
import com.anvritai.abhay.repository.gst.GstLiabilityRepository;
import com.anvritai.abhay.repository.gst.GstReturnRepository;
import com.anvritai.abhay.repository.sales.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GstReportService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final CompanyAccessService access;
    private final InvoiceRepository invoices;
    private final GstAlertRepository alerts;
    private final GstReturnRepository returns;
    private final GstLiabilityRepository liabilities;
    private final FinancialYearRepository financialYears;

    public GstReportService(
            CompanyAccessService access, InvoiceRepository invoices, GstAlertRepository alerts,
            GstReturnRepository returns, GstLiabilityRepository liabilities,
            FinancialYearRepository financialYears) {
        this.access = access;
        this.invoices = invoices;
        this.alerts = alerts;
        this.returns = returns;
        this.liabilities = liabilities;
        this.financialYears = financialYears;
    }

    @Transactional(readOnly = true)
    public GstSummaryResponse summary(UUID userId, UUID companyId, LocalDate from, LocalDate to) {
        access.requireMembership(companyId, userId);
        validatePeriod(from, to);
        return calculate(companyId, from, to);
    }

    @Transactional(readOnly = true)
    public GstRegisterResponse register(
            UUID userId, UUID companyId, InvoiceType type, LocalDate from, LocalDate to) {
        access.requireMembership(companyId, userId);
        validatePeriod(from, to);
        List<GstRegisterRow> rows = postedInvoices(companyId, from, to).stream()
                .filter(invoice -> invoice.getInvoiceType() == type).map(this::registerRow).toList();
        return new GstRegisterResponse(type, from, to, rows,
                sum(rows.stream().map(GstRegisterRow::taxableAmount).toList()),
                sum(rows.stream().map(GstRegisterRow::cgst).toList()),
                sum(rows.stream().map(GstRegisterRow::sgst).toList()),
                sum(rows.stream().map(GstRegisterRow::igst).toList()),
                sum(rows.stream().map(GstRegisterRow::cess).toList()),
                sum(rows.stream().map(GstRegisterRow::total).toList()));
    }

    @Transactional(readOnly = true)
    public List<GstPeriodSummary> monthly(UUID userId, UUID companyId, int year) {
        access.requireMembership(companyId, userId);
        List<GstPeriodSummary> result = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            YearMonth value = YearMonth.of(year, month);
            result.add(period(value.toString(), companyId, value.atDay(1), value.atEndOfMonth()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<GstPeriodSummary> quarterly(UUID userId, UUID companyId, int year) {
        access.requireMembership(companyId, userId);
        List<GstPeriodSummary> result = new ArrayList<>();
        for (int quarter = 1; quarter <= 4; quarter++) {
            int startMonth = (quarter - 1) * 3 + 1;
            LocalDate from = LocalDate.of(year, startMonth, 1);
            LocalDate to = YearMonth.of(year, startMonth + 2).atEndOfMonth();
            result.add(period(year + "-Q" + quarter, companyId, from, to));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public GstPeriodSummary yearly(UUID userId, UUID companyId, int year) {
        access.requireMembership(companyId, userId);
        return period(String.valueOf(year), companyId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    @Transactional(readOnly = true)
    public GstDashboardResponse dashboard(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        FinancialYear year = financialYears.findFirstByCompanyIdAndActiveTrueOrderByStartsOnDesc(companyId)
                .orElseThrow(() -> new AccountingRuleException("No active financial year exists for this company."));
        GstSummaryResponse summary = calculate(companyId, year.getStartsOn(), year.getEndsOn());
        List<GstAlertResponse> openAlerts = alerts.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(alert -> !alert.isResolved()).map(this::alertResponse).toList();
        Set<UUID> included = new HashSet<>();
        returns.findAllByCompanyIdOrderByPeriodEndDescCreatedAtDesc(companyId)
                .forEach(gstReturn -> gstReturn.getItems().stream()
                        .filter(item -> item.getInvoice() != null)
                        .forEach(item -> included.add(item.getInvoice().getId())));
        long pending = postedInvoices(companyId, year.getStartsOn(), year.getEndsOn()).stream()
                .filter(invoice -> !included.contains(invoice.getId())).count();
        int penalty = openAlerts.stream().mapToInt(alert -> switch (alert.severity()) {
            case CRITICAL -> 20;
            case HIGH -> 10;
            case MEDIUM -> 5;
            case LOW -> 2;
        }).sum();
        return new GstDashboardResponse(summary.outputGst(), summary.inputGst(), summary.netLiability(),
                pending, openAlerts.size(), Math.max(0, 100 - penalty), openAlerts.stream().limit(20).toList());
    }

    @Transactional
    public void refreshLiability(Invoice invoice) {
        YearMonth month = YearMonth.from(invoice.getInvoiceDate());
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        GstSummaryResponse summary = calculate(invoice.getCompany().getId(), from, to);
        GstLiability liability = liabilities.findByCompanyIdAndPeriodStartAndPeriodEnd(
                        invoice.getCompany().getId(), from, to)
                .orElseGet(GstLiability::new);
        liability.setCompany(invoice.getCompany());
        liability.setFinancialYear(invoice.getFinancialYear());
        liability.setPeriodStart(from);
        liability.setPeriodEnd(to);
        liability.setOutputTax(summary.outputGst());
        liability.setInputCredit(summary.inputGst());
        liability.setReverseChargeTax(summary.reverseChargeLiability());
        liability.setCessLiability(money(summary.outputCess().subtract(summary.inputCess()).max(BigDecimal.ZERO)));
        liability.setNetLiability(summary.netLiability());
        liabilities.save(liability);
    }

    private GstSummaryResponse calculate(UUID companyId, LocalDate from, LocalDate to) {
        List<Invoice> source = postedInvoices(companyId, from, to);
        BigDecimal output = ZERO;
        BigDecimal input = ZERO;
        BigDecimal reverseCharge = ZERO;
        BigDecimal outputCess = ZERO;
        BigDecimal inputCess = ZERO;
        long sales = 0;
        long purchases = 0;
        for (Invoice invoice : source) {
            BigDecimal gst = gst(invoice);
            if (invoice.getInvoiceType() == InvoiceType.SALES) {
                sales++;
                if (invoice.getGstTreatment() != GstTreatment.REVERSE_CHARGE) {
                    output = output.add(gst);
                    outputCess = outputCess.add(invoice.getCessTotal());
                }
            } else {
                purchases++;
                input = input.add(gst);
                inputCess = inputCess.add(invoice.getCessTotal());
                if (invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE) {
                    reverseCharge = reverseCharge.add(gst);
                    outputCess = outputCess.add(invoice.getCessTotal());
                }
            }
        }
        BigDecimal net = money(output.add(reverseCharge).add(outputCess).subtract(input).subtract(inputCess)
                .max(BigDecimal.ZERO));
        return new GstSummaryResponse(from, to, money(output), money(input), money(reverseCharge),
                money(outputCess), money(inputCess), net, sales, purchases);
    }

    private List<Invoice> postedInvoices(UUID companyId, LocalDate from, LocalDate to) {
        return invoices.findAllByCompanyIdAndStatusInAndInvoiceDateBetweenOrderByInvoiceDateDescInvoiceNumberDesc(
                companyId, List.of(InvoiceStatus.POSTED, InvoiceStatus.PAID), from, to);
    }

    private GstRegisterRow registerRow(Invoice invoice) {
        String partyName = invoice.getInvoiceType() == InvoiceType.SALES
                ? invoice.getCustomer().getDisplayName() : invoice.getVendor().getDisplayName();
        String gstin = invoice.getInvoiceType() == InvoiceType.SALES
                ? invoice.getCustomer().getGstin() : invoice.getVendor().getGstin();
        return new GstRegisterRow(invoice.getId(), invoice.getInvoiceType(), invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(), partyName, gstin, invoice.getPlaceOfSupply(), invoice.getGstTreatment(),
                money(invoice.getSubtotal()), money(invoice.getCgstTotal()), money(invoice.getSgstTotal()),
                money(invoice.getIgstTotal()), money(invoice.getCessTotal()), money(invoice.getTotal()),
                invoice.getStatus());
    }

    private GstPeriodSummary period(String label, UUID companyId, LocalDate from, LocalDate to) {
        GstSummaryResponse summary = calculate(companyId, from, to);
        return new GstPeriodSummary(label, from, to, summary.outputGst(), summary.inputGst(), summary.netLiability());
    }
    private GstAlertResponse alertResponse(GstAlert alert) {
        return new GstAlertResponse(alert.getId(), alert.getInvoice() == null ? null : alert.getInvoice().getId(),
                alert.getAlertType(), alert.getSeverity(), alert.getMessage(), alert.getReason(),
                alert.getConfidence(), alert.isResolved(), alert.getCreatedAt());
    }
    private BigDecimal gst(Invoice invoice) {
        return money(invoice.getCgstTotal().add(invoice.getSgstTotal()).add(invoice.getIgstTotal()));
    }
    private BigDecimal sum(List<BigDecimal> values) {
        return money(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("A valid date_from and date_to period is required.");
        }
    }
}
