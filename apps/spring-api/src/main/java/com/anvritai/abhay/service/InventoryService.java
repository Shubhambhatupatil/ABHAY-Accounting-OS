package com.anvritai.abhay.service;

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
import com.anvritai.abhay.api.InventoryDtos.StockLedgerRow;
import com.anvritai.abhay.api.InventoryDtos.StockMovementResponse;
import com.anvritai.abhay.api.InventoryDtos.StockSummaryResponse;
import com.anvritai.abhay.api.InventoryDtos.StockSummaryRow;
import com.anvritai.abhay.api.InventoryDtos.StockTransferRequest;
import com.anvritai.abhay.api.InventoryDtos.StockValuationResponse;
import com.anvritai.abhay.api.InventoryDtos.StockValuationRow;
import com.anvritai.abhay.api.InventoryDtos.UnitRequest;
import com.anvritai.abhay.api.InventoryDtos.UnitResponse;
import com.anvritai.abhay.api.InventoryDtos.WarehouseRequest;
import com.anvritai.abhay.api.InventoryDtos.WarehouseResponse;
import com.anvritai.abhay.api.InventoryDtos.WarehouseStockResponse;
import com.anvritai.abhay.domain.Company;
import com.anvritai.abhay.domain.RoleCode;
import com.anvritai.abhay.domain.User;
import com.anvritai.abhay.domain.inventory.InventoryAlert;
import com.anvritai.abhay.domain.inventory.InventoryAlertSeverity;
import com.anvritai.abhay.domain.inventory.InventoryUnit;
import com.anvritai.abhay.domain.inventory.InventoryValuationMethod;
import com.anvritai.abhay.domain.inventory.ItemCategory;
import com.anvritai.abhay.domain.inventory.StockAdjustment;
import com.anvritai.abhay.domain.inventory.StockAdjustmentType;
import com.anvritai.abhay.domain.inventory.StockBatch;
import com.anvritai.abhay.domain.inventory.StockLedgerEntry;
import com.anvritai.abhay.domain.inventory.StockMovement;
import com.anvritai.abhay.domain.inventory.StockMovementType;
import com.anvritai.abhay.domain.inventory.StockTransfer;
import com.anvritai.abhay.domain.inventory.StockValuationLayer;
import com.anvritai.abhay.domain.inventory.Warehouse;
import com.anvritai.abhay.domain.sales.Invoice;
import com.anvritai.abhay.domain.sales.InvoiceItem;
import com.anvritai.abhay.domain.sales.InvoiceType;
import com.anvritai.abhay.domain.sales.Item;
import com.anvritai.abhay.domain.sales.ItemType;
import com.anvritai.abhay.repository.CompanyRepository;
import com.anvritai.abhay.repository.UserRepository;
import com.anvritai.abhay.repository.inventory.InventoryAlertRepository;
import com.anvritai.abhay.repository.inventory.InventoryUnitRepository;
import com.anvritai.abhay.repository.inventory.ItemCategoryRepository;
import com.anvritai.abhay.repository.inventory.StockAdjustmentRepository;
import com.anvritai.abhay.repository.inventory.StockBatchRepository;
import com.anvritai.abhay.repository.inventory.StockLedgerEntryRepository;
import com.anvritai.abhay.repository.inventory.StockMovementRepository;
import com.anvritai.abhay.repository.inventory.StockTransferRepository;
import com.anvritai.abhay.repository.inventory.StockValuationLayerRepository;
import com.anvritai.abhay.repository.inventory.WarehouseRepository;
import com.anvritai.abhay.repository.sales.ItemRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");
    private static final BigDecimal ZERO_QUANTITY = new BigDecimal("0.0000");
    private static final RoleCode[] WRITE_ROLES = {RoleCode.OWNER, RoleCode.ADMIN, RoleCode.ACCOUNTANT};

    private final CompanyAccessService access;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final ItemRepository items;
    private final InventoryUnitRepository units;
    private final ItemCategoryRepository categories;
    private final WarehouseRepository warehouses;
    private final StockMovementRepository movements;
    private final StockLedgerEntryRepository ledger;
    private final StockBatchRepository batches;
    private final StockValuationLayerRepository layers;
    private final StockAdjustmentRepository adjustments;
    private final StockTransferRepository transfers;
    private final InventoryAlertRepository alerts;
    private final InventorySeedService seedService;
    private final AuditService audit;
    private final GstMemoryService memory;

    public InventoryService(
            CompanyAccessService access, CompanyRepository companies, UserRepository users,
            ItemRepository items, InventoryUnitRepository units, ItemCategoryRepository categories,
            WarehouseRepository warehouses, StockMovementRepository movements,
            StockLedgerEntryRepository ledger, StockBatchRepository batches,
            StockValuationLayerRepository layers, StockAdjustmentRepository adjustments,
            StockTransferRepository transfers, InventoryAlertRepository alerts,
            InventorySeedService seedService, AuditService audit, GstMemoryService memory) {
        this.access = access;
        this.companies = companies;
        this.users = users;
        this.items = items;
        this.units = units;
        this.categories = categories;
        this.warehouses = warehouses;
        this.movements = movements;
        this.ledger = ledger;
        this.batches = batches;
        this.layers = layers;
        this.adjustments = adjustments;
        this.transfers = transfers;
        this.alerts = alerts;
        this.seedService = seedService;
        this.audit = audit;
        this.memory = memory;
    }

    @Transactional
    public List<UnitResponse> listUnits(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        seedService.seedCompany(company(companyId));
        return units.findAllByCompanyIdOrderByName(companyId).stream().map(this::unitResponse).toList();
    }

    @Transactional
    public UnitResponse createUnit(UUID userId, UUID companyId, UnitRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (units.existsByCompanyIdAndSymbolIgnoreCase(companyId, request.symbol().trim())) {
            throw new ConflictException("An inventory unit with this symbol already exists.");
        }
        InventoryUnit unit = new InventoryUnit();
        unit.setCompany(company(companyId));
        applyUnit(unit, request);
        unit = units.save(unit);
        recordMaster(userId, unit.getCompany(), "INVENTORY_UNIT_CREATED", "INVENTORY_UNIT", unit.getId(),
                Map.of("symbol", unit.getSymbol()));
        return unitResponse(unit);
    }

    @Transactional
    public UnitResponse updateUnit(UUID userId, UUID companyId, UUID unitId, UnitRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        InventoryUnit unit = requireUnit(companyId, unitId);
        if (!unit.getSymbol().equalsIgnoreCase(request.symbol().trim())
                && units.existsByCompanyIdAndSymbolIgnoreCase(companyId, request.symbol().trim())) {
            throw new ConflictException("An inventory unit with this symbol already exists.");
        }
        applyUnit(unit, request);
        units.save(unit);
        recordMaster(userId, unit.getCompany(), "INVENTORY_UNIT_UPDATED", "INVENTORY_UNIT", unit.getId(),
                Map.of("symbol", unit.getSymbol(), "active", unit.isActive()));
        return unitResponse(unit);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return categories.findAllByCompanyIdOrderByName(companyId).stream().map(this::categoryResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(UUID userId, UUID companyId, CategoryRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (categories.existsByCompanyIdAndNameIgnoreCase(companyId, request.name().trim())) {
            throw new ConflictException("An item category with this name already exists.");
        }
        ItemCategory category = new ItemCategory();
        category.setCompany(company(companyId));
        applyCategory(category, companyId, request);
        category = categories.save(category);
        recordMaster(userId, category.getCompany(), "ITEM_CATEGORY_CREATED", "ITEM_CATEGORY", category.getId(),
                Map.of("name", category.getName()));
        return categoryResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(
            UUID userId, UUID companyId, UUID categoryId, CategoryRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        ItemCategory category = requireCategory(companyId, categoryId);
        if (!category.getName().equalsIgnoreCase(request.name().trim())
                && categories.existsByCompanyIdAndNameIgnoreCase(companyId, request.name().trim())) {
            throw new ConflictException("An item category with this name already exists.");
        }
        if (request.parentId() != null && request.parentId().equals(categoryId)) {
            throw new IllegalArgumentException("An item category cannot be its own parent.");
        }
        applyCategory(category, companyId, request);
        categories.save(category);
        recordMaster(userId, category.getCompany(), "ITEM_CATEGORY_UPDATED", "ITEM_CATEGORY", category.getId(),
                Map.of("name", category.getName(), "active", category.isActive()));
        return categoryResponse(category);
    }

    @Transactional
    public List<WarehouseResponse> listWarehouses(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        seedService.seedCompany(company(companyId));
        return warehouses.findAllByCompanyIdOrderByName(companyId).stream().map(this::warehouseResponse).toList();
    }

    @Transactional
    public WarehouseResponse createWarehouse(UUID userId, UUID companyId, WarehouseRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        validateWarehouseUnique(companyId, null, request.name(), request.code());
        Warehouse warehouse = new Warehouse();
        warehouse.setCompany(company(companyId));
        applyWarehouse(warehouse, request);
        if (warehouse.isPrimaryWarehouse()) clearOtherPrimary(companyId, null);
        warehouse = warehouses.save(warehouse);
        recordMaster(userId, warehouse.getCompany(), "WAREHOUSE_CREATED", "WAREHOUSE", warehouse.getId(),
                Map.of("name", warehouse.getName(), "code", warehouse.getCode()));
        return warehouseResponse(warehouse);
    }

    @Transactional
    public WarehouseResponse updateWarehouse(
            UUID userId, UUID companyId, UUID warehouseId, WarehouseRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        Warehouse warehouse = requireWarehouse(companyId, warehouseId);
        validateWarehouseUnique(companyId, warehouse, request.name(), request.code());
        applyWarehouse(warehouse, request);
        if (warehouse.isPrimaryWarehouse()) clearOtherPrimary(companyId, warehouseId);
        warehouses.save(warehouse);
        recordMaster(userId, warehouse.getCompany(), "WAREHOUSE_UPDATED", "WAREHOUSE", warehouse.getId(),
                Map.of("name", warehouse.getName(), "active", warehouse.isActive()));
        return warehouseResponse(warehouse);
    }

    @Transactional(readOnly = true)
    public InventorySettingsResponse settings(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        Company company = company(companyId);
        return new InventorySettingsResponse(company.getInventoryValuationMethod(), company.isAllowNegativeStock());
    }

    @Transactional
    public InventorySettingsResponse updateSettings(
            UUID userId, UUID companyId, InventorySettingsRequest request) {
        access.requireRole(companyId, userId, RoleCode.OWNER, RoleCode.ADMIN);
        Company company = company(companyId);
        company.setInventoryValuationMethod(request.valuationMethod());
        company.setAllowNegativeStock(request.allowNegativeStock());
        companies.save(company);
        recordMaster(userId, company, "INVENTORY_SETTINGS_UPDATED", "COMPANY", companyId,
                Map.of("valuationMethod", request.valuationMethod().name(),
                        "allowNegativeStock", request.allowNegativeStock()));
        return new InventorySettingsResponse(company.getInventoryValuationMethod(), company.isAllowNegativeStock());
    }

    @Transactional
    public StockActionResponse openingStock(UUID userId, UUID companyId, OpeningStockRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        UUID referenceId = UUID.randomUUID();
        StockMovement movement = addStock(userId, companyId, request.itemId(), request.warehouseId(),
                request.stockDate(), quantity(request.quantity()), cost(request.unitCost()), StockMovementType.OPENING,
                "OPENING_STOCK", referenceId, "Opening stock", blankToNull(request.batchNumber()), null);
        return new StockActionResponse(movement.getId(), List.of(movementResponse(movement)));
    }

    @Transactional
    public StockActionResponse adjustStock(UUID userId, UUID companyId, StockAdjustmentRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        StockAdjustment adjustment = new StockAdjustment();
        adjustment.setId(UUID.randomUUID());
        Item item = requireProduct(companyId, request.itemId());
        Warehouse warehouse = requireWarehouse(companyId, request.warehouseId());
        BigDecimal amount = quantity(request.quantity());
        StockMovement movement = request.adjustmentType() == StockAdjustmentType.INCREASE
                ? addStock(userId, companyId, item.getId(), warehouse.getId(), request.adjustmentDate(), amount,
                        cost(request.unitCost()), StockMovementType.ADJUSTMENT_IN, "STOCK_ADJUSTMENT",
                        adjustment.getId(), request.reason(), null, null)
                : removeStock(userId, companyId, item.getId(), warehouse.getId(), request.adjustmentDate(), amount,
                        StockMovementType.ADJUSTMENT_OUT, "STOCK_ADJUSTMENT", adjustment.getId(), request.reason(), null);
        adjustment.setCompany(item.getCompany());
        adjustment.setItem(item);
        adjustment.setWarehouse(warehouse);
        adjustment.setAdjustmentType(request.adjustmentType());
        adjustment.setAdjustmentDate(request.adjustmentDate());
        adjustment.setQuantity(amount);
        adjustment.setUnitCost(movement.getUnitCost());
        adjustment.setReason(request.reason().trim());
        adjustment.setStockMovement(movement);
        adjustment.setCreatedBy(user(userId));
        adjustments.save(adjustment);
        audit.record(item.getCompany(), user(userId), "STOCK_ADJUSTMENT_CREATED", "STOCK_ADJUSTMENT",
                adjustment.getId(), Map.of("type", request.adjustmentType().name(), "quantity", amount));
        return new StockActionResponse(adjustment.getId(), List.of(movementResponse(movement)));
    }

    @Transactional
    public StockActionResponse transferStock(UUID userId, UUID companyId, StockTransferRequest request) {
        access.requireRole(companyId, userId, WRITE_ROLES);
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new IllegalArgumentException("Stock transfer warehouses must be different.");
        }
        StockTransfer transfer = new StockTransfer();
        transfer.setId(UUID.randomUUID());
        Item item = requireProduct(companyId, request.itemId());
        Warehouse from = requireWarehouse(companyId, request.fromWarehouseId());
        Warehouse to = requireWarehouse(companyId, request.toWarehouseId());
        BigDecimal amount = quantity(request.quantity());
        StockMovement out = removeStock(userId, companyId, item.getId(), from.getId(), request.transferDate(), amount,
                StockMovementType.TRANSFER_OUT, "STOCK_TRANSFER", transfer.getId(), request.narration(), null);
        StockMovement in = addStock(userId, companyId, item.getId(), to.getId(), request.transferDate(), amount,
                out.getUnitCost(), StockMovementType.TRANSFER_IN, "STOCK_TRANSFER", transfer.getId(),
                request.narration(), null, null);
        transfer.setCompany(item.getCompany());
        transfer.setItem(item);
        transfer.setFromWarehouse(from);
        transfer.setToWarehouse(to);
        transfer.setTransferDate(request.transferDate());
        transfer.setQuantity(amount);
        transfer.setUnitCost(out.getUnitCost());
        transfer.setTotalValue(out.getTotalValue());
        transfer.setNarration(blankToNull(request.narration()));
        transfer.setOutMovement(out);
        transfer.setInMovement(in);
        transfer.setCreatedBy(user(userId));
        transfers.save(transfer);
        audit.record(item.getCompany(), user(userId), "STOCK_TRANSFER_CREATED", "STOCK_TRANSFER",
                transfer.getId(), Map.of("quantity", amount, "from", from.getCode(), "to", to.getCode()));
        return new StockActionResponse(transfer.getId(), List.of(movementResponse(out), movementResponse(in)));
    }

    @Transactional
    public void postInvoice(UUID userId, Invoice invoice) {
        UUID companyId = invoice.getCompany().getId();
        if (movements.existsByCompanyIdAndReferenceTypeAndReferenceId(companyId, "INVOICE", invoice.getId())) {
            throw new ConflictException("Inventory has already been posted for this invoice.");
        }
        Warehouse warehouse = primaryWarehouse(companyId);
        for (InvoiceItem line : invoice.getItems()) {
            if (line.getItem() == null || line.getItem().getType() == ItemType.SERVICE
                    || !line.getItem().isTrackInventory()) continue;
            if (invoice.getInvoiceType() == InvoiceType.PURCHASE) {
                addStock(userId, companyId, line.getItem().getId(), warehouse.getId(), invoice.getInvoiceDate(),
                        quantity(line.getQuantity()), cost(line.getUnitPrice()), StockMovementType.PURCHASE,
                        "INVOICE", invoice.getId(), "Purchase invoice " + invoice.getInvoiceNumber(), null, null);
            } else {
                removeStock(userId, companyId, line.getItem().getId(), warehouse.getId(), invoice.getInvoiceDate(),
                        quantity(line.getQuantity()), StockMovementType.SALE, "INVOICE", invoice.getId(),
                        "Sales invoice " + invoice.getInvoiceNumber(), null);
            }
        }
    }

    @Transactional
    public void reverseInvoice(UUID userId, Invoice invoice) {
        UUID companyId = invoice.getCompany().getId();
        if (movements.existsByCompanyIdAndReferenceTypeAndReferenceId(
                companyId, "INVOICE_REVERSAL", invoice.getId())) {
            throw new ConflictException("Inventory has already been reversed for this invoice.");
        }
        List<StockMovement> originals = movements
                .findAllByCompanyIdAndReferenceTypeAndReferenceIdOrderByCreatedAt(companyId, "INVOICE", invoice.getId());
        for (StockMovement original : originals) {
            if (isInbound(original.getMovementType())) {
                removeStock(userId, companyId, original.getItem().getId(), original.getWarehouse().getId(),
                        LocalDate.now(), original.getQuantity(), StockMovementType.REVERSAL_OUT,
                        "INVOICE_REVERSAL", invoice.getId(), "Reversal of " + invoice.getInvoiceNumber(), original);
            } else {
                addStock(userId, companyId, original.getItem().getId(), original.getWarehouse().getId(),
                        LocalDate.now(), original.getQuantity(), original.getUnitCost(), StockMovementType.REVERSAL_IN,
                        "INVOICE_REVERSAL", invoice.getId(), "Reversal of " + invoice.getInvoiceNumber(), null, original);
            }
        }
    }

    @Transactional(readOnly = true)
    public StockSummaryResponse stockSummary(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        List<StockSummaryRow> rows = summaryRows(companyId, null);
        return new StockSummaryResponse(rows, sumQuantity(rows), sumMoney(rows));
    }

    @Transactional(readOnly = true)
    public ItemStockLedgerResponse itemLedger(UUID userId, UUID companyId, UUID itemId) {
        access.requireMembership(companyId, userId);
        Item item = requireProduct(companyId, itemId);
        List<StockLedgerEntry> entries = ledger.findAllByCompanyIdAndItemIdOrderByEntryDateAscCreatedAtAsc(
                companyId, itemId);
        Map<UUID, StockLedgerEntry> latest = new HashMap<>();
        List<StockLedgerRow> rows = entries.stream().map(entry -> {
            latest.put(entry.getWarehouse().getId(), entry);
            boolean inbound = entry.getQuantityDelta().signum() > 0;
            return new StockLedgerRow(entry.getId(), entry.getEntryDate(), entry.getWarehouse().getId(),
                    entry.getWarehouse().getName(), entry.getStockMovement().getMovementType(),
                    entry.getStockMovement().getReferenceType(), entry.getStockMovement().getReferenceId(),
                    inbound ? entry.getQuantityDelta() : ZERO_QUANTITY,
                    inbound ? ZERO_QUANTITY : entry.getQuantityDelta().abs(), entry.getStockMovement().getUnitCost(),
                    entry.getValueDelta(), entry.getBalanceQuantity(), entry.getBalanceValue());
        }).toList();
        BigDecimal closingQuantity = latest.values().stream().map(StockLedgerEntry::getBalanceQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal closingValue = latest.values().stream().map(StockLedgerEntry::getBalanceValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ItemStockLedgerResponse(itemId, item.getName(), rows,
                quantity(closingQuantity), money(closingValue));
    }

    @Transactional(readOnly = true)
    public WarehouseStockResponse warehouseStock(UUID userId, UUID companyId, UUID warehouseId) {
        access.requireMembership(companyId, userId);
        Warehouse warehouse = requireWarehouse(companyId, warehouseId);
        List<StockSummaryRow> rows = summaryRows(companyId, warehouseId);
        return new WarehouseStockResponse(warehouseId, warehouse.getName(), rows, sumQuantity(rows), sumMoney(rows));
    }

    @Transactional(readOnly = true)
    public StockValuationResponse valuation(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        Company company = company(companyId);
        List<StockValuationRow> rows = summaryRows(companyId, null).stream().map(row -> new StockValuationRow(
                row.itemId(), row.itemName(), company.getInventoryValuationMethod(), row.quantity(), row.stockValue(),
                row.quantity().signum() == 0 ? ZERO_QUANTITY
                        : row.stockValue().divide(row.quantity(), 4, RoundingMode.HALF_UP))).toList();
        return new StockValuationResponse(company.getInventoryValuationMethod(), rows,
                money(rows.stream().map(StockValuationRow::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Transactional(readOnly = true)
    public List<InventoryAlertResponse> lowStockAlerts(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        return alerts.findAllByCompanyIdAndResolvedFalseOrderByCreatedAtDesc(companyId).stream()
                .map(this::alertResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryDashboardResponse dashboard(UUID userId, UUID companyId) {
        access.requireMembership(companyId, userId);
        List<StockSummaryRow> rows = summaryRows(companyId, null);
        List<InventoryAlertResponse> openAlerts = alerts
                .findAllByCompanyIdAndResolvedFalseOrderByCreatedAtDesc(companyId).stream()
                .map(this::alertResponse).toList();
        long products = items.findAllByCompanyIdOrderByName(companyId).stream()
                .filter(item -> item.getType() == ItemType.PRODUCT).count();
        long activeWarehouses = warehouses.findAllByCompanyIdOrderByName(companyId).stream()
                .filter(Warehouse::isActive).count();
        return new InventoryDashboardResponse(products, activeWarehouses, sumQuantity(rows), sumMoney(rows),
                rows.stream().filter(StockSummaryRow::lowStock).count(),
                rows.stream().filter(row -> row.quantity().signum() < 0).count(), openAlerts);
    }

    private StockMovement addStock(
            UUID userId, UUID companyId, UUID itemId, UUID warehouseId, LocalDate date,
            BigDecimal amount, BigDecimal unitCost, StockMovementType type, String referenceType,
            UUID referenceId, String narration, String batchNumber, StockMovement reversalOf) {
        Item item = lockProduct(companyId, itemId);
        Warehouse warehouse = requireActiveWarehouse(companyId, warehouseId);
        Balance before = balance(companyId, itemId, warehouseId);
        BigDecimal totalValue = money(amount.multiply(unitCost));
        StockMovement movement = movement(userId, item, warehouse, date, amount, unitCost, totalValue,
                type, referenceType, referenceId, narration, reversalOf);
        saveLedger(movement, amount, totalValue, before);
        StockBatch batch = new StockBatch();
        batch.setCompany(item.getCompany());
        batch.setItem(item);
        batch.setWarehouse(warehouse);
        batch.setSourceMovement(movement);
        batch.setBatchNumber(batchNumber == null ? "AUTO-" + movement.getId() : batchNumber);
        batch.setReceivedDate(date);
        batch.setOriginalQuantity(amount);
        batch.setAvailableQuantity(amount);
        batch.setUnitCost(unitCost);
        batch = batches.save(batch);
        StockValuationLayer layer = new StockValuationLayer();
        layer.setCompany(item.getCompany());
        layer.setItem(item);
        layer.setWarehouse(warehouse);
        layer.setStockBatch(batch);
        layer.setValuationMethod(item.getCompany().getInventoryValuationMethod());
        layer.setOriginalQuantity(amount);
        layer.setRemainingQuantity(amount);
        layer.setUnitCost(unitCost);
        layer.setReceivedDate(date);
        layers.save(layer);
        afterMovement(userId, movement);
        return movement;
    }

    private StockMovement removeStock(
            UUID userId, UUID companyId, UUID itemId, UUID warehouseId, LocalDate date,
            BigDecimal amount, StockMovementType type, String referenceType, UUID referenceId,
            String narration, StockMovement reversalOf) {
        Item item = lockProduct(companyId, itemId);
        Warehouse warehouse = requireActiveWarehouse(companyId, warehouseId);
        Balance before = balance(companyId, itemId, warehouseId);
        if (!item.getCompany().isAllowNegativeStock() && before.quantity().compareTo(amount) < 0) {
            throw new AccountingRuleException("Insufficient stock for " + item.getName()
                    + ". Available: " + before.quantity() + ", required: " + amount + ".");
        }
        BigDecimal totalValue = item.getCompany().getInventoryValuationMethod() == InventoryValuationMethod.FIFO
                ? consumeFifo(companyId, itemId, warehouseId, amount, item.getCompany().isAllowNegativeStock())
                : consumeWeightedAverage(companyId, itemId, warehouseId, amount, before,
                        item.getCompany().isAllowNegativeStock());
        BigDecimal unitCost = amount.signum() == 0 ? ZERO_QUANTITY
                : totalValue.divide(amount, 4, RoundingMode.HALF_UP);
        StockMovement movement = movement(userId, item, warehouse, date, amount, unitCost, totalValue,
                type, referenceType, referenceId, narration, reversalOf);
        saveLedger(movement, amount.negate(), totalValue.negate(), before);
        afterMovement(userId, movement);
        return movement;
    }

    private BigDecimal consumeFifo(
            UUID companyId, UUID itemId, UUID warehouseId, BigDecimal amount, boolean allowNegative) {
        BigDecimal remaining = amount;
        BigDecimal value = ZERO_MONEY;
        for (StockValuationLayer layer : layers.lockAvailableLayers(companyId, itemId, warehouseId)) {
            if (remaining.signum() <= 0) break;
            BigDecimal consumed = remaining.min(layer.getRemainingQuantity());
            layer.setRemainingQuantity(quantity(layer.getRemainingQuantity().subtract(consumed)));
            StockBatch batch = layer.getStockBatch();
            batch.setAvailableQuantity(quantity(batch.getAvailableQuantity().subtract(consumed)));
            batches.save(batch);
            layers.save(layer);
            value = value.add(consumed.multiply(layer.getUnitCost()));
            remaining = remaining.subtract(consumed);
        }
        if (remaining.signum() > 0 && !allowNegative) {
            throw new AccountingRuleException("FIFO valuation layers do not contain enough stock.");
        }
        return money(value);
    }

    private BigDecimal consumeWeightedAverage(
            UUID companyId, UUID itemId, UUID warehouseId, BigDecimal amount,
            Balance before, boolean allowNegative) {
        BigDecimal average = before.quantity().signum() <= 0 ? ZERO_QUANTITY
                : before.value().divide(before.quantity(), 4, RoundingMode.HALF_UP);
        BigDecimal remaining = amount;
        for (StockValuationLayer layer : layers.lockAvailableLayers(companyId, itemId, warehouseId)) {
            if (remaining.signum() <= 0) break;
            BigDecimal consumed = remaining.min(layer.getRemainingQuantity());
            layer.setRemainingQuantity(quantity(layer.getRemainingQuantity().subtract(consumed)));
            StockBatch batch = layer.getStockBatch();
            batch.setAvailableQuantity(quantity(batch.getAvailableQuantity().subtract(consumed)));
            batches.save(batch);
            layers.save(layer);
            remaining = remaining.subtract(consumed);
        }
        if (remaining.signum() > 0 && !allowNegative) {
            throw new AccountingRuleException("Weighted-average layers do not contain enough stock.");
        }
        return money(amount.multiply(average));
    }

    private StockMovement movement(
            UUID userId, Item item, Warehouse warehouse, LocalDate date, BigDecimal amount,
            BigDecimal unitCost, BigDecimal totalValue, StockMovementType type, String referenceType,
            UUID referenceId, String narration, StockMovement reversalOf) {
        StockMovement movement = new StockMovement();
        movement.setCompany(item.getCompany());
        movement.setItem(item);
        movement.setWarehouse(warehouse);
        movement.setMovementType(type);
        movement.setMovementDate(date);
        movement.setQuantity(amount);
        movement.setUnitCost(unitCost);
        movement.setTotalValue(totalValue);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setNarration(blankToNull(narration));
        movement.setReversalOf(reversalOf);
        movement.setCreatedBy(user(userId));
        return movements.save(movement);
    }

    private void saveLedger(StockMovement movement, BigDecimal quantityDelta, BigDecimal valueDelta, Balance before) {
        StockLedgerEntry entry = new StockLedgerEntry();
        entry.setCompany(movement.getCompany());
        entry.setItem(movement.getItem());
        entry.setWarehouse(movement.getWarehouse());
        entry.setStockMovement(movement);
        entry.setEntryDate(movement.getMovementDate());
        entry.setQuantityDelta(quantity(quantityDelta));
        entry.setValueDelta(money(valueDelta));
        entry.setBalanceQuantity(quantity(before.quantity().add(quantityDelta)));
        entry.setBalanceValue(money(before.value().add(valueDelta)));
        ledger.save(entry);
    }

    private void afterMovement(UUID userId, StockMovement movement) {
        Balance current = balance(movement.getCompany().getId(), movement.getItem().getId(),
                movement.getWarehouse().getId());
        refreshAlert(movement.getItem(), movement.getWarehouse(), current.quantity());
        Map<String, Object> details = Map.of(
                "movementType", movement.getMovementType().name(), "quantity", movement.getQuantity(),
                "unitCost", movement.getUnitCost(), "totalValue", movement.getTotalValue(),
                "warehouseId", movement.getWarehouse().getId(), "referenceType", movement.getReferenceType(),
                "itemId", movement.getItem().getId(), "itemName", movement.getItem().getName());
        User actor = user(userId);
        audit.record(movement.getCompany(), actor, "STOCK_MOVEMENT_POSTED", "STOCK_MOVEMENT",
                movement.getId(), details);
        memory.record(movement.getCompany(), "STOCK_MOVEMENT_POSTED", "STOCK_MOVEMENT", movement.getId(),
                "Inventory quantity and valuation changed from a posted business event.",
                new BigDecimal("1.0000"), details);
    }

    private void refreshAlert(Item item, Warehouse warehouse, BigDecimal currentQuantity) {
        var existing = alerts.findFirstByCompanyIdAndItemIdAndWarehouseIdAndResolvedFalse(
                item.getCompany().getId(), item.getId(), warehouse.getId());
        if (item.getReorderLevel().signum() <= 0 || currentQuantity.compareTo(item.getReorderLevel()) > 0) {
            existing.ifPresent(alert -> { alert.setResolved(true); alerts.save(alert); });
            return;
        }
        InventoryAlert alert = existing.orElseGet(InventoryAlert::new);
        alert.setCompany(item.getCompany());
        alert.setItem(item);
        alert.setWarehouse(warehouse);
        alert.setAlertType("LOW_STOCK");
        alert.setSeverity(currentQuantity.signum() <= 0 ? InventoryAlertSeverity.HIGH : InventoryAlertSeverity.MEDIUM);
        alert.setMessage(item.getName() + " is at or below its reorder level in " + warehouse.getName() + ".");
        alert.setCurrentQuantity(quantity(currentQuantity));
        alert.setReorderLevel(quantity(item.getReorderLevel()));
        alert.setResolved(false);
        alerts.save(alert);
    }

    private List<StockSummaryRow> summaryRows(UUID companyId, UUID warehouseFilter) {
        Map<String, StockLedgerEntry> latest = new LinkedHashMap<>();
        for (StockLedgerEntry entry : ledger.findAllByCompanyIdOrderByEntryDateAscCreatedAtAsc(companyId)) {
            if (warehouseFilter != null && !warehouseFilter.equals(entry.getWarehouse().getId())) continue;
            latest.put(entry.getItem().getId() + ":" + entry.getWarehouse().getId(), entry);
        }
        Map<UUID, Aggregate> totals = new LinkedHashMap<>();
        for (StockLedgerEntry entry : latest.values()) {
            Aggregate aggregate = totals.computeIfAbsent(entry.getItem().getId(), ignored -> new Aggregate(entry.getItem()));
            aggregate.quantity = aggregate.quantity.add(entry.getBalanceQuantity());
            aggregate.value = aggregate.value.add(entry.getBalanceValue());
        }
        return totals.values().stream().map(aggregate -> {
            BigDecimal qty = quantity(aggregate.quantity);
            BigDecimal value = money(aggregate.value);
            BigDecimal average = qty.signum() == 0 ? ZERO_QUANTITY
                    : value.divide(qty, 4, RoundingMode.HALF_UP);
            return new StockSummaryRow(aggregate.item.getId(), aggregate.item.getName(), aggregate.item.getSku(),
                    qty, value, average, quantity(aggregate.item.getReorderLevel()),
                    aggregate.item.getReorderLevel().signum() > 0
                            && qty.compareTo(aggregate.item.getReorderLevel()) <= 0);
        }).sorted(Comparator.comparing(StockSummaryRow::itemName)).toList();
    }

    private Balance balance(UUID companyId, UUID itemId, UUID warehouseId) {
        return ledger.findFirstByCompanyIdAndItemIdAndWarehouseIdOrderByCreatedAtDesc(companyId, itemId, warehouseId)
                .map(entry -> new Balance(entry.getBalanceQuantity(), entry.getBalanceValue()))
                .orElse(new Balance(ZERO_QUANTITY, ZERO_MONEY));
    }

    private void applyUnit(InventoryUnit unit, UnitRequest request) {
        unit.setName(request.name().trim());
        unit.setSymbol(request.symbol().trim().toUpperCase(Locale.ROOT));
        unit.setDecimalPlaces(request.decimalPlaces() == null ? 2 : request.decimalPlaces());
        unit.setActive(request.active() == null || request.active());
    }
    private void applyCategory(ItemCategory category, UUID companyId, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(blankToNull(request.description()));
        category.setParent(request.parentId() == null ? null : requireCategory(companyId, request.parentId()));
        category.setActive(request.active() == null || request.active());
    }
    private void applyWarehouse(Warehouse warehouse, WarehouseRequest request) {
        warehouse.setName(request.name().trim());
        warehouse.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        warehouse.setAddress(blankToNull(request.address()));
        warehouse.setPrimaryWarehouse(request.primaryWarehouse() != null && request.primaryWarehouse());
        warehouse.setActive(request.active() == null || request.active());
    }
    private void clearOtherPrimary(UUID companyId, UUID exceptId) {
        warehouses.findAllByCompanyIdOrderByName(companyId).stream()
                .filter(Warehouse::isPrimaryWarehouse)
                .filter(warehouse -> exceptId == null || !warehouse.getId().equals(exceptId))
                .forEach(warehouse -> { warehouse.setPrimaryWarehouse(false); warehouses.save(warehouse); });
    }
    private void validateWarehouseUnique(UUID companyId, Warehouse current, String name, String code) {
        if ((current == null || !current.getName().equalsIgnoreCase(name.trim()))
                && warehouses.existsByCompanyIdAndNameIgnoreCase(companyId, name.trim())) {
            throw new ConflictException("A warehouse with this name already exists.");
        }
        if ((current == null || !current.getCode().equalsIgnoreCase(code.trim()))
                && warehouses.existsByCompanyIdAndCodeIgnoreCase(companyId, code.trim())) {
            throw new ConflictException("A warehouse with this code already exists.");
        }
    }

    private void recordMaster(
            UUID userId, Company company, String action, String type, UUID id, Map<String, Object> details) {
        User actor = user(userId);
        audit.record(company, actor, action, type, id, details);
        memory.record(company, action, type, id, "Inventory master configuration changed.",
                new BigDecimal("1.0000"), details);
    }
    private UnitResponse unitResponse(InventoryUnit unit) {
        return new UnitResponse(unit.getId(), unit.getName(), unit.getSymbol(), unit.getDecimalPlaces(),
                unit.isActive(), unit.getCreatedAt(), unit.getUpdatedAt());
    }
    private CategoryResponse categoryResponse(ItemCategory category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
                category.getParent() == null ? null : category.getParent().getId(), category.isActive(),
                category.getCreatedAt(), category.getUpdatedAt());
    }
    private WarehouseResponse warehouseResponse(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getName(), warehouse.getCode(),
                warehouse.getAddress(), warehouse.isPrimaryWarehouse(), warehouse.isActive(),
                warehouse.getCreatedAt(), warehouse.getUpdatedAt());
    }
    private StockMovementResponse movementResponse(StockMovement movement) {
        return new StockMovementResponse(movement.getId(), movement.getItem().getId(), movement.getItem().getName(),
                movement.getWarehouse().getId(), movement.getWarehouse().getName(), movement.getMovementType(),
                movement.getMovementDate(), movement.getQuantity(), movement.getUnitCost(), movement.getTotalValue(),
                movement.getReferenceType(), movement.getReferenceId(), movement.getNarration(), movement.getCreatedAt());
    }
    private InventoryAlertResponse alertResponse(InventoryAlert alert) {
        return new InventoryAlertResponse(alert.getId(), alert.getItem().getId(), alert.getItem().getName(),
                alert.getWarehouse() == null ? null : alert.getWarehouse().getId(),
                alert.getWarehouse() == null ? null : alert.getWarehouse().getName(), alert.getAlertType(),
                alert.getSeverity(), alert.getMessage(), alert.getCurrentQuantity(), alert.getReorderLevel(),
                alert.getCreatedAt());
    }
    private BigDecimal sumQuantity(List<StockSummaryRow> rows) {
        return quantity(rows.stream().map(StockSummaryRow::quantity).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private BigDecimal sumMoney(List<StockSummaryRow> rows) {
        return money(rows.stream().map(StockSummaryRow::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    private Company company(UUID companyId) {
        return companies.findById(companyId).orElseThrow(() -> new NotFoundException("Company not found."));
    }
    private User user(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("Account not found."));
    }
    private InventoryUnit requireUnit(UUID companyId, UUID id) {
        return units.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Inventory unit not found."));
    }
    private ItemCategory requireCategory(UUID companyId, UUID id) {
        return categories.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Item category not found."));
    }
    private Warehouse requireWarehouse(UUID companyId, UUID id) {
        return warehouses.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Warehouse not found."));
    }
    private Warehouse requireActiveWarehouse(UUID companyId, UUID id) {
        Warehouse warehouse = requireWarehouse(companyId, id);
        if (!warehouse.isActive()) throw new AccountingRuleException("Inactive warehouses cannot receive movements.");
        return warehouse;
    }
    private Warehouse primaryWarehouse(UUID companyId) {
        seedService.seedCompany(company(companyId));
        return warehouses.findFirstByCompanyIdAndPrimaryWarehouseTrueAndActiveTrue(companyId)
                .orElseThrow(() -> new AccountingRuleException("No active primary warehouse exists."));
    }
    private Item requireProduct(UUID companyId, UUID id) {
        Item item = items.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Item not found."));
        if (item.getType() != ItemType.PRODUCT || !item.isTrackInventory()) {
            throw new AccountingRuleException("Stock movements are allowed only for product items.");
        }
        return item;
    }
    private Item lockProduct(UUID companyId, UUID id) {
        Item item = items.lockByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Item not found."));
        if (item.getType() != ItemType.PRODUCT || !item.isTrackInventory()) {
            throw new AccountingRuleException("Stock movements are allowed only for product items.");
        }
        return item;
    }
    private boolean isInbound(StockMovementType type) {
        return type == StockMovementType.OPENING || type == StockMovementType.PURCHASE
                || type == StockMovementType.ADJUSTMENT_IN || type == StockMovementType.TRANSFER_IN
                || type == StockMovementType.REVERSAL_IN;
    }
    private BigDecimal quantity(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }
    private BigDecimal cost(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
    }
    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private record Balance(BigDecimal quantity, BigDecimal value) { }
    private static final class Aggregate {
        private final Item item;
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal value = BigDecimal.ZERO;
        private Aggregate(Item item) { this.item = item; }
    }
}
