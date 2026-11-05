package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.CandidatePageResponse;
import com.recruitment.dto.CandidateRequest;
import com.recruitment.dto.CandidateResponse;
import com.recruitment.dto.CandidateStatus;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.CandidateService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
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
class CandidateApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private CandidateService candidateService;

    @Test
    void recruiterCanCreateCandidate() throws Exception {
        given(candidateService.create(any(CandidateRequest.class))).willReturn(candidate(3));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/candidates")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(3))
                .andExpect(jsonPath("$.email").value("candidate@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void placementOfficerCanViewAndSearchCandidates() throws Exception {
        given(candidateService.getCandidates(
                eq(null), eq("Ada"), eq(null), eq("Java"), eq(2025),
                eq(CandidateStatus.ACTIVE), eq(1), eq(10)))
                .willReturn(new CandidatePageResponse(List.of(candidate(3)), 1, 10, 11, 2));
        AuthenticatedSession authenticated = login("PLACEMENT_OFFICER");

        mockMvc.perform(get("/api/candidates")
                        .session(authenticated.session())
                        .param("name", "Ada")
                        .param("technology", "Java")
                        .param("passingYear", "2025")
                        .param("status", "ACTIVE")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].candidateId").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void recruiterCanUpdateCandidateDetailsAndStatus() throws Exception {
        given(candidateService.update(eq(3L), any(CandidateRequest.class))).willReturn(candidate(3));
        given(candidateService.updateStatus(3, CandidateStatus.INACTIVE))
                .willReturn(new CandidateResponse(
                        3,
                        "Ada Lovelace",
                        "candidate@example.com",
                        "+15551234567",
                        "BSc Computer Science",
                        2025,
                        new BigDecimal("92.50"),
                        0,
                        "Java, SQL",
                        CandidateStatus.INACTIVE,
                        LocalDateTime.of(2026, 1, 1, 10, 0),
                        LocalDateTime.of(2026, 1, 2, 10, 0)));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(put("/api/candidates/3")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateId").value(3));

        mockMvc.perform(patch("/api/candidates/3/status")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void recruiterCanDeleteCandidate() throws Exception {
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(delete("/api/candidates/3")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void placementOfficerCannotManageCandidates() throws Exception {
        AuthenticatedSession authenticated = login("PLACEMENT_OFFICER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/candidates")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidCandidateFieldsReturnValidationErrors() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());
        String invalidCandidate = """
                {
                  "fullName":"",
                  "email":"invalid-email",
                  "mobile":"",
                  "qualification":"Computer Science",
                  "passingYear":2025,
                  "percentage":101,
                  "backlogCount":-1
                }
                """;

        mockMvc.perform(post("/api/candidates")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidCandidate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fullName").exists())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.percentage").exists())
                .andExpect(jsonPath("$.details.backlogCount").exists());
    }

    @Test
    void invalidStatusFilterReturnsBadRequest() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        mockMvc.perform(get("/api/candidates")
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

    private static CandidateResponse candidate(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new CandidateResponse(
                id,
                "Ada Lovelace",
                "candidate@example.com",
                "+15551234567",
                "BSc Computer Science",
                2025,
                new BigDecimal("92.50"),
                0,
                "Java, SQL",
                CandidateStatus.ACTIVE,
                timestamp,
                timestamp);
    }

    private static String candidateJson() {
        return """
                {
                  "fullName":"Ada Lovelace",
                  "email":"candidate@example.com",
                  "mobile":"+15551234567",
                  "qualification":"BSc Computer Science",
                  "passingYear":2025,
                  "percentage":92.50,
                  "backlogCount":0,
                  "skills":"Java, SQL"
                }
                """;
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
