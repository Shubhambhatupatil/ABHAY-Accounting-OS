package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;

@Entity @Table(name = "document_versions")
public class DocumentVersion extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @Column(name = "version_number", nullable = false) private int versionNumber;
    @Column(name = "storage_key", nullable = false, length = 500) private String storageKey;
    @Column(name = "file_hash_sha256", nullable = false, length = 64) private String fileHashSha256;
    @Column(name = "file_size", nullable = false) private long fileSize;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private User createdBy;
    public void setCompany(Company v) { company = v; } public void setDocument(Document v) { document = v; }
    public void setVersionNumber(int v) { versionNumber = v; } public void setStorageKey(String v) { storageKey = v; }
    public void setFileHashSha256(String v) { fileHashSha256 = v; } public void setFileSize(long v) { fileSize = v; }
    public void setCreatedBy(User v) { createdBy = v; }
}
