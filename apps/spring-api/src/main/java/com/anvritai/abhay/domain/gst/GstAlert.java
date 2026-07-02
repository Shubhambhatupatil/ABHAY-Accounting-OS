package com.anvritai.abhay.domain.gst;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.sales.Invoice;
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
@Table(name = "gst_alerts")
public class GstAlert extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GstAlertSeverity severity;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(nullable = false, length = 1000)
    private String reason;
    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;
    @Column(nullable = false)
    private boolean resolved;

    public Company getCompany() { return company; }
    public void setCompany(Company value) { this.company = value; }
    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice value) { this.invoice = value; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String value) { this.alertType = value; }
    public GstAlertSeverity getSeverity() { return severity; }
    public void setSeverity(GstAlertSeverity value) { this.severity = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { this.message = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal value) { this.confidence = value; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean value) { this.resolved = value; }
}
