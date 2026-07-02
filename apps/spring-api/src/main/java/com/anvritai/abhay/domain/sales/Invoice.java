package com.anvritai.abhay.domain.sales;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.FinancialYear;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.gst.GstTreatment;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_id", nullable = false)
    private FinancialYear financialYear;
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false, length = 20)
    private InvoiceType invoiceType;
    @Column(name = "invoice_number", nullable = false, length = 80)
    private String invoiceNumber;
    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;
    @Column(name = "party_key", nullable = false)
    private UUID partyKey;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(name = "cgst_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal cgstTotal = BigDecimal.ZERO;
    @Column(name = "sgst_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal sgstTotal = BigDecimal.ZERO;
    @Column(name = "igst_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal igstTotal = BigDecimal.ZERO;
    @Column(name = "cess_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal cessTotal = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "gst_treatment", nullable = false, length = 30)
    private GstTreatment gstTreatment = GstTreatment.NORMAL;
    @Column(name = "place_of_supply", length = 2)
    private String placeOfSupply;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @Column(length = 2000)
    private String notes;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_voucher_id")
    private Voucher postedVoucher;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "posted_at")
    private Instant postedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber asc")
    private List<InvoiceItem> items = new ArrayList<>();

    public void addItem(InvoiceItem item) { item.setInvoice(this); item.setCompany(company); items.add(item); }
    public void clearItems() { items.clear(); }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public FinancialYear getFinancialYear() { return financialYear; }
    public void setFinancialYear(FinancialYear financialYear) { this.financialYear = financialYear; }
    public InvoiceType getInvoiceType() { return invoiceType; }
    public void setInvoiceType(InvoiceType invoiceType) { this.invoiceType = invoiceType; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    public UUID getPartyKey() { return partyKey; }
    public void setPartyKey(UUID partyKey) { this.partyKey = partyKey; }
    public InvoiceStatus getStatus() { return status; }
    public void setStatus(InvoiceStatus status) { this.status = status; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getCgstTotal() { return cgstTotal; }
    public void setCgstTotal(BigDecimal cgstTotal) { this.cgstTotal = cgstTotal; }
    public BigDecimal getSgstTotal() { return sgstTotal; }
    public void setSgstTotal(BigDecimal sgstTotal) { this.sgstTotal = sgstTotal; }
    public BigDecimal getIgstTotal() { return igstTotal; }
    public void setIgstTotal(BigDecimal igstTotal) { this.igstTotal = igstTotal; }
    public BigDecimal getCessTotal() { return cessTotal; }
    public void setCessTotal(BigDecimal cessTotal) { this.cessTotal = cessTotal; }
    public GstTreatment getGstTreatment() { return gstTreatment; }
    public void setGstTreatment(GstTreatment gstTreatment) { this.gstTreatment = gstTreatment; }
    public String getPlaceOfSupply() { return placeOfSupply; }
    public void setPlaceOfSupply(String placeOfSupply) { this.placeOfSupply = placeOfSupply; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Voucher getPostedVoucher() { return postedVoucher; }
    public void setPostedVoucher(Voucher postedVoucher) { this.postedVoucher = postedVoucher; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public User getApprovedBy() { return approvedBy; }
    public void setApprovedBy(User approvedBy) { this.approvedBy = approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public List<InvoiceItem> getItems() { return items; }
}
