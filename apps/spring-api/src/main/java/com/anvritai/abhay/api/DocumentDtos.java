package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.document.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class DocumentDtos {
    private DocumentDtos() { }

    public record DocumentResponse(
            UUID id, DocumentType documentType, String originalFileName, String fileType, long fileSize,
            DocumentStatus status, DocumentSource source, BigDecimal confidenceScore,
            String processingStatus, boolean duplicate, UUID linkedInvoiceId, UUID linkedVoucherId,
            Instant createdAt, Instant updatedAt) { }

    public record DocumentFieldResponse(
            UUID id, String fieldName, String rawValue, String normalizedValue,
            BigDecimal confidenceScore, boolean corrected, Instant updatedAt) { }

    public record FieldCorrectionRequest(
            @NotBlank @Size(max = 2000) String normalizedValue,
            @Size(max = 1000) String comment) { }

    public record ReviewRequest(@Size(max = 1000) String comment) { }

    public record InvoiceConversionRequest(
            UUID customerId, UUID vendorId, UUID itemId, LocalDate dueDate,
            @Pattern(regexp = "(^$)|(^[0-9]{2}$)") String placeOfSupply) { }

    public record VoucherConversionRequest(
            @Size(max = 30) String voucherTypeCode,
            UUID debitLedgerId, UUID creditLedgerId, UUID bankAccountId,
            @Size(max = 1000) String narration) { }

    public record ConversionResponse(
            UUID documentId, DocumentStatus status, UUID invoiceId, UUID voucherId,
            UUID bankStatementImportId, String message) { }

    public record DuplicateResponse(
            UUID id, UUID documentId, String documentName, UUID duplicateOfDocumentId,
            String duplicateOfDocumentName, String matchType, BigDecimal confidenceScore,
            boolean resolved, Instant createdAt) { }

    public record DocumentDashboardResponse(
            long totalDocuments, long processing, long reviewRequired, long approved,
            long converted, long rejected, long duplicateDocuments,
            Map<DocumentType, Long> byType) { }
}
