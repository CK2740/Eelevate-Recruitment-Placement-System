package com.recruitment;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "app.cors.allowed-origin=https://eelevate-recruitment-placement-syst-eight.vercel.app",
        "server.servlet.session.cookie.secure=true",
        "server.servlet.session.cookie.same-site=None"
})
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    private static final String FRONTEND_ORIGIN =
            "https://eelevate-recruitment-placement-syst-eight.vercel.app";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServerProperties serverProperties;

    @Test
    void preflightAllowsOnlyConfiguredOriginAndCsrfHeader() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PUT")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("DELETE")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("OPTIONS")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("content-type")))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("x-xsrf-token")));
    }

    @Test
    void disallowedOriginCannotUseCors() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void ordinaryOptionsRequestStillRequiresAuthorization() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
    }

    @Test
    void csrfCookieUsesConfiguredSameSiteAndSecureWithoutHttpOnly() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        var csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        org.junit.jupiter.api.Assertions.assertNotNull(csrfCookie);
        org.junit.jupiter.api.Assertions.assertTrue(csrfCookie.getSecure());
        assertFalse(csrfCookie.isHttpOnly());
        org.junit.jupiter.api.Assertions.assertEquals("None", csrfCookie.getAttribute("SameSite"));
    }

    @Test
    void sessionCookieRemainsSecureAndHttpOnlyWithConfiguredSameSite() {
        var cookie = serverProperties.getServlet().getSession().getCookie();

        org.junit.jupiter.api.Assertions.assertTrue(cookie.getSecure());
        org.junit.jupiter.api.Assertions.assertTrue(cookie.getHttpOnly());
        org.junit.jupiter.api.Assertions.assertEquals("NONE", cookie.getSameSite().toString());
    }
}
