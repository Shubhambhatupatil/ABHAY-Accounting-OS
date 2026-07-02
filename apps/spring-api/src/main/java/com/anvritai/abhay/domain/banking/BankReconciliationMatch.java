package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.accounting.Voucher;
import com.anvritai.abhay.domain.sales.InvoicePayment;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bank_reconciliation_matches")
public class BankReconciliationMatch extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bank_transaction_id") private BankTransaction bankTransaction;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "voucher_id") private Voucher voucher;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "invoice_payment_id") private InvoicePayment invoicePayment;
    @Column(name = "match_type", nullable = false, length = 30) private String matchType;
    @Column(nullable = false, precision = 5, scale = 4) private BigDecimal confidence;
    @Column(nullable = false) private boolean active = true;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "confirmed_by") private User confirmedBy;
    @Column(name = "confirmed_at", nullable = false) private Instant confirmedAt;
    @Column(name = "unmatched_at") private Instant unmatchedAt;
    public Company getCompany() { return company; }
    public void setCompany(Company v) { company = v; }
    public BankTransaction getBankTransaction() { return bankTransaction; }
    public void setBankTransaction(BankTransaction v) { bankTransaction = v; }
    public Voucher getVoucher() { return voucher; }
    public void setVoucher(Voucher v) { voucher = v; }
    public InvoicePayment getInvoicePayment() { return invoicePayment; }
    public void setInvoicePayment(InvoicePayment v) { invoicePayment = v; }
    public String getMatchType() { return matchType; }
    public void setMatchType(String v) { matchType = v; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal v) { confidence = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
    public User getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(User v) { confirmedBy = v; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant v) { confirmedAt = v; }
    public Instant getUnmatchedAt() { return unmatchedAt; }
    public void setUnmatchedAt(Instant v) { unmatchedAt = v; }
}
