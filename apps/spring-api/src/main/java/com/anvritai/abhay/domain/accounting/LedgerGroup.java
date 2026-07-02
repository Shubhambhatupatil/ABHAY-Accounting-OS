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

@Entity
@Table(name = "ledger_groups")
public class LedgerGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_nature", nullable = false, length = 20)
    private AccountNature accountNature;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private LedgerGroup parent;

    @Column(name = "system_group", nullable = false)
    private boolean systemGroup;

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AccountNature getAccountNature() {
        return accountNature;
    }

    public void setAccountNature(AccountNature accountNature) {
        this.accountNature = accountNature;
    }

    public LedgerGroup getParent() {
        return parent;
    }

    public void setParent(LedgerGroup parent) {
        this.parent = parent;
    }

    public boolean isSystemGroup() {
        return systemGroup;
    }

    public void setSystemGroup(boolean systemGroup) {
        this.systemGroup = systemGroup;
    }
}
