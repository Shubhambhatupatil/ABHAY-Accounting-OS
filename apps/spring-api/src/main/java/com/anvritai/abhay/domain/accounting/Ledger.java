package com.anvritai.abhay.domain.accounting;

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
@Table(name = "ledgers")
public class Ledger extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "ledger_group_id", nullable = false)
    private LedgerGroup ledgerGroup;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 40)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false, length = 10)
    private NormalBalance normalBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_type", nullable = false, length = 20)
    private LedgerType ledgerType = LedgerType.GENERAL;

    @Column(name = "opening_debit", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingDebit = BigDecimal.ZERO;

    @Column(name = "opening_credit", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingCredit = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public LedgerGroup getLedgerGroup() {
        return ledgerGroup;
    }

    public void setLedgerGroup(LedgerGroup ledgerGroup) {
        this.ledgerGroup = ledgerGroup;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public NormalBalance getNormalBalance() {
        return normalBalance;
    }

    public void setNormalBalance(NormalBalance normalBalance) {
        this.normalBalance = normalBalance;
    }

    public LedgerType getLedgerType() {
        return ledgerType;
    }

    public void setLedgerType(LedgerType ledgerType) {
        this.ledgerType = ledgerType;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
