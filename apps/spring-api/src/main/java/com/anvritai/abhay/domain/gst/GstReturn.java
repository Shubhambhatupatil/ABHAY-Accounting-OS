package com.anvritai.abhay.domain.gst;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "gst_returns")
public class GstReturn extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_id", nullable = false)
    private FinancialYear financialYear;
    @Enumerated(EnumType.STRING)
    @Column(name = "return_type", nullable = false, length = 20)
    private GstReturnType returnType;
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GstReturnStatus status = GstReturnStatus.DRAFT;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generated_by", nullable = false)
    private User generatedBy;
    @OneToMany(mappedBy = "gstReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt asc")
    private List<GstReturnItem> items = new ArrayList<>();

    public void addItem(GstReturnItem item) { item.setGstReturn(this); item.setCompany(company); items.add(item); }
    public Company getCompany() { return company; }
    public void setCompany(Company value) { this.company = value; }
    public FinancialYear getFinancialYear() { return financialYear; }
    public void setFinancialYear(FinancialYear value) { this.financialYear = value; }
    public GstReturnType getReturnType() { return returnType; }
    public void setReturnType(GstReturnType value) { this.returnType = value; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate value) { this.periodStart = value; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate value) { this.periodEnd = value; }
    public GstReturnStatus getStatus() { return status; }
    public void setStatus(GstReturnStatus value) { this.status = value; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String value) { this.snapshotJson = value; }
    public User getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(User value) { this.generatedBy = value; }
    public List<GstReturnItem> getItems() { return items; }
}
