package com.dimos.wms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * System parameters. Everything site-bound happens on a private test site (9006) so real settings are never touched;
 * values written by the tests carry the actor "zt-actor" and are removed again.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParameterTests {

    private static final long SITE = 9006L;
    private static final String BASE = "/api/admin/parameters";
    private static final String SESSION = "security.session_timeout";
    private static final String MIN_LENGTH = "security.password_min_length";
    private static final String ATTEMPTS = "security.lockout_attempts";
    private static final String MINUTES = "security.lockout_minutes";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ParameterService parameters;

    @Autowired
    AuthService auth;

    @MockitoBean
    AuditService audit;

    private static UsernamePasswordAuthenticationToken actor(long site, String... permissions) {
        var principal = new WmsUserPrincipal(999_999_999L, site, "zt-actor", "Actor", List.of("SUPER_ADMIN"), List.of(permissions), false);
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private static final UsernamePasswordAuthenticationToken ADMIN =
            actor(SITE, "PARAMETER_VIEW", "PARAMETER_EDIT", "WORKSTATION_MANAGE", "USER_MANAGE");

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + SITE + ", 'ZTPARAM', 'Parameter test site', 0, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test')");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from parameter_value where updated_by = 'zt-actor' or site_id = " + SITE);
        jdbc.update("delete from workstation where site_id = " + SITE);
        jdbc.update("delete from workstation_group where site_id = " + SITE);
        jdbc.update("delete from user_role where user_id in (select id from app_user where site_id = " + SITE + ")");
        jdbc.update("delete from app_user where site_id = " + SITE);
        jdbc.update("delete from site where id = " + SITE);
        parameters.invalidate();
        reset(audit);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private String call(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder r = request.with(authentication(who)).with(csrf());
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private static String value(String scope, Long ref, String value, Long version) {
        return "{\"scope\":\"" + scope + "\",\"scopeRefId\":" + ref + ",\"value\":\"" + value + "\"" + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private String setValue(String code, String scope, Long ref, String value, int expected) throws Exception {
        return call(put(BASE + "/" + code + "/values"), ADMIN, value(scope, ref, value, null), expected);
    }

    private void removeValue(String code, String scope, Long ref) throws Exception {
        call(delete(BASE + "/" + code + "/values?scope=" + scope + (ref == null ? "" : "&scopeRefId=" + ref)), ADMIN, null, 200);
    }

    private long createGroup(String code) throws Exception {
        String body = "{\"code\":\"" + code + "\",\"name\":\"" + code + "\",\"allowedPermissions\":[]}";
        return ((Number) JsonPath.read(call(post("/api/admin/workstation-groups"), ADMIN, body, 201), "$.id")).longValue();
    }

    private long createWorkstation(String code, Long group) throws Exception {
        String body = "{\"code\":\"" + code + "\",\"name\":\"" + code + "\",\"type\":\"KIOSK\",\"workstationGroupId\":" + group + "}";
        return ((Number) JsonPath.read(call(post("/api/admin/workstations"), ADMIN, body, 201), "$.id")).longValue();
    }

    private String effective(String code, Long workstationId) throws Exception {
        String list = call(get(BASE + (workstationId == null ? "" : "?workstationId=" + workstationId)), ADMIN, null, 200);
        return JsonPath.<List<String>>read(list, "$[?(@.code == '" + code + "')].effective.value").get(0)
                + "@" + JsonPath.<List<String>>read(list, "$[?(@.code == '" + code + "')].effective.source").get(0);
    }

    private boolean globalValueExists(String code) {
        return jdbc.queryForObject("select count(*) from parameter_value v join parameter_definition d on d.id = v.parameter_definition_id "
                + "where d.code = ? and v.scope = 'GLOBAL'", Integer.class, code) > 0;
    }

    /** A user of the test site with a temporary password: {username, password, id}. */
    private Object[] createUser(String username) throws Exception {
        String created = call(post("/api/admin/users"), ADMIN, "{\"username\":\"" + username + "\",\"displayName\":\"T\",\"roles\":[\"VIEWER\"]}", 201);
        return new Object[] {username, JsonPath.read(created, "$.temporaryPassword"), ((Number) JsonPath.read(created, "$.user.id")).longValue()};
    }

    // ---- the definitions -----------------------------------------------------------------------------------

    @Test
    void theSeededSecurityBoundsAreExactlyTheAgreedOnes() {
        Map<String, String[]> expected = Map.of(
                MIN_LENGTH, new String[] {"INT", "8", "8", "128", "GLOBAL,SITE"},
                ATTEMPTS, new String[] {"INT", "5", "3", "10", "GLOBAL,SITE"},
                MINUTES, new String[] {"INT", "15", "5", "1440", "GLOBAL,SITE"},
                SESSION, new String[] {"DURATION", "PT30M", "PT5M", "PT12H", "GLOBAL,SITE,WS_GROUP,WORKSTATION"},
                "audit.retention_days", new String[] {"INT", "365", "30", "3650", "GLOBAL,SITE"});
        expected.forEach((code, e) -> {
            Map<String, Object> row = jdbc.queryForMap("select value_type, default_value, min_value, max_value, allowed_scopes, restart_needed "
                    + "from parameter_definition where code = ?", code);
            assertThat(new String[] {(String) row.get("value_type"), (String) row.get("default_value"), (String) row.get("min_value"),
                    (String) row.get("max_value"), (String) row.get("allowed_scopes")}).as(code).containsExactly(e[0], e[1], e[2], e[3], e[4]);
            assertThat(row.get("restart_needed")).as(code + " restart").isEqualTo(false);
        });
    }

    // ---- most specific wins -----------------------------------------------------------------------------------

    @Test
    void theMostSpecificValueWins() throws Exception {
        Assumptions.assumeFalse(globalValueExists(SESSION), "a real GLOBAL value exists: not touching it");
        long group = createGroup("ZT-KIOSKS");
        long other = createGroup("ZT-OTHER");
        long kiosk = createWorkstation("ZT-K1", group);
        long kiosk2 = createWorkstation("ZT-K2", group);
        long elsewhere = createWorkstation("ZT-E1", other);

        assertThat(effective(SESSION, kiosk)).isEqualTo("PT30M@DEFAULT");

        setValue(SESSION, "GLOBAL", null, "PT2H", 200);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT2H@GLOBAL");
        setValue(SESSION, "SITE", null, "PT1H", 200);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT1H@SITE");
        assertThat(effective(SESSION, null)).isEqualTo("PT1H@SITE"); // site-level view

        setValue(SESSION, "WS_GROUP", group, "PT45M", 200);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT45M@WS_GROUP");
        assertThat(effective(SESSION, kiosk2)).isEqualTo("PT45M@WS_GROUP");
        assertThat(effective(SESSION, elsewhere)).isEqualTo("PT1H@SITE");  // another group: not affected
        assertThat(effective(SESSION, null)).isEqualTo("PT1H@SITE");

        setValue(SESSION, "WORKSTATION", kiosk, "PT10M", 200);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT10M@WORKSTATION");
        assertThat(effective(SESSION, kiosk2)).isEqualTo("PT45M@WS_GROUP"); // only this workstation
        assertThat(parameters.durationValue(SESSION, SITE, group, kiosk)).isEqualTo(Duration.ofMinutes(10));
        assertThat(parameters.durationValue(SESSION, SITE, null, null)).isEqualTo(Duration.ofHours(1));

        // taking the overrides away falls back step by step
        removeValue(SESSION, "WORKSTATION", kiosk);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT45M@WS_GROUP");
        removeValue(SESSION, "WS_GROUP", group);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT1H@SITE");
        removeValue(SESSION, "SITE", null);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT2H@GLOBAL");
        removeValue(SESSION, "GLOBAL", null);
        assertThat(effective(SESSION, kiosk)).isEqualTo("PT30M@DEFAULT");

        // the screen names where a value comes from
        setValue(SESSION, "WS_GROUP", group, "PT45M", 200);
        String list = call(get(BASE + "?workstationId=" + kiosk), ADMIN, null, 200);
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.code == '" + SESSION + "')].effective.sourceRefCode")).containsExactly("ZT-KIOSKS");
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.code == '" + SESSION + "')].overrides[*].scope")).containsExactly("WS_GROUP");
    }

    // ---- bounds ------------------------------------------------------------------------------------------------

    @Test
    void theSecurityBoundsCannotBeWidened() throws Exception {
        Object[][] cases = {
                {MIN_LENGTH, "7", 400}, {MIN_LENGTH, "8", 200}, {MIN_LENGTH, "128", 200}, {MIN_LENGTH, "129", 400},
                {ATTEMPTS, "2", 400}, {ATTEMPTS, "3", 200}, {ATTEMPTS, "10", 200}, {ATTEMPTS, "11", 400},
                {MINUTES, "4", 400}, {MINUTES, "5", 200}, {MINUTES, "1440", 200}, {MINUTES, "1441", 400},
                {SESSION, "PT4M", 400}, {SESSION, "PT5M", 200}, {SESSION, "PT12H", 200}, {SESSION, "PT12H1M", 400}, {SESSION, "PT0S", 400},
                {SESSION, "-PT5M", 400}, {SESSION, "30", 400}, {SESSION, "abc", 400}, {MIN_LENGTH, "abc", 400}, {MIN_LENGTH, "8.5", 400}, {MIN_LENGTH, "", 400},
        };
        for (Object[] c : cases) {
            String body = setValue((String) c[0], "SITE", null, (String) c[1], (Integer) c[2]);
            if ((Integer) c[2] == 400) {
                assertThat(JsonPath.<List<String>>read(body, "$.details").get(0)).as(c[0] + " = " + c[1]).startsWith("value: ");
            } else {
                removeValue((String) c[0], "SITE", null); // so the next case is a new value, not a change
            }
        }
        // a value inside the bounds is stored in its normal form (PT90M -> PT1H30M)
        setValue(SESSION, "SITE", null, "pt90m", 200);
        assertThat(parameters.durationValue(SESSION, SITE, null, null)).isEqualTo(Duration.ofMinutes(90));
        // the screen cannot change the bounds: there is no endpoint at all for definitions (404)
        call(put(BASE + "/" + MIN_LENGTH), ADMIN, "{\"min\":\"1\"}", 404);
    }

    @Test
    void scopesPermissionsAndVersionsAreChecked() throws Exception {
        // lockout settings are GLOBAL / SITE only
        long group = createGroup("ZT-G");
        long ws = createWorkstation("ZT-W", group);
        String refused = setValue(ATTEMPTS, "WORKSTATION", ws, "4", 400);
        assertThat(JsonPath.<List<String>>read(refused, "$.details")).containsExactly("scope: this parameter cannot be set for WORKSTATION");
        setValue(ATTEMPTS, "WS_GROUP", group, "4", 400);
        setValue(SESSION, "WORKSTATION", 999_999_999L, "PT10M", 400);   // unknown workstation
        setValue(SESSION, "WORKSTATION", null, "PT10M", 400);           // workstation missing
        setValue(SESSION, "PLANET", null, "PT10M", 400);
        setValue("no.such.parameter", "SITE", null, "1", 404);

        // changing an existing value needs the version, and a stale one is refused
        String created = setValue(ATTEMPTS, "SITE", null, "4", 200);
        long version = ((Number) JsonPath.<List<Integer>>read(created, "$[?(@.code == '" + ATTEMPTS + "')].overrides[0].version").get(0)).longValue();
        call(put(BASE + "/" + ATTEMPTS + "/values"), ADMIN, value("SITE", null, "5", null), 400);
        call(put(BASE + "/" + ATTEMPTS + "/values"), ADMIN, value("SITE", null, "5", version), 200);
        String stale = call(put(BASE + "/" + ATTEMPTS + "/values"), ADMIN, value("SITE", null, "6", version), 409);
        assertThat(JsonPath.<String>read(stale, "$.code")).isEqualTo("STALE_VERSION");
        call(delete(BASE + "/" + ATTEMPTS + "/values?scope=WORKSTATION&scopeRefId=" + ws), ADMIN, null, 404); // nothing to remove

        // looking needs PARAMETER_VIEW, changing needs PARAMETER_EDIT
        var viewOnly = actor(SITE, "PARAMETER_VIEW");
        var nothing = actor(SITE, "DASHBOARD_VIEW");
        call(get(BASE), viewOnly, null, 200);
        call(put(BASE + "/" + ATTEMPTS + "/values"), viewOnly, value("SITE", null, "5", version + 1), 403);
        call(delete(BASE + "/" + ATTEMPTS + "/values?scope=SITE"), viewOnly, null, 403);
        call(get(BASE), nothing, null, 403);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void everyChangeIsAudited() throws Exception {
        String created = setValue(MINUTES, "SITE", null, "30", 200);
        long version = ((Number) JsonPath.<List<Integer>>read(created, "$[?(@.code == '" + MINUTES + "')].overrides[0].version").get(0)).longValue();
        call(put(BASE + "/" + MINUTES + "/values"), ADMIN, value("SITE", null, "45", version), 200);
        removeValue(MINUTES, "SITE", null);

        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "PARAMETER_VALUE_SET".equals(e.action())
                && e.beforeValue() == null && e.afterValue().contains(MINUTES) && e.afterValue().contains("\"30\"")));
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "PARAMETER_VALUE_SET".equals(e.action())
                && e.beforeValue() != null && e.beforeValue().contains("\"30\"") && e.afterValue().contains("\"45\"")));
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "PARAMETER_VALUE_REMOVED".equals(e.action())
                && e.beforeValue().contains("\"45\"") && e.afterValue() == null));
    }

    // ---- the existing settings now read the parameters ----------------------------------------------------------

    @Test
    void lockoutFollowsTheParameters() throws Exception {
        Object[] user = createUser("zt-lock");
        setValue(ATTEMPTS, "SITE", null, "3", 200);
        setValue(MINUTES, "SITE", null, "7", 200);

        assertThat(auth.login("zt-lock", "wrong-1", "127.0.0.1").status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        assertThat(auth.login("zt-lock", "wrong-2", "127.0.0.1").status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        AuthService.LoginResult third = auth.login("zt-lock", "wrong-3", "127.0.0.1"); // the 3rd, not the default 5th
        assertThat(third.status()).isEqualTo(AuthService.Status.LOCKED);
        assertThat(Duration.between(Instant.now(), third.lockedUntil()).toMinutes()).isBetween(5L, 7L); // about 7 minutes
        assertThat(auth.login("zt-lock", (String) user[1], "127.0.0.1").status()).isEqualTo(AuthService.Status.LOCKED);
    }

    @Test
    void passwordLengthFollowsTheParameter() throws Exception {
        Object[] user = createUser("zt-pw");
        var principal = new WmsUserPrincipal((Long) user[2], SITE, "zt-pw", "T", List.of("VIEWER"), List.of(), true);
        String temp = (String) user[1];

        assertThat(auth.passwordMinLength(SITE)).isEqualTo(8); // default
        setValue(MIN_LENGTH, "SITE", null, "12", 200);
        assertThat(auth.passwordMinLength(SITE)).isEqualTo(12);
        assertThat(auth.changePassword(principal, temp, "only-ten-ch", "127.0.0.1")).isEqualTo(AuthService.ChangeStatus.TOO_SHORT); // 11
        assertThat(auth.changePassword(principal, temp, "twelve-chars", "127.0.0.1")).isEqualTo(AuthService.ChangeStatus.OK);
    }

    @Test
    void sessionTimeoutFollowsTheParametersAndTheWorkstation() throws Exception {
        Object[] user = createUser("zt-session");
        long group = createGroup("ZT-SESS");
        long kiosk = createWorkstation("ZT-SK", group);
        setValue(SESSION, "SITE", null, "PT20M", 200);
        setValue(SESSION, "WORKSTATION", kiosk, "PT7M", 200);
        setValue(MIN_LENGTH, "SITE", null, "10", 200);

        MvcResult office = login("zt-session", (String) user[1], null);
        assertThat(office.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(20 * 60);
        assertThat(JsonPath.<Integer>read(office.getResponse().getContentAsString(), "$.passwordMinLength")).isEqualTo(10);

        // a browser registered as the kiosk gets the shorter timeout
        String token = register(kiosk);
        MvcResult atKiosk = login("zt-session", (String) user[1], token);
        assertThat(atKiosk.getRequest().getSession(false).getMaxInactiveInterval()).isEqualTo(7 * 60);
    }

    private String register(long workstationId) throws Exception {
        var response = mvc.perform(post("/api/admin/workstations/" + workstationId + "/register").with(authentication(ADMIN)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        return response.getCookie(WorkstationResolver.COOKIE).getValue();
    }

    private MvcResult login(String username, String password, String cookie) throws Exception {
        MockHttpServletRequestBuilder r = post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        if (cookie != null) {
            r = r.cookie(new Cookie(WorkstationResolver.COOKIE, cookie));
        }
        return mvc.perform(r).andExpect(status().isOk()).andReturn();
    }

    // ---- housekeeping --------------------------------------------------------------------------------------------

    @Test
    void deletingAWorkstationOrGroupTakesItsParameterValuesWithIt() throws Exception {
        long group = createGroup("ZT-DEL");
        long ws = createWorkstation("ZT-DELW", group);
        setValue(SESSION, "WS_GROUP", group, "PT15M", 200);
        setValue(SESSION, "WORKSTATION", ws, "PT6M", 200);
        assertThat(jdbc.queryForObject("select count(*) from parameter_value where scope in ('WS_GROUP','WORKSTATION') and "
                + "scope_ref_id in (" + group + "," + ws + ")", Integer.class)).isEqualTo(2);

        call(delete("/api/admin/workstations/" + ws), ADMIN, null, 204);
        call(delete("/api/admin/workstation-groups/" + group), ADMIN, null, 204);
        assertThat(jdbc.queryForObject("select count(*) from parameter_value where scope in ('WS_GROUP','WORKSTATION') and "
                + "scope_ref_id in (" + group + "," + ws + ")", Integer.class)).isZero();
        // and the list no longer shows them
        String list = call(get(BASE), ADMIN, null, 200);
        assertThat(JsonPath.<List<Object>>read(list, "$[?(@.code == '" + SESSION + "')].overrides[*]")).isEmpty();
    }
}
