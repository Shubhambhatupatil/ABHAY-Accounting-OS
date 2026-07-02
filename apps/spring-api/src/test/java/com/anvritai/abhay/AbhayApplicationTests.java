package com.anvritai.abhay;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AbhayApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void signupAndLoginWork() throws Exception {
        String email = uniqueEmail("auth");
        AuthSession signup = signup("Auth Owner", email);

        mockMvc.perform(get("/api/me").header("Authorization", bearer(signup.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Auth Owner"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "StrongPass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void companyCreateMakesCreatorOwnerAndWritesAuditLog() throws Exception {
        AuthSession owner = signup("Company Owner", uniqueEmail("owner"));
        UUID companyId = createCompany(owner.token(), "ANVRITAI Test Company");

        mockMvc.perform(get("/api/companies").header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(companyId.toString()))
                .andExpect(jsonPath("$[0].myRole").value("OWNER"));

        mockMvc.perform(get("/api/companies/{companyId}/members", companyId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("OWNER"));

        mockMvc.perform(get("/api/companies/{companyId}/audit-logs", companyId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("COMPANY_CREATED")));
    }

    @Test
    void userCannotAccessAnotherCompany() throws Exception {
        AuthSession firstOwner = signup("First Owner", uniqueEmail("first"));
        AuthSession outsider = signup("Outsider", uniqueEmail("outsider"));
        UUID companyId = createCompany(firstOwner.token(), "Private Company");

        mockMvc.perform(get("/api/companies/{companyId}", companyId)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/companies/{companyId}/audit-logs", companyId)
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanAddMemberAndAuditIsRecorded() throws Exception {
        AuthSession owner = signup("Member Owner", uniqueEmail("member-owner"));
        String accountantEmail = uniqueEmail("accountant");
        signup("Company Accountant", accountantEmail);
        UUID companyId = createCompany(owner.token(), "Member Test Company");

        mockMvc.perform(post("/api/companies/{companyId}/members", companyId)
                        .header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", accountantEmail, "role", "ACCOUNTANT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(accountantEmail))
                .andExpect(jsonPath("$.role").value("ACCOUNTANT"));

        mockMvc.perform(get("/api/companies/{companyId}/audit-logs", companyId)
                        .header("Authorization", bearer(owner.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("MEMBER_ADDED")));
    }

    private AuthSession signup(String name, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "email", email,
                                "password", "StrongPass123!"))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new AuthSession(body.get("accessToken").asText(), UUID.fromString(body.get("userId").asText()));
    }

    private UUID createCompany(String token, String legalName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "legalName", legalName,
                                "tradeName", legalName,
                                "gstin", "27ABCDE1234F1Z5",
                                "stateCode", "27",
                                "industry", "Professional Services",
                                "financialYearStart", "2026-04-01",
                                "financialYearEnd", "2027-03-31"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
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

    private record AuthSession(String token, UUID userId) {
    }
}
