package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.sales.InvoiceStatus;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.domain.sales.ItemType;
import com.anvritai.abhay.domain.gst.GstTreatment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalesPurchaseDtos {
    private SalesPurchaseDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String displayName,
            @Pattern(regexp = "(^$)|(^[0-9A-Z]{15}$)") String gstin,
            @Pattern(regexp = "(^$)|(^[A-Z]{5}[0-9]{4}[A-Z]$)") String pan,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 1000) String billingAddress,
            @Size(max = 1000) String shippingAddress,
            @Size(max = 100) String state,
            @Size(max = 100) String country,
            @DecimalMin("0.00") BigDecimal creditLimit,
            @Min(0) Integer paymentTermsDays,
            @DecimalMin("0.00") BigDecimal openingBalance,
            Boolean active) {
    }

    public record CustomerResponse(
            UUID id, UUID ledgerId, String name, String displayName, String gstin, String pan,
            String email, String phone, String billingAddress, String shippingAddress, String state,
            String country, BigDecimal creditLimit, int paymentTermsDays, BigDecimal openingBalance,
            boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record VendorRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String displayName,
            @Pattern(regexp = "(^$)|(^[0-9A-Z]{15}$)") String gstin,
            @Pattern(regexp = "(^$)|(^[A-Z]{5}[0-9]{4}[A-Z]$)") String pan,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String phone,
            @Size(max = 1000) String address,
            @Size(max = 100) String state,
            @Size(max = 100) String country,
            @Min(0) Integer paymentTermsDays,
            @DecimalMin("0.00") BigDecimal openingBalance,
            Boolean active) {
    }

    public record VendorResponse(
            UUID id, UUID ledgerId, String name, String displayName, String gstin, String pan,
            String email, String phone, String address, String state, String country,
            int paymentTermsDays, BigDecimal openingBalance, boolean active,
            Instant createdAt, Instant updatedAt) {
    }

    public record ItemRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull ItemType type,
            @Size(max = 80) String sku,
            @Size(max = 20) String hsnSac,
            @NotBlank @Size(max = 30) String unit,
            @DecimalMin("0.00") BigDecimal salesPrice,
            @DecimalMin("0.00") BigDecimal purchasePrice,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstRate,
            UUID inventoryUnitId,
            UUID itemCategoryId,
            @DecimalMin("0.0000") BigDecimal reorderLevel,
            Boolean active) {
    }

    public record ItemResponse(
            UUID id, String name, ItemType type, String sku, String hsnSac, String unit,
            BigDecimal salesPrice, BigDecimal purchasePrice, BigDecimal gstRate,
            UUID inventoryUnitId, UUID itemCategoryId, BigDecimal reorderLevel, boolean trackInventory,
            boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record InvoiceLineRequest(
            UUID itemId,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal gstRate,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal cessRate) {
    }

    public record InvoiceCreateRequest(
            @NotNull InvoiceType invoiceType,
            @NotBlank @Size(max = 80) String invoiceNumber,
            @NotNull LocalDate invoiceDate,
            @NotNull LocalDate dueDate,
            UUID customerId,
            UUID vendorId,
            @Pattern(regexp = "(^$)|(^[0-9]{2}$)") String placeOfSupply,
            GstTreatment gstTreatment,
            @Size(max = 2000) String notes,
            @NotEmpty @Size(max = 200) List<@Valid InvoiceLineRequest> items) {
    }

    public record InvoiceUpdateRequest(
            @NotNull LocalDate invoiceDate,
            @NotNull LocalDate dueDate,
            @Pattern(regexp = "(^$)|(^[0-9]{2}$)") String placeOfSupply,
            GstTreatment gstTreatment,
            @Size(max = 2000) String notes,
            @NotEmpty @Size(max = 200) List<@Valid InvoiceLineRequest> items) {
    }

    public record InvoiceLineResponse(
            UUID id, UUID itemId, int lineNumber, String description, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal gstRate, BigDecimal taxableAmount,
            BigDecimal cgstAmount, BigDecimal sgstAmount, BigDecimal igstAmount,
            BigDecimal cessRate, BigDecimal cessAmount, BigDecimal lineTotal) {
    }

    public record InvoiceResponse(
            UUID id, UUID financialYearId, InvoiceType invoiceType, String invoiceNumber,
            LocalDate invoiceDate, LocalDate dueDate, UUID customerId, UUID vendorId,
            String partyName, InvoiceStatus status, BigDecimal subtotal, BigDecimal cgstTotal,
            BigDecimal sgstTotal, BigDecimal igstTotal, BigDecimal cessTotal,
            GstTreatment gstTreatment, String placeOfSupply, BigDecimal total, BigDecimal paidAmount,
            BigDecimal balanceAmount, String notes, UUID postedVoucherId,
            List<InvoiceLineResponse> items, Instant createdAt, Instant updatedAt) {
    }

    public record InvoicePaymentRequest(
            @NotNull LocalDate paymentDate,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank @Size(max = 30) String mode,
            @Size(max = 120) String reference) {
    }

    public record InvoicePaymentResponse(
            UUID id, LocalDate paymentDate, BigDecimal amount, String mode, String reference,
            UUID linkedVoucherId, Instant createdAt) {
    }

    public record InvoiceRegisterRow(
            UUID invoiceId, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate,
            String partyName, String gstin, BigDecimal taxableAmount, BigDecimal gstAmount,
            BigDecimal total, InvoiceStatus status) {
    }

    public record InvoiceRegisterResponse(
            InvoiceType invoiceType, List<InvoiceRegisterRow> invoices,
            BigDecimal totalTaxable, BigDecimal totalGst, BigDecimal grandTotal) {
    }

    public record OutstandingInvoiceRow(
            UUID invoiceId, InvoiceType invoiceType, String party, String invoiceNumber,
            LocalDate invoiceDate, LocalDate dueDate, BigDecimal total, BigDecimal paid,
            BigDecimal balance, long overdueDays) {
    }

    public record OutstandingInvoicesResponse(
            List<OutstandingInvoiceRow> invoices, BigDecimal totalOutstanding) {
    }
}
