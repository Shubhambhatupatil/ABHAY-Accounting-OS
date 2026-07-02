package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.anvritai.abhay.repository.accounting.AiMemoryEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingTreasuryIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiMemoryEventRepository memoryEvents;

    @Test
    void createsBankAccountAndRejectsNonBankLedger() throws Exception {
        Workspace workspace = workspace("bank-account");
        UUID bank = ledgerId(workspace, "Primary Bank");
        mockMvc.perform(post(bank(workspace, "/accounts")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(accountJson(bank, "********1234")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ledgerName").value("Primary Bank"))
                .andExpect(jsonPath("$.accountNumberMasked").value("********1234"));
        UUID sales = ledgerId(workspace, "Sales");
        mockMvc.perform(post(bank(workspace, "/accounts")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(accountJson(sales, "********5678")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_RULE_FAILED"));
    }

    @Test
    void importsCsvAndPreventsDuplicateTransactions() throws Exception {
        Workspace workspace = workspace("bank-import");
        UUID account = createBankAccount(workspace);
        String csv = "date,description,reference,debit,credit,balance,counterparty\n"
                + "2026-07-01,Vendor transfer,UTR-100,1000,0,9000,Vendor One\n"
                + "2026-07-01,Vendor transfer,UTR-100,1000,0,9000,Vendor One\n";
        mockMvc.perform(multipart(bank(workspace, "/accounts/" + account + "/statement-imports"))
                        .file(csv("statement.csv", csv)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importedRows").value(1))
                .andExpect(jsonPath("$.duplicateRows").value(1));
        mockMvc.perform(get(bank(workspace, "/accounts/" + account + "/transactions"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(multipart(bank(workspace, "/accounts/" + account + "/statement-imports"))
                        .file(csv("statement.csv", csv)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importedRows").value(1));
    }

    @Test
    void suggestsConfirmsAndUnmatchesVoucher() throws Exception {
        Workspace workspace = workspace("bank-match");
        UUID account = createBankAccount(workspace);
        UUID voucher = createPostedVoucher(workspace, "PAYMENT", "Vendor transfer UTR-200", "1000.00");
        UUID transaction = importOne(workspace, account,
                "2026-06-30,Vendor transfer UTR-200,UTR-200,1000,0,9000,Vendor");
        MvcResult suggestion = mockMvc.perform(get(bank(workspace, "/reconciliation/suggestions"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].targetType").value("VOUCHER"))
                .andExpect(jsonPath("$[0].targetId").value(voucher.toString())).andReturn();
        if (responseJson(suggestion).get(0).get("confidence").decimalValue().doubleValue() < 0.8) {
            throw new AssertionError("Expected a high-confidence exact voucher suggestion.");
        }
        mockMvc.perform(post(bank(workspace, "/reconciliation/" + transaction + "/confirm"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetType", "VOUCHER", "targetId", voucher))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MATCHED"));
        UUID duplicateTarget = importOne(workspace, account,
                "2026-07-01,Duplicate target attempt,OTHER-200,1000,0,8000,Vendor");
        mockMvc.perform(post(bank(workspace, "/reconciliation/" + duplicateTarget + "/confirm"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("targetType", "VOUCHER", "targetId", voucher))))
                .andExpect(status().isConflict());
        mockMvc.perform(post(bank(workspace, "/reconciliation/" + transaction + "/unmatch"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UNMATCHED"));
    }

    @Test
    void ignoresTransactionAndKeepsItInReconciliationReport() throws Exception {
        Workspace workspace = workspace("bank-ignore");
        UUID account = createBankAccount(workspace);
        UUID transaction = importOne(workspace, account, "2026-07-01,Bank charge,CHG-1,50,0,9950,Bank");
        mockMvc.perform(post(bank(workspace, "/reconciliation/" + transaction + "/ignore"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IGNORED"));
        mockMvc.perform(get(bank(workspace, "/reports/reconciliation")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ignored").value(1))
                .andExpect(jsonPath("$.transactions[0].status").value("IGNORED"));
    }

    @Test
    void bankBookAndCashPositionUsePersistedMovements() throws Exception {
        Workspace workspace = workspace("bank-book");
        UUID account = createBankAccount(workspace);
        importOne(workspace, account, "2026-07-01,Customer receipt,RCPT-10,0,2500,12500,Customer");
        mockMvc.perform(get(bank(workspace, "/reports/bank-book")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accounts[0].openingBalance").value(10000.00))
                .andExpect(jsonPath("$.accounts[0].closingBalance").value(12500.00));
        mockMvc.perform(get(company(workspace, "/treasury/cash-position"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreconciledCredits").value(2500.00))
                .andExpect(jsonPath("$.projectedLiquidity").value(12500.00));
    }

    @Test
    void invoicePaymentAppearsInReconciliationSuggestions() throws Exception {
        Workspace workspace = workspace("bank-invoice-payment");
        UUID account = createBankAccount(workspace);
        UUID payment = createPaidSalesInvoice(workspace, "BANK-RCPT-900");
        importOne(workspace, account, "2026-07-01,Customer payment BANK-RCPT-900,BANK-RCPT-900,0,1180,11180,Customer");
        mockMvc.perform(get(bank(workspace, "/reconciliation/suggestions"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].targetType", hasItem("INVOICE_PAYMENT")))
                .andExpect(jsonPath("$[*].targetId", hasItem(payment.toString())));
    }

    @Test
    void treasuryDashboardIncludesLiquidityAndReconciliationAlerts() throws Exception {
        Workspace workspace = workspace("treasury-dashboard");
        UUID account = createBankAccount(workspace);
        importOne(workspace, account, "2026-07-01,Pending credit,PENDING-1,0,500,10500,Customer");
        mockMvc.perform(get(company(workspace, "/treasury/dashboard")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalBankBalance").value(10000.00))
                .andExpect(jsonPath("$.totalLiquidity").value(10000.00))
                .andExpect(jsonPath("$.unreconciledCount").value(1))
                .andExpect(jsonPath("$.alerts[0].type").value("UNRECONCILED_TRANSACTIONS"));
    }

    @Test
    void viewerCanReadButCannotImportOrReconcile() throws Exception {
        Workspace owner = workspace("bank-viewer-owner");
        UUID account = createBankAccount(owner);
        UUID transaction = importOne(owner, account, "2026-07-01,Read only,RO-1,100,0,9900,Vendor");
        UserSession viewer = signup("Bank Viewer", uniqueEmail("bank-viewer"));
        addMember(owner, viewer.email(), "VIEWER");
        mockMvc.perform(get(bank(owner, "/reports/bank-book")).header(auth(), bearer(viewer.token())))
                .andExpect(status().isOk());
        mockMvc.perform(multipart(bank(owner, "/accounts/" + account + "/statement-imports"))
                        .file(csv("denied.csv", validCsv("2026-07-02,Denied,D-1,10,0,9890,Vendor")))
                        .header(auth(), bearer(viewer.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(bank(owner, "/reconciliation/" + transaction + "/ignore"))
                        .header(auth(), bearer(viewer.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void bankingEndpointsDenyCrossCompanyAccess() throws Exception {
        Workspace owner = workspace("bank-scope-owner");
        Workspace outsider = workspace("bank-scope-outsider");
        UUID account = createBankAccount(owner);
        mockMvc.perform(get(bank(owner, "/accounts")).header(auth(), bearer(outsider.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(bank(owner, "/accounts/" + account + "/transactions"))
                        .header(auth(), bearer(outsider.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void reconciliationCreatesAuditAndAiMemoryEvents() throws Exception {
        Workspace workspace = workspace("bank-memory");
        UUID account = createBankAccount(workspace);
        UUID transaction = importOne(workspace, account, "2026-07-01,Audit transaction,AUD-1,100,0,9900,Vendor");
        mockMvc.perform(post(bank(workspace, "/reconciliation/" + transaction + "/ignore"))
                        .header(auth(), bearer(workspace.token()))).andExpect(status().isOk());
        mockMvc.perform(get(company(workspace, "/audit-logs")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].action", hasItem("BANK_TRANSACTION_IGNORED")));
        if (memoryEvents.countByCompanyIdAndEntityId(workspace.companyId(), transaction) != 1) {
            throw new AssertionError("Reconciliation AI memory event was not created.");
        }
    }

    private UUID createBankAccount(Workspace workspace) throws Exception {
        UUID ledger = ledgerId(workspace, "Primary Bank");
        MvcResult result = mockMvc.perform(post(bank(workspace, "/accounts"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(ledger, "********1234")))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private String accountJson(UUID ledger, String masked) throws Exception {
        return json(Map.of("ledgerId", ledger, "bankName", "State Bank of India",
                "accountName", "Primary Operations", "accountNumberMasked", masked,
                "accountType", "CURRENT", "ifsc", "SBIN0001234", "currency", "INR",
                "openingBalance", "10000.00", "active", true));
    }

    private UUID importOne(Workspace workspace, UUID account, String row) throws Exception {
        mockMvc.perform(multipart(bank(workspace, "/accounts/" + account + "/statement-imports"))
                        .file(csv(UUID.randomUUID() + ".csv", validCsv(row)))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.importedRows").value(1));
        JsonNode rows = responseJson(mockMvc.perform(get(bank(workspace, "/accounts/" + account + "/transactions"))
                        .header(auth(), bearer(workspace.token()))).andExpect(status().isOk()).andReturn());
        return UUID.fromString(rows.get(rows.size() - 1).get("id").asText());
    }

    private String validCsv(String row) {
        return "date,description,reference,debit,credit,balance,counterparty\n" + row + "\n";
    }

    private MockMultipartFile csv(String name, String content) {
        return new MockMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private UUID createPostedVoucher(Workspace workspace, String type, String narration, String amount)
            throws Exception {
        UUID bankLedger = ledgerId(workspace, "Primary Bank");
        UUID cashLedger = ledgerId(workspace, "Cash");
        MvcResult draft = mockMvc.perform(post(company(workspace, "/vouchers"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("voucherTypeCode", type, "voucherDate", "2026-06-30",
                                "narration", narration, "lines", List.of(
                                        Map.of("ledgerId", cashLedger, "debit", amount, "credit", "0.00"),
                                        Map.of("ledgerId", bankLedger, "debit", "0.00", "credit", amount))))))
                .andExpect(status().isCreated()).andReturn();
        UUID voucher = id(draft);
        mockMvc.perform(post(company(workspace, "/vouchers/" + voucher + "/post"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("POSTED"));
        return voucher;
    }

    private UUID createPaidSalesInvoice(Workspace workspace, String reference) throws Exception {
        UUID customer = id(mockMvc.perform(post(company(workspace, "/customers"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Bank Customer", "state", "27"))))
                .andExpect(status().isCreated()).andReturn());
        UUID item = id(mockMvc.perform(post(company(workspace, "/items"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Banking Service", "type", "SERVICE", "sku", "BANK-SVC-" + UUID.randomUUID(),
                                "unit", "NOS", "salesPrice", "1000", "purchasePrice", "0", "gstRate", "18"))))
                .andExpect(status().isCreated()).andReturn());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoiceType", "SALES"); body.put("invoiceNumber", "INV-" + UUID.randomUUID());
        body.put("invoiceDate", "2026-06-30"); body.put("dueDate", "2026-07-15");
        body.put("customerId", customer); body.put("placeOfSupply", "27"); body.put("gstTreatment", "NORMAL");
        body.put("items", List.of(Map.of("itemId", item, "description", "Monthly service", "quantity", "1",
                "unitPrice", "1000", "gstRate", "18", "cessRate", "0")));
        UUID invoice = id(mockMvc.perform(post(company(workspace, "/invoices"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))).andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/approve"))
                .header(auth(), bearer(workspace.token()))).andExpect(status().isOk());
        mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/post"))
                .header(auth(), bearer(workspace.token()))).andExpect(status().isOk());
        MvcResult payment = mockMvc.perform(post(company(workspace, "/invoices/" + invoice + "/payments"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentDate", "2026-07-01", "amount", "1180.00",
                                "mode", "BANK", "reference", reference))))
                .andExpect(status().isCreated()).andReturn();
        return id(payment);
    }

    private UUID ledgerId(Workspace workspace, String name) throws Exception {
        JsonNode result = responseJson(mockMvc.perform(get(company(workspace, "/ledgers"))
                        .header(auth(), bearer(workspace.token()))).andExpect(status().isOk()).andReturn());
        for (JsonNode ledger : result) if (name.equals(ledger.get("name").asText())) return UUID.fromString(ledger.get("id").asText());
        throw new AssertionError("Ledger not found: " + name);
    }

    private void addMember(Workspace owner, String email, String role) throws Exception {
        mockMvc.perform(post(company(owner, "/members")).header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());
    }

    private Workspace workspace(String prefix) throws Exception {
        UserSession owner = signup("Banking Owner", uniqueEmail(prefix));
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

    private String bank(Workspace workspace, String suffix) { return company(workspace, "/bank" + suffix); }
    private String company(Workspace workspace, String suffix) { return "/api/companies/" + workspace.companyId() + suffix; }
    private UUID id(MvcResult result) throws Exception { return UUID.fromString(responseJson(result).get("id").asText()); }
    private JsonNode responseJson(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String auth() { return "Authorization"; }
    private String bearer(String token) { return "Bearer " + token; }
    private String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@abhay.test"; }
    private record UserSession(String token, String email) { }
    private record Workspace(String token, UUID companyId) { }
}
