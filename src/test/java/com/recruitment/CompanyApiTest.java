package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.CompanyPageResponse;
import com.recruitment.dto.CompanyRequest;
import com.recruitment.dto.CompanyResponse;
import com.recruitment.dto.CompanyStatus;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.CompanyService;
import com.recruitment.service.AuditLogService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "server.servlet.session.cookie.secure=false"
})
@AutoConfigureMockMvc
class CompanyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private CompanyService companyService;

    @Test
    void adminCanCreateViewSearchUpdateAndDeleteCompanies() throws Exception {
        given(companyService.create(any(CompanyRequest.class))).willReturn(company(5));
        given(companyService.getById(5)).willReturn(company(5));
        given(companyService.getCompanies(
                eq(null), eq("Acme"), eq("Technology"), eq("London"),
                eq(CompanyStatus.ACTIVE), eq(1), eq(10)))
                .willReturn(new CompanyPageResponse(List.of(company(5)), 1, 10, 11, 2));
        given(companyService.update(eq(5L), any(CompanyRequest.class))).willReturn(company(5));
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/companies")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(5));
        verify(auditLogService).log(
                eq(7L), eq("COMPANY_CREATED"), eq("COMPANY"), eq(5L), any());

        mockMvc.perform(get("/api/companies/5").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Inc"));

        mockMvc.perform(get("/api/companies")
                        .session(authenticated.session())
                        .param("name", "Acme")
                        .param("industry", "Technology")
                        .param("location", "London")
                        .param("status", "ACTIVE")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyId").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(put("/api/companies/5")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(5));

        mockMvc.perform(delete("/api/companies/5")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void recruiterCanManageCompanies() throws Exception {
        given(companyService.create(any(CompanyRequest.class))).willReturn(company(5));
        given(companyService.getById(5)).willReturn(company(5));
        given(companyService.update(eq(5L), any(CompanyRequest.class))).willReturn(company(5));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/companies")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/5").session(authenticated.session()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/companies/5")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/companies/5")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void placementOfficerCanViewButCannotManageCompanies() throws Exception {
        given(companyService.getById(5)).willReturn(company(5));
        AuthenticatedSession authenticated = login("PLACEMENT_OFFICER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(get("/api/companies/5").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(5));

        mockMvc.perform(post("/api/companies")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/companies/5")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(companyJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/companies/5")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCompanyFieldsReturnValidationErrors() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());
        String invalidCompany = """
                {
                  "companyName":"",
                  "email":"invalid-email",
                  "mobile":"not-a-phone",
                  "website":"not-a-url"
                }
                """;

        mockMvc.perform(post("/api/companies")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCompany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.companyName").exists())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.mobile").exists())
                .andExpect(jsonPath("$.details.website").exists());
    }

    @Test
    void invalidCompanyStatusFilterReturnsBadRequest() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");

        mockMvc.perform(get("/api/companies")
                        .session(authenticated.session())
                        .param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    private AuthenticatedSession login(String role) throws Exception {
        String email = role.toLowerCase(java.util.Locale.ROOT) + "@example.com";
        given(userDetailsService.loadUserByUsername(email))
                .willReturn(new UserPrincipal(
                        7,
                        email,
                        "Test User",
                        passwordEncoder.encode("correct-password"),
                        true,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        CsrfInfo csrf = obtainCsrf(null);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return new AuthenticatedSession((MockHttpSession) result.getRequest().getSession(false));
    }

    private CsrfInfo obtainCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        return new CsrfInfo(body.get("token").asText(), body.get("headerName").asText(), cookie);
    }

    private static CompanyResponse company(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new CompanyResponse(
                id,
                "Acme Inc",
                "Technology",
                "London",
                "Ada Lovelace",
                "contact@acme.example",
                "+441234567890",
                "https://acme.example",
                CompanyStatus.ACTIVE,
                timestamp,
                timestamp);
    }

    private static String companyJson() {
        return """
                {
                  "companyName":"Acme Inc",
                  "industry":"Technology",
                  "location":"London",
                  "contactPerson":"Ada Lovelace",
                  "email":"contact@acme.example",
                  "mobile":"+441234567890",
                  "website":"https://acme.example",
                  "status":"ACTIVE"
                }
                """;
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
