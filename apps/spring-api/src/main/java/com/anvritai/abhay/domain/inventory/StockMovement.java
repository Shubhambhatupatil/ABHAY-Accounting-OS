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
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
public class StockMovement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private StockMovementType movementType;
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;
    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;
    @Column(name = "total_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalValue;
    @Column(name = "reference_type", nullable = false, length = 40)
    private String referenceType;
    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;
    @Column(length = 500)
    private String narration;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversal_of_id")
    private StockMovement reversalOf;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    public Company getCompany() { return company; }
    public void setCompany(Company value) { this.company = value; }
    public Item getItem() { return item; }
    public void setItem(Item value) { this.item = value; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse value) { this.warehouse = value; }
    public StockMovementType getMovementType() { return movementType; }
    public void setMovementType(StockMovementType value) { this.movementType = value; }
    public LocalDate getMovementDate() { return movementDate; }
    public void setMovementDate(LocalDate value) { this.movementDate = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { this.quantity = value; }
    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal value) { this.unitCost = value; }
    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal value) { this.totalValue = value; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String value) { this.referenceType = value; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID value) { this.referenceId = value; }
    public String getNarration() { return narration; }
    public void setNarration(String value) { this.narration = value; }
    public StockMovement getReversalOf() { return reversalOf; }
    public void setReversalOf(StockMovement value) { this.reversalOf = value; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User value) { this.createdBy = value; }
}
