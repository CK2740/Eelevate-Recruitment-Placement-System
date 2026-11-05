package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.ApplicationPageResponse;
import com.recruitment.dto.ApplicationResponse;
import com.recruitment.dto.ApplicationStatus;
import com.recruitment.dto.ApplicationStatusHistoryResponse;
import com.recruitment.dto.CandidateProfileResponse;
import com.recruitment.dto.CandidateNotificationResponse;
import com.recruitment.dto.CandidateResumeDownload;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobEligibilityRequest;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.AuditLogService;
import com.recruitment.service.CandidatePortalService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
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
class CandidatePortalApiTest {

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
    private CandidatePortalService portalService;

    @Test
    void candidatePortalUsesAuthenticatedUserForProfileApplicationsAndApplicationHistory() throws Exception {
        given(portalService.getProfile(7)).willReturn(new CandidateProfileResponse(
                "Test Candidate", "candidate@example.com", null, null, null, null, 0,
                null, null, false, false, null));
        given(portalService.getApplications(7, 0, 20))
                .willReturn(new ApplicationPageResponse(List.of(), 0, 20, 0, 0));
        given(portalService.getAvailableJobs(null, null, null, null, null, null, null, 0, 20))
                .willReturn(new JobPageResponse(List.of(), 0, 20, 0, 0));
        given(portalService.getApplicationHistory(7, 55)).willReturn(List.of(
                new ApplicationStatusHistoryResponse(
                        1, null, ApplicationStatus.APPLIED, 7L, null, LocalDateTime.now())));
        given(portalService.getNotifications(7)).willReturn(List.of(new CandidateNotificationResponse(
                55, "APPLICATION_STATUS", "Your application was submitted", LocalDateTime.now(), 55)));
        given(portalService.apply(7, 42)).willReturn(new ApplicationResponse(
                55, 42, "Test Candidate", 42, "Engineer", 3, "Acme",
                ApplicationStatus.APPLIED, null, LocalDateTime.now(), LocalDateTime.now()));

        AuthenticatedSession candidate = login("CANDIDATE");
        CsrfInfo csrf = obtainCsrf(candidate.session());

        mockMvc.perform(get("/api/portal/profile").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("candidate@example.com"));
        verify(portalService).getProfile(7);

        mockMvc.perform(get("/api/portal/jobs").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(portalService).getAvailableJobs(null, null, null, null, null, null, null, 0, 20);

        mockMvc.perform(get("/api/portal/applications").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        verify(portalService).getApplications(7, 0, 20);

        mockMvc.perform(get("/api/portal/applications/55/history").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStatus").value("APPLIED"));
        verify(portalService).getApplicationHistory(7, 55);

        mockMvc.perform(get("/api/portal/notifications").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("APPLICATION_STATUS"));
        verify(portalService).getNotifications(7);

        mockMvc.perform(post("/api/portal/jobs/42/applications")
                        .session(candidate.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.candidateId").value(42));
        verify(portalService).apply(7, 42);
        verify(auditLogService, atLeastOnce()).log(eq(7L), anyString(), anyString(), any(), any());
    }

    @Test
    void recruiterCannotAccessCandidatePortal() throws Exception {
        AuthenticatedSession recruiter = login("RECRUITER");

        mockMvc.perform(get("/api/portal/profile").session(recruiter.session()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/portal/jobs/42/applications")
                        .session(recruiter.session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidatePortalShowsApplicationStatusUpdatedByRecruitmentStaff() throws Exception {
        LocalDateTime timestamp = LocalDateTime.now();
        given(portalService.getApplications(7, 0, 20)).willReturn(new ApplicationPageResponse(
                List.of(new ApplicationResponse(
                        55,
                        42,
                        "Test Candidate",
                        8,
                        "Software Engineer",
                        3,
                        "Acme Inc",
                        ApplicationStatus.INTERVIEW,
                        "Technical interview scheduled",
                        timestamp,
                        timestamp)),
                0,
                20,
                1,
                1));
        AuthenticatedSession candidate = login("CANDIDATE");

        mockMvc.perform(get("/api/portal/applications").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("INTERVIEW"))
                .andExpect(jsonPath("$.content[0].statusReason").value("Technical interview scheduled"));
    }

    @Test
    void candidateBrowseReturnsOpenJobBeforeItsDeadlineWithoutEligibilityFilter() throws Exception {
        JobResponse openJob = new JobResponse(
                42,
                3,
                "Acme Inc",
                "Software Engineer",
                "Java",
                4,
                "London",
                new BigDecimal("85000.00"),
                "Build backend systems",
                "Java, SQL",
                "Computer Science",
                LocalDate.now().plusDays(30),
                EmploymentType.FULL_TIME,
                JobStatus.OPEN,
                new JobEligibilityRequest(new BigDecimal("70.00"), 2023, 2026, 1),
                LocalDateTime.now(),
                LocalDateTime.now());
        given(portalService.getAvailableJobs(
                null, null, null, null, null, null, null, 0, 20))
                .willReturn(new JobPageResponse(List.of(openJob), 0, 20, 1, 1));
        AuthenticatedSession candidate = login("CANDIDATE");

        mockMvc.perform(get("/api/portal/jobs").session(candidate.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].jobId").value(42))
                .andExpect(jsonPath("$.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(portalService).getAvailableJobs(
                null, null, null, null, null, null, null, 0, 20);
    }

    @Test
    void recruiterCanViewCandidateResumeButCandidateCannotUseRecruiterEndpoint() throws Exception {
        given(portalService.getCandidateResume(42)).willReturn(new CandidateResumeDownload(
                new ByteArrayResource("%PDF-test".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "candidate.pdf",
                "application/pdf"));
        AuthenticatedSession recruiter = login("RECRUITER");
        mockMvc.perform(get("/api/candidates/42/resume").session(recruiter.session()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentType("application/pdf"));
        verify(portalService).getCandidateResume(42);

        AuthenticatedSession candidate = login("CANDIDATE");
        mockMvc.perform(get("/api/candidates/42/resume").session(candidate.session()))
                .andExpect(status().isForbidden());
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

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
