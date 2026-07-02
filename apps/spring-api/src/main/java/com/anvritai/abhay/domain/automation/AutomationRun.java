package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "automation_runs")
public class AutomationRun extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "automation_rule_id") private AutomationRule rule;
    @Column(name = "trigger_type", nullable = false, length = 30) private String triggerType;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "matched_count", nullable = false) private int matchedCount;
    @Column(length = 1000) private String summary;
    @Column(name = "correlation_id", length = 100) private String correlationId;
    public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public AutomationRule getRule(){return rule;} public void setRule(AutomationRule v){rule=v;}
    public String getTriggerType(){return triggerType;} public void setTriggerType(String v){triggerType=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
    public int getMatchedCount(){return matchedCount;} public void setMatchedCount(int v){matchedCount=v;}
    public String getSummary(){return summary;} public void setSummary(String v){summary=v;}
    public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
}
