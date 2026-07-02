package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GstEngineIntegrationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void standardRatesAreSeededForEveryCompany() throws Exception {
        Workspace workspace = workspace("gst-rates");
        mockMvc.perform(get(gst(workspace, "/rates")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[*].rate", hasItem(0.00)))
                .andExpect(jsonPath("$[*].rate", hasItem(5.00)))
                .andExpect(jsonPath("$[*].rate", hasItem(12.00)))
                .andExpect(jsonPath("$[*].rate", hasItem(18.00)))
                .andExpect(jsonPath("$[*].rate", hasItem(28.00)));
    }

    @Test
    void customRateSupportsCreateUpdateAndDeactivate() throws Exception {
        Workspace workspace = workspace("gst-rate-crud");
        UUID rate = createRate(workspace, "Special GST", "3.00", "1.00");
        mockMvc.perform(patch(gst(workspace, "/rates/" + rate))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(rateBody("Updated GST", "4.00", "1.00"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rate").value(4.00));
        mockMvc.perform(delete(gst(workspace, "/rates/" + rate)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(gst(workspace, "/rates")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='" + rate + "')].active").value(false));
    }

    @Test
    void duplicateRateCombinationIsRejected() throws Exception {
        Workspace workspace = workspace("gst-rate-duplicate");
        mockMvc.perform(post(gst(workspace, "/rates")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(rateBody("Duplicate 18", "18", "0"))))
                .andExpect(status().isConflict());
    }

    @Test
    void hsnSacSupportsCreateUpdateAndDeactivate() throws Exception {
        Workspace workspace = workspace("hsn-crud");
        UUID code = createHsn(workspace, "9983", "SAC", "18.00", "0.00");
        mockMvc.perform(patch(gst(workspace, "/hsn-sac/" + code))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(hsnBody("9983", "SAC", "Business support services", "12.00", "0.00"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gstRate").value(12.00));
        mockMvc.perform(delete(gst(workspace, "/hsn-sac/" + code)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isNoContent());
    }

    @Test
    void hsnSacSearchUsesCodeAndDescription() throws Exception {
        Workspace workspace = workspace("hsn-search");
        createHsn(workspace, "1001", "HSN", "5.00", "0.00");
        createHsn(workspace, "9983", "SAC", "18.00", "0.00");
        mockMvc.perform(get(gst(workspace, "/hsn-sac")).queryParam("search", "9983")
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("9983"));
        mockMvc.perform(get(gst(workspace, "/hsn-sac")).queryParam("search", "Business")
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].code", hasItem("9983")));
    }

    @Test
    void gstRulesSupportFullLifecycle() throws Exception {
        Workspace workspace = workspace("gst-rule");
        MvcResult create = mockMvc.perform(post(gst(workspace, "/rules"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("Transport RCM", "REVERSE_CHARGE", "9965", "5.00", true))))
                .andExpect(status().isCreated()).andReturn();
        UUID id = id(create);
        mockMvc.perform(get(gst(workspace, "/rules")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].reverseCharge").value(true));
        mockMvc.perform(patch(gst(workspace, "/rules/" + id)).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(ruleBody("Transport RCM Updated", "REVERSE_CHARGE", "9965", "12.00", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gstRate").value(12.00));
        mockMvc.perform(delete(gst(workspace, "/rules/" + id)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isNoContent());
    }

    @Test
    void validGstinPassesValidation() throws Exception {
        Workspace workspace = workspace("gstin-valid");
        mockMvc.perform(post(gst(workspace, "/validate-gstin")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gstin", "27ABCDE1234F1Z5", "expectedStateCode", "27"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.stateCode").value("27"));
    }

    @Test
    void invalidGstinFormatReturnsValidationErrors() throws Exception {
        Workspace workspace = workspace("gstin-invalid");
        mockMvc.perform(post(gst(workspace, "/validate-gstin")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gstin", "27INVALID000000"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors.length()").value(1));
    }

    @Test
    void gstinStateMismatchIsDetected() throws Exception {
        Workspace workspace = workspace("gstin-state");
        mockMvc.perform(post(gst(workspace, "/validate-gstin")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gstin", "29ABCDE1234F1Z5", "expectedStateCode", "27"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0]").value("GSTIN state code does not match the expected state."));
    }

    @Test
    void intraStateInvoiceCalculatesEqualCgstAndSgst() throws Exception {
        InvoiceFixture fixture = invoice("gst-intra", "SALES", "27", "NORMAL", "18.00", "0.00", true, true);
        mockMvc.perform(get(invoiceUrl(fixture)).header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cgstTotal").value(90.00))
                .andExpect(jsonPath("$.sgstTotal").value(90.00)).andExpect(jsonPath("$.igstTotal").value(0.00))
                .andExpect(jsonPath("$.total").value(1180.00));
    }

    @Test
    void interStateInvoiceCalculatesIgst() throws Exception {
        InvoiceFixture fixture = invoice("gst-inter", "SALES", "29", "NORMAL", "18.00", "0.00", true, true);
        mockMvc.perform(get(invoiceUrl(fixture)).header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cgstTotal").value(0.00))
                .andExpect(jsonPath("$.sgstTotal").value(0.00)).andExpect(jsonPath("$.igstTotal").value(180.00));
    }

    @Test
    void cessIsCalculatedAndIncludedInInvoiceTotal() throws Exception {
        InvoiceFixture fixture = invoice("gst-cess", "SALES", "27", "NORMAL", "28.00", "12.00", true, true);
        mockMvc.perform(get(invoiceUrl(fixture)).header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cessTotal").value(120.00))
                .andExpect(jsonPath("$.total").value(1400.00))
                .andExpect(jsonPath("$.items[0].cessAmount").value(120.00));
    }

    @Test
    void compositionTreatmentSuppressesGstAndCess() throws Exception {
        InvoiceFixture fixture = invoice(
                "gst-composition", "SALES", "27", "COMPOSITION", "18.00", "12.00", true, false);
        mockMvc.perform(get(invoiceUrl(fixture)).header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.gstTreatment").value("COMPOSITION"))
                .andExpect(jsonPath("$.cgstTotal").value(0.00)).andExpect(jsonPath("$.cessTotal").value(0.00))
                .andExpect(jsonPath("$.total").value(1000.00));
    }

    @Test
    void reverseChargePurchasePostsBalancedTaxLiabilityAndCredit() throws Exception {
        InvoiceFixture fixture = invoice(
                "gst-rcm", "PURCHASE", "27", "REVERSE_CHARGE", "18.00", "0.00", true, true);
        approveAndPost(fixture);
        mockMvc.perform(get(invoiceUrl(fixture)).header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1000.00))
                .andExpect(jsonPath("$.cgstTotal").value(90.00));
        mockMvc.perform(get(gst(fixture.workspace(), "/reports/summary"))
                        .queryParam("date_from", "2026-06-01").queryParam("date_to", "2026-06-30")
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reverseChargeLiability").value(180.00))
                .andExpect(jsonPath("$.inputGst").value(180.00)).andExpect(jsonPath("$.netLiability").value(0.00));
    }

    @Test
    void duplicateGstInvoiceIsRejected() throws Exception {
        InvoiceFixture fixture = invoice("gst-duplicate", "SALES", "27", "NORMAL", "18.00", "0.00", true, true);
        mockMvc.perform(post(company(fixture.workspace(), "/invoices"))
                        .header(auth(), bearer(fixture.workspace().token())).contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody("SALES", "INV-001", fixture.partyId(), fixture.itemId(),
                                "27", "NORMAL", "18.00", "0.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void missingGstinCreatesRiskAlert() throws Exception {
        InvoiceFixture fixture = invoice("gst-missing-gstin", "SALES", "27", "NORMAL", "18", "0", false, true);
        mockMvc.perform(post(gst(fixture.workspace(), "/invoices/" + fixture.invoiceId() + "/scan"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.issues[*].code", hasItem("MISSING_GSTIN")));
    }

    @Test
    void nonMasterGstRateCreatesRiskAlert() throws Exception {
        InvoiceFixture fixture = invoice("gst-wrong-rate", "SALES", "27", "NORMAL", "7", "0", true, false);
        mockMvc.perform(post(gst(fixture.workspace(), "/invoices/" + fixture.invoiceId() + "/scan"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issues[*].code", hasItem("WRONG_GST_RATE")));
    }

    @Test
    void unknownHsnCreatesRiskAlert() throws Exception {
        InvoiceFixture fixture = invoice("gst-wrong-hsn", "SALES", "27", "NORMAL", "18", "0", true, false);
        mockMvc.perform(post(gst(fixture.workspace(), "/invoices/" + fixture.invoiceId() + "/scan"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.issues[*].code", hasItem("WRONG_HSN_SAC")));
    }

    @Test
    void salesGstRegisterUsesOnlyPostedSalesInvoices() throws Exception {
        InvoiceFixture fixture = invoice("gst-sales-register", "SALES", "27", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        mockMvc.perform(get(gst(fixture.workspace(), "/reports/sales-register"))
                        .queryParam("date_from", "2026-06-01").queryParam("date_to", "2026-06-30")
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoices.length()").value(1))
                .andExpect(jsonPath("$.taxableAmount").value(1000.00)).andExpect(jsonPath("$.cgst").value(90.00));
    }

    @Test
    void purchaseGstRegisterUsesOnlyPostedPurchaseInvoices() throws Exception {
        InvoiceFixture fixture = invoice(
                "gst-purchase-register", "PURCHASE", "29", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        mockMvc.perform(get(gst(fixture.workspace(), "/reports/purchase-register"))
                        .queryParam("date_from", "2026-06-01").queryParam("date_to", "2026-06-30")
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invoices.length()").value(1))
                .andExpect(jsonPath("$.igst").value(180.00));
    }

    @Test
    void summaryInputCreditAndOutputLiabilityAreCalculated() throws Exception {
        Workspace workspace = workspace("gst-summary");
        InvoiceFixture sales = invoice(workspace, "SALES", "27", "NORMAL", "18", "0", true, true, "SAL-1");
        InvoiceFixture purchase = invoice(workspace, "PURCHASE", "27", "NORMAL", "18", "0", true, false, "PUR-1");
        approveAndPost(sales);
        approveAndPost(purchase);
        for (String report : List.of("summary", "input-credit", "output-liability")) {
            mockMvc.perform(get(gst(workspace, "/reports/" + report)).queryParam("date_from", "2026-06-01")
                            .queryParam("date_to", "2026-06-30").header(auth(), bearer(workspace.token())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.outputGst").value(180.00))
                    .andExpect(jsonPath("$.inputGst").value(180.00));
        }
    }

    @Test
    void monthlySummaryReturnsTwelvePeriods() throws Exception {
        InvoiceFixture fixture = invoice("gst-monthly", "SALES", "27", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        mockMvc.perform(get(gst(fixture.workspace(), "/reports/monthly")).queryParam("year", "2026")
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[5].outputGst").value(180.00));
    }

    @Test
    void quarterlyAndYearlySummariesAggregatePostedTax() throws Exception {
        InvoiceFixture fixture = invoice("gst-period", "SALES", "27", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        mockMvc.perform(get(gst(fixture.workspace(), "/reports/quarterly")).queryParam("year", "2026")
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].outputGst").value(180.00));
        mockMvc.perform(get(gst(fixture.workspace(), "/reports/yearly")).queryParam("year", "2026")
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outputGst").value(180.00));
    }

    @Test
    void gstDashboardShowsLiabilityPendingFilingAndCompliance() throws Exception {
        InvoiceFixture fixture = invoice("gst-dashboard", "SALES", "27", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        mockMvc.perform(get(gst(fixture.workspace(), "/dashboard"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outputGst").value(180.00))
                .andExpect(jsonPath("$.pendingFiling").value(1))
                .andExpect(jsonPath("$.complianceScore").value(100));
    }

    @Test
    void gstr1DraftProducesJsonStructureAndCsv() throws Exception {
        InvoiceFixture fixture = invoice("gstr1", "SALES", "27", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        UUID returnId = generateReturn(fixture.workspace(), "GSTR1");
        mockMvc.perform(get(gst(fixture.workspace(), "/returns/" + returnId))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.jsonStructure.return_type").value("GSTR1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sectionCode").value("B2B"));
        mockMvc.perform(get(gst(fixture.workspace(), "/returns/" + returnId + "/csv"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gstr1-draft.csv\""));
    }

    @Test
    void gstr3bDraftIncludesPurchasesAndCanBeFinalized() throws Exception {
        InvoiceFixture fixture = invoice("gstr3b", "PURCHASE", "27", "NORMAL", "18", "0", true, true);
        approveAndPost(fixture);
        UUID returnId = generateReturn(fixture.workspace(), "GSTR3B");
        mockMvc.perform(get(gst(fixture.workspace(), "/returns/" + returnId))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sectionCode").value("4_ITC"));
        mockMvc.perform(post(gst(fixture.workspace(), "/returns/" + returnId + "/finalize"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINALIZED"));
    }

    @Test
    void riskAlertsCanBeResolved() throws Exception {
        InvoiceFixture fixture = invoice("gst-alert", "SALES", "27", "NORMAL", "7", "0", false, false);
        mockMvc.perform(post(gst(fixture.workspace(), "/invoices/" + fixture.invoiceId() + "/scan"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk());
        MvcResult list = mockMvc.perform(get(gst(fixture.workspace(), "/alerts"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andReturn();
        UUID alertId = UUID.fromString(responseJson(list).get(0).get("id").asText());
        mockMvc.perform(post(gst(fixture.workspace(), "/alerts/" + alertId + "/resolve"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resolved").value(true));
    }

    @Test
    void viewerIsReadOnlyAndCrossCompanyAccessIsDenied() throws Exception {
        Workspace owner = workspace("gst-owner");
        Workspace outsider = workspace("gst-outsider");
        UserSession viewer = signup("GST Viewer", uniqueEmail("gst-viewer"));
        mockMvc.perform(post(company(owner, "/members")).header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", viewer.email(), "role", "VIEWER"))))
                .andExpect(status().isCreated());
        mockMvc.perform(get(gst(owner, "/rates")).header(auth(), bearer(viewer.token())))
                .andExpect(status().isOk());
        mockMvc.perform(post(gst(owner, "/rates")).header(auth(), bearer(viewer.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(rateBody("Viewer Rate", "3", "0"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(gst(owner, "/dashboard")).header(auth(), bearer(outsider.token())))
                .andExpect(status().isForbidden());
    }

    private UUID generateReturn(Workspace workspace, String type) throws Exception {
        MvcResult result = mockMvc.perform(post(gst(workspace, "/returns"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("returnType", type, "periodStart", "2026-06-01",
                                "periodEnd", "2026-06-30"))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private void approveAndPost(InvoiceFixture fixture) throws Exception {
        mockMvc.perform(post(company(fixture.workspace(), "/invoices/" + fixture.invoiceId() + "/approve"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk());
        mockMvc.perform(post(company(fixture.workspace(), "/invoices/" + fixture.invoiceId() + "/post"))
                        .header(auth(), bearer(fixture.workspace().token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("POSTED"));
    }

    private InvoiceFixture invoice(
            String prefix, String type, String state, String treatment, String gstRate,
            String cessRate, boolean withGstin, boolean withMaster) throws Exception {
        return invoice(workspace(prefix), type, state, treatment, gstRate, cessRate, withGstin, withMaster, "INV-001");
    }

    private InvoiceFixture invoice(
            Workspace workspace, String type, String state, String treatment, String gstRate,
            String cessRate, boolean withGstin, boolean withMaster, String number) throws Exception {
        if (withMaster) createHsn(workspace, "9983", "SAC", gstRate, cessRate);
        String gstin = withGstin ? state + "ABCDE1234F1Z5" : null;
        UUID party = "SALES".equals(type)
                ? createCustomer(workspace, "GST Customer " + number, gstin, state)
                : createVendor(workspace, "GST Vendor " + number, gstin, state);
        UUID item = createItem(workspace, "GST Service " + number, "GST-" + UUID.randomUUID(), "9983", gstRate);
        MvcResult result = mockMvc.perform(post(company(workspace, "/invoices"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(invoiceBody(type, number, party, item, state, treatment, gstRate, cessRate)))
                .andExpect(status().isCreated()).andReturn();
        return new InvoiceFixture(workspace, id(result), party, item);
    }

    private String invoiceBody(
            String type, String number, UUID party, UUID item, String state,
            String treatment, String gstRate, String cessRate) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("invoiceType", type);
        body.put("invoiceNumber", number);
        body.put("invoiceDate", "2026-06-30");
        body.put("dueDate", "2026-07-15");
        body.put("placeOfSupply", state);
        body.put("gstTreatment", treatment);
        body.put("notes", "GST integration invoice");
        body.put("items", List.of(Map.of(
                "itemId", item.toString(), "description", "GST service", "quantity", "1.0000",
                "unitPrice", "1000.00", "gstRate", gstRate, "cessRate", cessRate)));
        body.put("SALES".equals(type) ? "customerId" : "vendorId", party.toString());
        return json(body);
    }

    private UUID createCustomer(Workspace workspace, String name, String gstin, String state) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name); body.put("displayName", name); body.put("state", state);
        body.put("openingBalance", "0.00");
        if (gstin != null) body.put("gstin", gstin);
        MvcResult result = mockMvc.perform(post(company(workspace, "/customers"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))).andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private UUID createVendor(Workspace workspace, String name, String gstin, String state) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name); body.put("displayName", name); body.put("state", state);
        body.put("openingBalance", "0.00");
        if (gstin != null) body.put("gstin", gstin);
        MvcResult result = mockMvc.perform(post(company(workspace, "/vendors"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(body))).andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private UUID createItem(Workspace workspace, String name, String sku, String hsn, String gstRate) throws Exception {
        MvcResult result = mockMvc.perform(post(company(workspace, "/items"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "type", "SERVICE", "sku", sku, "hsnSac", hsn,
                                "unit", "NOS", "salesPrice", "1000", "purchasePrice", "1000",
                                "gstRate", gstRate))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private UUID createHsn(Workspace workspace, String code, String type, String gstRate, String cessRate)
            throws Exception {
        MvcResult result = mockMvc.perform(post(gst(workspace, "/hsn-sac"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(hsnBody(code, type, "Business support services", gstRate, cessRate))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private UUID createRate(Workspace workspace, String name, String rate, String cess) throws Exception {
        MvcResult result = mockMvc.perform(post(gst(workspace, "/rates"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(rateBody(name, rate, cess))))
                .andExpect(status().isCreated()).andReturn();
        return id(result);
    }

    private Map<String, Object> rateBody(String name, String rate, String cess) {
        return Map.of("name", name, "rate", rate, "cessRate", cess, "reverseChargeAllowed", true, "active", true);
    }
    private Map<String, Object> hsnBody(
            String code, String type, String description, String gstRate, String cessRate) {
        return Map.of("code", code, "codeType", type, "description", description,
                "gstRate", gstRate, "cessRate", cessRate, "active", true);
    }
    private Map<String, Object> ruleBody(
            String name, String treatment, String prefix, String rate, boolean reverseCharge) {
        return Map.of("name", name, "gstTreatment", treatment, "hsnSacPrefix", prefix,
                "gstRate", rate, "cessRate", "0", "reverseCharge", reverseCharge, "active", true);
    }

    private Workspace workspace(String prefix) throws Exception {
        UserSession owner = signup("GST Owner", uniqueEmail(prefix));
        MvcResult result = mockMvc.perform(post("/api/companies").header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("legalName", prefix + " Company", "tradeName", prefix + " Company",
                                "gstin", "27ABCDE1234F1Z5", "stateCode", "27", "industry", "Trading",
                                "financialYearStart", "2026-04-01", "financialYearEnd", "2027-03-31"))))
                .andExpect(status().isCreated()).andReturn();
        return new Workspace(owner.token(), id(result));
    }

    private UserSession signup(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated()).andReturn();
        return new UserSession(responseJson(result).get("accessToken").asText(), email);
    }

    private String gst(Workspace workspace, String suffix) { return company(workspace, "/gst" + suffix); }
    private String company(Workspace workspace, String suffix) {
        return "/api/companies/" + workspace.companyId() + suffix;
    }
    private String invoiceUrl(InvoiceFixture fixture) {
        return company(fixture.workspace(), "/invoices/" + fixture.invoiceId());
    }
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
    private record InvoiceFixture(Workspace workspace, UUID invoiceId, UUID partyId, UUID itemId) { }
}
