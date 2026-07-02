package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="background_jobs")
public class BackgroundJob extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
    @Column(name="job_type",nullable=false,length=50) private String jobType;
    @Column(nullable=false,length=20) private String status;
    @Column(name="payload_json",nullable=false,columnDefinition="text") private String payloadJson;
    @Column(name="result_json",columnDefinition="text") private String resultJson;
    @Column(nullable=false) private int attempts;
    @Column(name="max_attempts",nullable=false) private int maxAttempts=3;
    @Column(name="scheduled_at",nullable=false) private Instant scheduledAt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="correlation_id",length=100) private String correlationId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="created_by") private User createdBy;
    public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public String getJobType(){return jobType;} public void setJobType(String v){jobType=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getPayloadJson(){return payloadJson;} public void setPayloadJson(String v){payloadJson=v;}
    public String getResultJson(){return resultJson;} public void setResultJson(String v){resultJson=v;}
    public int getAttempts(){return attempts;} public void setAttempts(int v){attempts=v;}
    public int getMaxAttempts(){return maxAttempts;} public void setMaxAttempts(int v){maxAttempts=v;}
    public Instant getScheduledAt(){return scheduledAt;} public void setScheduledAt(Instant v){scheduledAt=v;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;}
    public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;}
    public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
    public User getCreatedBy(){return createdBy;} public void setCreatedBy(User v){createdBy=v;}
}
