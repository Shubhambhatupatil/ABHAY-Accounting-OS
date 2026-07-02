package com.anvritai.abhay.domain.inventory;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "warehouses")
public class Warehouse extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(length = 1000)
    private String address;
    @Column(name = "primary_warehouse", nullable = false)
    private boolean primaryWarehouse;
    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company value) { this.company = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getCode() { return code; }
    public void setCode(String value) { this.code = value; }
    public String getAddress() { return address; }
    public void setAddress(String value) { this.address = value; }
    public boolean isPrimaryWarehouse() { return primaryWarehouse; }
    public void setPrimaryWarehouse(boolean value) { this.primaryWarehouse = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
