package com.dimos.wms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** The audit trail viewer. Test rows use the actor "zt-audit" and are removed again. */
@SpringBootTest
@AutoConfigureMockMvc
class AuditAdminTests {

    private static final String URL = "/api/admin/audit";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static UsernamePasswordAuthenticationToken viewer(String... permissions) {
        var principal = new WmsUserPrincipal(999_999_999L, 1L, "zt-viewer", "V", List.of("ADMIN"), List.of(permissions), false);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }

    private static final UsernamePasswordAuthenticationToken AUDITOR = viewer("AUDIT_VIEW");

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from audit_log where actor = 'zt-audit'");
    }

    private void event(String time, String action, String entityType, String entityId, String before, String after,
                       String detail, Long siteId) {
        jdbc.update("insert into audit_log (id, site_id, occurred_at, actor, action, entity_type, entity_id, before_value, "
                + "after_value, detail, remote_addr) values (nextval('audit_log_seq'), ?, ?::timestamptz, 'zt-audit', ?, ?, ?, ?, ?, ?, '10.0.0.7')",
                siteId, time, action, entityType, entityId, before, after, detail);
    }

    private String fetch(String query) throws Exception {
        return mvc.perform(get(URL + query).with(authentication(AUDITOR))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
    }

    @Test
    void listsNewestFirstAndFiltersByActionEntityUserTextAndDate() throws Exception {
        event("2026-01-01T08:00:00Z", "CREATE", "area", "7", null, "{\"code\":\"ZT-A\"}", null, 1L);
        event("2026-01-02T08:00:00Z", "UPDATE", "area", "7", "{\"name\":\"old\"}", "{\"name\":\"new\"}", null, 1L);
        event("2026-01-03T08:00:00Z", "DELETE", "location", "9", "{\"code\":\"L9\"}", null, null, 1L);
        event("2026-01-04T08:00:00Z", "LOGIN_FAILURE", null, null, null, null, "Unknown user name", null);

        String all = fetch("?actor=zt-audit");
        assertThat(JsonPath.<List<String>>read(all, "$.items[*].action"))
                .containsExactly("LOGIN_FAILURE", "DELETE", "UPDATE", "CREATE"); // newest first
        assertThat(JsonPath.<Integer>read(all, "$.total")).isEqualTo(4);

        assertThat(JsonPath.<List<String>>read(fetch("?actor=ZT-AU&action=UPDATE"), "$.items[*].entityId")).containsExactly("7");
        assertThat(JsonPath.<List<String>>read(fetch("?actor=zt-audit&entityType=area"), "$.items[*].action"))
                .containsExactly("UPDATE", "CREATE");
        assertThat(JsonPath.<List<String>>read(fetch("?actor=zt-audit&entityType=area&entityId=7&sort=occurredAt,asc"), "$.items[*].action"))
                .containsExactly("CREATE", "UPDATE"); // oldest first on request
        assertThat(JsonPath.<List<String>>read(fetch("?actor=zt-audit&q=new"), "$.items[*].action"))
                .containsExactly("UPDATE"); // text search looks into before/after values too
        assertThat(JsonPath.<List<String>>read(fetch("?actor=zt-audit&from=2026-01-02T00:00:00Z&to=2026-01-03T23:59:59Z"), "$.items[*].action"))
                .containsExactly("DELETE", "UPDATE");
        // events of no site (a login with an unknown name) are visible; paging works
        String page = fetch("?actor=zt-audit&size=2&page=1");
        assertThat(JsonPath.<List<String>>read(page, "$.items[*].action")).containsExactly("UPDATE", "CREATE");
        assertThat(JsonPath.<Integer>read(page, "$.total")).isEqualTo(4);
    }

    @Test
    void rowsCarryEverythingTheDetailViewShows() throws Exception {
        event("2026-01-02T08:00:00Z", "UPDATE", "area", "7", "{\"name\":\"old\"}", "{\"name\":\"new\"}", "why", 1L);
        String row = fetch("?actor=zt-audit");
        assertThat(JsonPath.<String>read(row, "$.items[0].beforeValue")).isEqualTo("{\"name\":\"old\"}");
        assertThat(JsonPath.<String>read(row, "$.items[0].afterValue")).isEqualTo("{\"name\":\"new\"}");
        assertThat(JsonPath.<String>read(row, "$.items[0].detail")).isEqualTo("why");
        assertThat(JsonPath.<String>read(row, "$.items[0].remoteAddr")).isEqualTo("10.0.0.7");
        assertThat(JsonPath.<String>read(row, "$.items[0].occurredAt")).startsWith("2026-01-02T08:00:00");
    }

    @Test
    void filterListsNameTheActionsAndEntityTypesInTheTrail() throws Exception {
        event("2026-01-01T08:00:00Z", "ZT_SPECIAL_ACTION", "zt_thing", "1", null, null, null, 1L);
        String filters = mvc.perform(get(URL + "/filters").with(authentication(AUDITOR))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(filters, "$.actions")).contains("ZT_SPECIAL_ACTION");
        assertThat(JsonPath.<List<String>>read(filters, "$.entityTypes")).contains("zt_thing");
    }

    @Test
    void badInputIsReportedAndOnlyAuditorsMayLook() throws Exception {
        mvc.perform(get(URL + "?from=yesterday").with(authentication(AUDITOR))).andExpect(status().isBadRequest());
        mvc.perform(get(URL + "?sort=beforeValue,asc").with(authentication(AUDITOR))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).with(authentication(viewer("USER_MANAGE")))).andExpect(status().isForbidden());
        mvc.perform(get(URL + "/filters").with(authentication(viewer("MASTERDATA_VIEW")))).andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
