package com.anvritai.abhay.service;

import com.anvritai.abhay.api.AccountingDtos.*;
import com.anvritai.abhay.api.BankingDtos.StatementImportResponse;
import com.anvritai.abhay.api.DocumentDtos.*;
import com.anvritai.abhay.api.SalesPurchaseDtos.*;
import com.anvritai.abhay.domain.*;
import com.anvritai.abhay.domain.accounting.*;
import com.anvritai.abhay.domain.document.*;
import com.anvritai.abhay.domain.gst.GstTreatment;
import com.anvritai.abhay.domain.sales.*;
import com.anvritai.abhay.repository.*;
import com.anvritai.abhay.repository.accounting.*;
import com.anvritai.abhay.repository.document.*;
import com.anvritai.abhay.repository.sales.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentIntelligenceService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of("pdf", "jpg", "jpeg", "png", "csv", "xlsx");
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};
    private final CompanyAccessService access;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final DocumentRepository documents;
    private final DocumentVersionRepository versions;
    private final DocumentPageRepository pages;
    private final DocumentFieldRepository fields;
    private final DocumentProcessingJobRepository jobs;
    private final DocumentExtractionResultRepository results;
    private final DocumentReviewActionRepository reviews;
    private final DocumentDuplicateRepository duplicates;
    private final DocumentStorageService storage;
    private final DocumentExtractionService extractor;
    private final CustomerRepository customers;
    private final VendorRepository vendors;
    private final ItemRepository items;
    private final InvoiceRepository invoices;
    private final VoucherRepository vouchers;
    private final SalesPurchaseService salesPurchase;
    private final AccountingService accounting;
    private final BankingService banking;
    private final AuditService audit;
    private final GstMemoryService memory;
    private final ObjectMapper objectMapper;

    public DocumentIntelligenceService(
            CompanyAccessService access, CompanyRepository companies, UserRepository users,
            DocumentRepository documents, DocumentVersionRepository versions, DocumentPageRepository pages,
            DocumentFieldRepository fields, DocumentProcessingJobRepository jobs,
            DocumentExtractionResultRepository results, DocumentReviewActionRepository reviews,
            DocumentDuplicateRepository duplicates, DocumentStorageService storage,
            DocumentExtractionService extractor, CustomerRepository customers, VendorRepository vendors,
            ItemRepository items, InvoiceRepository invoices, VoucherRepository vouchers,
            SalesPurchaseService salesPurchase, AccountingService accounting, BankingService banking,
            AuditService audit, GstMemoryService memory, ObjectMapper objectMapper) {
        this.access = access; this.companies = companies; this.users = users; this.documents = documents;
        this.versions = versions; this.pages = pages; this.fields = fields; this.jobs = jobs;
        this.results = results; this.reviews = reviews; this.duplicates = duplicates; this.storage = storage;
        this.extractor = extractor; this.customers = customers; this.vendors = vendors; this.items = items;
        this.invoices = invoices; this.vouchers = vouchers; this.salesPurchase = salesPurchase;
        this.accounting = accounting; this.banking = banking; this.audit = audit; this.memory = memory;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DocumentResponse upload(UUID userId, UUID companyId, DocumentType type, DocumentSource source,
            String originalFileName, String contentType, byte[] content) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        validateFile(originalFileName, contentType, content);
        Company company = company(companyId);
        User uploader = user(userId);
        String extension = extension(originalFileName);
        String hash = sha256(content);
        Optional<Document> duplicateOf = documents.findFirstByCompanyIdAndFileHashSha256OrderByCreatedAtAsc(companyId, hash);
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setCompany(company);
        document.setUploadedBy(uploader);
        document.setDocumentType(type);
        document.setOriginalFileName(cleanFileName(originalFileName));
        document.setFileType(extension.toUpperCase(Locale.ROOT));
        document.setFileSize(content.length);
        document.setFileHashSha256(hash);
        document.setStorageKey(storage.reserveStorageKey(companyId, document.getId(), 1, document.getOriginalFileName()));
        document.setStatus(DocumentStatus.UPLOADED);
        document.setSource(source == null ? DocumentSource.MANUAL_UPLOAD : source);
        document = documents.save(document);
        createVersion(document, uploader);
        if (duplicateOf.isPresent()) createDuplicate(document, duplicateOf.get());
        DocumentProcessingJob job = createJob(document);
        process(document, job, extension, content);
        record(userId, company, "DOCUMENT_UPLOADED", document.getId(),
                Map.of("documentType", type.name(), "fileType", extension, "duplicate", duplicateOf.isPresent()));
        return response(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return documents.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(UUID userId, UUID companyId, UUID documentId) {
        access.requireMembership(companyId, userId);
        return response(document(companyId, documentId));
    }

    @Transactional(readOnly = true)
    public List<DocumentFieldResponse> fields(UUID userId, UUID companyId, UUID documentId) {
        access.requireMembership(companyId, userId);
        document(companyId, documentId);
        return fields.findAllByCompanyIdAndDocumentIdOrderByFieldName(companyId, documentId).stream()
                .map(this::fieldResponse).toList();
    }

    @Transactional
    public DocumentFieldResponse correctField(UUID userId, UUID companyId, UUID documentId, UUID fieldId,
            FieldCorrectionRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Document document = mutableDocument(companyId, documentId);
        DocumentField field = fields.findByIdAndCompanyIdAndDocumentId(fieldId, companyId, documentId)
                .orElseThrow(() -> new NotFoundException("Document field was not found."));
        String oldValue = field.getNormalizedValue();
        field.setNormalizedValue(request.normalizedValue().trim());
        field.setCorrected(true);
        field.setConfidenceScore(BigDecimal.ONE.setScale(4));
        fields.save(field);
        document.setStatus(DocumentStatus.REVIEW_REQUIRED);
        documents.save(document);
        review(document, field, userId, "FIELD_CORRECTED", oldValue, field.getNormalizedValue(), request.comment());
        Map<String, Object> correction = new LinkedHashMap<>();
        correction.put("field", field.getFieldName()); correction.put("fieldId", field.getId());
        if (oldValue != null) correction.put("oldValue", oldValue);
        correction.put("newValue", field.getNormalizedValue());
        record(userId, document.getCompany(), "DOCUMENT_FIELD_CORRECTED", documentId, correction);
        return fieldResponse(field);
    }

    @Transactional
    public DocumentResponse approve(UUID userId, UUID companyId, UUID documentId, ReviewRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Document document = mutableDocument(companyId, documentId);
        if (document.getStatus() == DocumentStatus.PROCESSING || document.getStatus() == DocumentStatus.UPLOADED) {
            throw new AccountingRuleException("Document processing must finish before approval.");
        }
        if (document.getStatus() == DocumentStatus.REJECTED) {
            throw new AccountingRuleException("Rejected documents cannot be approved without reprocessing.");
        }
        document.setStatus(DocumentStatus.APPROVED);
        documents.save(document);
        review(document, null, userId, "APPROVED", null, null, request == null ? null : request.comment());
        record(userId, document.getCompany(), "DOCUMENT_APPROVED", documentId, Map.of());
        return response(document);
    }

    @Transactional
    public DocumentResponse reject(UUID userId, UUID companyId, UUID documentId, ReviewRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Document document = mutableDocument(companyId, documentId);
        document.setStatus(DocumentStatus.REJECTED);
        documents.save(document);
        review(document, null, userId, "REJECTED", null, null, request == null ? null : request.comment());
        record(userId, document.getCompany(), "DOCUMENT_REJECTED", documentId, Map.of());
        return response(document);
    }

    @Transactional
    public ConversionResponse convertToInvoice(UUID userId, UUID companyId, UUID documentId,
            InvoiceConversionRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Document document = approvedForConversion(companyId, documentId);
        if (document.getDocumentType() != DocumentType.SALES_INVOICE
                && document.getDocumentType() != DocumentType.PURCHASE_INVOICE) {
            throw new AccountingRuleException("Only approved sales or purchase invoice documents can create invoice drafts.");
        }
        Map<String, String> values = fieldValues(companyId, documentId);
        InvoiceType invoiceType = document.getDocumentType() == DocumentType.SALES_INVOICE
                ? InvoiceType.SALES : InvoiceType.PURCHASE;
        UUID customerId = invoiceType == InvoiceType.SALES
                ? resolveCustomer(companyId, request == null ? null : request.customerId(), values) : null;
        UUID vendorId = invoiceType == InvoiceType.PURCHASE
                ? resolveVendor(companyId, request == null ? null : request.vendorId(), values) : null;
        UUID itemId = request == null ? null : request.itemId();
        if (itemId != null && items.findByIdAndCompanyId(itemId, companyId).isEmpty()) {
            throw new NotFoundException("Item was not found.");
        }
        BigDecimal taxable = amount(values, "taxable_amount");
        BigDecimal total = amount(values, "total_amount");
        BigDecimal tax = amount(values, "cgst_amount").add(amount(values, "sgst_amount"))
                .add(amount(values, "igst_amount"));
        if (taxable.signum() == 0 && total.signum() > 0) taxable = total.subtract(tax).max(BigDecimal.ZERO);
        if (taxable.signum() <= 0) throw new AccountingRuleException("Taxable or total amount is required before conversion.");
        BigDecimal gstRate = tax.signum() == 0 ? BigDecimal.ZERO
                : tax.multiply(BigDecimal.valueOf(100)).divide(taxable, 2, RoundingMode.HALF_UP);
        LocalDate invoiceDate = parseDate(values.get("invoice_date"), LocalDate.now());
        LocalDate dueDate = request != null && request.dueDate() != null ? request.dueDate() : invoiceDate;
        String invoiceNumber = required(values, "invoice_number", "Invoice number is required before conversion.");
        String partyName = invoiceType == InvoiceType.SALES ? values.get("customer_name") : values.get("vendor_name");
        InvoiceCreateRequest create = new InvoiceCreateRequest(invoiceType, invoiceNumber, invoiceDate, dueDate,
                customerId, vendorId, request == null ? null : request.placeOfSupply(), GstTreatment.NORMAL,
                "Draft created from reviewed document " + documentId,
                List.of(new InvoiceLineRequest(itemId, partyName == null ? "Document line item" : partyName,
                        BigDecimal.ONE, taxable, gstRate, BigDecimal.ZERO)));
        InvoiceResponse created = salesPurchase.createInvoice(userId, companyId, create);
        document.setLinkedInvoice(invoices.findByIdAndCompanyId(created.id(), companyId)
                .orElseThrow(() -> new IllegalStateException("Created invoice could not be loaded.")));
        converted(document, userId, "invoiceId", created.id());
        return new ConversionResponse(documentId, DocumentStatus.CONVERTED, created.id(), null, null,
                "Purchase/sales invoice draft created for review.");
    }

    @Transactional
    public ConversionResponse convertToVoucher(UUID userId, UUID companyId, UUID documentId,
            VoucherConversionRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Document document = approvedForConversion(companyId, documentId);
        if (document.getDocumentType() == DocumentType.BANK_STATEMENT) {
            if (request == null || request.bankAccountId() == null) {
                throw new AccountingRuleException("Bank account is required to import an approved bank statement.");
            }
            DocumentExtractionResult result = results.findFirstByCompanyIdAndDocumentIdOrderByCreatedAtDesc(companyId, documentId)
                    .orElseThrow(() -> new AccountingRuleException("Extracted CSV content is unavailable."));
            if (!"CSV".equals(document.getFileType())) {
                throw new AccountingRuleException("Only CSV bank statements can be imported in this release.");
            }
            StatementImportResponse imported = banking.importStatement(userId, companyId, request.bankAccountId(),
                    document.getOriginalFileName(), result.getExtractedText().getBytes(StandardCharsets.UTF_8));
            converted(document, userId, "bankStatementImportId", imported.id());
            return new ConversionResponse(documentId, DocumentStatus.CONVERTED, null, null, imported.id(),
                    "Bank statement imported for reconciliation.");
        }
        if (request == null || request.debitLedgerId() == null || request.creditLedgerId() == null) {
            throw new AccountingRuleException("Debit and credit ledgers are required for voucher conversion.");
        }
        BigDecimal amount = amount(fieldValues(companyId, documentId), "total_amount");
        if (amount.signum() <= 0) amount = amount(fieldValues(companyId, documentId), "taxable_amount");
        if (amount.signum() <= 0) throw new AccountingRuleException("Total amount is required before voucher conversion.");
        String voucherType = request.voucherTypeCode() == null || request.voucherTypeCode().isBlank()
                ? inferredVoucherType(document.getDocumentType()) : request.voucherTypeCode().trim().toUpperCase(Locale.ROOT);
        VoucherResponse created = accounting.createVoucher(userId, companyId,
                new VoucherCreateRequest(voucherType, LocalDate.now(),
                        request.narration() == null ? "Draft from reviewed document " + documentId : request.narration(),
                        List.of(new VoucherLineRequest(request.debitLedgerId(), amount, BigDecimal.ZERO, null),
                                new VoucherLineRequest(request.creditLedgerId(), BigDecimal.ZERO, amount, null))));
        document.setLinkedVoucher(vouchers.findByIdAndCompanyId(created.id(), companyId)
                .orElseThrow(() -> new IllegalStateException("Created voucher could not be loaded.")));
        converted(document, userId, "voucherId", created.id());
        return new ConversionResponse(documentId, DocumentStatus.CONVERTED, null, created.id(), null,
                "Voucher draft created for review.");
    }

    @Transactional(readOnly = true)
    public List<DuplicateResponse> duplicates(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return duplicates.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(this::duplicateResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> search(UUID userId, UUID companyId, String query,
            DocumentType type, DocumentStatus status) {
        access.requireMembership(companyId, userId);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return documents.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(d -> normalized.isBlank() || d.getOriginalFileName().toLowerCase(Locale.ROOT).contains(normalized))
                .filter(d -> type == null || d.getDocumentType() == type)
                .filter(d -> status == null || d.getStatus() == status)
                .limit(200).map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public DocumentDashboardResponse dashboard(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        Map<DocumentType, Long> byType = new EnumMap<>(DocumentType.class);
        for (DocumentType type : DocumentType.values()) {
            long count = documents.countByCompanyIdAndDocumentType(companyId, type);
            if (count > 0) byType.put(type, count);
        }
        return new DocumentDashboardResponse(documents.countByCompanyId(companyId),
                documents.countByCompanyIdAndStatus(companyId, DocumentStatus.PROCESSING),
                documents.countByCompanyIdAndStatus(companyId, DocumentStatus.REVIEW_REQUIRED),
                documents.countByCompanyIdAndStatus(companyId, DocumentStatus.APPROVED),
                documents.countByCompanyIdAndStatus(companyId, DocumentStatus.CONVERTED),
                documents.countByCompanyIdAndStatus(companyId, DocumentStatus.REJECTED),
                duplicates.findAllByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                        .filter(d -> !d.isResolved()).count(), byType);
    }

    private void process(Document document, DocumentProcessingJob job, String extension, byte[] content) {
        document.setStatus(DocumentStatus.PROCESSING);
        documents.save(document);
        job.setStatus("PROCESSING"); job.setStartedAt(Instant.now()); job.setAttempts(1); jobs.save(job);
        DocumentExtractionService.ExtractionOutcome outcome = extractor.extract(extension, content);
        job.setStatus(outcome.jobStatus()); job.setMessage(outcome.message());
        if ("COMPLETED".equals(outcome.jobStatus())) job.setCompletedAt(Instant.now());
        jobs.save(job);
        if (!outcome.text().isBlank()) createPage(document, outcome);
        outcome.fields().forEach((name, value) -> createField(document, name, value));
        createResult(document, job, outcome);
        document.setConfidenceScore(outcome.confidence());
        if ("COMPLETED".equals(outcome.jobStatus())) {
            document.setStatus(outcome.fields().isEmpty() || outcome.confidence().compareTo(new BigDecimal("0.8000")) < 0
                    ? DocumentStatus.REVIEW_REQUIRED : DocumentStatus.EXTRACTED);
        } else {
            document.setStatus(DocumentStatus.PROCESSING);
        }
        documents.save(document);
    }

    private void createVersion(Document document, User user) {
        DocumentVersion version = new DocumentVersion(); version.setCompany(document.getCompany());
        version.setDocument(document); version.setVersionNumber(1); version.setStorageKey(document.getStorageKey());
        version.setFileHashSha256(document.getFileHashSha256()); version.setFileSize(document.getFileSize());
        version.setCreatedBy(user); versions.save(version);
    }
    private DocumentProcessingJob createJob(Document document) {
        DocumentProcessingJob job = new DocumentProcessingJob(); job.setCompany(document.getCompany());
        job.setDocument(document); job.setJobType("EXTRACT"); job.setStatus("PENDING"); return jobs.save(job);
    }
    private void createPage(Document document, DocumentExtractionService.ExtractionOutcome outcome) {
        DocumentPage page = new DocumentPage(); page.setCompany(document.getCompany()); page.setDocument(document);
        page.setPageNumber(1); page.setExtractedText(outcome.text()); page.setOcrStatus("COMPLETED");
        page.setConfidenceScore(outcome.confidence()); pages.save(page);
    }
    private void createField(Document document, String name, DocumentExtractionService.ExtractedValue value) {
        DocumentField field = new DocumentField(); field.setCompany(document.getCompany()); field.setDocument(document);
        field.setFieldName(name); field.setRawValue(value.raw()); field.setNormalizedValue(value.normalized());
        field.setConfidenceScore(value.confidence()); fields.save(field);
    }
    private void createResult(Document document, DocumentProcessingJob job,
            DocumentExtractionService.ExtractionOutcome outcome) {
        DocumentExtractionResult result = new DocumentExtractionResult(); result.setCompany(document.getCompany());
        result.setDocument(document); result.setProcessingJob(job); result.setExtractor(outcome.extractor());
        result.setExtractedText(outcome.text()); result.setConfidenceScore(outcome.confidence());
        try { result.setResultJson(objectMapper.writeValueAsString(outcome.fields())); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Extraction result could not be serialized.", e); }
        results.save(result);
    }
    private void createDuplicate(Document document, Document original) {
        DocumentDuplicate duplicate = new DocumentDuplicate(); duplicate.setCompany(document.getCompany());
        duplicate.setDocument(document); duplicate.setDuplicateOfDocument(original); duplicate.setMatchType("SHA256_EXACT");
        duplicate.setConfidenceScore(BigDecimal.ONE.setScale(4)); duplicates.save(duplicate);
        document.setStatus(DocumentStatus.REVIEW_REQUIRED); documents.save(document);
    }
    private void review(Document document, DocumentField field, UUID userId, String action,
            String oldValue, String newValue, String comment) {
        DocumentReviewAction review = new DocumentReviewAction(); review.setCompany(document.getCompany());
        review.setDocument(document); review.setDocumentField(field); review.setAction(action);
        review.setOldValue(oldValue); review.setNewValue(newValue); review.setComment(comment); review.setReviewedBy(user(userId));
        reviews.save(review);
    }
    private void converted(Document document, UUID userId, String key, UUID value) {
        document.setStatus(DocumentStatus.CONVERTED); documents.save(document);
        review(document, null, userId, "CONVERTED", null, value.toString(), key);
        record(userId, document.getCompany(), "DOCUMENT_CONVERTED", document.getId(), Map.of(key, value));
    }
    private void record(UUID userId, Company company, String action, UUID documentId, Map<String, Object> details) {
        audit.record(company, user(userId), action, "DOCUMENT", documentId, details);
        memory.record(company, action, "DOCUMENT", documentId, "Document intelligence lifecycle event",
                BigDecimal.ONE.setScale(4), details);
    }

    private void validateFile(String fileName, String contentType, byte[] content) {
        if (fileName == null || fileName.isBlank()) throw new AccountingRuleException("File name is required.");
        String extension = extension(fileName);
        if (!ALLOWED.contains(extension)) throw new AccountingRuleException("Unsupported document file type.");
        if (content.length == 0 || content.length > MAX_FILE_SIZE) {
            throw new AccountingRuleException("Document must be between 1 byte and 10 MB.");
        }
        boolean magicValid = switch (extension) {
            case "pdf" -> startsWith(content, "%PDF".getBytes(StandardCharsets.US_ASCII));
            case "png" -> content.length > 8 && (content[0] & 0xff) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G';
            case "jpg", "jpeg" -> content.length > 2 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8;
            case "xlsx" -> content.length > 2 && content[0] == 'P' && content[1] == 'K';
            case "csv" -> !new String(content, StandardCharsets.UTF_8).contains("\u0000");
            default -> false;
        };
        if (!magicValid) throw new AccountingRuleException("File content does not match its extension.");
        if (contentType != null && contentType.length() > 100) throw new AccountingRuleException("Invalid content type.");
    }

    private Document approvedForConversion(UUID companyId, UUID documentId) {
        Document document = document(companyId, documentId);
        if (document.getStatus() != DocumentStatus.APPROVED) {
            throw new AccountingRuleException("Document must be approved before conversion.");
        }
        if (duplicates.existsByCompanyIdAndDocumentId(companyId, documentId)) {
            throw new AccountingRuleException("Resolve the duplicate warning before conversion.");
        }
        return document;
    }
    private Document mutableDocument(UUID companyId, UUID documentId) {
        Document document = document(companyId, documentId);
        if (document.getStatus() == DocumentStatus.CONVERTED) {
            throw new AccountingRuleException("Converted documents are immutable.");
        }
        return document;
    }
    private Map<String, String> fieldValues(UUID companyId, UUID documentId) {
        Map<String, String> values = new HashMap<>();
        fields.findAllByCompanyIdAndDocumentIdOrderByFieldName(companyId, documentId)
                .forEach(field -> values.put(field.getFieldName(), field.getNormalizedValue()));
        return values;
    }
    private UUID resolveCustomer(UUID companyId, UUID requested, Map<String, String> values) {
        if (requested != null) return customers.findByIdAndCompanyId(requested, companyId)
                .orElseThrow(() -> new NotFoundException("Customer was not found.")).getId();
        String name = values.get("customer_name"); String gstin = values.get("gstin");
        return customers.findAllByCompanyIdOrderByDisplayName(companyId).stream()
                .filter(c -> same(c.getName(), name) || same(c.getDisplayName(), name) || same(c.getGstin(), gstin))
                .map(Customer::getId).findFirst()
                .orElseThrow(() -> new AccountingRuleException("Select a customer before invoice conversion."));
    }
    private UUID resolveVendor(UUID companyId, UUID requested, Map<String, String> values) {
        if (requested != null) return vendors.findByIdAndCompanyId(requested, companyId)
                .orElseThrow(() -> new NotFoundException("Vendor was not found.")).getId();
        String name = values.get("vendor_name"); String gstin = values.get("gstin");
        return vendors.findAllByCompanyIdOrderByDisplayName(companyId).stream()
                .filter(v -> same(v.getName(), name) || same(v.getDisplayName(), name) || same(v.getGstin(), gstin))
                .map(Vendor::getId).findFirst()
                .orElseThrow(() -> new AccountingRuleException("Select a vendor before invoice conversion."));
    }
    private boolean same(String left, String right) { return left != null && right != null && left.trim().equalsIgnoreCase(right.trim()); }
    private BigDecimal amount(Map<String, String> values, String key) {
        try { String value = values.get(key); return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value.replace(",", "")); }
        catch (NumberFormatException e) { throw new AccountingRuleException("Correct the " + key + " field before conversion."); }
    }
    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) return fallback;
        for (java.time.format.DateTimeFormatter formatter : List.of(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE,
                java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"), java.time.format.DateTimeFormatter.ofPattern("d-M-uuuu"))) {
            try { return LocalDate.parse(value, formatter); } catch (java.time.format.DateTimeParseException ignored) { }
        }
        throw new AccountingRuleException("Correct the invoice_date field before conversion.");
    }
    private String required(Map<String, String> values, String key, String message) {
        String value = values.get(key); if (value == null || value.isBlank()) throw new AccountingRuleException(message); return value.trim();
    }
    private String inferredVoucherType(DocumentType type) {
        return switch (type) {
            case RECEIPT -> "RECEIPT";
            case PAYMENT_ADVICE, PURCHASE_INVOICE -> "PAYMENT";
            case CREDIT_NOTE -> "CREDIT_NOTE";
            case DEBIT_NOTE -> "DEBIT_NOTE";
            default -> "JOURNAL";
        };
    }
    private Document document(UUID companyId, UUID id) { return documents.findByIdAndCompanyId(id, companyId).orElseThrow(() -> new NotFoundException("Document was not found.")); }
    private Company company(UUID id) { return companies.findById(id).orElseThrow(() -> new NotFoundException("Company was not found.")); }
    private User user(UUID id) { return users.findById(id).orElseThrow(() -> new NotFoundException("User was not found.")); }
    private String cleanFileName(String value) { String normalized = value.replace('\\', '/'); return normalized.substring(normalized.lastIndexOf('/') + 1); }
    private String extension(String fileName) { int dot = fileName.lastIndexOf('.'); return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private String sha256(byte[] value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 is unavailable.", e); } }
    private boolean startsWith(byte[] value, byte[] prefix) { if (value.length < prefix.length) return false; for (int i = 0; i < prefix.length; i++) if (value[i] != prefix[i]) return false; return true; }

    private DocumentResponse response(Document d) {
        String processing = jobs.findFirstByCompanyIdAndDocumentIdOrderByCreatedAtDesc(d.getCompany().getId(), d.getId())
                .map(DocumentProcessingJob::getStatus).orElse("PENDING");
        return new DocumentResponse(d.getId(), d.getDocumentType(), d.getOriginalFileName(), d.getFileType(),
                d.getFileSize(), d.getStatus(), d.getSource(), d.getConfidenceScore(), processing,
                duplicates.existsByCompanyIdAndDocumentId(d.getCompany().getId(), d.getId()),
                d.getLinkedInvoice() == null ? null : d.getLinkedInvoice().getId(),
                d.getLinkedVoucher() == null ? null : d.getLinkedVoucher().getId(), d.getCreatedAt(), d.getUpdatedAt());
    }
    private DocumentFieldResponse fieldResponse(DocumentField f) { return new DocumentFieldResponse(f.getId(), f.getFieldName(), f.getRawValue(), f.getNormalizedValue(), f.getConfidenceScore(), f.isCorrected(), f.getUpdatedAt()); }
    private DuplicateResponse duplicateResponse(DocumentDuplicate d) { return new DuplicateResponse(d.getId(), d.getDocument().getId(), d.getDocument().getOriginalFileName(), d.getDuplicateOfDocument().getId(), d.getDuplicateOfDocument().getOriginalFileName(), d.getMatchType(), d.getConfidenceScore(), d.isResolved(), d.getCreatedAt()); }
}
