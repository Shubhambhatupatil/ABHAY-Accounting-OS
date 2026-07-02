package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.anvritai.abhay.repository.document.DocumentReviewActionRepository;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentIntelligenceIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DocumentReviewActionRepository reviewActions;

    @Test
    void uploadsDocumentMetadataWithoutExposingStoragePath() throws Exception {
        Workspace workspace = workspace("doc-upload");
        MvcResult result = upload(workspace, "PURCHASE_INVOICE", "invoice.csv", invoiceText("DOC-UP-1"));
        mockMvc.perform(get(documents(workspace, "/" + id(result))).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.originalFileName").value("invoice.csv"))
                .andExpect(jsonPath("$.fileType").value("CSV"))
                .andExpect(jsonPath("$.processingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    void rejectsUnsupportedFileType() throws Exception {
        Workspace workspace = workspace("doc-type");
        mockMvc.perform(multipart(documents(workspace, "/upload"))
                        .file(file("file", "unsafe.txt", "text/plain", "invoice".getBytes(StandardCharsets.UTF_8)))
                        .param("documentType", "OTHER").header(auth(), bearer(workspace.token())))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void rejectsFileOverTenMegabytes() throws Exception {
        Workspace workspace = workspace("doc-size");
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        Arrays.fill(oversized, (byte) 'A');
        mockMvc.perform(multipart(documents(workspace, "/upload"))
                        .file(file("file", "large.csv", "text/csv", oversized))
                        .param("documentType", "OTHER").header(auth(), bearer(workspace.token())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Document must be between 1 byte and 10 MB."));
    }

    @Test
    void detectsExactDuplicateWithinCompany() throws Exception {
        Workspace workspace = workspace("doc-duplicate");
        byte[] content = invoiceText("DUP-1");
        upload(workspace, "PURCHASE_INVOICE", "first.csv", content);
        MvcResult duplicate = upload(workspace, "PURCHASE_INVOICE", "second.csv", content);
        mockMvc.perform(get(documents(workspace, "/" + id(duplicate))).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
        mockMvc.perform(get(documents(workspace, "/duplicates")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchType").value("SHA256_EXACT"));
    }

    @Test
    void savesExtractedAccountingFields() throws Exception {
        Workspace workspace = workspace("doc-fields");
        UUID document = id(upload(workspace, "PURCHASE_INVOICE", "fields.csv", invoiceText("FIELD-1")));
        mockMvc.perform(get(documents(workspace, "/" + document + "/fields"))
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].fieldName", hasItem("gstin")))
                .andExpect(jsonPath("$[*].fieldName", hasItem("invoice_number")))
                .andExpect(jsonPath("$[*].fieldName", hasItem("total_amount")))
                .andExpect(jsonPath("$[?(@.fieldName=='total_amount')].normalizedValue").value("1180"));
    }

    @Test
    void fieldCorrectionCreatesReviewActionAndMemorySafeState() throws Exception {
        Workspace workspace = workspace("doc-correction");
        UUID document = id(upload(workspace, "PURCHASE_INVOICE", "correction.csv", invoiceText("CORR-1")));
        UUID totalField = fieldId(workspace, document, "total_amount");
        mockMvc.perform(patch(documents(workspace, "/" + document + "/fields/" + totalField))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("normalizedValue", "1200.00", "comment", "Verified against bill"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.corrected").value(true))
                .andExpect(jsonPath("$.confidenceScore").value(1.0000));
        if (reviewActions.countByCompanyIdAndDocumentIdAndAction(
                workspace.companyId(), document, "FIELD_CORRECTED") != 1) {
            throw new AssertionError("Field correction review action was not saved.");
        }
    }

    @Test
    void approvesExtractedDocument() throws Exception {
        Workspace workspace = workspace("doc-approve");
        UUID document = id(upload(workspace, "PURCHASE_INVOICE", "approve.csv", invoiceText("APP-1")));
        mockMvc.perform(post(documents(workspace, "/" + document + "/approve"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "Amounts verified"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectsDocumentAndPreservesAuditVisibility() throws Exception {
        Workspace workspace = workspace("doc-reject");
        UUID document = id(upload(workspace, "OTHER", "reject.csv", "Unknown content".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(documents(workspace, "/" + document + "/reject"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "Not an accounting document"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(get(company(workspace, "/audit-logs")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].action", hasItem("DOCUMENT_REJECTED")));
    }

    @Test
    void convertsApprovedPurchaseDocumentToRealInvoiceDraft() throws Exception {
        Workspace workspace = workspace("doc-invoice");
        UUID vendor = createVendor(workspace);
        UUID document = id(upload(workspace, "PURCHASE_INVOICE", "purchase.csv", invoiceText("PUR-DOC-1")));
        approve(workspace, document);
        MvcResult conversion = mockMvc.perform(post(documents(workspace, "/" + document + "/convert-to-invoice"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("vendorId", vendor, "dueDate", "2026-07-15", "placeOfSupply", "27"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.invoiceId").isNotEmpty()).andReturn();
        UUID invoice = UUID.fromString(responseJson(conversion).get("invoiceId").asText());
        mockMvc.perform(get(company(workspace, "/invoices/" + invoice)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.total").value(1180.00));
    }

    @Test
    void convertsApprovedDocumentToBalancedVoucherDraft() throws Exception {
        Workspace workspace = workspace("doc-voucher");
        UUID cash = ledgerId(workspace, "Cash");
        UUID bank = ledgerId(workspace, "Primary Bank");
        UUID document = id(upload(workspace, "RECEIPT", "receipt.csv", receiptText("RCPT-DOC-1")));
        approve(workspace, document);
        MvcResult conversion = mockMvc.perform(post(documents(workspace, "/" + document + "/convert-to-voucher"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("voucherTypeCode", "RECEIPT", "debitLedgerId", cash,
                                "creditLedgerId", bank, "narration", "Reviewed receipt document"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.voucherId").isNotEmpty()).andReturn();
        UUID voucher = UUID.fromString(responseJson(conversion).get("voucherId").asText());
        mockMvc.perform(get(company(workspace, "/vouchers/" + voucher)).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalDebit").value(500.00))
                .andExpect(jsonPath("$.totalCredit").value(500.00));
    }

    @Test
    void viewerCannotUploadReviewOrConvert() throws Exception {
        Workspace owner = workspace("doc-viewer-owner");
        UUID document = id(upload(owner, "RECEIPT", "secure.csv", receiptText("SEC-DOC-1")));
        approve(owner, document);
        UserSession viewer = signup("Document Viewer", uniqueEmail("doc-viewer"));
        addMember(owner, viewer.email(), "VIEWER");
        mockMvc.perform(multipart(documents(owner, "/upload"))
                        .file(file("file", "denied.csv", "text/csv", receiptText("DENIED")))
                        .param("documentType", "RECEIPT").header(auth(), bearer(viewer.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(documents(owner, "/" + document + "/convert-to-voucher"))
                        .header(auth(), bearer(viewer.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("debitLedgerId", ledgerId(owner, "Cash"),
                                "creditLedgerId", ledgerId(owner, "Primary Bank")))))
                .andExpect(status().isForbidden());
    }

    @Test
    void documentAccessIsCompanyScoped() throws Exception {
        Workspace owner = workspace("doc-scope-owner");
        Workspace outsider = workspace("doc-scope-outsider");
        UUID document = id(upload(owner, "OTHER", "private.csv", "Private accounting record".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(get(documents(owner, "/" + document)).header(auth(), bearer(outsider.token())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(documents(owner, "")).header(auth(), bearer(outsider.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardReportsPersistedLifecycleCounts() throws Exception {
        Workspace workspace = workspace("doc-dashboard");
        UUID approved = id(upload(workspace, "PURCHASE_INVOICE", "approved.csv", invoiceText("DASH-1")));
        approve(workspace, approved);
        UUID rejected = id(upload(workspace, "OTHER", "rejected.csv", "Other record".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(post(documents(workspace, "/" + rejected + "/reject"))
                .header(auth(), bearer(workspace.token()))).andExpect(status().isOk());
        mockMvc.perform(get(documents(workspace, "/dashboard")).header(auth(), bearer(workspace.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalDocuments").value(2))
                .andExpect(jsonPath("$.approved").value(1)).andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.byType.PURCHASE_INVOICE").value(1));
    }

    private MvcResult upload(Workspace workspace, String type, String name, byte[] content) throws Exception {
        return mockMvc.perform(multipart(documents(workspace, "/upload"))
                        .file(file("file", name, "text/csv", content)).param("documentType", type)
                        .header(auth(), bearer(workspace.token())))
                .andExpect(status().isCreated()).andReturn();
    }
    private void approve(Workspace workspace, UUID document) throws Exception {
        mockMvc.perform(post(documents(workspace, "/" + document + "/approve"))
                .header(auth(), bearer(workspace.token()))).andExpect(status().isOk());
    }
    private UUID fieldId(Workspace workspace, UUID document, String name) throws Exception {
        JsonNode values = responseJson(mockMvc.perform(get(documents(workspace, "/" + document + "/fields"))
                .header(auth(), bearer(workspace.token()))).andExpect(status().isOk()).andReturn());
        for (JsonNode value : values) if (name.equals(value.get("fieldName").asText())) return UUID.fromString(value.get("id").asText());
        throw new AssertionError("Field not found: " + name);
    }
    private byte[] invoiceText(String number) {
        return ("Vendor: Test Vendor\nInvoice No: " + number + "\nInvoice Date: 2026-06-30\n"
                + "GSTIN: 27ABCDE1234F1Z5\nTaxable Amount: 1000\nCGST: 90\nSGST: 90\nTotal Amount: 1180\nHSN: 9983\n")
                .getBytes(StandardCharsets.UTF_8);
    }
    private byte[] receiptText(String number) {
        return ("Payment Reference: " + number + "\nTotal Amount: 500\n").getBytes(StandardCharsets.UTF_8);
    }
    private MockMultipartFile file(String part, String name, String type, byte[] content) {
        return new MockMultipartFile(part, name, type, content);
    }
    private UUID createVendor(Workspace workspace) throws Exception {
        return id(mockMvc.perform(post(company(workspace, "/vendors"))
                        .header(auth(), bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Test Vendor", "gstin", "27ABCDE1234F1Z5", "state", "27"))))
                .andExpect(status().isCreated()).andReturn());
    }
    private UUID ledgerId(Workspace workspace, String name) throws Exception {
        JsonNode values = responseJson(mockMvc.perform(get(company(workspace, "/ledgers"))
                .header(auth(), bearer(workspace.token()))).andExpect(status().isOk()).andReturn());
        for (JsonNode value : values) if (name.equals(value.get("name").asText())) return UUID.fromString(value.get("id").asText());
        throw new AssertionError("Ledger not found: " + name);
    }
    private void addMember(Workspace workspace, String email, String role) throws Exception {
        mockMvc.perform(post(company(workspace, "/members")).header(auth(), bearer(workspace.token()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "role", role))))
                .andExpect(status().isCreated());
    }
    private Workspace workspace(String prefix) throws Exception {
        UserSession owner = signup("Document Owner", uniqueEmail(prefix));
        MvcResult company = mockMvc.perform(post("/api/companies").header(auth(), bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("legalName", prefix + " Company", "stateCode", "27", "industry", "Trading",
                                "financialYearStart", "2026-04-01", "financialYearEnd", "2027-03-31"))))
                .andExpect(status().isCreated()).andReturn();
        return new Workspace(owner.token(), id(company));
    }
    private UserSession signup(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "StrongPass123!"))))
                .andExpect(status().isCreated()).andReturn();
        return new UserSession(responseJson(result).get("accessToken").asText(), email);
    }
    private String documents(Workspace workspace, String suffix) { return company(workspace, "/documents" + suffix); }
    private String company(Workspace workspace, String suffix) { return "/api/companies/" + workspace.companyId() + suffix; }
    private UUID id(MvcResult result) throws Exception { return UUID.fromString(responseJson(result).get("id").asText()); }
    private JsonNode responseJson(MvcResult result) throws Exception { return objectMapper.readTree(result.getResponse().getContentAsString()); }
    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String auth() { return "Authorization"; } private String bearer(String token) { return "Bearer " + token; }
    private String uniqueEmail(String prefix) { return prefix + "-" + UUID.randomUUID() + "@abhay.test"; }
    private record UserSession(String token, String email) { }
    private record Workspace(String token, UUID companyId) { }
}
