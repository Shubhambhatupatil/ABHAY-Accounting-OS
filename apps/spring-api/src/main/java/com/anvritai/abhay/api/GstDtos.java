package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.gst.GstAlertSeverity;
import com.anvritai.abhay.domain.gst.GstCodeType;
import com.anvritai.abhay.domain.gst.GstReturnStatus;
import com.anvritai.abhay.domain.gst.GstReturnType;
import com.anvritai.abhay.domain.gst.GstTreatment;
import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GstDtos {
    private GstDtos() {
    }

    public record GstRateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal rate,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal cessRate,
            Boolean reverseChargeAllowed,
            Boolean active) {
    }

    public record GstRateResponse(
            UUID id, String name, BigDecimal rate, BigDecimal cessRate, boolean systemRate,
            boolean reverseChargeAllowed, boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record HsnSacRequest(
            @NotBlank @Pattern(regexp = "^[0-9]{4,8}$") String code,
            @NotNull GstCodeType codeType,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstRate,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal cessRate,
            Boolean active) {
    }

    public record HsnSacResponse(
            UUID id, String code, GstCodeType codeType, String description, BigDecimal gstRate,
            BigDecimal cessRate, boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record GstRuleRequest(
            @NotBlank @Size(max = 160) String name,
            @NotNull GstTreatment gstTreatment,
            @Pattern(regexp = "(^$)|(^[0-9]{2,8}$)") String hsnSacPrefix,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstRate,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal cessRate,
            Boolean reverseCharge,
            Boolean active) {
    }

    public record GstRuleResponse(
            UUID id, String name, GstTreatment gstTreatment, String hsnSacPrefix,
            BigDecimal gstRate, BigDecimal cessRate, boolean reverseCharge,
            boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record GstinValidationRequest(
            @NotBlank @Size(max = 15) String gstin,
            @Pattern(regexp = "(^$)|(^[0-9]{2}$)") String expectedStateCode) {
    }

    public record GstinValidationResponse(
            String gstin, boolean valid, String stateCode, List<String> errors) {
    }

    public record GstValidationIssue(
            String code, GstAlertSeverity severity, String message, String reason, BigDecimal confidence) {
    }

    public record InvoiceGstValidationResponse(
            UUID invoiceId, boolean valid, int riskScore, List<GstValidationIssue> issues) {
    }

    public record GstRegisterRow(
            UUID invoiceId, InvoiceType invoiceType, String invoiceNumber, LocalDate invoiceDate,
            String partyName, String gstin, String placeOfSupply, GstTreatment gstTreatment,
            BigDecimal taxableAmount, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
            BigDecimal cess, BigDecimal total, InvoiceStatus status) {
    }

    public record GstRegisterResponse(
            InvoiceType invoiceType, LocalDate dateFrom, LocalDate dateTo, List<GstRegisterRow> invoices,
            BigDecimal taxableAmount, BigDecimal cgst, BigDecimal sgst, BigDecimal igst,
            BigDecimal cess, BigDecimal total) {
    }

    public record GstSummaryResponse(
            LocalDate dateFrom, LocalDate dateTo, BigDecimal outputGst, BigDecimal inputGst,
            BigDecimal reverseChargeLiability, BigDecimal outputCess, BigDecimal inputCess,
            BigDecimal netLiability, long salesInvoices, long purchaseInvoices) {
    }

    public record GstPeriodSummary(
            String period, LocalDate dateFrom, LocalDate dateTo, BigDecimal outputGst,
            BigDecimal inputGst, BigDecimal netLiability) {
    }

    public record GstAlertResponse(
            UUID id, UUID invoiceId, String alertType, GstAlertSeverity severity, String message,
            String reason, BigDecimal confidence, boolean resolved, Instant createdAt) {
    }

    public record GstDashboardResponse(
            BigDecimal outputGst, BigDecimal inputGst, BigDecimal netLiability,
            long pendingFiling, long mismatchAlerts, int complianceScore,
            List<GstAlertResponse> alerts) {
    }

    public record DraftReturnRequest(
            @NotNull GstReturnType returnType,
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd) {
    }

    public record GstReturnItemResponse(
            UUID invoiceId, String invoiceNumber, String sectionCode, BigDecimal taxableAmount,
            BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal cess, BigDecimal total) {
    }

    public record GstReturnResponse(
            UUID id, GstReturnType returnType, LocalDate periodStart, LocalDate periodEnd,
            GstReturnStatus status, Map<String, Object> jsonStructure, List<GstReturnItemResponse> items,
            Instant createdAt) {
    }
}
