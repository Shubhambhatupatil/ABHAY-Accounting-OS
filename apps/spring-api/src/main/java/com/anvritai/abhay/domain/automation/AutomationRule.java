package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "automation_rules")
public class AutomationRule extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @Column(nullable = false, length = 160) private String name;
    @Column(name = "rule_type", nullable = false, length = 40) private String ruleType;
    @Column(name = "schedule_type", nullable = false, length = 30) private String scheduleType;
    @Column(name = "parameters_json", nullable = false, columnDefinition = "text") private String parametersJson;
    @Column(name = "notification_channel", nullable = false, length = 20) private String notificationChannel;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "next_run_at") private Instant nextRunAt;
    @Column(name = "last_run_at") private Instant lastRunAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private User createdBy;

    public Company getCompany() { return company; } public void setCompany(Company v) { company = v; }
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getRuleType() { return ruleType; } public void setRuleType(String v) { ruleType = v; }
    public String getScheduleType() { return scheduleType; } public void setScheduleType(String v) { scheduleType = v; }
    public String getParametersJson() { return parametersJson; } public void setParametersJson(String v) { parametersJson = v; }
    public String getNotificationChannel() { return notificationChannel; } public void setNotificationChannel(String v) { notificationChannel = v; }
    public boolean isActive() { return active; } public void setActive(boolean v) { active = v; }
    public Instant getNextRunAt() { return nextRunAt; } public void setNextRunAt(Instant v) { nextRunAt = v; }
    public Instant getLastRunAt() { return lastRunAt; } public void setLastRunAt(Instant v) { lastRunAt = v; }
    public User getCreatedBy() { return createdBy; } public void setCreatedBy(User v) { createdBy = v; }
}
