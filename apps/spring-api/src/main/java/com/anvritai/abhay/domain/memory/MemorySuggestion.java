package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="memory_suggestions")
public class MemorySuggestion extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="pattern_id") private MemoryPattern pattern;
 @Column(name="suggestion_type",nullable=false,length=80) private String suggestionType;
 @Column(name="input_key",nullable=false,length=500) private String inputKey;
 @Column(name="suggested_value",length=1000) private String suggestedValue;
 @Column(name="confidence_score",nullable=false,precision=5,scale=4) private BigDecimal confidenceScore;
 @Column(name="low_confidence",nullable=false) private boolean lowConfidence;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MemorySuggestionStatus status=MemorySuggestionStatus.PENDING;
 @Column(name="supporting_event_count",nullable=false) private long supportingEventCount;
 @Column(name="last_similar_at") private Instant lastSimilarAt;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by") private User createdBy;
 public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
 public MemoryPattern getPattern(){return pattern;} public void setPattern(MemoryPattern v){pattern=v;}
 public String getSuggestionType(){return suggestionType;} public void setSuggestionType(String v){suggestionType=v;}
 public String getInputKey(){return inputKey;} public void setInputKey(String v){inputKey=v;}
 public String getSuggestedValue(){return suggestedValue;} public void setSuggestedValue(String v){suggestedValue=v;}
 public BigDecimal getConfidenceScore(){return confidenceScore;} public void setConfidenceScore(BigDecimal v){confidenceScore=v;}
 public boolean isLowConfidence(){return lowConfidence;} public void setLowConfidence(boolean v){lowConfidence=v;}
 public MemorySuggestionStatus getStatus(){return status;} public void setStatus(MemorySuggestionStatus v){status=v;}
 public long getSupportingEventCount(){return supportingEventCount;} public void setSupportingEventCount(long v){supportingEventCount=v;}
 public Instant getLastSimilarAt(){return lastSimilarAt;} public void setLastSimilarAt(Instant v){lastSimilarAt=v;}
 public User getCreatedBy(){return createdBy;} public void setCreatedBy(User v){createdBy=v;}
}
