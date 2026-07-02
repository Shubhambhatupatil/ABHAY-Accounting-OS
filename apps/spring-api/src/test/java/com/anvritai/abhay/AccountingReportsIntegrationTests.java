package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AccountingReportsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void openingBalancesAffectTrialBalanceAndBalanceSheet() throws Exception {
        Workspace workspace = workspace("opening");
        JsonNode ledgers = ledgers(workspace);
        UUID cash = ledgerId(ledgers, "Cash");
        UUID capitalGroup = groupId(workspace, "Capital Account");
        UUID capital = createLedger(workspace, capitalGroup, "Owner Capital", "OWNER-CAP", "CREDIT", "GENERAL");

        setOpening(workspace, cash, "10000.00", "0.00");
        setOpening(workspace, capital, "0.00", "10000.00");

        mockMvc.perform(get("/api/companies/{companyId}/reports/trial-balance", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebit").value(10000.00))
                .andExpect(jsonPath("$.totalCredit").value(10000.00))
                .andExpect(jsonPath("$.difference").value(0.00));

        mockMvc.perform(get("/api/companies/{companyId}/reports/balance-sheet", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAssets").value(10000.00))
                .andExpect(jsonPath("$.totalLiabilitiesAndEquity").value(10000.00))
                .andExpect(jsonPath("$.difference").value(0.00));
    }

    @Test
    void postedBooksDriveStatementsRegistersAndFinancialReports() throws Exception {
        Workspace workspace = workspace("reports");
        JsonNode seededLedgers = ledgers(workspace);
        UUID cash = ledgerId(seededLedgers, "Cash");
        UUID bank = ledgerId(seededLedgers, "Primary Bank");
        UUID sales = ledgerId(seededLedgers, "Sales");
        UUID purchase = ledgerId(seededLedgers, "Purchase");
        UUID gstInput = ledgerId(seededLedgers, "GST Input");
        UUID gstOutput = ledgerId(seededLedgers, "GST Output");
        UUID customer = createLedger(
                workspace, groupId(workspace, "Sundry Debtors"), "Test Customer", "CUST-01", "DEBIT", "CUSTOMER");
        UUID vendor = createLedger(
                workspace, groupId(workspace, "Sundry Creditors"), "Test Vendor", "VEND-01", "CREDIT", "VENDOR");

        UUID salesVoucher = createAndPost(workspace, "SALES", "Sales invoice", List.of(
                line(customer, "1180.00", "0.00"),
                line(sales, "0.00", "1000.00"),
                line(gstOutput, "0.00", "180.00")));
        createAndPost(workspace, "PURCHASE", "Purchase invoice", List.of(
                line(purchase, "500.00", "0.00"),
                line(gstInput, "90.00", "0.00"),
                line(vendor, "0.00", "590.00")));

        mockMvc.perform(get("/api/companies/{companyId}/reports/receivables", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1180.00));
        mockMvc.perform(get("/api/companies/{companyId}/reports/payables", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(590.00));

        createAndPost(workspace, "RECEIPT", "Customer receipt", List.of(
                line(cash, "1180.00", "0.00"),
                line(customer, "0.00", "1180.00")));
        createAndPost(workspace, "PAYMENT", "Vendor payment", List.of(
                line(vendor, "590.00", "0.00"),
                line(bank, "0.00", "590.00")));

        mockMvc.perform(get("/api/companies/{companyId}/ledgers/{ledgerId}/statement", workspace.companyId(), customer)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.closingDebit").value(0.00))
                .andExpect(jsonPath("$.closingCredit").value(0.00));

        mockMvc.perform(get("/api/companies/{companyId}/reports/day-book", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vouchers.length()").value(4))
                .andExpect(jsonPath("$.vouchers[*].status", hasItem("POSTED")));

        mockMvc.perform(get("/api/companies/{companyId}/vouchers", workspace.companyId())
                        .queryParam("status", "posted")
                        .queryParam("voucher_type", "sales")
                        .queryParam("ledger_id", customer.toString())
                        .queryParam("search", "Sales invoice")
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(salesVoucher.toString()));

        mockMvc.perform(get("/api/companies/{companyId}/reports/trial-balance", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difference").value(0.00));

        mockMvc.perform(get("/api/companies/{companyId}/reports/profit-and-loss", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(1000.00))
                .andExpect(jsonPath("$.totalExpenses").value(500.00))
                .andExpect(jsonPath("$.netProfit").value(500.00));

        mockMvc.perform(get("/api/companies/{companyId}/reports/balance-sheet", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.difference").value(0.00));

        mockMvc.perform(get("/api/companies/{companyId}/reports/cash-book", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookType").value("CASH"))
                .andExpect(jsonPath("$.totalClosingDebit").value(1180.00));

        mockMvc.perform(get("/api/companies/{companyId}/reports/bank-book", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookType").value("BANK"))
                .andExpect(jsonPath("$.totalClosingCredit").value(590.00));

        mockMvc.perform(get("/api/companies/{companyId}/dashboard/accounting", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalVouchers").value(4))
                .andExpect(jsonPath("$.postedVouchers").value(4))
                .andExpect(jsonPath("$.income").value(1000.00))
                .andExpect(jsonPath("$.expenses").value(500.00))
                .andExpect(jsonPath("$.profit").value(500.00));
    }

    @Test
    void lockedFinancialYearBlocksOpeningAndVoucherPosting() throws Exception {
        Workspace workspace = workspace("locked");
        JsonNode seededLedgers = ledgers(workspace);
        UUID voucher = createDraft(workspace, "JOURNAL", "Lock test", List.of(
                line(ledgerId(seededLedgers, "Cash"), "100.00", "0.00"),
                line(ledgerId(seededLedgers, "Sales"), "0.00", "100.00")));

        mockMvc.perform(post(
                        "/api/companies/{companyId}/financial-years/{financialYearId}/lock",
                        workspace.companyId(),
                        workspace.financialYearId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        mockMvc.perform(post("/api/companies/{companyId}/vouchers/{voucherId}/post", workspace.companyId(), voucher)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_RULE_FAILED"));

        mockMvc.perform(patch(
                        "/api/companies/{companyId}/ledgers/{ledgerId}/opening-balance",
                        workspace.companyId(),
                        ledgerId(seededLedgers, "Cash"))
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("openingDebit", "100.00", "openingCredit", "0.00"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void viewerIsReadOnlyAndReportsDenyCrossCompanyAccess() throws Exception {
        Workspace owner = workspace("owner-security");
        UserSession viewer = signup("Viewer User", uniqueEmail("viewer"));
        Workspace outsider = workspace("outsider-security");
        JsonNode seededLedgers = ledgers(owner);

        mockMvc.perform(post("/api/companies/{companyId}/members", owner.companyId())
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", viewer.email(), "role", "VIEWER"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/vouchers", owner.companyId())
                        .header("Authorization", bearer(viewer.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson("JOURNAL", "Viewer attempt", List.of(
                                line(ledgerId(seededLedgers, "Cash"), "10.00", "0.00"),
                                line(ledgerId(seededLedgers, "Sales"), "0.00", "10.00")))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/companies/{companyId}/reports/trial-balance", owner.companyId())
                        .header("Authorization", bearer(viewer.token())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/reports/balance-sheet", owner.companyId())
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private Workspace workspace(String prefix) throws Exception {
        UserSession owner = signup("Accounting Owner", uniqueEmail(prefix));
        MvcResult company = mockMvc.perform(post("/api/companies")
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "legalName", prefix + " Company",
                                "tradeName", prefix + " Company",
                                "stateCode", "27",
                                "industry", "Professional Services",
                                "financialYearStart", "2026-04-01",
                                "financialYearEnd", "2027-03-31"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = responseJson(company);
        return new Workspace(
                owner.token(),
                UUID.fromString(body.get("id").asText()),
                UUID.fromString(body.get("activeFinancialYearId").asText()));
    }

    private UserSession signup(String name, String email) throws Exception {
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        return new UserSession(responseJson(signup).get("accessToken").asText(), email);
    }

    private JsonNode ledgers(Workspace workspace) throws Exception {
        return responseJson(mockMvc.perform(get("/api/companies/{companyId}/ledgers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andReturn());
    }

    private UUID ledgerId(JsonNode ledgers, String name) {
        for (JsonNode ledger : ledgers) {
            if (name.equals(ledger.get("name").asText())) {
                return UUID.fromString(ledger.get("id").asText());
            }
        }
        throw new AssertionError("Ledger not found: " + name);
    }

    private UUID groupId(Workspace workspace, String name) throws Exception {
        JsonNode groups = responseJson(mockMvc.perform(
                        get("/api/companies/{companyId}/ledger-groups", workspace.companyId())
                                .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode group : groups) {
            if (name.equals(group.get("name").asText())) {
                return UUID.fromString(group.get("id").asText());
            }
        }
        throw new AssertionError("Ledger group not found: " + name);
    }

    private UUID createLedger(
            Workspace workspace,
            UUID groupId,
            String name,
            String code,
            String normalBalance,
            String ledgerType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/ledgers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "ledgerGroupId", groupId.toString(),
                                "name", name,
                                "code", code,
                                "normalBalance", normalBalance,
                                "ledgerType", ledgerType,
                                "openingDebit", "0.00",
                                "openingCredit", "0.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private void setOpening(Workspace workspace, UUID ledgerId, String debit, String credit) throws Exception {
        mockMvc.perform(patch(
                        "/api/companies/{companyId}/ledgers/{ledgerId}/opening-balance",
                        workspace.companyId(), ledgerId)
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "financialYearId", workspace.financialYearId().toString(),
                                "openingDebit", debit,
                                "openingCredit", credit))))
                .andExpect(status().isOk());
    }

    private UUID createAndPost(
            Workspace workspace,
            String voucherType,
            String narration,
            List<Map<String, String>> lines) throws Exception {
        UUID voucher = createDraft(workspace, voucherType, narration, lines);
        mockMvc.perform(post("/api/companies/{companyId}/vouchers/{voucherId}/post", workspace.companyId(), voucher)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));
        return voucher;
    }

    private UUID createDraft(
            Workspace workspace,
            String voucherType,
            String narration,
            List<Map<String, String>> lines) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/vouchers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherJson(voucherType, narration, lines)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private String voucherJson(String voucherType, String narration, List<Map<String, String>> lines)
            throws Exception {
        return json(Map.of(
                "voucherTypeCode", voucherType,
                "voucherDate", "2026-06-30",
                "narration", narration,
                "lines", lines));
    }

    private Map<String, String> line(UUID ledgerId, String debit, String credit) {
        return Map.of("ledgerId", ledgerId.toString(), "debit", debit, "credit", credit);
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@abhay.test";
    }

    private record UserSession(String token, String email) {
    }

    private record Workspace(String token, UUID companyId, UUID financialYearId) {
    }
}
