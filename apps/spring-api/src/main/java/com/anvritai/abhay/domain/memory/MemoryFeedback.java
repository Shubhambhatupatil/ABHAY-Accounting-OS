package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
@Entity @Table(name="memory_feedback")
public class MemoryFeedback extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @OneToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="suggestion_id") private MemorySuggestion suggestion;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="user_id") private User user;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private MemoryFeedbackAction action;
 @Column(name="corrected_value",length=1000) private String correctedValue;
 @Column(length=1000) private String comment;
 public void setCompany(Company v){company=v;} public MemorySuggestion getSuggestion(){return suggestion;} public void setSuggestion(MemorySuggestion v){suggestion=v;}
 public void setUser(User v){user=v;} public MemoryFeedbackAction getAction(){return action;} public void setAction(MemoryFeedbackAction v){action=v;}
 public String getCorrectedValue(){return correctedValue;} public void setCorrectedValue(String v){correctedValue=v;} public void setComment(String v){comment=v;}
}
