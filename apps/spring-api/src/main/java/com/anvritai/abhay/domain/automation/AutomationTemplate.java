package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name="automation_templates")
public class AutomationTemplate extends BaseEntity {
    @Column(nullable=false,unique=true,length=80) private String code;
    @Column(nullable=false,length=160) private String name;
    @Column(nullable=false,length=1000) private String description;
    @Column(name="rule_type",nullable=false,length=40) private String ruleType;
    @Column(name="default_schedule_type",nullable=false,length=30) private String defaultScheduleType;
    @Column(name="default_parameters_json",nullable=false,columnDefinition="text") private String defaultParametersJson;
    @Column(nullable=false) private boolean active;
    public String getCode(){return code;} public String getName(){return name;} public String getDescription(){return description;}
    public String getRuleType(){return ruleType;} public String getDefaultScheduleType(){return defaultScheduleType;}
    public String getDefaultParametersJson(){return defaultParametersJson;} public boolean isActive(){return active;}
}
