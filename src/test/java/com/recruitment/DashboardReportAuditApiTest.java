package com.recruitment;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.AuditLogPageResponse;
import com.recruitment.dto.DashboardMetricsResponse;
import com.recruitment.dto.ReportFilters;
import com.recruitment.dto.ReportResponse;
import com.recruitment.dto.ReportType;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.AuditLogService;
import com.recruitment.service.DashboardService;
import com.recruitment.service.ReportService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class DashboardReportAuditApiTest extends AbstractLoginRateLimitApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private ReportService reportService;

    @MockitoBean
    private AuditLogService auditLogService;

    @Test
    void recruiterCanViewDashboardAndReportsButNotAuditLogs() throws Exception {
        given(dashboardService.getMetrics()).willReturn(new DashboardMetricsResponse(
                10, 4, 3, 8, 2, 1, 1, new BigDecimal("10.00")));
        given(reportService.getReport(
                eq(ReportType.RECRUITMENT_FUNNEL),
                eq(new ReportFilters(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        5L,
                        "Java",
                        8L,
                        2025)),
                eq(2),
                eq(50)))
                .willReturn(new ReportResponse(
                        ReportType.RECRUITMENT_FUNNEL, List.of(), LocalDateTime.now(), 2, 50, 0, 0));
        MockHttpSession session = login("RECRUITER");

        mockMvc.perform(get("/api/dashboard/metrics").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"totalCandidates":10,"totalCompanies":4,"openJobs":3,
                         "totalApplications":8,"scheduledInterviews":2,
                         "selectedCandidates":1,"joinedCandidates":1,
                         "placementPercentage":10.00}
                        """));
        mockMvc.perform(get("/api/reports/RECRUITMENT_FUNNEL")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .param("companyId", "5")
                        .param("technology", "Java")
                        .param("recruiterId", "8")
                        .param("passingYear", "2025")
                        .param("page", "2")
                        .param("size", "50")
                        .session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit-logs").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void candidateCannotViewReports() throws Exception {
        MockHttpSession session = login("CANDIDATE");
        mockMvc.perform(get("/api/reports/RECRUITMENT_FUNNEL").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanViewAuditLogsAndExportCsv() throws Exception {
        given(auditLogService.getLogs(null, null, null, null, null, 0, 20))
                .willReturn(new AuditLogPageResponse(List.of(), 0, 20, 0, 0));
        given(reportService.exportCsv(
                eq(ReportType.OPEN_JOB_AGING),
                eq(new ReportFilters(null, null, null, null, null, null))))
                .willReturn(output -> output.write(
                        "job_id,age_days\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        MockHttpSession session = login("ADMIN");

        mockMvc.perform(get("/api/audit-logs").session(session))
                .andExpect(status().isOk());
        MvcResult csvResult = mockMvc.perform(get("/api/reports/OPEN_JOB_AGING/csv").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"open_job_aging.csv\""))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(csvResult))
                .andExpect(status().isOk())
                .andExpect(content().string("job_id,age_days\r\n"));
    }

    private MockHttpSession login(String role) throws Exception {
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
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"correct-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private CsrfInfo obtainCsrf(MockHttpSession session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(cookie);
        return new CsrfInfo(body.get("token").asText(), body.get("headerName").asText(), cookie);
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }
}
