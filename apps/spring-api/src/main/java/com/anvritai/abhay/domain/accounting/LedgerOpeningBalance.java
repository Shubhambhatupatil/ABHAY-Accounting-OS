package com.anvritai.abhay.domain.accounting;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.FinancialYear;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "ledger_opening_balances")
public class LedgerOpeningBalance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_year_id", nullable = false)
    private FinancialYear financialYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_id", nullable = false)
    private Ledger ledger;

    @Column(name = "opening_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingDebit = BigDecimal.ZERO;

    @Column(name = "opening_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingCredit = BigDecimal.ZERO;

    public void setCompany(Company company) {
        this.company = company;
    }

    public void setFinancialYear(FinancialYear financialYear) {
        this.financialYear = financialYear;
    }

    public Ledger getLedger() {
        return ledger;
    }

    public void setLedger(Ledger ledger) {
        this.ledger = ledger;
    }

    public BigDecimal getOpeningDebit() {
        return openingDebit;
    }

    public void setOpeningDebit(BigDecimal openingDebit) {
        this.openingDebit = openingDebit;
    }

    public BigDecimal getOpeningCredit() {
        return openingCredit;
    }

    public void setOpeningCredit(BigDecimal openingCredit) {
        this.openingCredit = openingCredit;
    }
}
