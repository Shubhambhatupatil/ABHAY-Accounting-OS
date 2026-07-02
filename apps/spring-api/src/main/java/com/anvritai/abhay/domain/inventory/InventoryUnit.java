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
@Table(name = "inventory_units")
public class InventoryUnit extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 20)
    private String symbol;
    @Column(name = "decimal_places", nullable = false)
    private int decimalPlaces = 2;
    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company value) { this.company = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String value) { this.symbol = value; }
    public int getDecimalPlaces() { return decimalPlaces; }
    public void setDecimalPlaces(int value) { this.decimalPlaces = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { this.active = value; }
}
