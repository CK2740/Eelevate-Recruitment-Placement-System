package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatus;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import com.recruitment.dto.ApplicationStatusRequest;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.JobApplicationService;
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
class JobApplicationApiTest {

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
    private JobApplicationService applicationService;

    @Test
    void adminCanApplyViewFilterUpdateStatusAndReadHistory() throws Exception {
        given(applicationService.apply(8, 4, 7, true, false))
                .willReturn(application(12, ApplicationStatus.APPLIED));
        given(applicationService.getApplications(
                null, "Ada", "Acme", "Engineer", ApplicationStatus.APPLIED, 2025,
                7, false, 1, 10))
                .willReturn(new ApplicationPageResponse(
                        List.of(application(12, ApplicationStatus.APPLIED)), 1, 10, 11, 2));
        given(applicationService.getById(12, 7, true, false))
                .willReturn(application(12, ApplicationStatus.APPLIED));
        given(applicationService.updateStatus(
                eq(12L), any(ApplicationStatusRequest.class), eq(7L), eq(true), eq(false)))
                .willReturn(application(12, ApplicationStatus.SHORTLISTED));
        given(applicationService.getHistory(12, 7, true, false))
                .willReturn(List.of(new ApplicationStatusHistoryResponse(
                        20, ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED,
                        7L, "Strong match", LocalDateTime.of(2026, 1, 2, 10, 0))));
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/jobs/8/applications")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"candidateId":4}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPLIED"));

        mockMvc.perform(get("/api/applications")
                        .session(authenticated.session())
                        .param("candidate", "Ada")
                        .param("company", "Acme")
                        .param("job", "Engineer")
                        .param("status", "APPLIED")
                        .param("passingYear", "2025")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicationId").value(12))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/applications/12").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidateName").value("Ada Lovelace"));

        mockMvc.perform(put("/api/applications/12/status")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"SHORTLISTED","reason":"Strong match"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHORTLISTED"));

        mockMvc.perform(get("/api/applications/12/history").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previousStatus").value("APPLIED"))
                .andExpect(jsonPath("$[0].reason").value("Strong match"));
    }

    @Test
    void recruiterCanApplyAndManageApplications() throws Exception {
        given(applicationService.apply(8, 4, 7, false, true))
                .willReturn(application(12, ApplicationStatus.APPLIED));
        given(applicationService.getApplications(
                null, null, null, null, null, null, 7, true, 0, 20))
                .willReturn(new ApplicationPageResponse(
                        List.of(application(12, ApplicationStatus.APPLIED)), 0, 20, 1, 1));
        given(applicationService.getById(12, 7, false, false))
                .willReturn(application(12, ApplicationStatus.APPLIED));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/jobs/8/applications")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"candidateId":4}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/applications").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicationId").value(12));

        mockMvc.perform(get("/api/applications/12").session(authenticated.session()))
                .andExpect(status().isOk());
    }

    @Test
    void placementOfficerCanViewAndUpdatePlacementWorkflowButCannotApply() throws Exception {
        given(applicationService.getApplications(
                null, null, null, null, null, null, 7, false, 0, 20))
                .willReturn(new ApplicationPageResponse(
                        List.of(application(12, ApplicationStatus.SHORTLISTED)), 0, 20, 1, 1));
        given(applicationService.updateStatus(
                eq(12L), any(ApplicationStatusRequest.class), eq(7L), eq(false), eq(true)))
                .willReturn(application(12, ApplicationStatus.INTERVIEW));
        AuthenticatedSession authenticated = login("PLACEMENT_OFFICER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(get("/api/applications").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("SHORTLISTED"));

        mockMvc.perform(put("/api/applications/12/status")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"INTERVIEW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"));

        mockMvc.perform(post("/api/jobs/8/applications")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"candidateId":4}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationRequiresCandidateId() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/jobs/8/applications")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.candidateId").exists());
    }

    @Test
    void invalidApplicationStatusFilterReturnsBadRequest() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");

        mockMvc.perform(get("/api/applications")
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

    private static ApplicationResponse application(long id, ApplicationStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new ApplicationResponse(
                id,
                4,
                "Ada Lovelace",
                8,
                "Software Engineer",
                3,
                "Acme Inc",
                status,
                status == ApplicationStatus.APPLIED ? "Application submitted" : "Strong match",
                timestamp,
                timestamp);
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
