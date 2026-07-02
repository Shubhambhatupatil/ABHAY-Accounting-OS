package com.anvritai.abhay.domain.memory;
import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;
@Entity @Table(name="memory_preferences")
public class MemoryPreference extends BaseEntity {
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") private User user;
 @Column(name="preference_key",nullable=false,length=160) private String preferenceKey;
 @Column(name="preference_value",nullable=false,length=2000) private String preferenceValue;
}
