package com.recruitment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recruitment.dto.AuthenticatedUserResponse;
import com.recruitment.dto.CreateUserRequest;
import com.recruitment.security.UserPrincipal;
import com.recruitment.service.AuditLogService;
import com.recruitment.service.UserManagementService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "server.servlet.session.cookie.secure=false"
})
@AutoConfigureMockMvc
class AuthenticationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuditLogService auditLogService;

    @MockitoBean
    private UserManagementService userManagementService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginCreatesSessionAndCurrentUserReturnsAuthenticatedPrincipal() throws Exception {
        given(userDetailsService.loadUserByUsername("recruiter@example.com"))
                .willReturn(principal("recruiter@example.com", "RECRUITER"));
        MockHttpSession existingSession = new MockHttpSession();
        String oldSessionId = existingSession.getId();
        CsrfInfo csrfInfo = obtainCsrf(existingSession);

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .session(existingSession)
                        .cookie(csrfInfo.cookie())
                        .header(csrfInfo.headerName(), csrfInfo.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"recruiter@example.com","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("recruiter@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("RECRUITER"))
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) login.getRequest().getSession(false);
        org.junit.jupiter.api.Assertions.assertNotNull(authenticatedSession);
        org.junit.jupiter.api.Assertions.assertNotEquals(oldSessionId, authenticatedSession.getId());

        mockMvc.perform(get("/api/auth/me").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    void invalidCredentialsReturnUnauthorized() throws Exception {
        given(userDetailsService.loadUserByUsername("unknown@example.com"))
                .willThrow(new UsernameNotFoundException("User not found"));
        CsrfInfo csrfInfo = obtainCsrf(null);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfInfo.cookie())
                        .header(csrfInfo.headerName(), csrfInfo.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        given(userDetailsService.loadUserByUsername("known@example.com"))
                .willReturn(principal("known@example.com", "RECRUITER"));
        CsrfInfo wrongPasswordCsrf = obtainCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .cookie(wrongPasswordCsrf.cookie())
                        .header(wrongPasswordCsrf.headerName(), wrongPasswordCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@example.com","password":"incorrect-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        given(userDetailsService.loadUserByUsername("admin@example.com"))
                .willReturn(principal("admin@example.com", "ADMIN"));
        CsrfInfo loginCsrf = obtainCsrf(null);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        CsrfInfo logoutCsrf = obtainCsrf(session);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()))
                .andExpect(status().isNoContent());
        verify(auditLogService).log(
                eq(7L),
                eq("USER_LOGGED_OUT"),
                eq("AUTHENTICATION"),
                eq(7L),
                any());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCreationIsRestrictedToAdministrators() throws Exception {
        given(userDetailsService.loadUserByUsername("recruiter@example.com"))
                .willReturn(principal("recruiter@example.com", "RECRUITER"));
        CsrfInfo loginCsrf = obtainCsrf(null);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"recruiter@example.com","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        CsrfInfo createCsrf = obtainCsrf(session);

        mockMvc.perform(post("/api/auth/users")
                        .session(session)
                        .cookie(createCsrf.cookie())
                        .header(createCsrf.headerName(), createCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"new-user@example.com",
                                  "fullName":"New User",
                                  "password":"long-enough-password",
                                  "role":"RECRUITER"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateUsersWithServerAssignedRole() throws Exception {
        given(userDetailsService.loadUserByUsername("admin@example.com"))
                .willReturn(principal("admin@example.com", "ADMIN"));
        given(userManagementService.createUser(any(CreateUserRequest.class)))
                .willReturn(new AuthenticatedUserResponse(
                        8,
                        "new-user@example.com",
                        "New User",
                        List.of("RECRUITER")));
        CsrfInfo loginCsrf = obtainCsrf(null);
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header(loginCsrf.headerName(), loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"correct-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        CsrfInfo createCsrf = obtainCsrf(session);

        mockMvc.perform(post("/api/auth/users")
                        .session(session)
                        .cookie(createCsrf.cookie())
                        .header(createCsrf.headerName(), createCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"new-user@example.com",
                                  "fullName":"New User",
                                  "password":"long-enough-password",
                                  "role":"RECRUITER"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("RECRUITER"));
    }

    @Test
    void loginRequiresValidEmailAndPasswordFields() throws Exception {
        CsrfInfo csrfInfo = obtainCsrf(null);
        mockMvc.perform(post("/api/auth/login")
                        .cookie(csrfInfo.cookie())
                        .header(csrfInfo.headerName(), csrfInfo.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.password").exists());
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

    private UserPrincipal principal(String email, String role) {
        return new UserPrincipal(
                7,
                email,
                "Test User",
                passwordEncoder.encode("correct-password"),
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private record CsrfInfo(String token, String headerName, Cookie cookie) {
    }
}
