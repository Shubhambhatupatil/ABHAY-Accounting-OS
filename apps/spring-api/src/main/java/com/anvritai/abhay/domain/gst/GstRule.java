package com.anvritai.abhay.domain.gst;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "gst_rules")
public class GstRule extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false, length = 160)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "gst_treatment", nullable = false, length = 30)
    private GstTreatment gstTreatment;
    @Column(name = "hsn_sac_prefix", length = 20)
    private String hsnSacPrefix;
    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate;
    @Column(name = "cess_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal cessRate = BigDecimal.ZERO;
    @Column(name = "reverse_charge", nullable = false)
    private boolean reverseCharge;
    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public GstTreatment getGstTreatment() { return gstTreatment; }
    public void setGstTreatment(GstTreatment value) { this.gstTreatment = value; }
    public String getHsnSacPrefix() { return hsnSacPrefix; }
    public void setHsnSacPrefix(String value) { this.hsnSacPrefix = value; }
    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal value) { this.gstRate = value; }
    public BigDecimal getCessRate() { return cessRate; }
    public void setCessRate(BigDecimal value) { this.cessRate = value; }
    public boolean isReverseCharge() { return reverseCharge; }
    public void setReverseCharge(boolean value) { this.reverseCharge = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
