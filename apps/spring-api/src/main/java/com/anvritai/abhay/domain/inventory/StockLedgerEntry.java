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
@Table(name = "stock_ledger_entries")
public class StockLedgerEntry extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "stock_movement_id", nullable = false)
    private StockMovement stockMovement;
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;
    @Column(name = "quantity_delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityDelta;
    @Column(name = "value_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal valueDelta;
    @Column(name = "balance_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceQuantity;
    @Column(name = "balance_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceValue;

    public void setCompany(Company value) { this.company = value; }
    public Item getItem() { return item; }
    public void setItem(Item value) { this.item = value; }
    public Warehouse getWarehouse() { return warehouse; }
    public void setWarehouse(Warehouse value) { this.warehouse = value; }
    public StockMovement getStockMovement() { return stockMovement; }
    public void setStockMovement(StockMovement value) { this.stockMovement = value; }
    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate value) { this.entryDate = value; }
    public BigDecimal getQuantityDelta() { return quantityDelta; }
    public void setQuantityDelta(BigDecimal value) { this.quantityDelta = value; }
    public BigDecimal getValueDelta() { return valueDelta; }
    public void setValueDelta(BigDecimal value) { this.valueDelta = value; }
    public BigDecimal getBalanceQuantity() { return balanceQuantity; }
    public void setBalanceQuantity(BigDecimal value) { this.balanceQuantity = value; }
    public BigDecimal getBalanceValue() { return balanceValue; }
    public void setBalanceValue(BigDecimal value) { this.balanceValue = value; }
}
