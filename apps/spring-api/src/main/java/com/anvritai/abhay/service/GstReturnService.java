package com.anvritai.abhay.service;

import com.anvritai.abhay.api.GstDtos.DraftReturnRequest;
import com.anvritai.abhay.api.GstDtos.GstReturnItemResponse;
import com.anvritai.abhay.api.GstDtos.GstReturnResponse;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.gst.GstReturn;
import com.anvritai.abhay.domain.gst.GstReturnItem;
import com.anvritai.abhay.domain.gst.GstReturnStatus;
import com.anvritai.abhay.domain.gst.GstReturnType;
import com.anvritai.abhay.domain.gst.GstTreatment;
import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.repository.FinancialYearRepository;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.gst.GstReturnRepository;
import com.anvritai.abhay.repository.sales.InvoiceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GstReturnService {
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};
    private final CompanyAccessService access;
    private final FinancialYearRepository financialYears;
    private final UserRepository users;
    private final InvoiceRepository invoices;
    private final GstReturnRepository returns;
    private final AuditService audit;
    private final GstMemoryService memory;
    private final ObjectMapper objectMapper;

    public GstReturnService(
            CompanyAccessService access, FinancialYearRepository financialYears, UserRepository users,
            InvoiceRepository invoices, GstReturnRepository returns, AuditService audit,
            GstMemoryService memory, ObjectMapper objectMapper) {
        this.access = access;
        this.financialYears = financialYears;
        this.users = users;
        this.invoices = invoices;
        this.returns = returns;
        this.audit = audit;
        this.memory = memory;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GstReturnResponse generate(UUID userId, UUID companyId, DraftReturnRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new IllegalArgumentException("GST return period end cannot precede its start.");
        }
        FinancialYear year = financialYears.findFirstByCompanyIdAndActiveTrueOrderByStartsOnDesc(companyId)
                .orElseThrow(() -> new AccountingRuleException("No active financial year exists for this company."));
        if (request.periodStart().isBefore(year.getStartsOn()) || request.periodEnd().isAfter(year.getEndsOn())) {
            throw new AccountingRuleException("GST return period must fall inside the active financial year.");
        }
        User actor = users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
        List<Invoice> source = invoices
                .findAllByCompanyIdAndStatusInAndInvoiceDateBetweenOrderByInvoiceDateDescInvoiceNumberDesc(
                        companyId, List.of(InvoiceStatus.POSTED, InvoiceStatus.PAID),
                        request.periodStart(), request.periodEnd()).stream()
                .filter(invoice -> request.returnType() == GstReturnType.GSTR3B
                        || invoice.getInvoiceType() == InvoiceType.SALES)
                .toList();
        GstReturn gstReturn = new GstReturn();
        gstReturn.setCompany(year.getCompany());
        gstReturn.setFinancialYear(year);
        gstReturn.setReturnType(request.returnType());
        gstReturn.setPeriodStart(request.periodStart());
        gstReturn.setPeriodEnd(request.periodEnd());
        gstReturn.setStatus(GstReturnStatus.DRAFT);
        gstReturn.setGeneratedBy(actor);
        for (Invoice invoice : source) gstReturn.addItem(returnItem(invoice, request.returnType()));
        Map<String, Object> snapshot = snapshot(gstReturn, source);
        try {
            gstReturn.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("GST return JSON could not be generated.", exception);
        }
        gstReturn = returns.save(gstReturn);
        audit.record(gstReturn.getCompany(), actor, "GST_RETURN_DRAFT_GENERATED", "GST_RETURN", gstReturn.getId(),
                Map.of("returnType", gstReturn.getReturnType().name(), "invoiceCount", source.size()));
        memory.record(gstReturn.getCompany(), "GST_RETURN_DRAFT_GENERATED", "GST_RETURN", gstReturn.getId(),
                "Draft return generated from posted invoices; CA review is required before filing.",
                new BigDecimal("0.9800"), Map.of("returnType", gstReturn.getReturnType().name(),
                        "invoiceCount", source.size()));
        return response(gstReturn);
    }

    @Transactional(readOnly = true)
    public List<GstReturnResponse> list(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return returns.findAllByCompanyIdOrderByPeriodEndDescCreatedAtDesc(companyId).stream()
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public GstReturnResponse get(UUID userId, UUID companyId, UUID returnId) {
        access.requireMembership(companyId, userId);
        return response(requireReturn(companyId, returnId));
    }

    @Transactional
    public GstReturnResponse setStatus(
            UUID userId, UUID companyId, UUID returnId, GstReturnStatus status) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (status == GstReturnStatus.DRAFT) throw new IllegalArgumentException("Use draft generation to create a draft.");
        GstReturn gstReturn = requireReturn(companyId, returnId);
        if (gstReturn.getStatus() != GstReturnStatus.DRAFT) {
            throw new AccountingRuleException("Only draft GST returns can change status.");
        }
        gstReturn.setStatus(status);
        returns.save(gstReturn);
        audit.record(gstReturn.getCompany(), user(userId), "GST_RETURN_" + status.name(), "GST_RETURN",
                gstReturn.getId(), Map.of("returnType", gstReturn.getReturnType().name()));
        return response(gstReturn);
    }

    @Transactional(readOnly = true)
    public String csv(UUID userId, UUID companyId, UUID returnId) {
        access.requireMembership(companyId, userId);
        GstReturn gstReturn = requireReturn(companyId, returnId);
        StringBuilder csv = new StringBuilder(
                "section,invoice_number,invoice_date,party_gstin,place_of_supply,taxable,cgst,sgst,igst,cess,total\n");
        for (GstReturnItem item : gstReturn.getItems()) {
            Invoice invoice = item.getInvoice();
            String gstin = invoice == null ? "" : partyGstin(invoice);
            csv.append(escape(item.getSectionCode())).append(',')
                    .append(escape(invoice == null ? "" : invoice.getInvoiceNumber())).append(',')
                    .append(invoice == null ? "" : invoice.getInvoiceDate()).append(',')
                    .append(escape(gstin)).append(',')
                    .append(escape(invoice == null ? "" : invoice.getPlaceOfSupply())).append(',')
                    .append(item.getTaxableAmount()).append(',').append(item.getCgstAmount()).append(',')
                    .append(item.getSgstAmount()).append(',').append(item.getIgstAmount()).append(',')
                    .append(item.getCessAmount()).append(',').append(item.getTotalAmount()).append('\n');
        }
        return csv.toString();
    }

    private GstReturnItem returnItem(Invoice invoice, GstReturnType returnType) {
        GstReturnItem item = new GstReturnItem();
        item.setInvoice(invoice);
        item.setSectionCode(section(invoice, returnType));
        item.setTaxableAmount(money(invoice.getSubtotal()));
        item.setCgstAmount(money(invoice.getCgstTotal()));
        item.setSgstAmount(money(invoice.getSgstTotal()));
        item.setIgstAmount(money(invoice.getIgstTotal()));
        item.setCessAmount(money(invoice.getCessTotal()));
        item.setTotalAmount(money(invoice.getTotal()));
        return item;
    }

    private String section(Invoice invoice, GstReturnType type) {
        if (type == GstReturnType.GSTR1) {
            if (invoice.getGstTreatment() == GstTreatment.EXPORT) return "EXP";
            if (invoice.getGstTreatment() == GstTreatment.SEZ) return "SEZ";
            return partyGstin(invoice) == null ? "B2C" : "B2B";
        }
        if (invoice.getInvoiceType() == InvoiceType.SALES) return "3.1_OUTWARD";
        return invoice.getGstTreatment() == GstTreatment.REVERSE_CHARGE ? "3.1_RCM" : "4_ITC";
    }

    private Map<String, Object> snapshot(GstReturn gstReturn, List<Invoice> source) {
        List<Map<String, Object>> invoiceRows = new ArrayList<>();
        for (Invoice invoice : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("invoice_id", invoice.getId());
            row.put("invoice_number", invoice.getInvoiceNumber());
            row.put("invoice_date", invoice.getInvoiceDate());
            row.put("invoice_type", invoice.getInvoiceType().name());
            row.put("gst_treatment", invoice.getGstTreatment().name());
            row.put("place_of_supply", invoice.getPlaceOfSupply());
            row.put("taxable_amount", invoice.getSubtotal());
            row.put("cgst", invoice.getCgstTotal());
            row.put("sgst", invoice.getSgstTotal());
            row.put("igst", invoice.getIgstTotal());
            row.put("cess", invoice.getCessTotal());
            row.put("total", invoice.getTotal());
            invoiceRows.add(row);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("return_type", gstReturn.getReturnType().name());
        root.put("period_start", gstReturn.getPeriodStart());
        root.put("period_end", gstReturn.getPeriodEnd());
        root.put("draft", true);
        root.put("requires_ca_review", true);
        root.put("invoices", invoiceRows);
        return root;
    }

    private GstReturnResponse response(GstReturn gstReturn) {
        Map<String, Object> structure;
        try {
            structure = objectMapper.readValue(gstReturn.getSnapshotJson(), new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored GST return JSON is invalid.", exception);
        }
        List<GstReturnItemResponse> items = gstReturn.getItems().stream().map(item -> new GstReturnItemResponse(
                item.getInvoice() == null ? null : item.getInvoice().getId(),
                item.getInvoice() == null ? null : item.getInvoice().getInvoiceNumber(), item.getSectionCode(),
                item.getTaxableAmount(), item.getCgstAmount(), item.getSgstAmount(), item.getIgstAmount(),
                item.getCessAmount(), item.getTotalAmount())).toList();
        return new GstReturnResponse(gstReturn.getId(), gstReturn.getReturnType(), gstReturn.getPeriodStart(),
                gstReturn.getPeriodEnd(), gstReturn.getStatus(), structure, items, gstReturn.getCreatedAt());
    }

    private GstReturn requireReturn(UUID companyId, UUID returnId) {
        return returns.findByIdAndCompanyId(returnId, companyId)
                .orElseThrow(() -> new NotFoundException("GST return not found."));
    }
    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
    }
    private String partyGstin(Invoice invoice) {
        return invoice.getInvoiceType() == InvoiceType.SALES
                ? invoice.getCustomer().getGstin() : invoice.getVendor().getGstin();
    }
    private String escape(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"") + '"';
    }
    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
