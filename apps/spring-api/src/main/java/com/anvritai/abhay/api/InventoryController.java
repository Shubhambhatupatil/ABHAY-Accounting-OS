package com.anvritai.abhay.api;

import com.anvritai.abhay.api.InventoryDtos.CategoryRequest;
import com.anvritai.abhay.api.InventoryDtos.CategoryResponse;
import com.anvritai.abhay.api.InventoryDtos.InventoryAlertResponse;
import com.anvritai.abhay.api.InventoryDtos.InventoryDashboardResponse;
import com.anvritai.abhay.api.InventoryDtos.InventorySettingsRequest;
import com.anvritai.abhay.api.InventoryDtos.InventorySettingsResponse;
import com.anvritai.abhay.api.InventoryDtos.ItemStockLedgerResponse;
import com.anvritai.abhay.api.InventoryDtos.OpeningStockRequest;
import com.anvritai.abhay.api.InventoryDtos.StockActionResponse;
import com.anvritai.abhay.api.InventoryDtos.StockAdjustmentRequest;
import com.anvritai.abhay.api.InventoryDtos.StockSummaryResponse;
import com.anvritai.abhay.api.InventoryDtos.StockTransferRequest;
import com.anvritai.abhay.api.InventoryDtos.StockValuationResponse;
import com.anvritai.abhay.api.InventoryDtos.UnitRequest;
import com.anvritai.abhay.api.InventoryDtos.UnitResponse;
import com.anvritai.abhay.api.InventoryDtos.WarehouseRequest;
import com.anvritai.abhay.api.InventoryDtos.WarehouseResponse;
import com.anvritai.abhay.api.InventoryDtos.WarehouseStockResponse;
import com.anvritai.abhay.security.UserPrincipal;
import com.anvritai.abhay.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/units")
    public List<UnitResponse> units(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.listUnits(principal.id(), companyId);
    }

    @PostMapping("/units")
    @ResponseStatus(HttpStatus.CREATED)
    public UnitResponse createUnit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody UnitRequest request) {
        return inventory.createUnit(principal.id(), companyId, request);
    }

    @PatchMapping("/units/{unitId}")
    public UnitResponse updateUnit(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID unitId, @Valid @RequestBody UnitRequest request) {
        return inventory.updateUnit(principal.id(), companyId, unitId, request);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.listCategories(principal.id(), companyId);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody CategoryRequest request) {
        return inventory.createCategory(principal.id(), companyId, request);
    }

    @PatchMapping("/categories/{categoryId}")
    public CategoryResponse updateCategory(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID categoryId, @Valid @RequestBody CategoryRequest request) {
        return inventory.updateCategory(principal.id(), companyId, categoryId, request);
    }

    @GetMapping("/warehouses")
    public List<WarehouseResponse> warehouses(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.listWarehouses(principal.id(), companyId);
    }

    @PostMapping("/warehouses")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseResponse createWarehouse(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody WarehouseRequest request) {
        return inventory.createWarehouse(principal.id(), companyId, request);
    }

    @PatchMapping("/warehouses/{warehouseId}")
    public WarehouseResponse updateWarehouse(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID warehouseId, @Valid @RequestBody WarehouseRequest request) {
        return inventory.updateWarehouse(principal.id(), companyId, warehouseId, request);
    }

    @GetMapping("/settings")
    public InventorySettingsResponse settings(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.settings(principal.id(), companyId);
    }

    @PatchMapping("/settings")
    public InventorySettingsResponse updateSettings(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody InventorySettingsRequest request) {
        return inventory.updateSettings(principal.id(), companyId, request);
    }

    @PostMapping("/opening-stock")
    @ResponseStatus(HttpStatus.CREATED)
    public StockActionResponse openingStock(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody OpeningStockRequest request) {
        return inventory.openingStock(principal.id(), companyId, request);
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockActionResponse adjustment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return inventory.adjustStock(principal.id(), companyId, request);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public StockActionResponse transfer(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @Valid @RequestBody StockTransferRequest request) {
        return inventory.transferStock(principal.id(), companyId, request);
    }

    @GetMapping("/stock-summary")
    public StockSummaryResponse stockSummary(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.stockSummary(principal.id(), companyId);
    }

    @GetMapping("/items/{itemId}/stock-ledger")
    public ItemStockLedgerResponse itemLedger(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID itemId) {
        return inventory.itemLedger(principal.id(), companyId, itemId);
    }

    @GetMapping("/warehouses/{warehouseId}/stock")
    public WarehouseStockResponse warehouseStock(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId,
            @PathVariable UUID warehouseId) {
        return inventory.warehouseStock(principal.id(), companyId, warehouseId);
    }

    @GetMapping("/valuation")
    public StockValuationResponse valuation(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.valuation(principal.id(), companyId);
    }

    @GetMapping("/alerts/low-stock")
    public List<InventoryAlertResponse> lowStockAlerts(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.lowStockAlerts(principal.id(), companyId);
    }

    @GetMapping("/dashboard")
    public InventoryDashboardResponse dashboard(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID companyId) {
        return inventory.dashboard(principal.id(), companyId);
    }
}
