package com.dimos.wms.identity;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dimos.wms.audit.AuditService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web-level security rules. The audit service is mocked so these tests never write to the database. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityWebTests {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditService audit;

    /** Does what the browser does: a first request hands out the XSRF-TOKEN cookie, later writes echo it. */
    private Cookie xsrfCookie() throws Exception {
        return mvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private static UsernamePasswordAuthenticationToken session(boolean mustChange) {
        var p = new WmsUserPrincipal(1L, 1L, "tester", "Test User", List.of("VIEWER"), mustChange);
        return UsernamePasswordAuthenticationToken.authenticated(p, null,
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER")));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresLoginAndSendsCsrfCookieAndCorrelationId() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownUserGetsGenericMessage() throws Exception {
        Cookie xsrf = xsrfCookie();
        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"no-such-user\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void blankLoginIsAValidationError() throws Exception {
        Cookie xsrf = xsrfCookie();
        mvc.perform(post("/api/auth/login").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void forcedPasswordChangeBlocksTheRestOfTheApi() throws Exception {
        mvc.perform(get("/api/anything").with(authentication(session(true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/auth/me").with(authentication(session(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"));
    }

    @Test
    void unknownApiPathReturnsJsonNotStackTrace() throws Exception {
        mvc.perform(get("/api/anything").with(authentication(session(false))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
