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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Workstations, workstation groups, browser registration and the server-side restriction of rights. */
@SpringBootTest
@AutoConfigureMockMvc
class WorkstationTests {

    private static final String WS = "/api/admin/workstations";
    private static final String GROUPS = "/api/admin/workstation-groups";
    private static final String ZT_AREA_SQL = "CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AuditService audit;

    private static UsernamePasswordAuthenticationToken actor(String role, String... permissions) {
        var principal = new WmsUserPrincipal(999_999_999L, 1L, "zt-actor", "Actor", List.of(role), List.of(permissions), false);
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    /** Everything an administrator normally has. */
    private static final String[] ALL = {"DASHBOARD_VIEW", "MASTERDATA_VIEW", "MASTERDATA_EDIT", "USER_MANAGE", "AUDIT_VIEW",
            "WORKSTATION_MANAGE", "ROLE_MANAGE"};
    private static final UsernamePasswordAuthenticationToken SUPER = actor("SUPER_ADMIN", ALL);
    private static final UsernamePasswordAuthenticationToken ADMIN = actor("ADMIN", ALL);

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from workstation where code like 'ZT-%'");
        jdbc.update("delete from workstation_group_permission where workstation_group_id in (select id from workstation_group where code like 'ZT-%')");
        jdbc.update("delete from workstation_group where code like 'ZT-%'");
        jdbc.update("delete from location where code like 'ZT-%'");
        jdbc.update("delete from location_type where code like 'ZT-%'");
        jdbc.update("delete from area where code like 'ZT-%'");
        reset(audit);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who,
                                         String cookie, String body, int expected) throws Exception {
        MockHttpServletRequestBuilder r = request.with(authentication(who)).with(csrf());
        if (cookie != null) {
            r = r.cookie(new Cookie(WorkstationResolver.COOKIE, cookie));
        }
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse();
    }

    private String json(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who, String cookie, String body,
                        int expected) throws Exception {
        return call(request, who, cookie, body, expected).getContentAsString();
    }

    private long createGroup(String code, String allowed, boolean enabled) throws Exception {
        String body = "{\"code\":\"" + code + "\",\"name\":\"Group " + code + "\",\"enabled\":" + enabled + ",\"allowedPermissions\":" + allowed + "}";
        return ((Number) JsonPath.read(json(post(GROUPS), SUPER, null, body, 201), "$.id")).longValue();
    }

    private static String workstation(String code, String type, Long group, String ip, boolean checkIp, boolean enabled, Long version) {
        return "{\"code\":\"" + code + "\",\"name\":\"WS " + code + "\",\"type\":\"" + type + "\",\"workstationGroupId\":" + group
                + ",\"hostName\":\"pc-" + code.toLowerCase() + "\",\"ipAddress\":" + (ip == null ? "null" : "\"" + ip + "\"")
                + ",\"checkIp\":" + checkIp + ",\"defaultPrinter\":\"Zebra-1\",\"enabled\":" + enabled
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private long createWorkstation(String code, Long group, boolean enabled) throws Exception {
        return ((Number) JsonPath.read(json(post(WS), SUPER, null, workstation(code, "HANDHELD", group, null, false, enabled, null), 201), "$.id")).longValue();
    }

    /** Registers the (test) browser and returns the cookie value the browser would keep. */
    private String register(long workstationId) throws Exception {
        MockHttpServletResponse response = call(post(WS + "/" + workstationId + "/register"), SUPER, null, null, 200);
        Cookie cookie = response.getCookie(WorkstationResolver.COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private List<String> mePermissions(UsernamePasswordAuthenticationToken who, String cookie) throws Exception {
        return JsonPath.read(json(get("/api/auth/me"), who, cookie, null, 200), "$.permissions");
    }

    private String workstationMode(UsernamePasswordAuthenticationToken who, String cookie) throws Exception {
        return JsonPath.read(json(get("/api/auth/me"), who, cookie, null, 200), "$.workstation.mode");
    }

    private static final String AREA_BODY = "{\"code\":\"ZT-A\",\"name\":\"x\",\"areaType\":\"STORAGE\",\"version\":0}";

    // ---- groups and workstations -----------------------------------------------------------------------

    @Test
    void groupsAndWorkstationsAreManagedValidatedAndAudited() throws Exception {
        long group = createGroup("ZT-SHOP", "[\"DASHBOARD_VIEW\",\"MASTERDATA_VIEW\"]", true);
        String read = json(get(GROUPS + "/" + group), ADMIN, null, null, 200);
        assertThat(JsonPath.<List<String>>read(read, "$.allowedPermissions")).containsExactly("DASHBOARD_VIEW", "MASTERDATA_VIEW");
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "WORKSTATION_GROUP_CREATED".equals(e.action())
                && e.afterValue().contains("MASTERDATA_VIEW")));

        long id = createWorkstation("ZT-PICK-01", group, true);
        String ws = json(get(WS + "/" + id), ADMIN, null, null, 200);
        assertThat(JsonPath.<String>read(ws, "$.workstationGroupCode")).isEqualTo("ZT-SHOP");
        assertThat(JsonPath.<Boolean>read(ws, "$.registered")).isFalse();
        assertThat(JsonPath.<String>read(ws, "$.defaultPrinter")).isEqualTo("Zebra-1");

        // validation
        assertThat(JsonPath.<List<String>>read(json(post(WS), ADMIN, null, workstation("zt-pick-01", "PC", null, null, false, true, null), 400), "$.details"))
                .containsExactly("code: this code is already used");
        json(post(WS), ADMIN, null, workstation("ZT-X", "TABLET", null, null, false, true, null), 400);
        json(post(WS), ADMIN, null, workstation("ZT-X", "PC", 999_999_999L, null, false, true, null), 400);
        json(post(WS), ADMIN, null, workstation("ZT-X", "PC", null, null, true, true, null), 400); // IP check needs an address
        json(post(WS), ADMIN, null, workstation("ZT-X", "PC", null, "not an ip!", false, true, null), 400);
        json(post(GROUPS), ADMIN, null, "{\"code\":\"ZT-BAD\",\"name\":\"x\",\"allowedPermissions\":[\"NO_SUCH\"]}", 400);

        // update with a stale version is refused; the code is fixed
        long version = ((Number) JsonPath.read(ws, "$.version")).longValue();
        json(put(WS + "/" + id), ADMIN, null, workstation("ZT-PICK-01", "KIOSK", group, "10.1.1.5", false, true, version), 200);
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "WORKSTATION_UPDATED".equals(e.action())
                && e.beforeValue().contains("HANDHELD") && e.afterValue().contains("KIOSK")));
        json(put(WS + "/" + id), ADMIN, null, workstation("ZT-PICK-01", "PC", group, null, false, true, version), 409);
        json(put(WS + "/" + id), ADMIN, null, workstation("ZT-OTHER", "PC", group, null, false, true, version + 1), 400);

        // a group in use cannot be deleted, and the message says by what
        String refused = json(delete(GROUPS + "/" + group), ADMIN, null, null, 409);
        assertThat(JsonPath.<String>read(refused, "$.message")).contains("still used by 1 workstation");
        json(delete(WS + "/" + id), ADMIN, null, null, 204);
        json(delete(GROUPS + "/" + group), ADMIN, null, null, 204);
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "WORKSTATION_DELETED".equals(e.action()) && e.afterValue() == null));
    }

    @Test
    void aLocationUsedByAWorkstationCannotBeDeleted() throws Exception {
        jdbc.update("insert into area (id, site_id, code, name, area_type, version, created_at, created_by, updated_at, updated_by) "
                + "values (9_600_001, 1, 'ZT-AREA', 'a', 'STORAGE', 0, " + ZT_AREA_SQL + ")");
        jdbc.update("insert into location_type (id, site_id, code, name, is_storage, is_transfer, is_workstation, capacity_hu, version, "
                + "created_at, created_by, updated_at, updated_by) values (9_600_002, 1, 'ZT-LT', 't', false, false, true, 1, 0, " + ZT_AREA_SQL + ")");
        jdbc.update("insert into location (id, site_id, code, area_id, location_type_id, level_no, enabled, blocked_in, blocked_out, version, "
                + "created_at, created_by, updated_at, updated_by) values (9_600_003, 1, 'ZT-LOC', 9_600_001, 9_600_002, 0, true, false, false, 0, " + ZT_AREA_SQL + ")");
        String body = "{\"code\":\"ZT-AT-LOC\",\"name\":\"n\",\"type\":\"PC\",\"locationId\":9600003}";
        long id = ((Number) JsonPath.read(json(post(WS), ADMIN, null, body, 201), "$.id")).longValue();
        assertThat(JsonPath.<String>read(json(get(WS + "/" + id), ADMIN, null, null, 200), "$.locationCode")).isEqualTo("ZT-LOC");

        String refused = json(delete("/api/masterdata/locations/9600003"), ADMIN, null, null, 409);
        assertThat(JsonPath.<String>read(refused, "$.message")).contains("Location ZT-LOC cannot be deleted").contains("1 workstation placed here");
    }

    // ---- registration ------------------------------------------------------------------------------------

    @Test
    void registeringSetsAHardenedLongLivedCookieAndStoresOnlyItsHash() throws Exception {
        long id = createWorkstation("ZT-REG", null, true);
        MockHttpServletResponse response = call(post(WS + "/" + id + "/register"), SUPER, null, null, 200);
        String header = response.getHeader("Set-Cookie");
        assertThat(header).startsWith(WorkstationResolver.COOKIE + "=").contains("HttpOnly").contains("Secure")
                .contains("SameSite=Lax").contains("Path=/").contains("Max-Age=315360000");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        String token = response.getCookie(WorkstationResolver.COOKIE).getValue();
        assertThat(token.length()).isGreaterThanOrEqualTo(40);

        String stored = jdbc.queryForObject("select token_hash from workstation where id = ?", String.class, id);
        assertThat(stored).isEqualTo(WorkstationResolver.sha256Hex(token)).doesNotContain(token);
        assertThat(response.getContentAsString()).doesNotContain(token).contains("\"registered\":true"); // not in the JSON either
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "WORKSTATION_REGISTERED".equals(e.action())
                && !String.valueOf(e.detail()).contains(token) && e.afterValue() == null));

        // registering again replaces the browser: the old cookie no longer counts
        String second = register(id);
        assertThat(second).isNotEqualTo(token);
        assertThat(workstationMode(ADMIN, token)).isEqualTo("OFFICE");
        assertThat(workstationMode(ADMIN, second)).isEqualTo("REGISTERED");
    }

    @Test
    void aBrowserThatLostItsCookieIsOfficeModeAndCanBeRegisteredAgain() throws Exception {
        long id = createWorkstation("ZT-LOST", createGroup("ZT-G", "[\"DASHBOARD_VIEW\"]", true), true);
        String token = register(id);
        assertThat(workstationMode(ADMIN, token)).isEqualTo("REGISTERED");
        assertThat(mePermissions(ADMIN, token)).containsExactly("DASHBOARD_VIEW");

        // cookies cleared: no cookie at all = office mode, nothing restricted
        String noCookie = json(get("/api/auth/me"), ADMIN, null, null, 200);
        assertThat(JsonPath.<String>read(noCookie, "$.workstation.mode")).isEqualTo("OFFICE");
        assertThat(JsonPath.<Boolean>read(noCookie, "$.workstation.staleCookie")).isFalse();
        assertThat(JsonPath.<List<String>>read(noCookie, "$.permissions")).contains("MASTERDATA_EDIT");

        // a cookie the server does not know (revoked / replaced) is reported as "not registered"
        json(post(WS + "/" + id + "/revoke"), SUPER, null, null, 200);
        String stale = json(get("/api/auth/me"), ADMIN, token, null, 200);
        assertThat(JsonPath.<String>read(stale, "$.workstation.mode")).isEqualTo("OFFICE");
        assertThat(JsonPath.<Boolean>read(stale, "$.workstation.staleCookie")).isTrue();
        assertThat(JsonPath.<List<String>>read(stale, "$.permissions")).contains("MASTERDATA_EDIT");
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "WORKSTATION_REGISTRATION_REVOKED".equals(e.action())));

        // an administrator registers it again
        String again = register(id);
        assertThat(workstationMode(ADMIN, again)).isEqualTo("REGISTERED");
    }

    // ---- the restriction -----------------------------------------------------------------------------------

    @Test
    void aShopFloorWorkstationBlocksMasterDataEditButTheOfficeDoesNot() throws Exception {
        long group = createGroup("ZT-SHOP", "[\"DASHBOARD_VIEW\",\"MASTERDATA_VIEW\"]", true);
        String cookie = register(createWorkstation("ZT-SHOP-01", group, true));

        // at the shop-floor workstation: view yes, edit no, user administration no
        assertThat(mePermissions(ADMIN, cookie)).containsExactlyInAnyOrder("DASHBOARD_VIEW", "MASTERDATA_VIEW");
        json(get("/api/masterdata/areas"), ADMIN, cookie, null, 200);
        json(put("/api/masterdata/areas/999999999"), ADMIN, cookie, AREA_BODY, 403); // MASTERDATA_EDIT is cut off
        json(get("/api/admin/users"), ADMIN, cookie, null, 403);
        json(post("/api/masterdata/locations/bulk"), ADMIN, cookie, "{\"ids\":[1],\"action\":\"ENABLE\"}", 403);

        // the same user, same rights, in the office (no cookie): allowed (404 = not found, so permission was granted)
        json(put("/api/masterdata/areas/999999999"), ADMIN, null, AREA_BODY, 404);
        json(get("/api/admin/users"), ADMIN, null, null, 200);

        // the user's own rights still limit him at a workstation: a viewer does not gain anything
        var viewer = actor("VIEWER", "DASHBOARD_VIEW", "MASTERDATA_VIEW");
        assertThat(mePermissions(viewer, cookie)).containsExactlyInAnyOrder("DASHBOARD_VIEW", "MASTERDATA_VIEW");
        json(put("/api/masterdata/areas/999999999"), viewer, null, AREA_BODY, 403);
    }

    @Test
    void anEmptyAllowedListOrADisabledGroupMeansNoRestriction() throws Exception {
        long open = createGroup("ZT-OPEN", "[]", true);
        String c1 = register(createWorkstation("ZT-OPEN-01", open, true));
        assertThat(mePermissions(ADMIN, c1)).contains("MASTERDATA_EDIT", "USER_MANAGE");
        assertThat(workstationMode(ADMIN, c1)).isEqualTo("REGISTERED");

        long off = createGroup("ZT-OFF", "[\"DASHBOARD_VIEW\"]", false);
        String c2 = register(createWorkstation("ZT-OFF-01", off, true));
        assertThat(mePermissions(ADMIN, c2)).contains("MASTERDATA_EDIT"); // a disabled group restricts nothing
    }

    @Test
    void aDisabledWorkstationAllowsNothingButAdministratorsStillGetIn() throws Exception {
        String cookie = register(createWorkstation("ZT-DIS", null, false));
        assertThat(workstationMode(ADMIN, cookie)).isEqualTo("DISABLED");
        assertThat(mePermissions(ADMIN, cookie)).isEmpty();
        json(get("/api/masterdata/areas"), ADMIN, cookie, null, 403);
        json(get(WS), ADMIN, cookie, null, 403); // an ordinary admin is locked out of workstations here
        assertThat(mePermissions(SUPER, cookie)).containsExactly("WORKSTATION_MANAGE"); // a super administrator can fix it
        json(get(WS), SUPER, cookie, null, 200);
    }

    @Test
    void theIpCheckKeepsAStolenCookieFromActingAsTheWorkstation() throws Exception {
        long group = createGroup("ZT-IP", "[\"DASHBOARD_VIEW\"]", true);
        long elsewhere = ((Number) JsonPath.read(json(post(WS), SUPER, null,
                workstation("ZT-IP-OTHER", "PC", group, "10.9.9.9", true, true, null), 201), "$.id")).longValue();
        String cookie = register(elsewhere);
        // the request comes from 127.0.0.1, the registered address is 10.9.9.9: not treated as that workstation
        String me = json(get("/api/auth/me"), ADMIN, cookie, null, 200);
        assertThat(JsonPath.<String>read(me, "$.workstation.mode")).isEqualTo("IP_MISMATCH");
        assertThat(JsonPath.<String>read(me, "$.workstation.code")).isEqualTo("ZT-IP-OTHER");

        long here = ((Number) JsonPath.read(json(post(WS), SUPER, null,
                workstation("ZT-IP-HERE", "PC", group, "127.0.0.1", true, true, null), 201), "$.id")).longValue();
        assertThat(workstationMode(ADMIN, register(here))).isEqualTo("REGISTERED");
    }

    // ---- rule A: administration can never be locked out -----------------------------------------------------

    @Test
    void aRestrictionNeverRemovesWorkstationManageFromASuperAdministrator() throws Exception {
        long group = createGroup("ZT-TIGHT", "[\"DASHBOARD_VIEW\"]", true);
        String cookie = register(createWorkstation("ZT-TIGHT-01", group, true));

        assertThat(mePermissions(SUPER, cookie)).containsExactlyInAnyOrder("DASHBOARD_VIEW", "WORKSTATION_MANAGE");
        json(get(WS), SUPER, cookie, null, 200);                    // kept
        json(get("/api/admin/audit"), SUPER, cookie, null, 403);    // everything else follows the restriction
        // an ordinary administrator loses it like any other permission
        assertThat(mePermissions(ADMIN, cookie)).containsExactly("DASHBOARD_VIEW");
        json(get(WS), ADMIN, cookie, null, 403);
    }

    @Test
    void aSuperAdministratorCanAlwaysUnregisterThisBrowserFromTheDeviceItself() throws Exception {
        long group = createGroup("ZT-TIGHT", "[]", true);
        long id = createWorkstation("ZT-UNREG", group, true);
        String cookie = register(id);
        // make it as tight as possible: nothing at all allowed except one permission
        long version = ((Number) JsonPath.read(json(get(GROUPS + "/" + group), SUPER, null, null, 200), "$.version")).longValue();
        json(put(GROUPS + "/" + group), SUPER, null,
                "{\"code\":\"ZT-TIGHT\",\"name\":\"t\",\"enabled\":true,\"allowedPermissions\":[\"DASHBOARD_VIEW\"],\"version\":" + version + "}", 200);
        json(get("/api/admin/audit"), SUPER, cookie, null, 403);

        // only a super administrator may do it; it needs no other permission
        call(post("/api/workstation/unregister"), ADMIN, cookie, null, 403);
        MockHttpServletResponse done = call(post("/api/workstation/unregister"), SUPER, cookie, null, 204);
        assertThat(done.getHeader("Set-Cookie")).contains(WorkstationResolver.COOKIE + "=").contains("Max-Age=0");
        assertThat(jdbc.queryForObject("select token_hash from workstation where id = ?", String.class, id)).isNull();
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "WORKSTATION_BROWSER_UNREGISTERED".equals(e.action())));

        // back to office mode: no restriction any more (the old cookie is unknown now)
        assertThat(workstationMode(SUPER, cookie)).isEqualTo("OFFICE");
        json(get("/api/admin/audit"), SUPER, cookie, null, 200);
        // unregistering a browser that is not registered is harmless
        call(post("/api/workstation/unregister"), SUPER, null, null, 204);
    }

    @Test
    void onlyPeopleWhoManageWorkstationsMayManageThem() throws Exception {
        var viewer = actor("VIEWER", "DASHBOARD_VIEW");
        json(get(WS), viewer, null, null, 403);
        json(get(GROUPS), viewer, null, null, 403);
        json(post(WS), viewer, null, workstation("ZT-X", "PC", null, null, false, true, null), 403);
        json(post(WS + "/1/register"), viewer, null, null, 403);
        call(post("/api/workstation/unregister"), viewer, null, null, 403);
        mvc.perform(get(WS)).andExpect(status().isUnauthorized());
    }
}
