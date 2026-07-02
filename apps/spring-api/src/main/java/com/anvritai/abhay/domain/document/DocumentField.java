package com.anvritai.abhay.domain.document;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "document_fields")
public class DocumentField extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "document_id") private Document document;
    @Column(name = "field_name", nullable = false, length = 80) private String fieldName;
    @Column(name = "raw_value", length = 2000) private String rawValue;
    @Column(name = "normalized_value", length = 2000) private String normalizedValue;
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4) private BigDecimal confidenceScore;
    @Column(nullable = false) private boolean corrected;
    public Company getCompany() { return company; } public void setCompany(Company v) { company = v; }
    public Document getDocument() { return document; } public void setDocument(Document v) { document = v; }
    public String getFieldName() { return fieldName; } public void setFieldName(String v) { fieldName = v; }
    public String getRawValue() { return rawValue; } public void setRawValue(String v) { rawValue = v; }
    public String getNormalizedValue() { return normalizedValue; } public void setNormalizedValue(String v) { normalizedValue = v; }
    public BigDecimal getConfidenceScore() { return confidenceScore; } public void setConfidenceScore(BigDecimal v) { confidenceScore = v; }
    public boolean isCorrected() { return corrected; } public void setCorrected(boolean v) { corrected = v; }
}
