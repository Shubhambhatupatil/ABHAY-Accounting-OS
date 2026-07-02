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
class AccountingCoreIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JournalEntryRepository journalEntries;

    @Autowired
    private AiMemoryEventRepository aiMemoryEvents;

    @Test
    void ledgerGroupAndLedgerCreateWork() throws Exception {
        Workspace workspace = workspace("ledger");
        UUID assetGroup = createGroup(workspace, "Test Current Assets", "ASSET");
        UUID bankLedger = createLedger(workspace, assetGroup, "Test Primary Bank", "TBANK-01", "DEBIT");

        mockMvc.perform(get("/api/companies/{companyId}/ledger-groups", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Test Current Assets")));

        mockMvc.perform(get("/api/companies/{companyId}/ledgers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(bankLedger.toString())))
                .andExpect(jsonPath("$[?(@.id == '" + bankLedger + "')].ledgerGroupName", hasItem("Test Current Assets")));
    }

    @Test
    void balancedVoucherPostsUpdatesBalancesAndCanBeReversed() throws Exception {
        Workspace workspace = workspace("posting");
        LedgerPair pair = createLedgerPair(workspace);
        UUID voucherId = createVoucher(workspace, pair, "1000.00", "1000.00");

        mockMvc.perform(post("/api/companies/{companyId}/vouchers/{voucherId}/post", workspace.companyId(), voucherId)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.totalDebit").value(1000.00))
                .andExpect(jsonPath("$.totalCredit").value(1000.00));

        assert journalEntries.existsByVoucherId(voucherId);
        assert aiMemoryEvents.countByCompanyIdAndEntityId(workspace.companyId(), voucherId) == 1;

        mockMvc.perform(patch("/api/companies/{companyId}/vouchers/{voucherId}", workspace.companyId(), voucherId)
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(voucherUpdateJson(pair, "Edited after posting")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_RULE_FAILED"));

        JsonNode balancesBefore = responseJson(mockMvc.perform(
                        get("/api/companies/{companyId}/account-balances", workspace.companyId())
                                .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andReturn());
        assert balanceFor(balancesBefore, pair.bankLedger()).get("closingDebit").decimalValue().intValueExact() == 1000;
        assert balanceFor(balancesBefore, pair.salesLedger()).get("closingCredit").decimalValue().intValueExact() == 1000;

        MvcResult reverseResult = mockMvc.perform(
                        post("/api/companies/{companyId}/vouchers/{voucherId}/reverse", workspace.companyId(), voucherId)
                                .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversalVoucherId").isNotEmpty())
                .andReturn();
        UUID reversalVoucherId = UUID.fromString(
                responseJson(reverseResult).get("reversalVoucherId").asText());
        assert journalEntries.existsByVoucherId(reversalVoucherId);

        JsonNode balancesAfter = responseJson(mockMvc.perform(
                        get("/api/companies/{companyId}/account-balances", workspace.companyId())
                                .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andReturn());
        assert balanceFor(balancesAfter, pair.bankLedger()).get("closingDebit").decimalValue().signum() == 0;
        assert balanceFor(balancesAfter, pair.bankLedger()).get("closingCredit").decimalValue().signum() == 0;
        assert balanceFor(balancesAfter, pair.salesLedger()).get("closingDebit").decimalValue().signum() == 0;
        assert balanceFor(balancesAfter, pair.salesLedger()).get("closingCredit").decimalValue().signum() == 0;

        mockMvc.perform(get("/api/companies/{companyId}/audit-logs", workspace.companyId())
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("VOUCHER_POSTED")))
                .andExpect(jsonPath("$[*].action", hasItem("VOUCHER_REVERSED")));
    }

    @Test
    void unbalancedVoucherCannotBePosted() throws Exception {
        Workspace workspace = workspace("unbalanced");
        LedgerPair pair = createLedgerPair(workspace);
        UUID voucherId = createVoucher(workspace, pair, "1000.00", "900.00");

        mockMvc.perform(post("/api/companies/{companyId}/vouchers/{voucherId}/post", workspace.companyId(), voucherId)
                        .header("Authorization", bearer(workspace.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNTING_RULE_FAILED"));

        assert !journalEntries.existsByVoucherId(voucherId);
        assert aiMemoryEvents.countByCompanyIdAndEntityId(workspace.companyId(), voucherId) == 0;
    }

    @Test
    void accountingEndpointsDenyCrossCompanyAccess() throws Exception {
        Workspace ownerWorkspace = workspace("private-books");
        Workspace outsiderWorkspace = workspace("outsider-books");
        createGroup(ownerWorkspace, "Private Assets", "ASSET");

        mockMvc.perform(get("/api/companies/{companyId}/ledgers", ownerWorkspace.companyId())
                        .header("Authorization", bearer(outsiderWorkspace.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/companies/{companyId}/ledger-groups", ownerWorkspace.companyId())
                        .header("Authorization", bearer(outsiderWorkspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Injected Group", "accountNature", "ASSET"))))
                .andExpect(status().isForbidden());
    }

    private Workspace workspace(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@abhay.test";
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "Accounting Owner",
                                "email", email,
                                "password", "StrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        String token = responseJson(signup).get("accessToken").asText();
        MvcResult company = mockMvc.perform(post("/api/companies")
                        .header("Authorization", bearer(token))
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
        return new Workspace(token, UUID.fromString(responseJson(company).get("id").asText()));
    }

    private LedgerPair createLedgerPair(Workspace workspace) throws Exception {
        UUID assets = createGroup(workspace, "Test Bank Accounts", "ASSET");
        UUID income = createGroup(workspace, "Test Sales Accounts", "INCOME");
        return new LedgerPair(
                createLedger(workspace, assets, "Business Bank", "TEST-BANK", "DEBIT"),
                createLedger(workspace, income, "Sales Revenue", "TEST-SALES", "CREDIT"));
    }

    private UUID createGroup(Workspace workspace, String name, String nature) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/ledger-groups", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "accountNature", nature))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private UUID createLedger(
            Workspace workspace,
            UUID groupId,
            String name,
            String code,
            String normalBalance) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/ledgers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "ledgerGroupId", groupId.toString(),
                                "name", name,
                                "code", code,
                                "normalBalance", normalBalance,
                                "openingDebit", "0.00",
                                "openingCredit", "0.00"))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private UUID createVoucher(
            Workspace workspace,
            LedgerPair pair,
            String debit,
            String credit) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/vouchers", workspace.companyId())
                        .header("Authorization", bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "voucherTypeCode", "RECEIPT",
                                "voucherDate", "2026-06-30",
                                "narration", "Customer receipt",
                                "lines", List.of(
                                        Map.of("ledgerId", pair.bankLedger().toString(), "debit", debit, "credit", "0.00"),
                                        Map.of("ledgerId", pair.salesLedger().toString(), "debit", "0.00", "credit", credit))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private String voucherUpdateJson(LedgerPair pair, String narration) throws Exception {
        return json(Map.of(
                "voucherDate", "2026-06-30",
                "narration", narration,
                "lines", List.of(
                        Map.of("ledgerId", pair.bankLedger().toString(), "debit", "1000.00", "credit", "0.00"),
                        Map.of("ledgerId", pair.salesLedger().toString(), "debit", "0.00", "credit", "1000.00"))));
    }

    private JsonNode balanceFor(JsonNode balances, UUID ledgerId) {
        for (JsonNode balance : balances) {
            if (ledgerId.toString().equals(balance.get("ledgerId").asText())) {
                return balance;
            }
        }
        throw new AssertionError("Balance not found for ledger " + ledgerId);
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

    private record Workspace(String token, UUID companyId) {
    }

    private record LedgerPair(UUID bankLedger, UUID salesLedger) {
    }
}
