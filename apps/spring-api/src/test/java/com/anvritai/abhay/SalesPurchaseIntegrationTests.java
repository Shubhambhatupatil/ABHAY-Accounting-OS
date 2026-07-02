package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anvritai.abhay.repository.accounting.AiMemoryEventRepository;
import com.anvritai.abhay.repository.accounting.JournalEntryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class SalesPurchaseIntegrationTests {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JournalEntryRepository journalEntries;
    @Autowired
    private AiMemoryEventRepository aiMemoryEvents;

    @Test
    void mastersAndServerCalculatedInvoiceTotalsWork() throws Exception {
        Workspace workspace = workspace("masters");
        UUID customer = createCustomer(workspace, "Acme Retail", "27ABCDE1234F1Z5", "27");
        UUID vendor = createVendor(workspace, "Karnataka Supply", "29ABCDE1234F1Z5", "29");
        UUID item = createItem(workspace, "Accounting Service", "SERVICE", "SVC-001", "50000.00", "20000.00");

        mockMvc.perform(get("/api/companies/{companyId}/customers/{customerId}", workspace.companyId(), customer)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Acme Retail"))
                .andExpect(jsonPath("$.ledgerId").isNotEmpty());
        mockMvc.perform(get("/api/companies/{companyId}/vendors/{vendorId}", workspace.companyId(), vendor)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Karnataka Supply"));
        mockMvc.perform(get("/api/companies/{companyId}/items/{itemId}", workspace.companyId(), item)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gstRate").value(18.00));

        UUID salesInvoice = createInvoice(workspace, "SALES", "SAL-001", customer, null, item, "50000.00");
        mockMvc.perform(get("/api/companies/{companyId}/invoices/{invoiceId}", workspace.companyId(), salesInvoice)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(50000.00))
                .andExpect(jsonPath("$.cgstTotal").value(4500.00))
                .andExpect(jsonPath("$.sgstTotal").value(4500.00))
                .andExpect(jsonPath("$.igstTotal").value(0.00))
                .andExpect(jsonPath("$.total").value(59000.00));

        UUID purchaseInvoice = createInvoice(
                workspace, "PURCHASE", "PUR-001", null, vendor, item, "20000.00");
        mockMvc.perform(get("/api/companies/{companyId}/invoices/{invoiceId}", workspace.companyId(), purchaseInvoice)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(20000.00))
                .andExpect(jsonPath("$.cgstTotal").value(0.00))
                .andExpect(jsonPath("$.sgstTotal").value(0.00))
                .andExpect(jsonPath("$.igstTotal").value(3600.00))
                .andExpect(jsonPath("$.total").value(23600.00));

        mockMvc.perform(post("/api/companies/{companyId}/invoices", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("SALES", "SAL-001", customer, null, item, "100.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void postingCreatesBalancedBooksRegistersAuditAndMemory() throws Exception {
        Workspace workspace = workspace("posting");
        UUID customer = createCustomer(workspace, "Sales Customer", "27ABCDE1234F1Z5", "27");
        UUID vendor = createVendor(workspace, "Purchase Vendor", "27ABCDE1234F1Z5", "27");
        UUID item = createItem(workspace, "Trading Goods", "PRODUCT", "GOODS-001", "50000.00", "20000.00");
        UUID sales = createInvoice(workspace, "SALES", "SAL-POST-1", customer, null, item, "50000.00");
        UUID purchase = createInvoice(workspace, "PURCHASE", "PUR-POST-1", null, vendor, item, "20000.00");

        UUID purchaseVoucher = approveAndPost(workspace, purchase);
        UUID salesVoucher = approveAndPost(workspace, sales);

        if (!journalEntries.existsByVoucherId(salesVoucher) || !journalEntries.existsByVoucherId(purchaseVoucher)) {
            throw new AssertionError("Invoice posting did not create journal entries.");
        }
        if (aiMemoryEvents.countByCompanyIdAndEntityId(workspace.companyId(), sales) != 1) {
            throw new AssertionError("Invoice posting did not create its AI memory event.");
        }

        mockMvc.perform(get("/api/companies/{companyId}/reports/sales-register", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").value(59000.00))
                .andExpect(jsonPath("$.invoices[0].status").value("POSTED"));
        mockMvc.perform(get("/api/companies/{companyId}/reports/purchase-register", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grandTotal").value(23600.00));
        mockMvc.perform(get("/api/companies/{companyId}/reports/outstanding-invoices", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(82600.00));
        mockMvc.perform(get("/api/companies/{companyId}/reports/trial-balance", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difference").value(0.00));
        mockMvc.perform(get("/api/companies/{companyId}/audit-logs", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("INVOICE_POSTED")));
    }

    @Test
    void postedInvoiceIsImmutableAndPaymentClosesOutstanding() throws Exception {
        Workspace workspace = workspace("payment");
        UUID customer = createCustomer(workspace, "Payment Customer", "27ABCDE1234F1Z5", "27");
        UUID item = createItem(workspace, "Monthly Service", "SERVICE", "MONTHLY-1", "1000.00", "0.00");
        UUID invoice = createInvoice(workspace, "SALES", "SAL-PAY-1", customer, null, item, "1000.00");
        approveAndPost(workspace, invoice);

        mockMvc.perform(patch("/api/companies/{companyId}/invoices/{invoiceId}", workspace.companyId(), invoice)
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "invoiceDate", "2026-06-30",
                                "dueDate", "2026-07-15",
                                "items", List.of(invoiceLine(item, "500.00"))))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(post(
                        "/api/companies/{companyId}/invoices/{invoiceId}/payments", workspace.companyId(), invoice)
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "paymentDate", "2026-07-01",
                                "amount", "1180.00",
                                "mode", "CASH",
                                "reference", "RCPT-001"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(1180.00))
                .andExpect(jsonPath("$.linkedVoucherId").isNotEmpty());
        mockMvc.perform(get("/api/companies/{companyId}/invoices/{invoiceId}", workspace.companyId(), invoice)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidAmount").value(1180.00))
                .andExpect(jsonPath("$.balanceAmount").value(0.00));
        mockMvc.perform(get("/api/companies/{companyId}/reports/outstanding-invoices", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoices.length()").value(0))
                .andExpect(jsonPath("$.totalOutstanding").value(0.00));
    }

    @Test
    void viewerCannotCreateAndCompanyScopeCannotBeBypassed() throws Exception {
        Workspace owner = workspace("security-owner");
        Workspace outsider = workspace("security-outsider");
        UserSession viewer = signup("Invoice Viewer", uniqueEmail("invoice-viewer"));
        UUID customer = createCustomer(owner, "Secure Customer", "27ABCDE1234F1Z5", "27");
        UUID item = createItem(owner, "Secure Item", "SERVICE", "SECURE-1", "100.00", "0.00");

        mockMvc.perform(post("/api/companies/{companyId}/members", owner.companyId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", viewer.email(), "role", "VIEWER"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/companies/{companyId}/invoices", owner.companyId())
                        .header("Authorization", bearer(viewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("SALES", "VIEWER-1", customer, null, item, "100.00")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/companies/{companyId}/customers/{customerId}", owner.companyId(), customer)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/companies/{companyId}/invoices", outsider.companyId())
                        .header("Authorization", bearer(outsider.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson("SALES", "CROSS-1", customer, null, item, "100.00")))
                .andExpect(status().isNotFound());
    }

    private UUID approveAndPost(Workspace workspace, UUID invoice) throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/invoices/{invoiceId}/approve", workspace.companyId(), invoice)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        MvcResult result = mockMvc.perform(post(
                        "/api/companies/{companyId}/invoices/{invoiceId}/post", workspace.companyId(), invoice)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.postedVoucherId").isNotEmpty())
                .andReturn();
        return UUID.fromString(responseJson(result).get("postedVoucherId").asText());
    }

    private UUID createCustomer(Workspace workspace, String name, String gstin, String state) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/customers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "displayName", name,
                                "gstin", gstin,
                                "state", state,
                                "paymentTermsDays", 15,
                                "openingBalance", "0.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private UUID createVendor(Workspace workspace, String name, String gstin, String state) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/vendors", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "displayName", name,
                                "gstin", gstin,
                                "state", state,
                                "paymentTermsDays", 15,
                                "openingBalance", "0.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private UUID createItem(
            Workspace workspace, String name, String type, String sku, String salesPrice, String purchasePrice)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/items", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "type", type,
                                "sku", sku,
                                "hsnSac", "9983",
                                "unit", "NOS",
                                "salesPrice", salesPrice,
                                "purchasePrice", purchasePrice,
                                "gstRate", "18.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private UUID createInvoice(
            Workspace workspace, String type, String number, UUID customerId, UUID vendorId,
            UUID itemId, String unitPrice) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/invoices", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceJson(type, number, customerId, vendorId, itemId, unitPrice)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private String invoiceJson(
            String type, String number, UUID customerId, UUID vendorId, UUID itemId, String unitPrice)
            throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("invoiceType", type);
        body.put("invoiceNumber", number);
        body.put("invoiceDate", "2026-06-30");
        body.put("dueDate", "2026-07-15");
        if (customerId != null) body.put("customerId", customerId.toString());
        if (vendorId != null) body.put("vendorId", vendorId.toString());
        body.put("notes", "Integration test invoice");
        body.put("items", List.of(invoiceLine(itemId, unitPrice)));
        return json(body);
    }

    private Map<String, Object> invoiceLine(UUID itemId, String unitPrice) {
        return Map.of(
                "itemId", itemId.toString(),
                "description", "Professional accounting supply",
                "quantity", "1.0000",
                "unitPrice", unitPrice,
                "gstRate", "18.00");
    }

    private Workspace workspace(String prefix) throws Exception {
        UserSession owner = signup("Sales Owner", uniqueEmail(prefix));
        MvcResult company = mockMvc.perform(post("/api/companies")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "legalName", prefix + " Company",
                                "tradeName", prefix + " Company",
                                "stateCode", "27",
                                "industry", "Trading",
                                "financialYearStart", "2026-04-01",
                                "financialYearEnd", "2027-03-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new Workspace(owner.token(), UUID.fromString(responseJson(company).get("id").asText()));
    }

    private UserSession signup(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new UserSession(responseJson(result).get("accessToken").asText(), email);
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
    private String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@abhay.test"; }
    private record UserSession(String token, String email) { }
    private record Workspace(String token, UUID companyId) { }
}
