package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "document_processing_jobs")
public class DocumentProcessingJob extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @Column(name = "job_type", nullable = false, length = 30) private String jobType;
    @Column(nullable = false, length = 30) private String status;
    @Column(nullable = false) private int attempts;
    @Column(length = 500) private String message;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    public Document getDocument() { return document; } public void setCompany(Company v) { company = v; }
    public void setDocument(Document v) { document = v; } public String getStatus() { return status; }
    public void setJobType(String v) { jobType = v; } public void setStatus(String v) { status = v; }
    public void setAttempts(int v) { attempts = v; } public void setMessage(String v) { message = v; }
    public void setStartedAt(Instant v) { startedAt = v; } public void setCompletedAt(Instant v) { completedAt = v; }
}
