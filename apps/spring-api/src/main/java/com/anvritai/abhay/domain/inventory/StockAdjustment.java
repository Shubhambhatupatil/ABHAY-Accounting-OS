package com.anvritai.abhay.domain.inventory;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
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
import java.time.LocalDate;

@Entity
@Table(name = "stock_adjustments")
public class StockAdjustment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private StockAdjustmentType adjustmentType;
    @Column(name = "adjustment_date", nullable = false)
    private LocalDate adjustmentDate;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;
    @Column(nullable = false, length = 500)
    private String reason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_movement_id", nullable = false)
    private StockMovement stockMovement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public void setCompany(Company value) { this.company = value; }
    public void setItem(Item value) { this.item = value; }
    public void setWarehouse(Warehouse value) { this.warehouse = value; }
    public void setAdjustmentType(StockAdjustmentType value) { this.adjustmentType = value; }
    public StockAdjustmentType getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentDate(LocalDate value) { this.adjustmentDate = value; }
    public void setQuantity(BigDecimal value) { this.quantity = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setReason(String value) { this.reason = value; }
    public void setStockMovement(StockMovement value) { this.stockMovement = value; }
    public StockMovement getStockMovement() { return stockMovement; }
    public void setCreatedBy(User value) { this.createdBy = value; }
}
