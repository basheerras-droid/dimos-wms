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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** User groups and the rights users inherit from them. Test data: user names zt-*, group codes ZT-*, role codes ZT_*. */
@SpringBootTest
@AutoConfigureMockMvc
class GroupAdminTests {

    private static final String GROUPS = "/api/admin/groups";
    private static final String USERS = "/api/admin/users";
    private static final long ACTOR = 999_999_999L;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuthService auth;

    @Autowired
    UserSessions sessions;

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

    private static final UsernamePasswordAuthenticationToken SUPER = actor("SUPER_ADMIN", "USER_MANAGE", "ROLE_MANAGE");
    private static final UsernamePasswordAuthenticationToken ADMIN = actor("ADMIN", "USER_MANAGE");

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_group_member where user_group_id in (select id from user_group where code like 'ZT-%')");
        jdbc.update("delete from user_group_role where user_group_id in (select id from user_group where code like 'ZT-%')");
        jdbc.update("delete from user_group where code like 'ZT-%'");
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'zt-%')");
        jdbc.update("delete from user_role where role_id in (select id from app_role where code like 'ZT\\_%' escape '\\')");
        jdbc.update("delete from app_user where username like 'zt-%'");
        jdbc.update("delete from role_permission where role_id in (select id from app_role where code like 'ZT\\_%' escape '\\')");
        jdbc.update("delete from app_role where code like 'ZT\\_%' escape '\\'");
        reset(audit);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private String send(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who, String body,
                        int expected) throws Exception {
        MockHttpServletRequestBuilder r = request.with(authentication(who)).with(csrf());
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private void createRole(String code, String permissions) throws Exception {
        send(post("/api/admin/roles"), SUPER, "{\"code\":\"" + code + "\",\"name\":\"" + code + "\",\"permissions\":" + permissions + "}", 201);
    }

    /** Creates a user and returns {id, temporaryPassword}. */
    private Object[] createUser(String username, String roles) throws Exception {
        String created = send(post(USERS), SUPER, "{\"username\":\"" + username + "\",\"displayName\":\"" + username + "\",\"roles\":" + roles + "}", 201);
        return new Object[] {((Number) JsonPath.read(created, "$.user.id")).longValue(), JsonPath.read(created, "$.temporaryPassword")};
    }

    private static String group(String code, String roles, String members, boolean enabled, Long version) {
        return "{\"code\":\"" + code + "\",\"name\":\"Group " + code + "\",\"description\":\"d\",\"externalRef\":\"CN=" + code
                + "\",\"enabled\":" + enabled + ",\"roles\":" + roles + ",\"memberIds\":" + members
                + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private long createGroup(UsernamePasswordAuthenticationToken who, String code, String roles, String members, boolean enabled) throws Exception {
        return ((Number) JsonPath.read(send(post(GROUPS), who, group(code, roles, members, enabled, null), 201), "$.id")).longValue();
    }

    private List<String> permissionsAtLogin(String user, String password) {
        return auth.login(user, password, "127.0.0.1").principal().permissions();
    }

    // ---- inheritance -------------------------------------------------------------------------------------

    @Test
    void aUserInheritsTheRolesOfHisGroupsAndKeepsHisDirectRoles() throws Exception {
        createRole("ZT_AUDITOR", "[\"AUDIT_VIEW\"]");
        Object[] user = createUser("zt-ina", "[\"OPERATOR\"]");
        long id = (Long) user[0];
        String password = (String) user[1];

        List<String> before = permissionsAtLogin("zt-ina", password);
        assertThat(before).contains("DASHBOARD_VIEW", "MASTERDATA_VIEW").doesNotContain("AUDIT_VIEW");

        long group = createGroup(SUPER, "ZT-SHIFT", "[\"ZT_AUDITOR\"]", "[" + id + "]", true);
        WmsUserPrincipal principal = auth.login("zt-ina", password, "127.0.0.1").principal();
        assertThat(principal.roles()).containsExactlyInAnyOrder("OPERATOR", "ZT_AUDITOR"); // direct + group
        assertThat(principal.permissions()).containsExactlyInAnyOrder("DASHBOARD_VIEW", "MASTERDATA_VIEW", "AUDIT_VIEW"); // union

        // leave the group: the inherited right is gone at the next login, the direct ones stay
        String current = send(get(GROUPS + "/" + group), SUPER, null, 200);
        long version = ((Number) JsonPath.read(current, "$.version")).longValue();
        send(put(GROUPS + "/" + group), SUPER, group("ZT-SHIFT", "[\"ZT_AUDITOR\"]", "[]", true, version), 200);
        assertThat(permissionsAtLogin("zt-ina", password)).containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void aDisabledGroupGrantsNothing() throws Exception {
        createRole("ZT_AUDITOR", "[\"AUDIT_VIEW\"]");
        Object[] user = createUser("zt-dan", "[\"VIEWER\"]");
        long group = createGroup(SUPER, "ZT-OFF", "[\"ZT_AUDITOR\"]", "[" + user[0] + "]", false);
        assertThat(permissionsAtLogin("zt-dan", (String) user[1])).doesNotContain("AUDIT_VIEW");

        long version = ((Number) JsonPath.read(send(get(GROUPS + "/" + group), SUPER, null, 200), "$.version")).longValue();
        send(put(GROUPS + "/" + group), SUPER, group("ZT-OFF", "[\"ZT_AUDITOR\"]", "[" + user[0] + "]", true, version), 200);
        assertThat(permissionsAtLogin("zt-dan", (String) user[1])).contains("AUDIT_VIEW");
    }

    @Test
    void theUserDetailShowsGroupsAndWhyEachPermissionIsHeld() throws Exception {
        createRole("ZT_AUDITOR", "[\"AUDIT_VIEW\",\"MASTERDATA_VIEW\"]");
        Object[] user = createUser("zt-eli", "[\"OPERATOR\"]");
        createGroup(SUPER, "ZT-NIGHT", "[\"ZT_AUDITOR\"]", "[" + user[0] + "]", true);

        String detail = send(get(USERS + "/" + user[0]), ADMIN, null, 200);
        assertThat(JsonPath.<List<String>>read(detail, "$.roles")).containsExactly("OPERATOR"); // direct only
        assertThat(JsonPath.<List<String>>read(detail, "$.effectiveRoles")).containsExactlyInAnyOrder("OPERATOR", "ZT_AUDITOR");
        assertThat(JsonPath.<List<String>>read(detail, "$.groups[*].code")).containsExactly("ZT-NIGHT");
        // AUDIT_VIEW comes only through the group; MASTERDATA_VIEW both ways
        assertThat(JsonPath.<List<String>>read(detail, "$.effectivePermissions[?(@.code == 'AUDIT_VIEW')].via[*].group"))
                .containsExactly("ZT-NIGHT");
        assertThat(JsonPath.<List<String>>read(detail, "$.effectivePermissions[?(@.code == 'MASTERDATA_VIEW')].via[*].role"))
                .containsExactlyInAnyOrder("OPERATOR", "ZT_AUDITOR");
        assertThat(JsonPath.<List<String>>read(detail, "$.effectivePermissions[?(@.code == 'AUDIT_VIEW')].module")).containsExactly("administration");
        // lists stay light
        String list = send(get(USERS + "?q=zt-eli"), ADMIN, null, 200);
        assertThat(JsonPath.<List<Object>>read(list, "$.items[0].effectivePermissions")).isNull();
    }

    // ---- the group itself --------------------------------------------------------------------------------

    @Test
    void groupsCanBeCreatedChangedAndDeletedAndAreAudited() throws Exception {
        Object[] user = createUser("zt-fay", "[\"VIEWER\"]");
        long id = createGroup(SUPER, "ZT-ALPHA", "[\"OPERATOR\"]", "[" + user[0] + "]", true);

        String read = send(get(GROUPS + "/" + id), ADMIN, null, 200);
        assertThat(JsonPath.<List<String>>read(read, "$.members[*].username")).containsExactly("zt-fay");
        assertThat(JsonPath.<String>read(read, "$.code")).isEqualTo("ZT-ALPHA");
        assertThat(JsonPath.<String>read(read, "$.externalRef")).isEqualTo("CN=ZT-ALPHA"); // reserved for AD / LDAP later
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "GROUP_CREATED".equals(e.action())
                && e.afterValue().contains("zt-fay") && e.afterValue().contains("OPERATOR") && e.beforeValue() == null));

        long version = ((Number) JsonPath.read(read, "$.version")).longValue();
        send(put(GROUPS + "/" + id), ADMIN, group("ZT-ALPHA", "[\"VIEWER\"]", "[]", true, version), 200);
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "GROUP_UPDATED".equals(e.action())
                && e.beforeValue().contains("zt-fay") && !e.afterValue().contains("zt-fay") && e.afterValue().contains("VIEWER")));
        String stale = send(put(GROUPS + "/" + id), ADMIN, group("ZT-ALPHA", "[]", "[]", true, version), 409);
        assertThat(JsonPath.<String>read(stale, "$.code")).isEqualTo("STALE_VERSION");
        send(put(GROUPS + "/" + id), ADMIN, group("ZT-OTHER", "[]", "[]", true, version + 1), 400);

        send(delete(GROUPS + "/" + id), ADMIN, null, 204);
        assertThat(jdbc.queryForObject("select count(*) from user_group where id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_group_role where user_group_id = ?", Integer.class, id)).isZero();
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "GROUP_DELETED".equals(e.action())));
        // the member still exists
        assertThat(jdbc.queryForObject("select count(*) from app_user where username = 'zt-fay'", Integer.class)).isEqualTo(1);
    }

    @Test
    void badInputAndListFilters() throws Exception {
        Object[] user = createUser("zt-gus", "[\"VIEWER\"]");
        createGroup(SUPER, "ZT-ONE", "[]", "[]", true);
        createGroup(SUPER, "ZT-TWO", "[\"VIEWER\"]", "[" + user[0] + "]", false);

        String dup = send(post(GROUPS), ADMIN, group("zt-one", "[]", "[]", true, null), 400);
        assertThat(JsonPath.<List<String>>read(dup, "$.details")).containsExactly("code: this code is already used");
        send(post(GROUPS), ADMIN, group("ZT-X", "[\"NO_SUCH_ROLE\"]", "[]", true, null), 400);
        send(post(GROUPS), ADMIN, group("ZT-X", "[]", "[999999999]", true, null), 400);
        send(post(GROUPS), ADMIN, group("a", "[]", "[]", true, null), 400); // too short

        String all = send(get(GROUPS + "?q=zt-"), ADMIN, null, 200);
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].code")).containsExactly("ZT-ONE", "ZT-TWO");
        assertThat(JsonPath.<List<Integer>>read(all, "$.items[?(@.code == 'ZT-TWO')].memberCount")).containsExactly(1);
        assertThat(JsonPath.<List<String>>read(send(get(GROUPS + "?q=zt-&enabled=false"), ADMIN, null, 200), "$.items[*].code"))
                .containsExactly("ZT-TWO");
        send(get(GROUPS + "?sort=externalRef,asc"), ADMIN, null, 400);
    }

    // ---- who may do what ---------------------------------------------------------------------------------

    @Test
    void onlyASuperAdministratorHandlesGroupsThatCarrySuperAdmin() throws Exception {
        String refused = send(post(GROUPS), ADMIN, group("ZT-BOSS", "[\"SUPER_ADMIN\"]", "[]", true, null), 422);
        assertThat(JsonPath.<String>read(refused, "$.code")).isEqualTo("NOT_ALLOWED");

        long boss = createGroup(SUPER, "ZT-BOSS", "[\"SUPER_ADMIN\"]", "[]", true);
        long version = ((Number) JsonPath.read(send(get(GROUPS + "/" + boss), SUPER, null, 200), "$.version")).longValue();
        send(put(GROUPS + "/" + boss), ADMIN, group("ZT-BOSS", "[\"VIEWER\"]", "[]", true, version), 422);
        send(delete(GROUPS + "/" + boss), ADMIN, null, 422);
        send(put(GROUPS + "/" + boss), SUPER, group("ZT-BOSS", "[\"SUPER_ADMIN\"]", "[]", false, version), 200);
        send(delete(GROUPS + "/" + boss), SUPER, null, 204);
    }

    @Test
    void aUserWhoIsSuperAdministratorThroughAGroupIsProtectedLikeOne() throws Exception {
        Object[] user = createUser("zt-hal", "[\"VIEWER\"]");
        createGroup(SUPER, "ZT-BOSS", "[\"SUPER_ADMIN\"]", "[" + user[0] + "]", true);
        // an ordinary admin cannot reset his password or unlock him, although he has no direct SUPER_ADMIN role
        String refused = send(post(USERS + "/" + user[0] + "/reset-password"), ADMIN, null, 422);
        assertThat(JsonPath.<String>read(refused, "$.message")).isEqualTo("Only a super administrator can change a super administrator.");
        send(post(USERS + "/" + user[0] + "/unlock"), ADMIN, null, 422);
        send(post(USERS + "/" + user[0] + "/reset-password"), SUPER, null, 200);
    }

    @Test
    void withoutUserManageNothingWorks() throws Exception {
        var viewer = actor("VIEWER", "MASTERDATA_VIEW");
        send(get(GROUPS), viewer, null, 403);
        send(post(GROUPS), viewer, group("ZT-X", "[]", "[]", true, null), 403);
        mvc.perform(get(GROUPS)).andExpect(status().isUnauthorized());
    }

    // ---- effects -----------------------------------------------------------------------------------------

    @Test
    void changingAGroupSignsOutItsMembersButNotTheAdministrator() throws Exception {
        Object[] member = createUser("zt-ivy", "[\"VIEWER\"]");
        Object[] other = createUser("zt-jon", "[\"VIEWER\"]");
        long group = createGroup(SUPER, "ZT-TEAM", "[\"OPERATOR\"]", "[" + member[0] + "]", true);
        MockHttpSession memberSession = new MockHttpSession();
        MockHttpSession otherSession = new MockHttpSession();
        MockHttpSession adminSession = new MockHttpSession();
        sessions.register((Long) member[0], memberSession);
        sessions.register((Long) other[0], otherSession);
        sessions.register(ACTOR, adminSession);

        long version = ((Number) JsonPath.read(send(get(GROUPS + "/" + group), SUPER, null, 200), "$.version")).longValue();
        send(put(GROUPS + "/" + group), SUPER, group("ZT-TEAM", "[\"OPERATOR\",\"VIEWER\"]", "[" + member[0] + "]", true, version), 200);
        assertThat(memberSession.isInvalid()).as("member signed out").isTrue();
        assertThat(otherSession.isInvalid()).as("non-member untouched").isFalse();
        assertThat(adminSession.isInvalid()).as("administrator stays signed in").isFalse();
    }

    @Test
    void aRoleUsedByAGroupCannotBeDeleted() throws Exception {
        createRole("ZT_GRPROLE", "[\"DASHBOARD_VIEW\"]");
        createGroup(SUPER, "ZT-USES", "[\"ZT_GRPROLE\"]", "[]", true);
        long roleId = ((Number) JsonPath.<List<Integer>>read(send(get("/api/admin/roles"), SUPER, null, 200),
                "$[?(@.code == 'ZT_GRPROLE')].id").get(0)).longValue();
        String refused = send(delete("/api/admin/roles/" + roleId), SUPER, null, 409);
        assertThat(JsonPath.<String>read(refused, "$.message")).contains("still used by 1 user group");
    }
}
