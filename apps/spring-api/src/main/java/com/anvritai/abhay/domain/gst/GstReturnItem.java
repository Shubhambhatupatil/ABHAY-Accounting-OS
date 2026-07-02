package com.anvritai.abhay.domain.gst;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.sales.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "gst_return_items")
public class GstReturnItem extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gst_return_id", nullable = false)
    private GstReturn gstReturn;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;
    @Column(name = "section_code", nullable = false, length = 30)
    private String sectionCode;
    @Column(name = "taxable_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;
    @Column(name = "cgst_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal cgstAmount = BigDecimal.ZERO;
    @Column(name = "sgst_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal sgstAmount = BigDecimal.ZERO;
    @Column(name = "igst_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal igstAmount = BigDecimal.ZERO;
    @Column(name = "cess_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal cessAmount = BigDecimal.ZERO;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    public void setCompany(Company value) { this.company = value; }
    public void setGstReturn(GstReturn value) { this.gstReturn = value; }
    public void setInvoice(Invoice value) { this.invoice = value; }
    public Invoice getInvoice() { return invoice; }
    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String value) { this.sectionCode = value; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal value) { this.taxableAmount = value; }
    public BigDecimal getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(BigDecimal value) { this.cgstAmount = value; }
    public BigDecimal getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(BigDecimal value) { this.sgstAmount = value; }
    public BigDecimal getIgstAmount() { return igstAmount; }
    public void setIgstAmount(BigDecimal value) { this.igstAmount = value; }
    public BigDecimal getCessAmount() { return cessAmount; }
    public void setCessAmount(BigDecimal value) { this.cessAmount = value; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal value) { this.totalAmount = value; }
}
