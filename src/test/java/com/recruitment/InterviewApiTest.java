package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.InterviewMode;
import com.recruitment.dto.InterviewPageResponse;
import com.recruitment.dto.InterviewRecommendation;
import com.recruitment.dto.InterviewRequest;
import com.recruitment.dto.InterviewRescheduleRequest;
import com.recruitment.dto.InterviewResponse;
import com.recruitment.dto.InterviewStatus;
import com.recruitment.dto.InterviewStatusHistoryResponse;
import com.recruitment.dto.InterviewerFeedbackResponse;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.InterviewService;
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
class InterviewApiTest {

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
    private InterviewService interviewService;

    @Test
    void adminCanScheduleListRescheduleUpdateFeedbackAndReadHistory() throws Exception {
        given(interviewService.schedule(any(InterviewRequest.class), eq(7L), eq(true), eq(false)))
                .willReturn(interview(21, InterviewStatus.SCHEDULED));
        given(interviewService.getInterviews(
                eq("Ada"), eq("Ada"), eq("Engineer"), eq("Acme"),
                eq(InterviewStatus.SCHEDULED), eq(InterviewMode.VIDEO),
                any(LocalDateTime.class), any(LocalDateTime.class), eq(7L), eq(false), eq(1), eq(10)))
                .willReturn(new InterviewPageResponse(
                        List.of(interview(21, InterviewStatus.SCHEDULED)), 1, 10, 11, 2));
        given(interviewService.getById(21, 7, true, false))
                .willReturn(interview(21, InterviewStatus.SCHEDULED));
        given(interviewService.reschedule(
                eq(21L), any(InterviewRescheduleRequest.class), eq(7L), eq(true)))
                .willReturn(interview(21, InterviewStatus.SCHEDULED));
        given(interviewService.updateStatus(
                eq(21L), eq(InterviewStatus.COMPLETED), eq(null), eq(7L), eq(true), eq(false)))
                .willReturn(interview(21, InterviewStatus.COMPLETED));
        given(interviewService.submitFeedback(
                eq(21L), any(), eq(7L), eq(true), eq(false)))
                .willReturn(interview(21, InterviewStatus.COMPLETED));
        given(interviewService.getHistory(21, 7, true, false))
                .willReturn(List.of(new InterviewStatusHistoryResponse(
                        31,
                        InterviewStatus.SCHEDULED,
                        InterviewStatus.COMPLETED,
                        7L,
                        null,
                        LocalDateTime.of(2090, 5, 1, 12, 0))));
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/applications/15/interviews")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interviewJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interviewId").value(21))
                .andExpect(jsonPath("$.interviewers.length()").value(2));

        mockMvc.perform(get("/api/interviews")
                        .session(authenticated.session())
                        .param("search", "Ada")
                        .param("candidate", "Ada")
                        .param("job", "Engineer")
                        .param("company", "Acme")
                        .param("status", "SCHEDULED")
                        .param("mode", "VIDEO")
                        .param("from", "2090-05-01T00:00:00")
                        .param("to", "2090-05-02T00:00:00")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].interviewId").value(21))
                .andExpect(jsonPath("$.totalElements").value(11));

        mockMvc.perform(get("/api/interviews/21").session(authenticated.session()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/interviews/21/schedule")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"2090-05-02T10:00:00","durationMinutes":45}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/interviews/21/status")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(put("/api/interviews/21/feedback")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{
                                  "interviewerId":31,
                                  "feedback":{
                                    "technicalRating":8.5,
                                    "communicationRating":9.0,
                                    "recommendation":"PASS",
                                    "comments":"Strong interview"
                                  }
                                }]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/interviews/21/history").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStatus").value("COMPLETED"));
    }

    @Test
    void recruiterCanManageAssignedInterview() throws Exception {
        given(interviewService.schedule(any(InterviewRequest.class), eq(7L), eq(false), eq(false)))
                .willReturn(interview(21, InterviewStatus.SCHEDULED));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/applications/15/interviews")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interviewJson()))
                .andExpect(status().isCreated());

        given(interviewService.getInterviews(
                eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(null), eq(null), eq(7L), eq(true), eq(0), eq(20)))
                .willReturn(new InterviewPageResponse(
                        List.of(interview(21, InterviewStatus.SCHEDULED)), 0, 20, 1, 1));

        mockMvc.perform(get("/api/interviews").session(authenticated.session()))
                .andExpect(status().isOk());
    }

    @Test
    void placementOfficerCanViewAndCompleteInterviewButCannotScheduleOrReschedule() throws Exception {
        given(interviewService.getById(21, 7, false, true))
                .willReturn(interview(21, InterviewStatus.SCHEDULED));
        given(interviewService.updateStatus(
                eq(21L), eq(InterviewStatus.NO_SHOW), eq(null), eq(7L), eq(false), eq(true)))
                .willReturn(interview(21, InterviewStatus.NO_SHOW));
        AuthenticatedSession authenticated = login("PLACEMENT_OFFICER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(get("/api/interviews/21").session(authenticated.session()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/interviews/21/status")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"NO_SHOW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        mockMvc.perform(post("/api/applications/15/interviews")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interviewJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/interviews/21/schedule")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduledAt":"2090-05-02T10:00:00"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidInterviewDetailsAreRejected() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/applications/15/interviews")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "applicationId":15,
                                  "interviewRound":"",
                                  "interviewType":"INVALID",
                                  "roundNumber":0,
                                  "scheduledAt":"2000-01-01T10:00:00",
                                  "durationMinutes":0,
                                  "interviewMode":"VIDEO",
                                  "interviewers":[]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.interviewRound").exists())
                .andExpect(jsonPath("$.details.interviewType").exists())
                .andExpect(jsonPath("$.details.scheduledAt").exists())
                .andExpect(jsonPath("$.details.interviewers").exists());
    }

    @Test
    void pathAndBodyApplicationIdsMustMatch() throws Exception {
        AuthenticatedSession authenticated = login("ADMIN");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/applications/15/interviews")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interviewJson().replace("\"applicationId\":15", "\"applicationId\":16")))
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

    private static InterviewResponse interview(long id, InterviewStatus status) {
        LocalDateTime scheduledAt = LocalDateTime.of(2090, 5, 1, 10, 0);
        LocalDateTime createdAt = LocalDateTime.of(2090, 4, 1, 10, 0);
        return new InterviewResponse(
                id,
                15,
                4,
                "Ada Lovelace",
                8,
                "Software Engineer",
                "Acme Inc",
                "Technical",
                "TECHNICAL",
                1,
                scheduledAt,
                60,
                InterviewMode.VIDEO,
                null,
                "https://meeting.example/interview",
                status,
                List.of(
                        new InterviewerFeedbackResponse(
                                31,
                                "Grace Hopper",
                                "grace@example.com",
                                null,
                                null,
                                null,
                                null,
                                null),
                        new InterviewerFeedbackResponse(
                                32,
                                "Alan Turing",
                                "alan@example.com",
                                null,
                                null,
                                null,
                                null,
                                null)),
                createdAt,
                createdAt);
    }

    private static String interviewJson() {
        return """
                {
                  "applicationId":15,
                  "interviewRound":"Technical",
                  "interviewType":"TECHNICAL",
                  "roundNumber":1,
                  "scheduledAt":"2090-05-01T10:00:00",
                  "durationMinutes":60,
                  "interviewMode":"VIDEO",
                  "meetingLink":"https://meeting.example/interview",
                  "interviewers":[
                    {"name":"Grace Hopper","email":"grace@example.com"},
                    {"name":"Alan Turing","email":"alan@example.com"}
                  ]
                }
                """;
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
