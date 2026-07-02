package com.anvritai.abhay.domain.sales;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.inventory.InventoryUnit;
import com.anvritai.abhay.domain.inventory.ItemCategory;
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
@Table(name = "items")
public class Item extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private ItemType type;
    @Column(length = 80)
    private String sku;
    @Column(name = "hsn_sac", length = 20)
    private String hsnSac;
    @Column(nullable = false, length = 30)
    private String unit;
    @Column(name = "sales_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal salesPrice = BigDecimal.ZERO;
    @Column(name = "purchase_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal purchasePrice = BigDecimal.ZERO;
    @Column(name = "gst_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "inventory_unit_id")
    private InventoryUnit inventoryUnit;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_category_id")
    private ItemCategory itemCategory;
    @Column(name = "reorder_level", nullable = false, precision = 19, scale = 4)
    private BigDecimal reorderLevel = BigDecimal.ZERO;
    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory;
    @Column(nullable = false)
    private boolean active = true;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getHsnSac() { return hsnSac; }
    public void setHsnSac(String hsnSac) { this.hsnSac = hsnSac; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getSalesPrice() { return salesPrice; }
    public void setSalesPrice(BigDecimal salesPrice) { this.salesPrice = salesPrice; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getGstRate() { return gstRate; }
    public void setGstRate(BigDecimal gstRate) { this.gstRate = gstRate; }
    public InventoryUnit getInventoryUnit() { return inventoryUnit; }
    public void setInventoryUnit(InventoryUnit value) { this.inventoryUnit = value; }
    public ItemCategory getItemCategory() { return itemCategory; }
    public void setItemCategory(ItemCategory value) { this.itemCategory = value; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal value) { this.reorderLevel = value; }
    public boolean isTrackInventory() { return trackInventory; }
    public void setTrackInventory(boolean value) { this.trackInventory = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
