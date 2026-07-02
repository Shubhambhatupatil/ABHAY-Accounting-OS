package com.anvritai.abhay.domain.automation;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.*;

@Entity @Table(name="automation_logs")
public class AutomationLog extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="company_id") private Company company;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="automation_run_id") private AutomationRun run;
    @Column(nullable=false,length=20) private String level;
    @Column(nullable=false,length=1000) private String message;
    @Column(name="details_json",nullable=false,columnDefinition="text") private String detailsJson;
    public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public AutomationRun getRun(){return run;} public void setRun(AutomationRun v){run=v;}
    public String getLevel(){return level;} public void setLevel(String v){level=v;}
    public String getMessage(){return message;} public void setMessage(String v){message=v;}
    public String getDetailsJson(){return detailsJson;} public void setDetailsJson(String v){detailsJson=v;}
}
