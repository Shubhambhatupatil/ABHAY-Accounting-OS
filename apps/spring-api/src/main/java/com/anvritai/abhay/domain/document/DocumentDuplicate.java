package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "document_duplicates")
public class DocumentDuplicate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "document_id") private Document document;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "duplicate_of_document_id") private Document duplicateOfDocument;
    @Column(name = "match_type", nullable = false, length = 30) private String matchType;
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4) private BigDecimal confidenceScore;
    @Column(nullable = false) private boolean resolved;
    public Document getDocument() { return document; } public Document getDuplicateOfDocument() { return duplicateOfDocument; }
    public String getMatchType() { return matchType; } public BigDecimal getConfidenceScore() { return confidenceScore; }
    public boolean isResolved() { return resolved; } public void setCompany(Company v) { company = v; }
    public void setDocument(Document v) { document = v; } public void setDuplicateOfDocument(Document v) { duplicateOfDocument = v; }
    public void setMatchType(String v) { matchType = v; } public void setConfidenceScore(BigDecimal v) { confidenceScore = v; }
    public void setResolved(boolean v) { resolved = v; }
}
