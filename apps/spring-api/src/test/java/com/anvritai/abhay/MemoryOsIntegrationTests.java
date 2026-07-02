package com.anvritai.abhay;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class MemoryOsIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void voucherPostingCapturesAppendOnlyMemoryEvent() throws Exception {
        Workspace workspace=workspace("memory-voucher");
        postVoucher(workspace,"Office electricity payment");
        mockMvc.perform(get(memory(workspace,"/events")).header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$[*].eventType",hasItem("VOUCHER_POSTED")))
          .andExpect(jsonPath("$[?(@.eventType=='VOUCHER_POSTED')].memoryType",hasItem("VOUCHER_PATTERN_MEMORY")));
    }

    @Test
    void invoicePostingCapturesPartyAndGstMemory() throws Exception {
        Workspace workspace=workspace("memory-invoice");
        postSalesInvoice(workspace,"Memory Customer","MEM-INV-1");
        mockMvc.perform(get(memory(workspace,"/events")).header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$[*].eventType",hasItem("INVOICE_POSTED")))
          .andExpect(jsonPath("$[?(@.eventType=='INVOICE_POSTED')].context.party",hasItem("Memory Customer")))
          .andExpect(jsonPath("$[?(@.eventType=='INVOICE_POSTED')].context.gstTreatment",hasItem("NORMAL")));
    }

    @Test
    void documentCorrectionCapturesCorrectionMemory() throws Exception {
        Workspace workspace=workspace("memory-document");
        UUID document=uploadDocument(workspace,"MEM-DOC-1");UUID field=fieldId(workspace,document,"total_amount");
        mockMvc.perform(patch(documents(workspace,"/"+document+"/fields/"+field))
          .header(auth(),bearer(workspace.token())).contentType(MediaType.APPLICATION_JSON)
          .content(json(Map.of("normalizedValue","1250.00","comment","Verified correction"))))
          .andExpect(status().isOk());
        mockMvc.perform(get(memory(workspace,"/events")).header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$[*].eventType",hasItem("DOCUMENT_FIELD_CORRECTED")))
          .andExpect(jsonPath("$[?(@.eventType=='DOCUMENT_FIELD_CORRECTED')].context.newValue",hasItem("1250.00")));
    }

    @Test
    void patternRebuildIsIdempotent() throws Exception {
        Workspace workspace=workspace("memory-rebuild");postSalesInvoice(workspace,"Pattern Customer","PAT-1");
        mockMvc.perform(post(memory(workspace,"/rebuild-patterns")).header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$.patternsCreatedOrUpdated",greaterThan(0)));
        JsonNode first=patterns(workspace);
        mockMvc.perform(post(memory(workspace,"/rebuild-patterns")).header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk());
        JsonNode second=patterns(workspace);
        if(first.size()!=second.size())throw new AssertionError("Idempotent rebuild changed pattern count.");
        if(first.get(0).get("occurrenceCount").asLong()!=second.get(0).get("occurrenceCount").asLong())
          throw new AssertionError("Idempotent rebuild duplicated event occurrences.");
    }

    @Test
    void ledgerSuggestionIncludesExplanationAndEvidence() throws Exception {
        Workspace workspace=workspace("memory-ledger");postSalesInvoice(workspace,"Ledger Memory Customer","LED-1");rebuild(workspace);
        mockMvc.perform(get(memory(workspace,"/suggestions/ledger")).param("subject","Ledger Memory Customer")
          .header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$.suggestedValue").isNotEmpty())
          .andExpect(jsonPath("$.reason",containsString("observed")))
          .andExpect(jsonPath("$.supportingPreviousEvents").value(1))
          .andExpect(jsonPath("$.humanApprovalRequired").value(true));
    }

    @Test
    void gstSuggestionReturnsMeasuredConfidence() throws Exception {
        Workspace workspace=workspace("memory-gst");postSalesInvoice(workspace,"GST Memory Customer","GST-MEM-1");rebuild(workspace);
        mockMvc.perform(get(memory(workspace,"/suggestions/gst")).param("subject","GST Memory Customer")
          .header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$.suggestedValue").value("NORMAL"))
          .andExpect(jsonPath("$.confidenceScore").value(0.5000))
          .andExpect(jsonPath("$.lowConfidence").value(true));
    }

    @Test
    void feedbackRaisesAndLowersPatternConfidence() throws Exception {
        Workspace workspace=workspace("memory-feedback");postSalesInvoice(workspace,"Feedback Customer","FDB-1");rebuild(workspace);
        JsonNode first=suggestion(workspace,"ledger","Feedback Customer");
        MvcResult accepted=mockMvc.perform(post(memory(workspace,"/feedback")).header(auth(),bearer(workspace.token()))
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("suggestionId",first.get("id").asText(),"action","ACCEPTED"))))
          .andExpect(status().isOk()).andReturn();
        double improved=responseJson(accepted).get("updatedConfidence").asDouble();
        if(improved<=first.get("confidenceScore").asDouble())throw new AssertionError("Acceptance did not improve confidence.");
        JsonNode second=suggestion(workspace,"ledger","Feedback Customer");
        MvcResult rejected=mockMvc.perform(post(memory(workspace,"/feedback")).header(auth(),bearer(workspace.token()))
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("suggestionId",second.get("id").asText(),"action","REJECTED"))))
          .andExpect(status().isOk()).andReturn();
        if(responseJson(rejected).get("updatedConfidence").asDouble()>=improved)
          throw new AssertionError("Rejection did not reduce confidence.");
        JsonNode third=suggestion(workspace,"ledger","Feedback Customer");
        mockMvc.perform(post(memory(workspace,"/feedback")).header(auth(),bearer(workspace.token()))
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("suggestionId",third.get("id").asText(),
            "action","CORRECTED","correctedValue","corrected-ledger"))))
          .andExpect(status().isOk()).andExpect(jsonPath("$.suggestedValue").value("corrected-ledger"));
        mockMvc.perform(get(memory(workspace,"/suggestions/ledger")).param("subject","Feedback Customer")
          .header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$.suggestedValue").value("corrected-ledger"));
    }

    @Test
    void unknownBehaviorReturnsSafeLowConfidenceSuggestion() throws Exception {
        Workspace workspace=workspace("memory-low");
        mockMvc.perform(get(memory(workspace,"/suggestions/voucher")).param("subject","Never seen behavior")
          .header(auth(),bearer(workspace.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$.suggestedValue").doesNotExist())
          .andExpect(jsonPath("$.confidenceScore").value(0.0000)).andExpect(jsonPath("$.lowConfidence").value(true))
          .andExpect(jsonPath("$.warning",containsString("Low confidence")))
          .andExpect(jsonPath("$.humanApprovalRequired").value(true));
    }

    @Test
    void purgeRequiresOwnerAndWritesAudit() throws Exception {
        Workspace owner=workspace("memory-purge-owner");postVoucher(owner,"Purge test");rebuild(owner);
        UserSession admin=signup("Memory Admin",uniqueEmail("memory-admin"));addMember(owner,admin.email(),"ADMIN");
        mockMvc.perform(post(memory(owner,"/purge")).header(auth(),bearer(admin.token()))
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmation","PURGE"))))
          .andExpect(status().isForbidden());
        mockMvc.perform(post(memory(owner,"/purge")).header(auth(),bearer(owner.token()))
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("confirmation","PURGE"))))
          .andExpect(status().isOk()).andExpect(jsonPath("$.eventsDeleted",greaterThan(0)));
        mockMvc.perform(get(memory(owner,"/dashboard")).header(auth(),bearer(owner.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$.totalEvents").value(0));
        mockMvc.perform(get(company(owner,"/audit-logs")).header(auth(),bearer(owner.token())))
          .andExpect(status().isOk()).andExpect(jsonPath("$[*].action",hasItem("MEMORY_PURGED")));
    }

    @Test
    void viewerCanReadButCannotWriteMemory() throws Exception {
        Workspace owner=workspace("memory-viewer-owner");postVoucher(owner,"Viewer event");
        UserSession viewer=signup("Memory Viewer",uniqueEmail("memory-viewer"));addMember(owner,viewer.email(),"VIEWER");
        mockMvc.perform(get(memory(owner,"/events")).header(auth(),bearer(viewer.token()))).andExpect(status().isOk());
        mockMvc.perform(post(memory(owner,"/rebuild-patterns")).header(auth(),bearer(viewer.token())))
          .andExpect(status().isForbidden());
        JsonNode low=suggestionWithToken(owner,"voucher","unknown",viewer.token());
        mockMvc.perform(post(memory(owner,"/feedback")).header(auth(),bearer(viewer.token()))
          .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("suggestionId",low.get("id").asText(),"action","REJECTED"))))
          .andExpect(status().isForbidden());
    }

    @Test
    void memoryEndpointsDenyCrossCompanyAccess() throws Exception {
        Workspace owner=workspace("memory-scope-owner");Workspace outsider=workspace("memory-scope-outsider");postVoucher(owner,"Private memory");
        mockMvc.perform(get(memory(owner,"/events")).header(auth(),bearer(outsider.token()))).andExpect(status().isForbidden());
        mockMvc.perform(get(memory(owner,"/suggestions/ledger")).param("subject","private")
          .header(auth(),bearer(outsider.token()))).andExpect(status().isForbidden());
    }

    private void rebuild(Workspace w)throws Exception{mockMvc.perform(post(memory(w,"/rebuild-patterns")).header(auth(),bearer(w.token()))).andExpect(status().isOk());}
    private JsonNode patterns(Workspace w)throws Exception{return responseJson(mockMvc.perform(get(memory(w,"/patterns")).header(auth(),bearer(w.token()))).andExpect(status().isOk()).andReturn());}
    private JsonNode suggestion(Workspace w,String type,String subject)throws Exception{return suggestionWithToken(w,type,subject,w.token());}
    private JsonNode suggestionWithToken(Workspace w,String type,String subject,String token)throws Exception{return responseJson(mockMvc.perform(get(memory(w,"/suggestions/"+type)).param("subject",subject).header(auth(),bearer(token))).andExpect(status().isOk()).andReturn());}

    private void postVoucher(Workspace w,String narration)throws Exception{
      UUID cash=ledgerId(w,"Cash"),bank=ledgerId(w,"Primary Bank");
      MvcResult draft=mockMvc.perform(post(company(w,"/vouchers")).header(auth(),bearer(w.token())).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("voucherTypeCode","PAYMENT","voucherDate","2026-06-30","narration",narration,"lines",List.of(
          Map.of("ledgerId",cash,"debit","100","credit","0"),Map.of("ledgerId",bank,"debit","0","credit","100"))))))
        .andExpect(status().isCreated()).andReturn();
      mockMvc.perform(post(company(w,"/vouchers/"+id(draft)+"/post")).header(auth(),bearer(w.token()))).andExpect(status().isOk());
    }
    private void postSalesInvoice(Workspace w,String customerName,String number)throws Exception{
      UUID customer=id(mockMvc.perform(post(company(w,"/customers")).header(auth(),bearer(w.token())).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("name",customerName,"state","27")))).andExpect(status().isCreated()).andReturn());
      UUID item=id(mockMvc.perform(post(company(w,"/items")).header(auth(),bearer(w.token())).contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("name","Memory Service "+number,"type","SERVICE","sku","MEM-"+UUID.randomUUID(),"unit","NOS","salesPrice","1000","purchasePrice","0","gstRate","18"))))
        .andExpect(status().isCreated()).andReturn());
      Map<String,Object> body=new LinkedHashMap<>();body.put("invoiceType","SALES");body.put("invoiceNumber",number);
      body.put("invoiceDate","2026-06-30");body.put("dueDate","2026-07-15");body.put("customerId",customer);
      body.put("placeOfSupply","27");body.put("gstTreatment","NORMAL");body.put("items",List.of(Map.of(
        "itemId",item,"description","Memory service","quantity","1","unitPrice","1000","gstRate","18","cessRate","0")));
      UUID invoice=id(mockMvc.perform(post(company(w,"/invoices")).header(auth(),bearer(w.token())).contentType(MediaType.APPLICATION_JSON).content(json(body))).andExpect(status().isCreated()).andReturn());
      mockMvc.perform(post(company(w,"/invoices/"+invoice+"/approve")).header(auth(),bearer(w.token()))).andExpect(status().isOk());
      mockMvc.perform(post(company(w,"/invoices/"+invoice+"/post")).header(auth(),bearer(w.token()))).andExpect(status().isOk());
    }
    private UUID uploadDocument(Workspace w,String number)throws Exception{
      byte[] data=("Invoice No: "+number+"\nTotal Amount: 1180\nGSTIN: 27ABCDE1234F1Z5\n").getBytes(StandardCharsets.UTF_8);
      MockMultipartFile file=new MockMultipartFile("file","memory.csv","text/csv",data);
      return id(mockMvc.perform(multipart(documents(w,"/upload")).file(file).param("documentType","PURCHASE_INVOICE").header(auth(),bearer(w.token()))).andExpect(status().isCreated()).andReturn());
    }
    private UUID fieldId(Workspace w,UUID document,String field)throws Exception{JsonNode rows=responseJson(mockMvc.perform(get(documents(w,"/"+document+"/fields")).header(auth(),bearer(w.token()))).andExpect(status().isOk()).andReturn());for(JsonNode row:rows)if(field.equals(row.get("fieldName").asText()))return UUID.fromString(row.get("id").asText());throw new AssertionError("Field not found");}
    private UUID ledgerId(Workspace w,String name)throws Exception{JsonNode rows=responseJson(mockMvc.perform(get(company(w,"/ledgers")).header(auth(),bearer(w.token()))).andExpect(status().isOk()).andReturn());for(JsonNode row:rows)if(name.equals(row.get("name").asText()))return UUID.fromString(row.get("id").asText());throw new AssertionError("Ledger not found");}
    private void addMember(Workspace w,String email,String role)throws Exception{mockMvc.perform(post(company(w,"/members")).header(auth(),bearer(w.token())).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email",email,"role",role)))).andExpect(status().isCreated());}
    private Workspace workspace(String prefix)throws Exception{UserSession owner=signup("Memory Owner",uniqueEmail(prefix));MvcResult c=mockMvc.perform(post("/api/companies").header(auth(),bearer(owner.token())).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("legalName",prefix+" Company","stateCode","27","industry","Trading","financialYearStart","2026-04-01","financialYearEnd","2027-03-31")))).andExpect(status().isCreated()).andReturn();return new Workspace(owner.token(),id(c));}
    private UserSession signup(String name,String email)throws Exception{MvcResult r=mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name",name,"email",email,"password","StrongPass123!")))).andExpect(status().isCreated()).andReturn();return new UserSession(responseJson(r).get("accessToken").asText(),email);}
    private String memory(Workspace w,String suffix){return company(w,"/memory"+suffix);}private String documents(Workspace w,String suffix){return company(w,"/documents"+suffix);}private String company(Workspace w,String suffix){return "/api/companies/"+w.companyId()+suffix;}
    private UUID id(MvcResult r)throws Exception{return UUID.fromString(responseJson(r).get("id").asText());}private JsonNode responseJson(MvcResult r)throws Exception{return objectMapper.readTree(r.getResponse().getContentAsString());}private String json(Object v)throws Exception{return objectMapper.writeValueAsString(v);}private String auth(){return "Authorization";}private String bearer(String t){return "Bearer "+t;}private String uniqueEmail(String p){return p+"-"+UUID.randomUUID()+"@abhay.test";}
    private record UserSession(String token,String email){}private record Workspace(String token,UUID companyId){}
}
