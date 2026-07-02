package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "bank_transactions")
public class BankTransaction extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.EAGER, optional = false) @JoinColumn(name = "bank_account_id") private BankAccount bankAccount;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "statement_import_id") private BankStatementImport statementImport;
    @Column(name = "transaction_date", nullable = false) private LocalDate transactionDate;
    @Column(nullable = false, length = 1000) private String description;
    @Column(length = 160) private String reference;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal debit = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal credit = BigDecimal.ZERO;
    @Column(precision = 19, scale = 2) private BigDecimal balance;
    @Column(length = 200) private String counterparty;
    @Column(name = "raw_hash", nullable = false, length = 64) private String rawHash;
    @Enumerated(EnumType.STRING) @Column(name = "reconciliation_status", nullable = false, length = 20)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNMATCHED;
    public Company getCompany() { return company; }
    public void setCompany(Company value) { company = value; }
    public BankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(BankAccount value) { bankAccount = value; }
    public BankStatementImport getStatementImport() { return statementImport; }
    public void setStatementImport(BankStatementImport value) { statementImport = value; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate value) { transactionDate = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public String getReference() { return reference; }
    public void setReference(String value) { reference = value; }
    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal value) { debit = value; }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal value) { credit = value; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal value) { balance = value; }
    public String getCounterparty() { return counterparty; }
    public void setCounterparty(String value) { counterparty = value; }
    public String getRawHash() { return rawHash; }
    public void setRawHash(String value) { rawHash = value; }
    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(ReconciliationStatus value) { reconciliationStatus = value; }
    public BigDecimal amount() { return debit.signum() > 0 ? debit : credit; }
}
