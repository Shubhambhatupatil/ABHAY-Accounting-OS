package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="memory_patterns")
public class MemoryPattern extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @Enumerated(EnumType.STRING) @Column(name="memory_type",nullable=false,length=50) private MemoryType memoryType;
 @Column(name="pattern_key",nullable=false,length=500) private String patternKey;
 @Column(name="subject_key",nullable=false,length=300) private String subjectKey;
 @Column(name="suggestion_type",nullable=false,length=80) private String suggestionType;
 @Column(name="suggested_value",nullable=false,length=1000) private String suggestedValue;
 @Column(name="occurrence_count",nullable=false) private long occurrenceCount;
 @Column(name="success_count",nullable=false) private long successCount;
 @Column(name="failure_count",nullable=false) private long failureCount;
 @Column(name="confidence_score",nullable=false,precision=5,scale=4) private BigDecimal confidenceScore;
 @Column(name="last_used_at") private Instant lastUsedAt;
 @Column(name="last_similar_at") private Instant lastSimilarAt;
 @Column(nullable=false,length=1000) private String explanation;
 @Column(name="evidence_json",nullable=false,columnDefinition="text") private String evidenceJson;
 public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
 public MemoryType getMemoryType(){return memoryType;} public void setMemoryType(MemoryType v){memoryType=v;}
 public String getPatternKey(){return patternKey;} public void setPatternKey(String v){patternKey=v;}
 public String getSubjectKey(){return subjectKey;} public void setSubjectKey(String v){subjectKey=v;}
 public String getSuggestionType(){return suggestionType;} public void setSuggestionType(String v){suggestionType=v;}
 public String getSuggestedValue(){return suggestedValue;} public void setSuggestedValue(String v){suggestedValue=v;}
 public long getOccurrenceCount(){return occurrenceCount;} public void setOccurrenceCount(long v){occurrenceCount=v;}
 public long getSuccessCount(){return successCount;} public void setSuccessCount(long v){successCount=v;}
 public long getFailureCount(){return failureCount;} public void setFailureCount(long v){failureCount=v;}
 public BigDecimal getConfidenceScore(){return confidenceScore;} public void setConfidenceScore(BigDecimal v){confidenceScore=v;}
 public Instant getLastUsedAt(){return lastUsedAt;} public void setLastUsedAt(Instant v){lastUsedAt=v;}
 public Instant getLastSimilarAt(){return lastSimilarAt;} public void setLastSimilarAt(Instant v){lastSimilarAt=v;}
 public String getExplanation(){return explanation;} public void setExplanation(String v){explanation=v;}
 public String getEvidenceJson(){return evidenceJson;} public void setEvidenceJson(String v){evidenceJson=v;}
}
