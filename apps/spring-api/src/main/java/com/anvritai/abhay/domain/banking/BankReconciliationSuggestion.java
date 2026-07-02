package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.sales.InvoicePayment;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "bank_reconciliation_suggestions")
public class BankReconciliationSuggestion extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "bank_transaction_id") private BankTransaction bankTransaction;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "voucher_id") private Voucher voucher;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "invoice_payment_id") private InvoicePayment invoicePayment;
    @Enumerated(EnumType.STRING) @Column(name = "target_type", nullable = false, length = 30) private ReconciliationTargetType targetType;
    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @Column(nullable = false, length = 500) private String reason;
    @Column(nullable = false) private boolean active = true;
    public Company getCompany() { return company; }
    public void setCompany(Company v) { company = v; }
    public BankTransaction getBankTransaction() { return bankTransaction; }
    public void setBankTransaction(BankTransaction v) { bankTransaction = v; }
    public Voucher getVoucher() { return voucher; }
    public void setVoucher(Voucher v) { voucher = v; }
    public InvoicePayment getInvoicePayment() { return invoicePayment; }
    public void setInvoicePayment(InvoicePayment v) { invoicePayment = v; }
    public ReconciliationTargetType getTargetType() { return targetType; }
    public void setTargetType(ReconciliationTargetType v) { targetType = v; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal v) { confidence = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { reason = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
}
