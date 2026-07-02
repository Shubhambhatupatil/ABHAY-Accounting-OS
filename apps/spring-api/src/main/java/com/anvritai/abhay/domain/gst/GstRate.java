package com.anvritai.abhay.domain.gst;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "gst_rates")
public class GstRate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal rate;
    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cessRate = BigDecimal.ZERO;
    @Column(name = "system_rate", nullable = false)
    private boolean systemRate;
    @Column(name = "reverse_charge_allowed", nullable = false)
    private boolean reverseChargeAllowed = true;
    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getRate() { return rate; }
    public void setRate(BigDecimal rate) { this.rate = rate; }
    public BigDecimal getCessRate() { return cessRate; }
    public void setCessRate(BigDecimal cessRate) { this.cessRate = cessRate; }
    public boolean isSystemRate() { return systemRate; }
    public void setSystemRate(boolean systemRate) { this.systemRate = systemRate; }
    public boolean isReverseChargeAllowed() { return reverseChargeAllowed; }
    public void setReverseChargeAllowed(boolean value) { this.reverseChargeAllowed = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
