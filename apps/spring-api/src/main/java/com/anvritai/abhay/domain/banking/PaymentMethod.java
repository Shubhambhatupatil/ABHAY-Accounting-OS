package com.anvritai.abhay.domain.banking;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.*;

@Entity
@Table(name = "payment_methods")
public class PaymentMethod extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id") private Company company;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "method_type", nullable = false, length = 30) private String methodType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "bank_account_id") private BankAccount bankAccount;
    @Column(nullable = false) private boolean active = true;
    public Company getCompany() { return company; }
    public void setCompany(Company v) { company = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getMethodType() { return methodType; }
    public void setMethodType(String v) { methodType = v; }
    public BankAccount getBankAccount() { return bankAccount; }
    public void setBankAccount(BankAccount v) { bankAccount = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { active = v; }
}
