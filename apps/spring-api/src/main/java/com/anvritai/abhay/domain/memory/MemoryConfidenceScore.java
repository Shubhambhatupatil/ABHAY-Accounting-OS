package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
@Entity @Table(name="memory_confidence_scores")
public class MemoryConfidenceScore extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="pattern_id") private MemoryPattern pattern;
 @Column(name="confidence_score",nullable=false,precision=5,scale=4) private BigDecimal confidenceScore;
 @Column(name="occurrence_count",nullable=false) private long occurrenceCount;
 @Column(name="success_count",nullable=false) private long successCount;
 @Column(name="failure_count",nullable=false) private long failureCount;
 @Column(nullable=false,length=500) private String reason;
 @Column(name="calculated_at",nullable=false) private Instant calculatedAt;
 public void setCompany(Company v){company=v;} public void setPattern(MemoryPattern v){pattern=v;}
 public void setConfidenceScore(BigDecimal v){confidenceScore=v;} public void setOccurrenceCount(long v){occurrenceCount=v;}
 public void setSuccessCount(long v){successCount=v;} public void setFailureCount(long v){failureCount=v;}
 public void setReason(String v){reason=v;} public void setCalculatedAt(Instant v){calculatedAt=v;}
}
