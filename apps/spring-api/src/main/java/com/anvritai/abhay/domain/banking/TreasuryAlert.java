package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "treasury_alerts")
public class TreasuryAlert extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @Column(name = "alert_type", nullable = false, length = 40) private String alertType;
    @Column(nullable = false, length = 20) private String severity;
    @Column(nullable = false, length = 500) private String message;
    @Column(precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false) private boolean resolved;
    public Company getCompany() { return company; }
    public void setCompany(Company v) { company = v; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String v) { alertType = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { severity = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { message = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean v) { resolved = v; }
}
