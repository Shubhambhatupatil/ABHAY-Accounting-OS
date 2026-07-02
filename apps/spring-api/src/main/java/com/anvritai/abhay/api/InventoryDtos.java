package com.anvritai.abhay.api;

import com.anvritai.abhay.domain.inventory.InventoryAlertSeverity;
import com.anvritai.abhay.domain.inventory.InventoryValuationMethod;
import com.anvritai.abhay.domain.inventory.StockAdjustmentType;
import com.anvritai.abhay.domain.inventory.StockMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record UnitRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 20) String symbol,
            @Min(0) @Max(4) Integer decimalPlaces,
            Boolean active) {
    }
    public record UnitResponse(
            UUID id, String name, String symbol, int decimalPlaces, boolean active,
            Instant createdAt, Instant updatedAt) {
    }
    public record CategoryRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 500) String description,
            UUID parentId,
            Boolean active) {
    }
    public record CategoryResponse(
            UUID id, String name, String description, UUID parentId, boolean active,
            Instant createdAt, Instant updatedAt) {
    }
    public record WarehouseRequest(
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 40) String code,
            @Size(max = 1000) String address,
            Boolean primaryWarehouse,
            Boolean active) {
    }
    public record WarehouseResponse(
            UUID id, String name, String code, String address, boolean primaryWarehouse,
            boolean active, Instant createdAt, Instant updatedAt) {
    }
    public record InventorySettingsRequest(
            @NotNull InventoryValuationMethod valuationMethod,
            @NotNull Boolean allowNegativeStock) {
    }
    public record InventorySettingsResponse(
            InventoryValuationMethod valuationMethod, boolean allowNegativeStock) {
    }
    public record OpeningStockRequest(
            @NotNull UUID itemId,
            @NotNull UUID warehouseId,
            @NotNull LocalDate stockDate,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0000") BigDecimal unitCost,
            @Size(max = 100) String batchNumber) {
    }
    public record StockAdjustmentRequest(
            @NotNull UUID itemId,
            @NotNull UUID warehouseId,
            @NotNull LocalDate adjustmentDate,
            @NotNull StockAdjustmentType adjustmentType,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            @DecimalMin("0.0000") BigDecimal unitCost,
            @NotBlank @Size(max = 500) String reason) {
    }
    public record StockTransferRequest(
            @NotNull UUID itemId,
            @NotNull UUID fromWarehouseId,
            @NotNull UUID toWarehouseId,
            @NotNull LocalDate transferDate,
            @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
            @Size(max = 500) String narration) {
    }
    public record StockMovementResponse(
            UUID id, UUID itemId, String itemName, UUID warehouseId, String warehouseName,
            StockMovementType movementType, LocalDate movementDate, BigDecimal quantity,
            BigDecimal unitCost, BigDecimal totalValue, String referenceType, UUID referenceId,
            String narration, Instant createdAt) {
    }
    public record StockActionResponse(
            UUID id, List<StockMovementResponse> movements) {
    }
    public record StockSummaryRow(
            UUID itemId, String itemName, String sku, BigDecimal quantity,
            BigDecimal stockValue, BigDecimal averageCost, BigDecimal reorderLevel, boolean lowStock) {
    }
    public record StockSummaryResponse(
            List<StockSummaryRow> items, BigDecimal totalQuantity, BigDecimal totalStockValue) {
    }
    public record StockLedgerRow(
            UUID entryId, LocalDate date, UUID warehouseId, String warehouseName,
            StockMovementType movementType, String referenceType, UUID referenceId,
            BigDecimal quantityIn, BigDecimal quantityOut, BigDecimal unitCost,
            BigDecimal valueDelta, BigDecimal balanceQuantity, BigDecimal balanceValue) {
    }
    public record ItemStockLedgerResponse(
            UUID itemId, String itemName, List<StockLedgerRow> entries,
            BigDecimal closingQuantity, BigDecimal closingValue) {
    }
    public record WarehouseStockResponse(
            UUID warehouseId, String warehouseName, List<StockSummaryRow> items,
            BigDecimal totalQuantity, BigDecimal totalStockValue) {
    }
    public record StockValuationRow(
            UUID itemId, String itemName, InventoryValuationMethod valuationMethod,
            BigDecimal quantity, BigDecimal stockValue, BigDecimal unitCost) {
    }
    public record StockValuationResponse(
            InventoryValuationMethod valuationMethod, List<StockValuationRow> items,
            BigDecimal totalStockValue) {
    }
    public record InventoryAlertResponse(
            UUID id, UUID itemId, String itemName, UUID warehouseId, String warehouseName,
            String alertType, InventoryAlertSeverity severity, String message,
            BigDecimal currentQuantity, BigDecimal reorderLevel, Instant createdAt) {
    }
    public record InventoryDashboardResponse(
            long productItems, long warehouses, BigDecimal totalQuantity, BigDecimal stockValue,
            long lowStockItems, long negativeStockItems, List<InventoryAlertResponse> alerts) {
    }
}
