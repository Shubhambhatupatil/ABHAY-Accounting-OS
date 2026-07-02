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
import java.time.LocalDate;

@Entity
@Table(name = "stock_valuation_layers")
public class StockValuationLayer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_batch_id", nullable = false)
    private StockBatch stockBatch;
    @Enumerated(EnumType.STRING)
    @Column(name = "valuation_method", nullable = false, length = 30)
    private InventoryValuationMethod valuationMethod;
    @Column(name = "original_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalQuantity;
    @Column(name = "remaining_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal remainingQuantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;
    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    public void setCompany(Company value) { this.company = value; }
    public void setItem(Item value) { this.item = value; }
    public void setWarehouse(Warehouse value) { this.warehouse = value; }
    public StockBatch getStockBatch() { return stockBatch; }
    public void setStockBatch(StockBatch value) { this.stockBatch = value; }
    public void setValuationMethod(InventoryValuationMethod value) { this.valuationMethod = value; }
    public BigDecimal getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(BigDecimal value) { this.originalQuantity = value; }
    public BigDecimal getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(BigDecimal value) { this.remainingQuantity = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate value) { this.receivedDate = value; }
}
