package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.*;
import jakarta.persistence.*;

@Entity @Table(name="company_settings")
public class CompanySetting extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
    @Column(nullable=false,length=30) private String category;
    @Column(name="setting_key",nullable=false,length=120) private String settingKey;
    @Column(name="setting_value",nullable=false,length=4000) private String settingValue;
    @Column(name="value_type",nullable=false,length=20) private String valueType;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="updated_by") private User updatedBy;
    public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public String getCategory(){return category;} public void setCategory(String v){category=v;}
    public String getSettingKey(){return settingKey;} public void setSettingKey(String v){settingKey=v;}
    public String getSettingValue(){return settingValue;} public void setSettingValue(String v){settingValue=v;}
    public String getValueType(){return valueType;} public void setValueType(String v){valueType=v;}
    public User getUpdatedBy(){return updatedBy;} public void setUpdatedBy(User v){updatedBy=v;}
}
