package com.dimos.wms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

/**
 * User administration through the REST API. Users created here have the user name prefix "zt-" and are removed again.
 * The acting administrator is a made-up principal (not a database row) so real accounts are never touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAdminTests {

    private static final String BASE = "/api/admin/users";
    private static final long OTHER_SITE = 9002L;
    private static final long NOBODY = 999_999_999L;

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

    private static UsernamePasswordAuthenticationToken actor(long userId, long siteId, String role, String... permissions) {
        var principal = new WmsUserPrincipal(userId, siteId, "zt-actor", "Actor", List.of(role),
                List.of(permissions), false);
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private static UsernamePasswordAuthenticationToken superAdmin() {
        return actor(NOBODY, 1L, "SUPER_ADMIN", "USER_MANAGE", "AUDIT_VIEW");
    }

    private static UsernamePasswordAuthenticationToken admin() {
        return actor(NOBODY, 1L, "ADMIN", "USER_MANAGE", "AUDIT_VIEW");
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_role where user_id in (select id from app_user where username like 'zt-%')");
        jdbc.update("delete from app_user where username like 'zt-%'");
        jdbc.update("delete from site where id = " + OTHER_SITE);
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

    private static String user(String username, String roles) {
        return "{\"username\":\"" + username + "\",\"displayName\":\"Test " + username + "\",\"email\":\"" + username
                + "@example.com\",\"roles\":" + roles + "}";
    }

    private long id(String json) {
        return ((Number) JsonPath.read(json, "$.user.id")).longValue();
    }

    private static String userBody(String username, String displayName, String roles, boolean enabled, long version) {
        return "{\"username\":\"" + username + "\",\"displayName\":\"" + displayName + "\",\"email\":\"\",\"roles\":"
                + roles + ",\"enabled\":" + enabled + ",\"version\":" + version + "}";
    }

    private long versionOf(long id) {
        return jdbc.queryForObject("select version from app_user where id = ?", Long.class, id);
    }

    // ---- creating --------------------------------------------------------------------------------------

    @Test
    void createGivesATemporaryPasswordThatMustBeChanged() throws Exception {
        String created = send(post(BASE), admin(), user("zt-anna", "[\"OPERATOR\"]"), 201);
        String temporary = JsonPath.read(created, "$.temporaryPassword");
        assertThat(temporary).hasSize(12);
        assertThat((Boolean) JsonPath.read(created, "$.user.mustChangePassword")).isTrue();
        assertThat((Boolean) JsonPath.read(created, "$.user.hasPassword")).isTrue();
        assertThat(JsonPath.<List<String>>read(created, "$.user.roles")).containsExactly("OPERATOR");
        assertThat(JsonPath.<String>read(created, "$.user.username")).isEqualTo("zt-anna");

        // only a hash is stored, and the user can log in with the temporary password (and must change it)
        String stored = jdbc.queryForObject("select password_hash from app_user where username = 'zt-anna'", String.class);
        assertThat(stored).doesNotContain(temporary).startsWith("{");
        AuthService.LoginResult login = auth.login("zt-anna", temporary, "127.0.0.1");
        assertThat(login.status()).isEqualTo(AuthService.Status.OK);
        assertThat(login.principal().mustChangePassword()).isTrue();

        // the audit trail names the new user and the roles but never a password
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "USER_CREATED".equals(e.action())
                && e.afterValue().contains("zt-anna") && e.afterValue().contains("OPERATOR")
                && !e.afterValue().contains(temporary) && !e.afterValue().toLowerCase().contains("hash")));
    }

    @Test
    void duplicateAndBadInputAreRefusedWithFieldMessages() throws Exception {
        send(post(BASE), admin(), user("zt-bob", "[\"VIEWER\"]"), 201);
        String dup = send(post(BASE), admin(), user("ZT-BOB", "[\"VIEWER\"]"), 400);
        assertThat(JsonPath.<List<String>>read(dup, "$.details")).containsExactly("username: this user name is already used");
        send(post(BASE), admin(), user("zt-x y", "[\"VIEWER\"]"), 400);
        send(post(BASE), admin(), user("zt-nobody", "[\"NO_SUCH_ROLE\"]"), 400);
        send(post(BASE), admin(), user("zt-noroles", "[]"), 400);
        send(post(BASE), admin(), "{\"username\":\"zt-mail\",\"displayName\":\"x\",\"email\":\"not-an-email\",\"roles\":[\"VIEWER\"]}", 400);
    }

    // ---- editing ---------------------------------------------------------------------------------------

    @Test
    void updateChangesNameEmailRolesAndRefusesStaleVersions() throws Exception {
        long id = id(send(post(BASE), admin(), user("zt-carl", "[\"VIEWER\"]"), 201));
        long version = versionOf(id);

        String updated = send(put(BASE + "/" + id), admin(),
                userBody("zt-carl", "Carl Changed", "[\"OPERATOR\",\"VIEWER\"]", true, version), 200);
        assertThat(JsonPath.<String>read(updated, "$.displayName")).isEqualTo("Carl Changed");
        assertThat(JsonPath.<List<String>>read(updated, "$.roles")).containsExactlyInAnyOrder("OPERATOR", "VIEWER");

        String stale = send(put(BASE + "/" + id), admin(), userBody("zt-carl", "Late", "[\"VIEWER\"]", true, version), 409);
        assertThat(JsonPath.<String>read(stale, "$.message")).isEqualTo("This record was changed by someone else - reload");
        send(put(BASE + "/" + id), admin(), userBody("zt-other", "x", "[\"VIEWER\"]", true, versionOf(id)), 400); // user name is fixed
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "USER_UPDATED".equals(e.action())
                && e.beforeValue().contains("Test zt-carl") && e.afterValue().contains("Carl Changed")));
    }

    @Test
    void listFiltersBySearchEnabledAndRole() throws Exception {
        long a = id(send(post(BASE), admin(), user("zt-list-a", "[\"OPERATOR\"]"), 201));
        send(post(BASE), admin(), user("zt-list-b", "[\"VIEWER\"]"), 201);
        send(put(BASE + "/" + a), admin(), userBody("zt-list-a", "Alpha", "[\"OPERATOR\"]", false, versionOf(a)), 200);

        String found = send(get(BASE + "?q=zt-list"), admin(), null, 200);
        assertThat(JsonPath.<Integer>read(found, "$.total")).isEqualTo(2);
        String disabled = send(get(BASE + "?q=zt-list&enabled=false"), admin(), null, 200);
        assertThat(JsonPath.<List<String>>read(disabled, "$.items[*].username")).containsExactly("zt-list-a");
        String viewers = send(get(BASE + "?q=zt-list&role=VIEWER"), admin(), null, 200);
        assertThat(JsonPath.<List<String>>read(viewers, "$.items[*].username")).containsExactly("zt-list-b");
        send(get(BASE + "?sort=passwordHash,asc"), admin(), null, 400);
        String roles = send(get("/api/admin/roles"), admin(), null, 200);
        assertThat(JsonPath.<List<String>>read(roles, "$[*].code")).contains("SUPER_ADMIN", "ADMIN", "OPERATOR", "VIEWER");
    }

    // ---- who may do what -------------------------------------------------------------------------------

    @Test
    void anAdminCannotCreateOrTouchASuperAdministrator() throws Exception {
        String refused = send(post(BASE), admin(), user("zt-boss", "[\"SUPER_ADMIN\"]"), 422);
        assertThat(JsonPath.<String>read(refused, "$.code")).isEqualTo("NOT_ALLOWED");

        long boss = id(send(post(BASE), superAdmin(), user("zt-boss", "[\"SUPER_ADMIN\"]"), 201));
        send(put(BASE + "/" + boss), admin(), userBody("zt-boss", "Hacked", "[\"VIEWER\"]", true, versionOf(boss)), 422);
        send(post(BASE + "/" + boss + "/reset-password"), admin(), null, 422);
        send(post(BASE + "/" + boss + "/unlock"), admin(), null, 422);
        // and an admin cannot promote an ordinary user
        long worker = id(send(post(BASE), admin(), user("zt-worker", "[\"OPERATOR\"]"), 201));
        send(put(BASE + "/" + worker), admin(), userBody("zt-worker", "w", "[\"SUPER_ADMIN\"]", true, versionOf(worker)), 422);
        // a super administrator may
        send(put(BASE + "/" + worker), superAdmin(), userBody("zt-worker", "w", "[\"ADMIN\"]", true, versionOf(worker)), 200);
    }

    @Test
    void nobodyCanDisableThemselvesOrChangeTheirOwnRoles() throws Exception {
        long me = id(send(post(BASE), superAdmin(), user("zt-me", "[\"ADMIN\"]"), 201));
        var asMe = actor(me, 1L, "ADMIN", "USER_MANAGE");
        send(put(BASE + "/" + me), asMe, userBody("zt-me", "Me", "[\"ADMIN\"]", false, versionOf(me)), 422);
        send(put(BASE + "/" + me), asMe, userBody("zt-me", "Me", "[\"ADMIN\",\"OPERATOR\"]", true, versionOf(me)), 422);
        send(post(BASE + "/" + me + "/reset-password"), asMe, null, 422); // own password: use "Change password"
        // changing only the display name is fine
        send(put(BASE + "/" + me), asMe, userBody("zt-me", "My new name", "[\"ADMIN\"]", true, versionOf(me)), 200);
    }

    @Test
    void theLastEnabledSuperAdministratorCannotBeRemoved() throws Exception {
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + OTHER_SITE + ", 'ZTSITE2', 'Second test site', 0, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test')");
        var boss = actor(NOBODY, OTHER_SITE, "SUPER_ADMIN", "USER_MANAGE");
        long only = id(send(post(BASE), boss, user("zt-only", "[\"SUPER_ADMIN\"]"), 201));

        String demote = send(put(BASE + "/" + only), boss, userBody("zt-only", "x", "[\"ADMIN\"]", true, versionOf(only)), 422);
        assertThat(JsonPath.<String>read(demote, "$.message")).isEqualTo("At least one enabled super administrator must remain.");
        send(put(BASE + "/" + only), boss, userBody("zt-only", "x", "[\"SUPER_ADMIN\"]", false, versionOf(only)), 422);

        // with a second one, the first can go
        send(post(BASE), boss, user("zt-second", "[\"SUPER_ADMIN\"]"), 201);
        send(put(BASE + "/" + only), boss, userBody("zt-only", "x", "[\"ADMIN\"]", true, versionOf(only)), 200);
    }

    @Test
    void usersOfAnotherSiteAreInvisible() throws Exception {
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + OTHER_SITE + ", 'ZTSITE2', 'Second test site', 0, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test')");
        var other = actor(NOBODY, OTHER_SITE, "SUPER_ADMIN", "USER_MANAGE");
        long id = id(send(post(BASE), other, user("zt-elsewhere", "[\"VIEWER\"]"), 201));
        send(get(BASE + "/" + id), admin(), null, 404);
        String list = send(get(BASE + "?q=zt-elsewhere"), admin(), null, 200);
        assertThat(JsonPath.<Integer>read(list, "$.total")).isZero();
    }

    @Test
    void withoutTheUserManagePermissionNothingWorks() throws Exception {
        var viewer = actor(NOBODY, 1L, "VIEWER", "MASTERDATA_VIEW");
        send(get(BASE), viewer, null, 403);
        send(post(BASE), viewer, user("zt-x", "[\"VIEWER\"]"), 403);
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // ---- password reset, disable, unlock ----------------------------------------------------------------

    @Test
    void resetPasswordReplacesTheOldOneForcesAChangeAndSignsTheUserOut() throws Exception {
        String created = send(post(BASE), admin(), user("zt-dora", "[\"OPERATOR\"]"), 201);
        long id = id(created);
        String first = JsonPath.read(created, "$.temporaryPassword");
        MockHttpSession openSession = new MockHttpSession();
        sessions.register(id, openSession);

        String reset = send(post(BASE + "/" + id + "/reset-password"), admin(), null, 200);
        String second = JsonPath.read(reset, "$.temporaryPassword");
        assertThat(second).hasSize(12).isNotEqualTo(first);

        assertThat(openSession.isInvalid()).as("open session ended").isTrue();
        assertThat(auth.login("zt-dora", first, "127.0.0.1").status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        AuthService.LoginResult ok = auth.login("zt-dora", second, "127.0.0.1");
        assertThat(ok.status()).isEqualTo(AuthService.Status.OK);
        assertThat(ok.principal().mustChangePassword()).isTrue();
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "USER_PASSWORD_RESET".equals(e.action())
                && !String.valueOf(e.detail()).contains(second) && e.beforeValue() == null && e.afterValue() == null));
    }

    @Test
    void disablingStopsLoginSignsOutAndCanBeUndone() throws Exception {
        String created = send(post(BASE), admin(), user("zt-eve", "[\"OPERATOR\"]"), 201);
        long id = id(created);
        String password = JsonPath.read(created, "$.temporaryPassword");
        MockHttpSession openSession = new MockHttpSession();
        sessions.register(id, openSession);

        send(put(BASE + "/" + id), admin(), userBody("zt-eve", "Eve", "[\"OPERATOR\"]", false, versionOf(id)), 200);
        assertThat(openSession.isInvalid()).isTrue();
        assertThat(auth.login("zt-eve", password, "127.0.0.1").status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "USER_DISABLED".equals(e.action())));

        // a refused login counted as a failed attempt; unlock clears that, enabling lets the user in again
        send(put(BASE + "/" + id), admin(), userBody("zt-eve", "Eve", "[\"OPERATOR\"]", true, versionOf(id)), 200);
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "USER_ENABLED".equals(e.action())));
        send(post(BASE + "/" + id + "/unlock"), admin(), null, 200);
        assertThat(auth.login("zt-eve", password, "127.0.0.1").status()).isEqualTo(AuthService.Status.OK);
    }

    @Test
    void unlockClearsTheLockAfterTooManyWrongPasswords() throws Exception {
        String created = send(post(BASE), admin(), user("zt-finn", "[\"VIEWER\"]"), 201);
        long id = id(created);
        String password = JsonPath.read(created, "$.temporaryPassword");
        for (int i = 0; i < 5; i++) {
            auth.login("zt-finn", "wrong-password-" + i, "127.0.0.1");
        }
        assertThat(auth.login("zt-finn", password, "127.0.0.1").status()).isEqualTo(AuthService.Status.LOCKED);
        String locked = send(get(BASE + "/" + id), admin(), null, 200);
        assertThat(JsonPath.<Boolean>read(locked, "$.locked")).isTrue();

        String unlocked = send(post(BASE + "/" + id + "/unlock"), admin(), null, 200);
        assertThat(JsonPath.<Boolean>read(unlocked, "$.locked")).isFalse();
        assertThat(JsonPath.<Integer>read(unlocked, "$.failedAttempts")).isZero();
        assertThat(auth.login("zt-finn", password, "127.0.0.1").status()).isEqualTo(AuthService.Status.OK);
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "USER_UNLOCKED".equals(e.action())));
    }

    @Test
    void theTemporaryPasswordIsNeverCached() throws Exception {
        mvc.perform(post(BASE).with(authentication(admin())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(user("zt-gina", "[\"VIEWER\"]")))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }
}
