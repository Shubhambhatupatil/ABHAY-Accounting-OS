package com.anvritai.abhay.api;

import com.anvritai.abhay.api.DocumentDtos.*;
import com.anvritai.abhay.domain.document.*;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.*;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/companies/{companyId}/documents")
public class DocumentController {
    private final DocumentIntelligenceService documents;
    public DocumentController(DocumentIntelligenceService documents) { this.documents = documents; }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse upload(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam DocumentType documentType,
            @RequestParam(defaultValue = "MANUAL_UPLOAD") DocumentSource source,
            @RequestPart("file") MultipartFile file) {
        try {
            return documents.upload(principal.id(), companyId, documentType, source,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new AccountingRuleException("Document could not be read.");
        }
    }

    @GetMapping
    public List<DocumentResponse> list(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return documents.list(principal.id(), companyId);
    }

    @GetMapping("/duplicates")
    public List<DuplicateResponse> duplicates(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return documents.duplicates(principal.id(), companyId);
    }

    @GetMapping("/search")
    public List<DocumentResponse> search(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) DocumentStatus status) {
        return documents.search(principal.id(), companyId, q, documentType, status);
    }

    @GetMapping("/dashboard")
    public DocumentDashboardResponse dashboard(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return documents.dashboard(principal.id(), companyId);
    }

    @GetMapping("/{documentId}")
    public DocumentResponse get(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId) {
        return documents.get(principal.id(), companyId, documentId);
    }

    @GetMapping("/{documentId}/fields")
    public List<DocumentFieldResponse> fields(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId) {
        return documents.fields(principal.id(), companyId, documentId);
    }

    @PatchMapping("/{documentId}/fields/{fieldId}")
    public DocumentFieldResponse correctField(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId, @PathVariable UUID fieldId,
            @Valid @RequestBody FieldCorrectionRequest request) {
        return documents.correctField(principal.id(), companyId, documentId, fieldId, request);
    }

    @PostMapping("/{documentId}/approve")
    public DocumentResponse approve(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId, @RequestBody(required = false) ReviewRequest request) {
        return documents.approve(principal.id(), companyId, documentId, request);
    }

    @PostMapping("/{documentId}/reject")
    public DocumentResponse reject(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId, @RequestBody(required = false) ReviewRequest request) {
        return documents.reject(principal.id(), companyId, documentId, request);
    }

    @PostMapping("/{documentId}/convert-to-invoice")
    public ConversionResponse convertToInvoice(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId, @RequestBody(required = false) InvoiceConversionRequest request) {
        return documents.convertToInvoice(principal.id(), companyId, documentId, request);
    }

    @PostMapping("/{documentId}/convert-to-voucher")
    public ConversionResponse convertToVoucher(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID documentId, @RequestBody(required = false) VoucherConversionRequest request) {
        return documents.convertToVoucher(principal.id(), companyId, documentId, request);
    }
}
