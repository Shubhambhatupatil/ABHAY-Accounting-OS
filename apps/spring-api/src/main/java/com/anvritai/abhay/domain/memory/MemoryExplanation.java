package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="memory_explanations")
public class MemoryExplanation extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="suggestion_id") private MemorySuggestion suggestion;
 @Column(nullable=false,length=1000) private String reason;
 @Column(name="supporting_event_count",nullable=false) private long supportingEventCount;
 @Column(name="last_similar_at") private Instant lastSimilarAt;
 @Column(length=500) private String warning;
 public void setCompany(Company v){company=v;} public void setSuggestion(MemorySuggestion v){suggestion=v;}
 public String getReason(){return reason;} public void setReason(String v){reason=v;}
 public long getSupportingEventCount(){return supportingEventCount;} public void setSupportingEventCount(long v){supportingEventCount=v;}
 public Instant getLastSimilarAt(){return lastSimilarAt;} public void setLastSimilarAt(Instant v){lastSimilarAt=v;}
 public String getWarning(){return warning;} public void setWarning(String v){warning=v;}
}
