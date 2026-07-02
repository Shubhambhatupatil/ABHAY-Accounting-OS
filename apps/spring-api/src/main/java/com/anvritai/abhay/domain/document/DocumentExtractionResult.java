package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "document_extraction_results")
public class DocumentExtractionResult extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "processing_job_id") private DocumentProcessingJob processingJob;
    @Column(nullable = false, length = 80) private String extractor;
    @Column(name = "extracted_text", columnDefinition = "text") private String extractedText;
    @Column(name = "result_json", nullable = false, columnDefinition = "text") private String resultJson;
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4) private BigDecimal confidenceScore;
    public String getExtractedText() { return extractedText; } public void setCompany(Company v) { company = v; }
    public void setDocument(Document v) { document = v; } public void setProcessingJob(DocumentProcessingJob v) { processingJob = v; }
    public void setExtractor(String v) { extractor = v; } public void setExtractedText(String v) { extractedText = v; }
    public void setResultJson(String v) { resultJson = v; } public void setConfidenceScore(BigDecimal v) { confidenceScore = v; }
}
