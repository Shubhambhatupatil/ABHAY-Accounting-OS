package com.anvritai.abhay.domain.inventory;

import com.anvritai.abhay.domain.BaseEntity;
import com.anvritai.abhay.domain.Company;
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
@Table(name = "stock_batches")
public class StockBatch extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_movement_id", nullable = false)
    private StockMovement sourceMovement;
    @Column(name = "batch_number", nullable = false, length = 100)
    private String batchNumber;
    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;
    @Column(name = "original_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalQuantity;
    @Column(name = "available_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableQuantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    public void setCompany(Company value) { this.company = value; }
    public void setItem(Item value) { this.item = value; }
    public void setWarehouse(Warehouse value) { this.warehouse = value; }
    public void setSourceMovement(StockMovement value) { this.sourceMovement = value; }
    public void setBatchNumber(String value) { this.batchNumber = value; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate value) { this.receivedDate = value; }
    public BigDecimal getOriginalQuantity() { return originalQuantity; }
    public void setOriginalQuantity(BigDecimal value) { this.originalQuantity = value; }
    public BigDecimal getAvailableQuantity() { return availableQuantity; }
    public void setAvailableQuantity(BigDecimal value) { this.availableQuantity = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
}
