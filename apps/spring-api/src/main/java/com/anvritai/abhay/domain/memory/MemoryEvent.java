package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="memory_events")
public class MemoryEvent extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @Enumerated(EnumType.STRING) @Column(name="memory_type",nullable=false,length=50) private MemoryType memoryType;
 @Column(name="event_type",nullable=false,length=80) private String eventType;
 @Column(name="entity_type",nullable=false,length=80) private String entityType;
 @Column(name="entity_id",nullable=false) private UUID entityId;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="actor_id") private User actor;
 @Column(name="subject_key",length=300) private String subjectKey;
 @Column(name="context_json",nullable=false,columnDefinition="text") private String contextJson;
 @Column(name="occurred_at",nullable=false) private Instant occurredAt;
 public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
 public MemoryType getMemoryType(){return memoryType;} public void setMemoryType(MemoryType v){memoryType=v;}
 public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
 public String getEntityType(){return entityType;} public void setEntityType(String v){entityType=v;}
 public UUID getEntityId(){return entityId;} public void setEntityId(UUID v){entityId=v;}
 public User getActor(){return actor;} public void setActor(User v){actor=v;}
 public String getSubjectKey(){return subjectKey;} public void setSubjectKey(String v){subjectKey=v;}
 public String getContextJson(){return contextJson;} public void setContextJson(String v){contextJson=v;}
 public Instant getOccurredAt(){return occurredAt;} public void setOccurredAt(Instant v){occurredAt=v;}
}
