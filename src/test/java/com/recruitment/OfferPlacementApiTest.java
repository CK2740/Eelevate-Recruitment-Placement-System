package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.OfferPageResponse;
import com.recruitment.dto.OfferRequest;
import com.recruitment.dto.OfferResponse;
import com.recruitment.dto.OfferStatus;
import com.recruitment.dto.OfferStatusRequest;
import com.recruitment.dto.PlacementPageResponse;
import com.recruitment.dto.PlacementRequest;
import com.recruitment.dto.PlacementResponse;
import com.recruitment.dto.PlacementStatus;
import com.recruitment.dto.PlacementStatusHistoryResponse;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.OfferService;
import com.recruitment.service.PlacementService;
import com.recruitment.service.AuditLogService;
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
class OfferPlacementApiTest extends AbstractLoginRateLimitApiTest {

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
    private OfferService offerService;

    @MockitoBean
    private PlacementService placementService;

    @Test
    void recruiterCanCreateAndManageOffersAndPlacements() throws Exception {
        given(offerService.create(15, offerRequest(), 7, false, false))
                .willReturn(offer(30, OfferStatus.PENDING));
        given(offerService.getOffers(
                null, null, null, null, null, null, 7, true, 0, 20))
                .willReturn(new OfferPageResponse(List.of(offer(30, OfferStatus.PENDING)), 0, 20, 1, 1));
        given(offerService.updateStatus(eq(30L), any(OfferStatusRequest.class), eq(7L), eq(false), eq(false)))
                .willReturn(offer(30, OfferStatus.ACCEPTED));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/applications/15/offers")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offerJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Acme Inc"));

        mockMvc.perform(get("/api/offers").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].offerId").value(30));

        mockMvc.perform(patch("/api/offers/30/status")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACCEPTED","joiningDate":"2090-06-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/applications/15/placements")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placementJson()))
                .andExpect(status().isCreated());
        verify(auditLogService, atLeastOnce()).log(eq(7L), anyString(), anyString(), any(), any());
    }

    @Test
    void recruiterCanManageOffersAndPlacementWorkflow() throws Exception {
        given(offerService.create(15, offerRequest(), 7, false, false))
                .willReturn(offer(30, OfferStatus.PENDING));
        given(offerService.getOffers(
                null, "Ada", "Acme", "Engineer", OfferStatus.PENDING,
                LocalDate.now().plusDays(5), 7, true, 1, 10))
                .willReturn(new OfferPageResponse(List.of(offer(30, OfferStatus.PENDING)), 1, 10, 11, 2));
        given(placementService.create(15, placementRequest(), 7))
                .willReturn(placement(40, PlacementStatus.PENDING));
        given(placementService.getPlacements(
                "Ada", "Ada", "Acme", "Engineer", PlacementStatus.PENDING, null, 0, 10))
                .willReturn(new PlacementPageResponse(
                        List.of(placement(40, PlacementStatus.PENDING)), 0, 10, 1, 1));
        given(placementService.update(eq(40L), any(PlacementRequest.class), eq("Started"), eq(7L)))
                .willReturn(placement(40, PlacementStatus.JOINED));
        given(placementService.getHistory(40))
                .willReturn(List.of(new PlacementStatusHistoryResponse(
                        50, PlacementStatus.PENDING, PlacementStatus.JOINED, 7L,
                        "Started", LocalDateTime.of(2090, 6, 1, 10, 0))));
        AuthenticatedSession authenticated = login("RECRUITER");
        CsrfInfo csrf = obtainCsrf(authenticated.session());

        mockMvc.perform(post("/api/applications/15/offers")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(offerJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/offers")
                        .session(authenticated.session())
                        .param("candidate", "Ada")
                        .param("company", "Acme")
                        .param("designation", "Engineer")
                        .param("status", "PENDING")
                        .param("expiryBefore", LocalDate.now().plusDays(5).toString())
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(11));

        mockMvc.perform(post("/api/applications/15/placements")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(placementJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joiningStatus").value("PENDING"));

        mockMvc.perform(get("/api/placements")
                        .session(authenticated.session())
                        .param("search", "Ada")
                        .param("candidate", "Ada")
                        .param("company", "Acme")
                        .param("designation", "Engineer")
                        .param("status", "PENDING")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].placementId").value(40));

        mockMvc.perform(put("/api/placements/40")
                        .session(authenticated.session())
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .param("reason", "Started")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "designation":"Software Engineer",
                                  "finalPackage":90000,
                                  "actualJoiningDate":"2090-06-01",
                                  "joiningStatus":"JOINED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joiningStatus").value("JOINED"));

        mockMvc.perform(get("/api/placements/40/history").session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStatus").value("JOINED"));
    }

    @Test
    void adminCanViewOfferAndPlacementPages() throws Exception {
        given(offerService.getOffers(
                null, null, null, null, null, null, 7, false, 0, 20))
                .willReturn(new OfferPageResponse(List.of(), 0, 20, 0, 0));
        given(placementService.getPlacements(
                null, null, null, null, null, null, 0, 20))
                .willReturn(new PlacementPageResponse(List.of(), 0, 20, 0, 0));
        AuthenticatedSession authenticated = login("ADMIN");

        mockMvc.perform(get("/api/offers").session(authenticated.session()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/placements").session(authenticated.session()))
                .andExpect(status().isOk());
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

    private static OfferRequest offerRequest() {
        return new OfferRequest(
                "Software Engineer",
                new BigDecimal("90000"),
                LocalDate.now(),
                LocalDate.now().plusDays(10),
                "Permanent role");
    }

    private static PlacementRequest placementRequest() {
        return new PlacementRequest(
                "Software Engineer", new BigDecimal("90000"), null, PlacementStatus.PENDING);
    }

    private static OfferResponse offer(long id, OfferStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2090, 1, 1, 10, 0);
        return new OfferResponse(
                id,
                15,
                4,
                "Ada Lovelace",
                3,
                "Acme Inc",
                "Software Engineer",
                new BigDecimal("90000"),
                LocalDate.of(2090, 5, 1),
                LocalDate.of(2090, 5, 10),
                null,
                "Permanent role",
                status,
                timestamp,
                timestamp);
    }

    private static PlacementResponse placement(long id, PlacementStatus status) {
        LocalDateTime timestamp = LocalDateTime.of(2090, 1, 1, 10, 0);
        return new PlacementResponse(
                id,
                15,
                4,
                "Ada Lovelace",
                3,
                "Acme Inc",
                "Software Engineer",
                new BigDecimal("90000"),
                null,
                status,
                timestamp,
                timestamp);
    }

    private static String offerJson() {
        return """
                {
                  "designation":"Software Engineer",
                  "packageAmount":90000,
                  "offerDate":"%s",
                  "expiryDate":"%s",
                  "offerDetails":"Permanent role"
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(10));
    }

    private static String placementJson() {
        return """
                {
                  "designation":"Software Engineer",
                  "finalPackage":90000,
                  "joiningStatus":"PENDING"
                }
                """;
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }

    private record AuthenticatedSession(MockHttpSession session) {
    }
}
