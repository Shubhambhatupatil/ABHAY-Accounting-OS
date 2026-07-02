package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "automation_notifications")
public class AutomationNotification extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "automation_rule_id") private AutomationRule rule;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "automation_run_id") private AutomationRun run;
    @Column(nullable=false,length=20) private String channel;
    @Column(name="notification_type",nullable=false,length=20) private String notificationType;
    @Column(nullable=false,length=200) private String title;
    @Column(nullable=false,length=1200) private String message;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="recipient_user_id") private User recipient;
    @Column(name="delivery_status",nullable=false,length=20) private String deliveryStatus;
    @Column(name="read_at") private Instant readAt;
    public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public AutomationRule getRule(){return rule;} public void setRule(AutomationRule v){rule=v;}
    public AutomationRun getRun(){return run;} public void setRun(AutomationRun v){run=v;}
    public String getChannel(){return channel;} public void setChannel(String v){channel=v;}
    public String getNotificationType(){return notificationType;} public void setNotificationType(String v){notificationType=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public User getRecipient(){return recipient;} public void setRecipient(User v){recipient=v;}
    public String getDeliveryStatus(){return deliveryStatus;} public void setDeliveryStatus(String v){deliveryStatus=v;}
    public Instant getReadAt(){return readAt;} public void setReadAt(Instant v){readAt=v;}
}
