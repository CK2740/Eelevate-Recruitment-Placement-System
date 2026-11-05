package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.EmploymentType;
import com.recruitment.dto.JobEligibilityRequest;
import com.recruitment.dto.JobPageResponse;
import com.recruitment.dto.JobRequest;
import com.recruitment.dto.JobResponse;
import com.recruitment.dto.JobStatus;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.JobService;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class JobApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private JobService jobService;

    @Test
    void adminCanCreateViewSearchUpdateAndDeleteJobs() throws Exception {
        given(jobService.create(any(JobRequest.class))).willReturn(job(8));
        given(jobService.getById(8)).willReturn(job(8));
        given(jobService.getJobs(
                eq(null), eq("Engineer"), eq("Acme"), eq("Java"), eq("London"),
                eq(EmploymentType.FULL_TIME), eq(JobStatus.OPEN), eq(2025), eq(1), eq(10)))
                .willReturn(new JobPageResponse(List.of(job(8)), 1, 10, 11, 2));
        given(jobService.update(eq(8L), any(JobRequest.class))).willReturn(job(8));
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/jobs")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(8))
                .andExpect(jsonPath("$.eligibilityCriteria.minimumPassingYear").value(2023));

        mockMvc.perform(get("/api/jobs/8").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Acme Inc"));

        mockMvc.perform(get("/api/jobs")
                        .session(authenticated.session())
                        .param("jobTitle", "Engineer")
                        .param("company", "Acme")
                        .param("technology", "Java")
                        .param("location", "London")
                        .param("jobType", "FULL_TIME")
                        .param("status", "OPEN")
                        .param("passingYear", "2025")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].jobId").value(8))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(put("/api/jobs/8")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(8));

        mockMvc.perform(delete("/api/jobs/8")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void recruiterCanManageJobs() throws Exception {
        given(jobService.create(any(JobRequest.class))).willReturn(job(8));
        given(jobService.getById(8)).willReturn(job(8));
        given(jobService.update(eq(8L), any(JobRequest.class))).willReturn(job(8));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/jobs")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/jobs/8").session(authenticated.session()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/jobs/8")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/jobs/8")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void placementOfficerCanViewButCannotManageJobs() throws Exception {
        given(jobService.getById(8)).willReturn(job(8));
        AuthenticatedSession authenticated = login("PLACEMENT_OFFICER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(get("/api/jobs/8").session(authenticated.session()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/jobs")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/jobs/8")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jobJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/jobs/8")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidRequiredFieldsAndPastDeadlineReturnValidationErrors() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());
        String invalidJob = """
                {
                  "companyId":0,
                  "jobTitle":"",
                  "technology":"",
                  "openings":0,
                  "location":"",
                  "packageAmount":-1,
                  "applicationDeadline":"2000-01-01",
                  "employmentType":"FULL_TIME",
                  "status":"OPEN",
                  "eligibilityCriteria":{
                    "minimumPercentage":101,
                    "minimumPassingYear":2026,
                    "maximumPassingYear":2020,
                    "maximumBacklogs":-1
                  }
                }
                """;

        mockMvc.perform(post("/api/jobs")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJob))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.companyId").exists())
                .andExpect(jsonPath("$.details.jobTitle").exists())
                .andExpect(jsonPath("$.details.openings").exists())
                .andExpect(jsonPath("$.details.packageAmount").exists())
                .andExpect(jsonPath("$.details.applicationDeadline").exists())
                .andExpect(jsonPath("$.details['eligibilityCriteria.minimumPercentage']").exists())
                .andExpect(jsonPath("$.details['eligibilityCriteria.maximumBacklogs']").exists())
                .andExpect(jsonPath("$.details['eligibilityCriteria.passingYearRangeValid']").exists());
    }

    @Test
    void invalidJobFilterEnumsReturnBadRequest() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");

        mockMvc.perform(get("/api/jobs")
                        .session(authenticated.session())
                        .param("jobType", "UNKNOWN"))
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

    private static JobResponse job(long id) {
        LocalDateTime timestamp = LocalDateTime.of(2026, 1, 1, 10, 0);
        return new JobResponse(
                id,
                3,
                "Acme Inc",
                "Software Engineer",
                "Java",
                4,
                "London",
                new BigDecimal("85000.00"),
                "Build backend systems",
                "Java, SQL",
                LocalDate.now().plusDays(30),
                EmploymentType.FULL_TIME,
                JobStatus.OPEN,
                new JobEligibilityRequest(new BigDecimal("70.00"), 2023, 2026, 1),
                timestamp,
                timestamp);
    }

    private static String jobJson() {
        return """
                {
                  "companyId":3,
                  "jobTitle":"Software Engineer",
                  "technology":"Java",
                  "openings":4,
                  "location":"London",
                  "packageAmount":85000.00,
                  "jobDescription":"Build backend systems",
                  "skills":"Java, SQL",
                  "applicationDeadline":"%s",
                  "employmentType":"FULL_TIME",
                  "status":"OPEN",
                  "eligibilityCriteria":{
                    "minimumPercentage":70.00,
                    "minimumPassingYear":2023,
                    "maximumPassingYear":2026,
                    "maximumBacklogs":1
                  }
                }
                """.formatted(LocalDate.now().plusDays(30));
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
