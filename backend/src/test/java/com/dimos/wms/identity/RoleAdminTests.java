package com.dimos.wms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Permission catalogue and roles: built-in roles are locked, custom roles are managed by a super administrator only. */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAdminTests {

    private static final String BASE = "/api/admin/roles";
    private static final long ACTOR = 999_999_999L;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AppPermissionRepository permissionRepository;

    @Autowired
    UserSessions sessions;

    @Autowired
    AuthService auth;

    @MockitoBean
    AuditService audit;

    private static UsernamePasswordAuthenticationToken actor(String role, String... permissions) {
        var principal = new WmsUserPrincipal(ACTOR, 1L, "zt-actor", "Actor", List.of(role), List.of(permissions), false);
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private static final UsernamePasswordAuthenticationToken SUPER =
            actor("SUPER_ADMIN", "ROLE_MANAGE", "USER_MANAGE", "AUDIT_VIEW");
    private static final UsernamePasswordAuthenticationToken ADMIN_WITH_ROLE_MANAGE = actor("ADMIN", "ROLE_MANAGE", "USER_MANAGE");
    private static final UsernamePasswordAuthenticationToken ADMIN = actor("ADMIN", "USER_MANAGE");

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'zt-%')");
        jdbc.update("delete from user_role where role_id in (select id from app_role where code like 'ZT\\_%' escape '\\')");
        jdbc.update("delete from app_user where username like 'zt-%'");
        jdbc.update("delete from role_permission where role_id in (select id from app_role where code like 'ZT\\_%' escape '\\')");
        jdbc.update("delete from app_role where code like 'ZT\\_%' escape '\\'");
        reset(audit);
    }

    private String send(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who, String body,
                        int expected) throws Exception {
        MockHttpServletRequestBuilder r = request.with(authentication(who)).with(csrf());
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private static String role(String code, String name, String permissions, Long version) {
        return "{\"code\":\"" + code + "\",\"name\":\"" + name + "\",\"description\":\"d\",\"permissions\":" + permissions
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private long createRole(String code, String permissions) throws Exception {
        return ((Number) JsonPath.read(send(post(BASE), SUPER, role(code, "Test " + code, permissions, null), 201), "$.id")).longValue();
    }

    // ---- the catalogue ----------------------------------------------------------------------------------

    @Test
    void theCatalogueInCodeIsExactlyWhatTheDatabaseHolds() {
        Set<String> inDb = new HashSet<>();
        permissionRepository.findAll().forEach(p -> {
            inDb.add(p.getCode());
            PermissionCatalogue.Entry e = PermissionCatalogue.get(p.getCode());
            assertThat(e).as("catalogue entry for " + p.getCode()).isNotNull();
            assertThat(e.module()).as("module of " + p.getCode()).isEqualTo(p.getModule());
        });
        Set<String> inCode = new HashSet<>();
        PermissionCatalogue.ALL.forEach(e -> inCode.add(e.code()));
        assertThat(inDb).isEqualTo(inCode);
        assertThat(inCode).contains("ROLE_MANAGE", "WORKSTATION_MANAGE", "PARAMETER_VIEW", "PARAMETER_EDIT");
    }

    @Test
    void theCatalogueIsServedGroupedByModule() throws Exception {
        String list = send(get("/api/admin/permissions"), ADMIN, null, 200);
        assertThat(JsonPath.<List<String>>read(list, "$[*].code")).hasSize(PermissionCatalogue.ALL.size());
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.code == 'MASTERDATA_EDIT')].module")).containsExactly("masterdata");
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.code == 'ROLE_MANAGE')].module")).containsExactly("administration");
        send(get("/api/admin/permissions"), actor("VIEWER", "MASTERDATA_VIEW"), null, 403);
    }

    @Test
    void theBuiltInRolesHaveTheirDefaultRights() throws Exception {
        String list = send(get(BASE), ADMIN, null, 200);
        assertThat(JsonPath.<List<String>>read(list, "$[?(@.builtin == true)].code"))
                .containsExactlyInAnyOrder("SUPER_ADMIN", "ADMIN", "OPERATOR", "VIEWER");
        assertThat(JsonPath.<List<List<String>>>read(list, "$[?(@.code == 'SUPER_ADMIN')].permissions").get(0))
                .containsExactlyInAnyOrderElementsOf(PermissionCatalogue.ALL.stream().map(PermissionCatalogue.Entry::code).toList());
        assertThat(JsonPath.<List<List<String>>>read(list, "$[?(@.code == 'ADMIN')].permissions").get(0))
                .contains("WORKSTATION_MANAGE", "PARAMETER_VIEW", "USER_MANAGE").doesNotContain("ROLE_MANAGE", "PARAMETER_EDIT");
        assertThat(JsonPath.<List<List<String>>>read(list, "$[?(@.code == 'VIEWER')].permissions").get(0))
                .containsExactlyInAnyOrder("DASHBOARD_VIEW", "MASTERDATA_VIEW");
    }

    // ---- built-in roles are locked ----------------------------------------------------------------------

    @Test
    void aBuiltInRoleCannotBeChangedOrDeleted() throws Exception {
        String list = send(get(BASE), SUPER, null, 200);
        long viewer = ((Number) JsonPath.<List<Integer>>read(list, "$[?(@.code == 'VIEWER')].id").get(0)).longValue();
        long viewerVersion = ((Number) JsonPath.<List<Integer>>read(list, "$[?(@.code == 'VIEWER')].version").get(0)).longValue();

        String changed = send(put(BASE + "/" + viewer), SUPER, role("VIEWER", "Viewer", "[\"MASTERDATA_EDIT\"]", viewerVersion), 422);
        assertThat(JsonPath.<String>read(changed, "$.code")).isEqualTo("NOT_ALLOWED");
        assertThat(JsonPath.<String>read(changed, "$.message")).contains("built-in role VIEWER is locked");
        send(delete(BASE + "/" + viewer), SUPER, null, 422);
        // nothing changed
        assertThat(jdbc.queryForObject("select count(*) from role_permission rp join app_permission p on p.id = rp.permission_id "
                + "where rp.role_id = ? and p.code = 'MASTERDATA_EDIT'", Integer.class, viewer)).isZero();
    }

    // ---- custom roles ------------------------------------------------------------------------------------

    @Test
    void customRolesCanBeCreatedChangedAndDeletedAndEverythingIsAudited() throws Exception {
        long id = createRole("ZT_PICKER", "[\"DASHBOARD_VIEW\",\"MASTERDATA_VIEW\"]");
        String list = send(get(BASE), SUPER, null, 200);
        assertThat(JsonPath.<List<Boolean>>read(list, "$[?(@.code == 'ZT_PICKER')].builtin")).containsExactly(false);
        assertThat(JsonPath.<List<List<String>>>read(list, "$[?(@.code == 'ZT_PICKER')].permissions").get(0))
                .containsExactly("DASHBOARD_VIEW", "MASTERDATA_VIEW");
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "ROLE_CREATED".equals(e.action())
                && e.afterValue().contains("ZT_PICKER") && e.beforeValue() == null));

        long version = ((Number) JsonPath.<List<Integer>>read(list, "$[?(@.code == 'ZT_PICKER')].version").get(0)).longValue();
        String updated = send(put(BASE + "/" + id), SUPER,
                role("ZT_PICKER", "Picker", "[\"DASHBOARD_VIEW\",\"MASTERDATA_VIEW\",\"MASTERDATA_EDIT\"]", version), 200);
        assertThat(JsonPath.<List<String>>read(updated, "$.permissions")).containsExactly("DASHBOARD_VIEW", "MASTERDATA_EDIT", "MASTERDATA_VIEW");
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "ROLE_UPDATED".equals(e.action())
                && !e.beforeValue().contains("MASTERDATA_EDIT") && e.afterValue().contains("MASTERDATA_EDIT")));

        // a stale edit and a code change are refused
        String stale = send(put(BASE + "/" + id), SUPER, role("ZT_PICKER", "Late", "[]", version), 409);
        assertThat(JsonPath.<String>read(stale, "$.message")).isEqualTo("This record was changed by someone else - reload");
        send(put(BASE + "/" + id), SUPER, role("ZT_OTHER", "x", "[]", version + 1), 400);

        send(delete(BASE + "/" + id), SUPER, null, 204);
        assertThat(jdbc.queryForObject("select count(*) from app_role where id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from role_permission where role_id = ?", Integer.class, id)).isZero();
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "ROLE_DELETED".equals(e.action()) && e.afterValue() == null));
    }

    @Test
    void badInputIsRefusedWithFieldMessages() throws Exception {
        createRole("ZT_ONE", "[]");
        String dup = send(post(BASE), SUPER, role("ZT_ONE", "again", "[]", null), 400);
        assertThat(JsonPath.<List<String>>read(dup, "$.details")).containsExactly("code: this code is already used");
        send(post(BASE), SUPER, role("zt_lower", "x", "[]", null), 400);
        send(post(BASE), SUPER, role("ZT_X", "x", "[\"NO_SUCH_PERMISSION\"]", null), 400);
        send(post(BASE), SUPER, role("ZT_X", "", "[]", null), 400);
    }

    @Test
    void aRoleCodeThatWouldBecomeAPermissionIsReserved() throws Exception {
        // the login turns role X into the authority ROLE_X, and ROLE_MANAGE is a permission: a role "MANAGE" must not exist
        String reserved = send(post(BASE), SUPER, role("MANAGE", "Sneaky", "[]", null), 400);
        assertThat(JsonPath.<List<String>>read(reserved, "$.details")).containsExactly("code: this code is reserved");
    }

    // ---- who may change roles ---------------------------------------------------------------------------

    @Test
    void onlyASuperAdministratorChangesRoles() throws Exception {
        long id = createRole("ZT_GUARDED", "[]");
        // an admin who even holds ROLE_MANAGE is refused by the super-administrator rule
        String refused = send(post(BASE), ADMIN_WITH_ROLE_MANAGE, role("ZT_NEW", "x", "[]", null), 422);
        assertThat(JsonPath.<String>read(refused, "$.message")).isEqualTo("Only a super administrator can change roles.");
        send(put(BASE + "/" + id), ADMIN_WITH_ROLE_MANAGE, role("ZT_GUARDED", "x", "[]", 0L), 422);
        send(delete(BASE + "/" + id), ADMIN_WITH_ROLE_MANAGE, null, 422);
        // without ROLE_MANAGE the endpoints are closed; reading the list is open for user managers
        send(post(BASE), ADMIN, role("ZT_NEW", "x", "[]", null), 403);
        send(delete(BASE + "/" + id), ADMIN, null, 403);
        send(get(BASE), ADMIN, null, 200);
        send(get(BASE), actor("VIEWER", "MASTERDATA_VIEW"), null, 403);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verify(audit, never()).record(argThat((AuditEvent e) -> "ROLE_UPDATED".equals(e.action()) || "ROLE_DELETED".equals(e.action())));
    }

    // ---- use and effect of a role -----------------------------------------------------------------------

    @Test
    void aRoleInUseCannotBeDeletedAndTheMessageSaysBy() throws Exception {
        long id = createRole("ZT_USED", "[\"DASHBOARD_VIEW\"]");
        send(post("/api/admin/users"), SUPER, "{\"username\":\"zt-holder\",\"displayName\":\"H\",\"roles\":[\"ZT_USED\"]}", 201);
        String refused = send(delete(BASE + "/" + id), SUPER, null, 409);
        assertThat(JsonPath.<String>read(refused, "$.code")).isEqualTo("IN_USE");
        assertThat(JsonPath.<String>read(refused, "$.message")).contains("Role ZT_USED cannot be deleted").contains("still used by 1 user");
        assertThat(JsonPath.<List<Integer>>read(send(get(BASE), SUPER, null, 200), "$[?(@.code == 'ZT_USED')].userCount")).containsExactly(1);
    }

    @Test
    void changingARoleSignsOutItsHoldersButNotTheAdministrator() throws Exception {
        long id = createRole("ZT_SHIFT", "[\"DASHBOARD_VIEW\"]");
        String created = send(post("/api/admin/users"), SUPER, "{\"username\":\"zt-shift\",\"displayName\":\"S\",\"roles\":[\"ZT_SHIFT\"]}", 201);
        long userId = ((Number) JsonPath.read(created, "$.user.id")).longValue();
        String password = JsonPath.read(created, "$.temporaryPassword");
        MockHttpSession holder = new MockHttpSession();
        MockHttpSession admin = new MockHttpSession();
        sessions.register(userId, holder);
        sessions.register(ACTOR, admin);

        // at login the user gets exactly the permissions of the role
        assertThat(auth.login("zt-shift", password, "127.0.0.1").principal().permissions()).containsExactly("DASHBOARD_VIEW");

        long version = ((Number) JsonPath.<List<Integer>>read(send(get(BASE), SUPER, null, 200), "$[?(@.code == 'ZT_SHIFT')].version").get(0)).longValue();
        send(put(BASE + "/" + id), SUPER, role("ZT_SHIFT", "Shift", "[\"DASHBOARD_VIEW\",\"MASTERDATA_VIEW\"]", version), 200);
        assertThat(holder.isInvalid()).as("holder signed out").isTrue();
        assertThat(admin.isInvalid()).as("administrator stays signed in").isFalse();
        assertThat(auth.login("zt-shift", password, "127.0.0.1").principal().permissions())
                .containsExactly("DASHBOARD_VIEW", "MASTERDATA_VIEW");
    }
}
