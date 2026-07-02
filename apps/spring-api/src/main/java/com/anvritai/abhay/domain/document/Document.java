package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.sales.Invoice;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "documents")
public class Document extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploaded_by") private User uploadedBy;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false, length = 40) private DocumentType documentType;
    @Column(name = "original_file_name", nullable = false, length = 255) private String originalFileName;
    @Column(name = "file_type", nullable = false, length = 20) private String fileType;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @Column(name = "file_hash_sha256", nullable = false, length = 64) private String fileHashSha256;
    @Column(name = "storage_key", nullable = false, length = 500) private String storageKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DocumentStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DocumentSource source;
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4) private BigDecimal confidenceScore = BigDecimal.ZERO;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "linked_invoice_id") private Invoice linkedInvoice;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "linked_voucher_id") private Voucher linkedVoucher;
    @Column(length = 1000) private String notes;
    public Company getCompany() { return company; } public void setCompany(Company v) { company = v; }
    public User getUploadedBy() { return uploadedBy; } public void setUploadedBy(User v) { uploadedBy = v; }
    public DocumentType getDocumentType() { return documentType; } public void setDocumentType(DocumentType v) { documentType = v; }
    public String getOriginalFileName() { return originalFileName; } public void setOriginalFileName(String v) { originalFileName = v; }
    public String getFileType() { return fileType; } public void setFileType(String v) { fileType = v; }
    public long getFileSize() { return fileSize; } public void setFileSize(long v) { fileSize = v; }
    public String getFileHashSha256() { return fileHashSha256; } public void setFileHashSha256(String v) { fileHashSha256 = v; }
    public String getStorageKey() { return storageKey; } public void setStorageKey(String v) { storageKey = v; }
    public DocumentStatus getStatus() { return status; } public void setStatus(DocumentStatus v) { status = v; }
    public DocumentSource getSource() { return source; } public void setSource(DocumentSource v) { source = v; }
    public BigDecimal getConfidenceScore() { return confidenceScore; } public void setConfidenceScore(BigDecimal v) { confidenceScore = v; }
    public Invoice getLinkedInvoice() { return linkedInvoice; } public void setLinkedInvoice(Invoice v) { linkedInvoice = v; }
    public Voucher getLinkedVoucher() { return linkedVoucher; } public void setLinkedVoucher(Voucher v) { linkedVoucher = v; }
    public String getNotes() { return notes; } public void setNotes(String v) { notes = v; }
}
