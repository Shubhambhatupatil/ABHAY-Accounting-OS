package com.anvritai.abhay.domain.inventory;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.sales.Item;
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
@Table(name = "inventory_alerts")
public class InventoryAlert extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;
    @Column(name = "alert_type", nullable = false, length = 40)
    private String alertType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryAlertSeverity severity;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(name = "current_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentQuantity;
    @Column(name = "reorder_level", nullable = false, precision = 19, scale = 4)
    private BigDecimal reorderLevel;
    @Column(nullable = false)
    private boolean resolved;

    public void setCompany(Company value) { this.company = value; }
    public Item getItem() { return item; }
    public void setItem(Item value) { this.item = value; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse value) { this.warehouse = value; }
    public String getAlertType() { return alertType; }
    public void setAlertType(String value) { this.alertType = value; }
    public InventoryAlertSeverity getSeverity() { return severity; }
    public void setSeverity(InventoryAlertSeverity value) { this.severity = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { this.message = value; }
    public BigDecimal getCurrentQuantity() { return currentQuantity; }
    public void setCurrentQuantity(BigDecimal value) { this.currentQuantity = value; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal value) { this.reorderLevel = value; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean value) { this.resolved = value; }
}
