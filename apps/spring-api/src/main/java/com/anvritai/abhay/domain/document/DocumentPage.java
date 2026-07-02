package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "document_pages")
public class DocumentPage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @Column(name = "page_number", nullable = false) private int pageNumber;
    @Column(name = "extracted_text", columnDefinition = "text") private String extractedText;
    @Column(name = "ocr_status", nullable = false, length = 30) private String ocrStatus;
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4) private BigDecimal confidenceScore;
    public void setCompany(Company v) { company = v; } public void setDocument(Document v) { document = v; }
    public void setPageNumber(int v) { pageNumber = v; } public void setExtractedText(String v) { extractedText = v; }
    public void setOcrStatus(String v) { ocrStatus = v; } public void setConfidenceScore(BigDecimal v) { confidenceScore = v; }
}
