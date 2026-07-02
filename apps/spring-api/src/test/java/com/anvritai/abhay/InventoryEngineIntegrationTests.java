package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anvritai.abhay.repository.accounting.AiMemoryEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryEngineIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AiMemoryEventRepository memoryEvents;

    @Test
    void unitsAreSeededAndSupportCreateAndUpdate() throws Exception {
        Workspace workspace = workspace("inventory-unit");
        mockMvc.perform(get(inventory(workspace, "/units")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].symbol", hasItem("NOS")));
        MvcResult created = mockMvc.perform(post(inventory(workspace, "/units"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Kilograms", "symbol", "KG", "decimalPlaces", 3))))
                .andExpect(status().isCreated()).andReturn();
        mockMvc.perform(patch(inventory(workspace, "/units/" + id(created)))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Kilogram", "symbol", "KG", "decimalPlaces", 2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decimalPlaces").value(2));
    }

    @Test
    void itemCategoriesSupportParentAndUpdate() throws Exception {
        Workspace workspace = workspace("inventory-category");
        UUID parent = createCategory(workspace, "Electronics", null);
        UUID child = createCategory(workspace, "Mobile Devices", parent);
        mockMvc.perform(patch(inventory(workspace, "/categories/" + child))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Smartphones", "description", "Mobile inventory",
                                "parentId", parent.toString(), "active", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.parentId").value(parent.toString()))
                .andExpect(jsonPath("$.name").value("Smartphones"));
    }

    @Test
    void warehousesAreSeededAndSupportCreateAndUpdate() throws Exception {
        Workspace workspace = workspace("inventory-warehouse");
        mockMvc.perform(get(inventory(workspace, "/warehouses")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].code", hasItem("MAIN")));
        UUID warehouse = createWarehouse(workspace, "Pune Warehouse", "PUNE");
        mockMvc.perform(patch(inventory(workspace, "/warehouses/" + warehouse))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Pune Central", "code", "PUNE", "address", "Pune",
                                "primaryWarehouse", false, "active", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Pune Central"));
    }

    @Test
    void productItemStoresInventoryMetadata() throws Exception {
        Workspace workspace = workspace("inventory-product");
        UUID unit = unitId(workspace, "NOS");
        UUID category = createCategory(workspace, "Finished Goods", null);
        UUID item = createItem(workspace, "Finished Product", "PRODUCT", "PROD-1", unit, category, "5.0000");
        mockMvc.perform(get(company(workspace, "/items/" + item)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trackInventory").value(true))
                .andExpect(jsonPath("$.inventoryUnitId").value(unit.toString()))
                .andExpect(jsonPath("$.itemCategoryId").value(category.toString()))
                .andExpect(jsonPath("$.reorderLevel").value(5.0000));
    }

    @Test
    void openingStockCreatesLedgerAuditAndMemory() throws Exception {
        Workspace workspace = workspace("inventory-opening");
        UUID item = product(workspace, "Opening Product", "OPEN-1", "0");
        UUID warehouse = mainWarehouse(workspace);
        MvcResult result = opening(workspace, item, warehouse, "10", "100");
        UUID movementId = UUID.fromString(responseJson(result).get("movements").get(0).get("id").asText());
        mockMvc.perform(get(inventory(workspace, "/stock-summary")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(10.0000))
                .andExpect(jsonPath("$.items[0].stockValue").value(1000.00));
        if (memoryEvents.countByCompanyIdAndEntityId(workspace.companyId(), movementId) != 1) {
            throw new AssertionError("Stock movement AI memory event was not created.");
        }
        mockMvc.perform(get(company(workspace, "/audit-logs")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].action", hasItem("STOCK_MOVEMENT_POSTED")));
    }

    @Test
    void purchaseInvoicePostingIncreasesProductStock() throws Exception {
        Workspace workspace = workspace("inventory-purchase");
        UUID item = product(workspace, "Purchased Product", "PUR-STOCK", "0");
        UUID invoice = createInvoice(workspace, "PURCHASE", item, "5", "100", "PUR-STOCK-1");
        approveAndPost(workspace, invoice);
        assertStock(workspace, item, 5.0000, 500.00);
    }

    @Test
    void salesInvoicePostingDecreasesProductStock() throws Exception {
        Workspace workspace = workspace("inventory-sale");
        UUID item = product(workspace, "Sold Product", "SALE-STOCK", "0");
        opening(workspace, item, mainWarehouse(workspace), "10", "80");
        UUID invoice = createInvoice(workspace, "SALES", item, "3", "120", "SALE-STOCK-1");
        approveAndPost(workspace, invoice);
        assertStock(workspace, item, 7.0000, 560.00);
    }

    @Test
    void serviceInvoiceDoesNotCreateStockMovement() throws Exception {
        Workspace workspace = workspace("inventory-service");
        UUID service = createItem(workspace, "Consulting Service", "SERVICE", "SERVICE-1", null, null, "0");
        UUID invoice = createInvoice(workspace, "SALES", service, "2", "1000", "SERVICE-INV-1");
        approveAndPost(workspace, invoice);
        mockMvc.perform(get(inventory(workspace, "/stock-summary")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void negativeStockPreventionRollsBackInvoicePosting() throws Exception {
        Workspace workspace = workspace("inventory-negative");
        UUID item = product(workspace, "Blocked Product", "BLOCK-1", "0");
        UUID invoice = createInvoice(workspace, "SALES", item, "1", "100", "BLOCK-INV-1");
        approve(workspace, invoice);
        mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/post"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_RULE_FAILED"));
        mockMvc.perform(get(company(workspace, "/invoices/" + invoice)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(get(company(workspace, "/vouchers")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void fifoValuationConsumesOldestLayersFirst() throws Exception {
        Workspace workspace = workspace("inventory-fifo");
        UUID item = product(workspace, "FIFO Product", "FIFO-1", "0");
        UUID warehouse = mainWarehouse(workspace);
        opening(workspace, item, warehouse, "10", "10");
        adjust(workspace, item, warehouse, "INCREASE", "10", "20");
        adjust(workspace, item, warehouse, "DECREASE", "12", "0");
        assertStock(workspace, item, 8.0000, 160.00);
        mockMvc.perform(get(inventory(workspace, "/valuation")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valuationMethod").value("FIFO"))
                .andExpect(jsonPath("$.items[0].unitCost").value(20.0000));
    }

    @Test
    void weightedAverageValuationUsesCurrentAverageCost() throws Exception {
        Workspace workspace = workspace("inventory-average");
        setSettings(workspace, "WEIGHTED_AVERAGE", false);
        UUID item = product(workspace, "Average Product", "AVG-1", "0");
        UUID warehouse = mainWarehouse(workspace);
        opening(workspace, item, warehouse, "10", "10");
        adjust(workspace, item, warehouse, "INCREASE", "10", "20");
        adjust(workspace, item, warehouse, "DECREASE", "10", "0");
        assertStock(workspace, item, 10.0000, 150.00);
        mockMvc.perform(get(inventory(workspace, "/valuation")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valuationMethod").value("WEIGHTED_AVERAGE"))
                .andExpect(jsonPath("$.items[0].unitCost").value(15.0000));
    }

    @Test
    void stockTransferMovesQuantityWithoutChangingCompanyValue() throws Exception {
        Workspace workspace = workspace("inventory-transfer");
        UUID item = product(workspace, "Transfer Product", "TRANSFER-1", "0");
        UUID main = mainWarehouse(workspace);
        UUID branch = createWarehouse(workspace, "Branch Warehouse", "BRANCH");
        opening(workspace, item, main, "10", "50");
        mockMvc.perform(post(inventory(workspace, "/transfers")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemId", item, "fromWarehouseId", main, "toWarehouseId", branch,
                                "transferDate", "2026-06-30", "quantity", "4", "narration", "Branch stock"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.movements.length()").value(2));
        mockMvc.perform(get(inventory(workspace, "/warehouses/" + main + "/stock"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(6.0000));
        mockMvc.perform(get(inventory(workspace, "/warehouses/" + branch + "/stock"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(4.0000));
        assertStock(workspace, item, 10.0000, 500.00);
    }

    @Test
    void stockAdjustmentSupportsIncreaseAndDecrease() throws Exception {
        Workspace workspace = workspace("inventory-adjustment");
        UUID item = product(workspace, "Adjusted Product", "ADJUST-1", "0");
        UUID warehouse = mainWarehouse(workspace);
        adjust(workspace, item, warehouse, "INCREASE", "8", "25");
        adjust(workspace, item, warehouse, "DECREASE", "3", "0");
        assertStock(workspace, item, 5.0000, 125.00);
        mockMvc.perform(get(inventory(workspace, "/items/" + item + "/stock-ledger"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(2));
    }

    @Test
    void reorderLevelCreatesAndResolvesLowStockAlert() throws Exception {
        Workspace workspace = workspace("inventory-alert");
        UUID item = product(workspace, "Reorder Product", "REORDER-1", "5");
        UUID warehouse = mainWarehouse(workspace);
        opening(workspace, item, warehouse, "10", "10");
        adjust(workspace, item, warehouse, "DECREASE", "6", "0");
        mockMvc.perform(get(inventory(workspace, "/alerts/low-stock")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currentQuantity").value(4.0000));
        adjust(workspace, item, warehouse, "INCREASE", "3", "10");
        mockMvc.perform(get(inventory(workspace, "/alerts/low-stock")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void postedPurchaseCancellationReversesStockAndVoucher() throws Exception {
        Workspace workspace = workspace("inventory-reversal");
        UUID item = product(workspace, "Reversal Product", "REVERSE-1", "0");
        UUID invoice = createInvoice(workspace, "PURCHASE", item, "5", "100", "REVERSE-INV-1");
        approveAndPost(workspace, invoice);
        mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/cancel"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertStock(workspace, item, 0.0000, 0.00);
        mockMvc.perform(get(company(workspace, "/vouchers")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].status", hasItem("REVERSED")));
    }

    @Test
    void viewerCanReadButCannotWriteInventory() throws Exception {
        Workspace owner = workspace("inventory-viewer-owner");
        UserSession viewer = signup("Inventory Viewer", uniqueEmail("inventory-viewer"));
        mockMvc.perform(post(company(owner, "/members")).header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", viewer.email(), "role", "VIEWER"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get(inventory(owner, "/dashboard")).header(auth(), bearer(viewer.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post(inventory(owner, "/warehouses")).header(auth(), bearer(viewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Denied", "code", "DENIED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryEndpointsDenyCrossCompanyAccess() throws Exception {
        Workspace owner = workspace("inventory-scope-owner");
        Workspace outsider = workspace("inventory-scope-outsider");
        mockMvc.perform(get(inventory(owner, "/stock-summary")).header(auth(), bearer(outsider.token())))
                .andExpect(status().isForbidden());
        UUID outsiderItem = product(outsider, "Outsider Product", "OUTSIDER-1", "0");
        mockMvc.perform(post(inventory(owner, "/opening-stock")).header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemId", outsiderItem, "warehouseId", mainWarehouse(owner),
                                "stockDate", "2026-06-30", "quantity", "1", "unitCost", "10"))))
                .andExpect(status().isNotFound());
    }

    private void assertStock(Workspace workspace, UUID item, double quantity, double value) throws Exception {
        mockMvc.perform(get(inventory(workspace, "/stock-summary")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.itemId=='" + item + "')].quantity").value(quantity))
                .andExpect(jsonPath("$.items[?(@.itemId=='" + item + "')].stockValue").value(value));
    }

    private MvcResult opening(Workspace workspace, UUID item, UUID warehouse, String quantity, String cost)
            throws Exception {
        return mockMvc.perform(post(inventory(workspace, "/opening-stock"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemId", item, "warehouseId", warehouse, "stockDate", "2026-06-01",
                                "quantity", quantity, "unitCost", cost))))
                .andExpect(status().isCreated()).andReturn();
    }

    private void adjust(
            Workspace workspace, UUID item, UUID warehouse, String type, String quantity, String cost)
            throws Exception {
        mockMvc.perform(post(inventory(workspace, "/adjustments")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("itemId", item, "warehouseId", warehouse,
                                "adjustmentDate", "2026-06-15", "adjustmentType", type,
                                "quantity", quantity, "unitCost", cost, "reason", "Physical verification"))))
                .andExpect(status().isCreated());
    }

    private void setSettings(Workspace workspace, String method, boolean allowNegative) throws Exception {
        mockMvc.perform(patch(inventory(workspace, "/settings")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("valuationMethod", method, "allowNegativeStock", allowNegative))))
                .andExpect(status().isOk());
    }

    private UUID createInvoice(
            Workspace workspace, String type, UUID item, String quantity, String price, String number)
            throws Exception {
        UUID party = "SALES".equals(type) ? createCustomer(workspace, number) : createVendor(workspace, number);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoiceType", type); body.put("invoiceNumber", number);
        body.put("invoiceDate", "2026-06-30"); body.put("dueDate", "2026-07-15");
        body.put("placeOfSupply", "27"); body.put("gstTreatment", "NORMAL");
        body.put("items", List.of(Map.of("itemId", item, "description", "Inventory item",
                "quantity", quantity, "unitPrice", price, "gstRate", "0", "cessRate", "0")));
        body.put("SALES".equals(type) ? "customerId" : "vendorId", party);
        MvcResult result = mockMvc.perform(post(company(workspace, "/invoices"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))).andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private void approveAndPost(Workspace workspace, UUID invoice) throws Exception {
        approve(workspace, invoice);
        mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/post"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("POSTED"));
    }
    private void approve(Workspace workspace, UUID invoice) throws Exception {
        mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/approve"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk());
    }
    private UUID createCustomer(Workspace workspace, String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post(company(workspace, "/customers"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Customer " + suffix, "state", "27"))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }
    private UUID createVendor(Workspace workspace, String suffix) throws Exception {
        MvcResult result = mockMvc.perform(post(company(workspace, "/vendors"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Vendor " + suffix, "state", "27"))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private UUID product(Workspace workspace, String name, String sku, String reorder) throws Exception {
        return createItem(workspace, name, "PRODUCT", sku, unitId(workspace, "NOS"), null, reorder);
    }
    private UUID createItem(
            Workspace workspace, String name, String type, String sku, UUID unit, UUID category, String reorder)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name); body.put("type", type); body.put("sku", sku); body.put("unit", "NOS");
        body.put("salesPrice", "100"); body.put("purchasePrice", "100"); body.put("gstRate", "0");
        body.put("reorderLevel", reorder);
        if (unit != null) body.put("inventoryUnitId", unit);
        if (category != null) body.put("itemCategoryId", category);
        MvcResult result = mockMvc.perform(post(company(workspace, "/items"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))).andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private UUID createWarehouse(Workspace workspace, String name, String code) throws Exception {
        MvcResult result = mockMvc.perform(post(inventory(workspace, "/warehouses"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "code", code, "primaryWarehouse", false))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }
    private UUID createCategory(Workspace workspace, String name, UUID parent) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("name", name); body.put("active", true);
        if (parent != null) body.put("parentId", parent);
        MvcResult result = mockMvc.perform(post(inventory(workspace, "/categories"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))).andExpect(status().isCreated()).andReturn();
        return id(result);
    }
    private UUID mainWarehouse(Workspace workspace) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(get(inventory(workspace, "/warehouses"))
                        .header(auth(), bearer(workspace.token()))).andExpect(status().isOk()).andReturn());
        for (JsonNode row : response) if (row.get("primaryWarehouse").asBoolean()) return UUID.fromString(row.get("id").asText());
        throw new AssertionError("Primary warehouse not found.");
    }
    private UUID unitId(Workspace workspace, String symbol) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(get(inventory(workspace, "/units"))
                        .header(auth(), bearer(workspace.token()))).andExpect(status().isOk()).andReturn());
        for (JsonNode row : response) if (symbol.equals(row.get("symbol").asText())) return UUID.fromString(row.get("id").asText());
        throw new AssertionError("Inventory unit not found: " + symbol);
    }

    private Workspace workspace(String prefix) throws Exception {
        UserSession owner = signup("Inventory Owner", uniqueEmail(prefix));
        MvcResult result = mockMvc.perform(post("/api/companies").header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("legalName", prefix + " Company", "stateCode", "27",
                                "industry", "Trading", "financialYearStart", "2026-04-01",
                                "financialYearEnd", "2027-03-31"))))
                .andExpect(status().isCreated()).andReturn();
        return new Workspace(owner.token(), id(result));
    }
    private UserSession signup(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated()).andReturn();
        return new UserSession(responseJson(result).get("accessToken").asText(), email);
    }

    private String inventory(Workspace workspace, String suffix) { return company(workspace, "/inventory" + suffix); }
    private String company(Workspace workspace, String suffix) { return "/api/companies/" + workspace.companyId() + suffix; }
    private UUID id(MvcResult result) throws Exception { return UUID.fromString(responseJson(result).get("id").asText()); }
    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String auth() { return "Authorization"; }
    private String bearer(String token) { return "Bearer " + token; }
    private String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@abhay.test"; }
    private record UserSession(String token, String email) { }
    private record Workspace(String token, UUID companyId) { }
}
