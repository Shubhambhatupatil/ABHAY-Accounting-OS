package com.anvritai.abhay.domain;

import com.anvritai.abhay.domain.inventory.InventoryValuationMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(length = 15)
    private String gstin;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(length = 100)
    private String industry;

    @Enumerated(EnumType.STRING)
    @Column(name = "inventory_valuation_method", nullable = false, length = 30)
    private InventoryValuationMethod inventoryValuationMethod = InventoryValuationMethod.FIFO;

    @Column(name = "allow_negative_stock", nullable = false)
    private boolean allowNegativeStock;

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public void setTradeName(String tradeName) {
        this.tradeName = tradeName;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public InventoryValuationMethod getInventoryValuationMethod() { return inventoryValuationMethod; }
    public void setInventoryValuationMethod(InventoryValuationMethod value) { this.inventoryValuationMethod = value; }
    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public void setAllowNegativeStock(boolean value) { this.allowNegativeStock = value; }
}
