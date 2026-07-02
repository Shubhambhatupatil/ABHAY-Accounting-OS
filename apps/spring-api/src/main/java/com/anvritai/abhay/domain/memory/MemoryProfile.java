package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="memory_profiles")
public class MemoryProfile extends BaseEntity {
 @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @Column(nullable=false) private boolean enabled=true;
 @Column(name="retention_days",nullable=false) private int retentionDays=2555;
 @Column(name="last_rebuilt_at") private Instant lastRebuiltAt;
 public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
 public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;}
 public int getRetentionDays(){return retentionDays;} public void setRetentionDays(int v){retentionDays=v;}
 public Instant getLastRebuiltAt(){return lastRebuiltAt;} public void setLastRebuiltAt(Instant v){lastRebuiltAt=v;}
}
