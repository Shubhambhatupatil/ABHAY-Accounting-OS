package com.anvritai.abhay.domain.inventory;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.sales.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "stock_transfers")
public class StockTransfer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "from_warehouse_id", nullable = false)
    private Warehouse fromWarehouse;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "to_warehouse_id", nullable = false)
    private Warehouse toWarehouse;
    @Column(name = "transfer_date", nullable = false)
    private LocalDate transferDate;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;
    @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;
    @Column(length = 500)
    private String narration;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "out_movement_id", nullable = false)
    private StockMovement outMovement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "in_movement_id", nullable = false)
    private StockMovement inMovement;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public void setCompany(Company value) { this.company = value; }
    public void setItem(Item value) { this.item = value; }
    public void setFromWarehouse(Warehouse value) { this.fromWarehouse = value; }
    public Warehouse getFromWarehouse() { return fromWarehouse; }
    public void setToWarehouse(Warehouse value) { this.toWarehouse = value; }
    public Warehouse getToWarehouse() { return toWarehouse; }
    public void setTransferDate(LocalDate value) { this.transferDate = value; }
    public void setQuantity(BigDecimal value) { this.quantity = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setTotalValue(BigDecimal value) { this.totalValue = value; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setNarration(String value) { this.narration = value; }
    public void setOutMovement(StockMovement value) { this.outMovement = value; }
    public void setInMovement(StockMovement value) { this.inMovement = value; }
    public void setCreatedBy(User value) { this.createdBy = value; }
}
