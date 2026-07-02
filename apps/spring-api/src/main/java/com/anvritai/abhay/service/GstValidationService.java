package com.anvritai.abhay.service;

import com.anvritai.abhay.api.GstDtos.GstAlertResponse;
import com.anvritai.abhay.api.GstDtos.GstValidationIssue;
import com.anvritai.abhay.api.GstDtos.GstinValidationResponse;
import com.anvritai.abhay.api.GstDtos.InvoiceGstValidationResponse;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.gst.GstAlert;
import com.anvritai.abhay.domain.gst.GstAlertSeverity;
import com.anvritai.abhay.domain.gst.GstHsnSac;
import com.anvritai.abhay.domain.gst.GstRate;
import com.anvritai.abhay.domain.gst.GstRule;
import com.anvritai.abhay.domain.gst.GstTreatment;
import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoiceItem;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.gst.GstAlertRepository;
import com.anvritai.abhay.repository.gst.GstHsnSacRepository;
import com.anvritai.abhay.repository.gst.GstRateRepository;
import com.anvritai.abhay.repository.gst.GstRuleRepository;
import com.anvritai.abhay.repository.sales.InvoiceRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GstValidationService {
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};
    private static final Pattern GSTIN = Pattern.compile(
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");
    private static final Set<String> STATE_CODES = Set.of(
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13",
            "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26",
            "27", "28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38");
    private final CompanyAccessService access;
    private final UserRepository users;
    private final InvoiceRepository invoices;
    private final GstRateRepository rates;
    private final GstHsnSacRepository hsnSac;
    private final GstRuleRepository rules;
    private final GstAlertRepository alerts;
    private final AuditService audit;
    private final GstMemoryService memory;

    public GstValidationService(
            CompanyAccessService access, UserRepository users, InvoiceRepository invoices,
            GstRateRepository rates, GstHsnSacRepository hsnSac, GstRuleRepository rules,
            GstAlertRepository alerts, AuditService audit, GstMemoryService memory) {
        this.access = access;
        this.users = users;
        this.invoices = invoices;
        this.rates = rates;
        this.hsnSac = hsnSac;
        this.rules = rules;
        this.alerts = alerts;
        this.audit = audit;
        this.memory = memory;
    }

    public GstinValidationResponse validateGstin(String gstin, String expectedStateCode) {
        String normalized = gstin == null ? "" : gstin.trim().toUpperCase(java.util.Locale.ROOT);
        List<String> errors = new ArrayList<>();
        if (!GSTIN.matcher(normalized).matches()) errors.add("GSTIN format is invalid.");
        String state = normalized.length() >= 2 ? normalized.substring(0, 2) : null;
        if (state == null || !STATE_CODES.contains(state)) errors.add("GSTIN state code is invalid.");
        if (expectedStateCode != null && !expectedStateCode.isBlank() && state != null
                && !expectedStateCode.equals(state)) {
            errors.add("GSTIN state code does not match the expected state.");
        }
        return new GstinValidationResponse(normalized, errors.isEmpty(), state, errors);
    }

    @Transactional(readOnly = true)
    public InvoiceGstValidationResponse validateInvoice(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireMembership(companyId, userId);
        return response(requireInvoice(companyId, invoiceId), issues(requireInvoice(companyId, invoiceId)));
    }

    @Transactional
    public InvoiceGstValidationResponse scanInvoice(UUID userId, UUID companyId, UUID invoiceId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Invoice invoice = requireInvoice(companyId, invoiceId);
        List<GstValidationIssue> found = issues(invoice);
        alerts.deleteAllByCompanyIdAndInvoiceIdAndResolvedFalse(companyId, invoiceId);
        for (GstValidationIssue issue : found) {
            GstAlert alert = new GstAlert();
            alert.setCompany(invoice.getCompany());
            alert.setInvoice(invoice);
            alert.setAlertType(issue.code());
            alert.setSeverity(issue.severity());
            alert.setMessage(issue.message());
            alert.setReason(issue.reason());
            alert.setConfidence(issue.confidence());
            alert.setResolved(false);
            alerts.save(alert);
        }
        User actor = user(userId);
        audit.record(invoice.getCompany(), actor, "GST_INVOICE_VALIDATED", "INVOICE", invoiceId,
                Map.of("issueCount", found.size(), "riskScore", riskScore(found)));
        memory.record(invoice.getCompany(), "GST_INVOICE_VALIDATED", "INVOICE", invoiceId,
                "GST rules, tax splits, party registration and HSN/SAC were validated.",
                new BigDecimal("0.9500"), Map.of("issueCount", found.size(), "riskScore", riskScore(found)));
        return response(invoice, found);
    }

    @Transactional
    public void assessOnPosting(Invoice invoice) {
        List<GstValidationIssue> found = issues(invoice);
        alerts.deleteAllByCompanyIdAndInvoiceIdAndResolvedFalse(invoice.getCompany().getId(), invoice.getId());
        for (GstValidationIssue issue : found) {
            GstAlert alert = new GstAlert();
            alert.setCompany(invoice.getCompany());
            alert.setInvoice(invoice);
            alert.setAlertType(issue.code());
            alert.setSeverity(issue.severity());
            alert.setMessage(issue.message());
            alert.setReason(issue.reason());
            alert.setConfidence(issue.confidence());
            alert.setResolved(false);
            alerts.save(alert);
        }
    }

    @Transactional(readOnly = true)
    public List<GstAlertResponse> listAlerts(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return alerts.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::alertResponse).toList();
    }

    @Transactional
    public GstAlertResponse resolveAlert(UUID userId, UUID companyId, UUID alertId) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        GstAlert alert = alerts.findByIdAndCompanyId(alertId, companyId)
                .orElseThrow(() -> new NotFoundException("GST alert not found."));
        alert.setResolved(true);
        alerts.save(alert);
        audit.record(alert.getCompany(), user(userId), "GST_ALERT_RESOLVED", "GST_ALERT", alert.getId(),
                Map.of("alertType", alert.getAlertType()));
        return alertResponse(alert);
    }

    private List<GstValidationIssue> issues(Invoice invoice) {
        List<GstValidationIssue> found = new ArrayList<>();
        String gstin = partyGstin(invoice);
        boolean gstRequired = tax(invoice).signum() > 0
                && invoice.getGstTreatment() != GstTreatment.EXPORT;
        if (gstRequired && (gstin == null || gstin.isBlank())) {
            found.add(issue("MISSING_GSTIN", GstAlertSeverity.HIGH, "Party GSTIN is missing.",
                    "A taxable domestic invoice normally requires a valid party GSTIN.", "0.9800"));
        } else if (gstin != null && !gstin.isBlank()) {
            GstinValidationResponse result = validateGstin(gstin, null);
            if (!result.valid()) found.add(issue("INVALID_GSTIN", GstAlertSeverity.HIGH,
                    "Party GSTIN is invalid.", String.join(" ", result.errors()), "0.9900"));
        }
        if (invoice.getPlaceOfSupply() == null || !STATE_CODES.contains(invoice.getPlaceOfSupply())) {
            found.add(issue("INVALID_PLACE_OF_SUPPLY", GstAlertSeverity.HIGH,
                    "Place of supply is invalid.", "Use a valid two-digit Indian GST state code.", "0.9900"));
        }
        validateTaxSplit(invoice, found);
        validateLines(invoice, found);
        validateDuplicate(invoice, found);
        validateReverseCharge(invoice, found);
        return found;
    }

    private void validateTaxSplit(Invoice invoice, List<GstValidationIssue> found) {
        boolean intra = invoice.getCompany().getStateCode() == null
                || invoice.getCompany().getStateCode().equals(invoice.getPlaceOfSupply());
        if (intra && (invoice.getIgstTotal().signum() > 0
                || invoice.getCgstTotal().compareTo(invoice.getSgstTotal()) != 0)) {
            found.add(issue("WRONG_GST_SPLIT", GstAlertSeverity.CRITICAL,
                    "CGST/SGST and IGST split is inconsistent.",
                    "Intra-state supplies require equal CGST and SGST with no IGST.", "0.9950"));
        }
        if (!intra && (invoice.getCgstTotal().signum() > 0 || invoice.getSgstTotal().signum() > 0)) {
            found.add(issue("WRONG_GST_SPLIT", GstAlertSeverity.CRITICAL,
                    "CGST/SGST and IGST split is inconsistent.",
                    "Inter-state supplies require IGST with no CGST or SGST.", "0.9950"));
        }
    }

    private void validateLines(Invoice invoice, List<GstValidationIssue> found) {
        List<BigDecimal> activeRates = rates.findAllByCompanyIdOrderByRateAscCessRateAsc(invoice.getCompany().getId())
                .stream().filter(GstRate::isActive).map(GstRate::getRate).toList();
        for (InvoiceItem line : invoice.getItems()) {
            if (activeRates.stream().noneMatch(rate -> rate.compareTo(line.getGstRate()) == 0)) {
                found.add(issue("WRONG_GST_RATE", GstAlertSeverity.HIGH,
                        "GST rate is not enabled in the company GST master.",
                        "Line " + line.getLineNumber() + " uses " + line.getGstRate() + "% GST.", "0.9700"));
            }
            if (line.getItem() == null || line.getItem().getHsnSac() == null || line.getItem().getHsnSac().isBlank()) {
                found.add(issue("MISSING_HSN_SAC", GstAlertSeverity.MEDIUM,
                        "HSN/SAC is missing.", "Line " + line.getLineNumber() + " has no classified item code.", "0.9400"));
                continue;
            }
            GstHsnSac master = hsnSac.findByCompanyIdAndCodeIgnoreCase(
                    invoice.getCompany().getId(), line.getItem().getHsnSac()).orElse(null);
            if (master == null || !master.isActive()) {
                found.add(issue("WRONG_HSN_SAC", GstAlertSeverity.MEDIUM,
                        "HSN/SAC is not present in the active master.",
                        "Code " + line.getItem().getHsnSac() + " needs review.", "0.9300"));
            } else if (master.getGstRate().compareTo(line.getGstRate()) != 0
                    || master.getCessRate().compareTo(line.getCessRate()) != 0) {
                found.add(issue("HSN_RATE_MISMATCH", GstAlertSeverity.HIGH,
                        "Invoice tax rate differs from the HSN/SAC master.",
                        "Code " + master.getCode() + " expects GST " + master.getGstRate()
                                + "% and CESS " + master.getCessRate() + "%.", "0.9600"));
            }
            BigDecimal expected = money(line.getTaxableAmount().multiply(line.getGstRate())
                    .divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            BigDecimal actual = money(line.getCgstAmount().add(line.getSgstAmount()).add(line.getIgstAmount()));
            if (expected.subtract(actual).abs().compareTo(new BigDecimal("0.02")) > 0) {
                found.add(issue("HIGH_TAX_DIFFERENCE", GstAlertSeverity.CRITICAL,
                        "Calculated GST differs from stored GST.",
                        "Line " + line.getLineNumber() + " expected " + expected + " but stores " + actual + ".",
                        "0.9990"));
            }
        }
    }

    private void validateDuplicate(Invoice invoice, List<GstValidationIssue> found) {
        long duplicates = invoices.countByCompanyIdAndFinancialYearIdAndInvoiceTypeAndPartyKeyAndInvoiceNumberIgnoreCase(
                invoice.getCompany().getId(), invoice.getFinancialYear().getId(), invoice.getInvoiceType(),
                invoice.getPartyKey(), invoice.getInvoiceNumber());
        if (duplicates > 1) found.add(issue("DUPLICATE_GST_INVOICE", GstAlertSeverity.CRITICAL,
                "A duplicate GST invoice was detected.", "Party, invoice number and financial year repeat.", "1.0000"));
    }

    private void validateReverseCharge(Invoice invoice, List<GstValidationIssue> found) {
        if (invoice.getInvoiceType() != InvoiceType.PURCHASE || invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE) {
            return;
        }
        List<GstRule> activeRules = rules.findAllByCompanyIdOrderByName(invoice.getCompany().getId()).stream()
                .filter(GstRule::isActive).filter(GstRule::isReverseCharge).toList();
        boolean required = invoice.getItems().stream().anyMatch(line -> line.getItem() != null
                && line.getItem().getHsnSac() != null
                && activeRules.stream().anyMatch(rule -> rule.getHsnSacPrefix() != null
                        && line.getItem().getHsnSac().startsWith(rule.getHsnSacPrefix())));
        if (required) found.add(issue("REVERSE_CHARGE_MISSING", GstAlertSeverity.CRITICAL,
                "Reverse charge treatment appears to be missing.",
                "An active GST rule marks this HSN/SAC supply for reverse charge.", "0.9200"));
    }

    private InvoiceGstValidationResponse response(Invoice invoice, List<GstValidationIssue> issues) {
        return new InvoiceGstValidationResponse(invoice.getId(), issues.isEmpty(), riskScore(issues), issues);
    }
    private int riskScore(List<GstValidationIssue> issues) {
        return Math.min(100, issues.stream().mapToInt(issue -> switch (issue.severity()) {
            case CRITICAL -> 30;
            case HIGH -> 18;
            case MEDIUM -> 8;
            case LOW -> 3;
        }).sum());
    }
    private GstValidationIssue issue(
            String code, GstAlertSeverity severity, String message, String reason, String confidence) {
        return new GstValidationIssue(code, severity, message, reason, new BigDecimal(confidence));
    }
    private GstAlertResponse alertResponse(GstAlert alert) {
        return new GstAlertResponse(alert.getId(), alert.getInvoice() == null ? null : alert.getInvoice().getId(),
                alert.getAlertType(), alert.getSeverity(), alert.getMessage(), alert.getReason(),
                alert.getConfidence(), alert.isResolved(), alert.getCreatedAt());
    }
    private String partyGstin(Invoice invoice) {
        return invoice.getInvoiceType() == InvoiceType.SALES
                ? invoice.getCustomer().getGstin() : invoice.getVendor().getGstin();
    }
    private BigDecimal tax(Invoice invoice) {
        return money(invoice.getCgstTotal().add(invoice.getSgstTotal()).add(invoice.getIgstTotal()));
    }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private Invoice requireInvoice(UUID companyId, UUID invoiceId) {
        return invoices.findByIdAndCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new NotFoundException("Invoice not found."));
    }
    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
    }
}
