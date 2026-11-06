package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
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
import com.dimos.wms.identity.WmsUserPrincipal;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The three assignment editors (equipment - locations, location type - HU types, HU type nesting), exactly as the
 * web screen uses them: a PUT that replaces the list. Removing one entry must delete only that assignment row,
 * never the location / HU type / equipment itself, and must be audited. The data is really committed (so the tests can
 * look into the database afterwards) and removed again; all codes start with ZT.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssignmentEditorTests {

    private static final String BASE = "/api/masterdata";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AuditService audit;

    private static final UsernamePasswordAuthenticationToken ADMIN = admin();

    private static UsernamePasswordAuthenticationToken admin() {
        var principal = new WmsUserPrincipal(1L, 1L, "assign-test", "Test", List.of("ADMIN"),
                List.of("MASTERDATA_VIEW", "MASTERDATA_EDIT"), false);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("MASTERDATA_VIEW"),
                new SimpleGrantedAuthority("MASTERDATA_EDIT")));
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        String zt = "like 'ZT%'";
        jdbc.update("delete from location_link where from_location_id in (select id from location where code " + zt + ")");
        jdbc.update("delete from equipment_location where equipment_id in (select id from equipment where code " + zt + ")");
        jdbc.update("delete from equipment where code " + zt);
        jdbc.update("delete from location where code " + zt);
        jdbc.update("delete from hu_type_nesting where inner_hu_type_id in (select id from hu_type where code " + zt + ")");
        jdbc.update("delete from location_type_hu_type where hu_type_id in (select id from hu_type where code " + zt + ")");
        jdbc.update("delete from hu_type where code " + zt);
        jdbc.update("delete from location_type where code " + zt);
        jdbc.update("delete from area where code " + zt);
        reset(audit);
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private String call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body,
                        int expected) throws Exception {
        var r = request.with(authentication(ADMIN)).with(csrf());
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    private long create(String path, String body) throws Exception {
        return ((Number) JsonPath.read(call(post(BASE + path), body, 201), "$.id")).longValue();
    }

    @SuppressWarnings("unchecked")
    private static List<String> codes(String json) {
        return (List<String>) JsonPath.read(json, "$.items[*].code");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where code like 'ZT%'", Integer.class);
    }

    private int rows(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    // ---- equipment <-> locations -----------------------------------------------------------------------

    @Test
    void removingOneLocationFromEquipmentDeletesOnlyTheAssignment() throws Exception {
        long area = create("/areas", "{\"code\":\"ZT-AREA\",\"name\":\"a\",\"areaType\":\"STORAGE\"}");
        long type = create("/location-types", "{\"code\":\"ZT-LT\",\"name\":\"t\"}");
        long al1 = create("/locations", "{\"code\":\"ZT-AL1\",\"areaId\":" + area + ",\"locationTypeId\":" + type + "}");
        long al2 = create("/locations", "{\"code\":\"ZT-AL2\",\"areaId\":" + area + ",\"locationTypeId\":" + type + "}");
        long al3 = create("/locations", "{\"code\":\"ZT-AL3\",\"areaId\":" + area + ",\"locationTypeId\":" + type + "}");
        long conv = create("/equipment", "{\"code\":\"ZT-CONV\",\"name\":\"c\",\"equipmentTypeId\":1}");
        // a link that uses AL3 must survive too
        create("/location-links", "{\"fromLocationId\":" + al2 + ",\"toLocationId\":" + al3 + "}");

        String first = call(put(BASE + "/equipment/" + conv + "/locations"),
                "{\"locationIds\":[" + al1 + "," + al2 + "," + al3 + "]}", 200);
        assertThat((List<?>) JsonPath.read(first, "$.ids")).hasSize(3);
        reset(audit);

        // the screen sends the version it got from the GET; remove AL3
        String current = call(get(BASE + "/equipment/" + conv + "/locations"), null, 200);
        long version = ((Number) JsonPath.read(current, "$.version")).longValue();
        String after = call(put(BASE + "/equipment/" + conv + "/locations"),
                "{\"locationIds\":[" + al1 + "," + al2 + "],\"version\":" + version + "}", 200);

        assertThat(codes(after)).containsExactly("ZT-AL1", "ZT-AL2");
        // nothing but the assignment row was removed
        assertThat(count("location")).isEqualTo(3);
        assertThat(count("equipment")).isEqualTo(1);
        assertThat(rows("select count(*) from location_link where from_location_id = " + al2)).isEqualTo(1);
        assertThat(rows("select count(*) from equipment_location where equipment_id = " + conv)).isEqualTo(2);
        assertThat(rows("select count(*) from equipment_location where equipment_id = " + conv + " and location_id = " + al3))
                .isZero();
        // audited as an assignment change with before and after, and no delete was recorded
        verify(audit).record(argThat((AuditEvent e) -> "ASSIGN_LOCATIONS".equals(e.action())
                && "equipment".equals(e.entityType()) && e.beforeValue().contains(String.valueOf(al3))
                && !e.afterValue().contains(String.valueOf(al3)) && e.afterValue().contains(String.valueOf(al1))));
        verify(audit, never()).record(argThat((AuditEvent e) -> "DELETE".equals(e.action())));

        // and the locations can still be assigned again
        call(put(BASE + "/equipment/" + conv + "/locations"), "{\"locationIds\":[" + al1 + "," + al3 + "]}", 200);
        assertThat(rows("select count(*) from equipment_location where equipment_id = " + conv)).isEqualTo(2);
    }

    // ---- location type <-> HU types ---------------------------------------------------------------------

    @Test
    void removingOneHuTypeFromALocationTypeDeletesOnlyTheAssignment() throws Exception {
        long type = create("/location-types", "{\"code\":\"ZT-LT\",\"name\":\"t\"}");
        long h1 = create("/hu-types", "{\"code\":\"ZT-H1\",\"name\":\"h1\"}");
        long h2 = create("/hu-types", "{\"code\":\"ZT-H2\",\"name\":\"h2\"}");
        long h3 = create("/hu-types", "{\"code\":\"ZT-H3\",\"name\":\"h3\"}");
        call(put(BASE + "/location-types/" + type + "/hu-types"), "{\"huTypeIds\":[" + h1 + "," + h2 + "," + h3 + "]}", 200);
        reset(audit);

        String current = call(get(BASE + "/location-types/" + type + "/hu-types"), null, 200);
        long version = ((Number) JsonPath.read(current, "$.version")).longValue();
        String after = call(put(BASE + "/location-types/" + type + "/hu-types"),
                "{\"huTypeIds\":[" + h1 + "," + h2 + "],\"version\":" + version + "}", 200);

        assertThat(codes(after)).containsExactly("ZT-H1", "ZT-H2");
        assertThat(count("hu_type")).isEqualTo(3);
        assertThat(count("location_type")).isEqualTo(1);
        assertThat(rows("select count(*) from location_type_hu_type where location_type_id = " + type)).isEqualTo(2);
        verify(audit).record(argThat((AuditEvent e) -> "ASSIGN_HU_TYPES".equals(e.action())
                && e.beforeValue().contains(String.valueOf(h3)) && !e.afterValue().contains(String.valueOf(h3))));
        verify(audit, never()).record(argThat((AuditEvent e) -> "DELETE".equals(e.action())));
    }

    // ---- HU type nesting --------------------------------------------------------------------------------

    @Test
    void removingOneNestingDeletesOnlyTheNestingRow() throws Exception {
        long carton = create("/hu-types", "{\"code\":\"ZT-CARTON\",\"name\":\"carton\"}");
        long pallet = create("/hu-types", "{\"code\":\"ZT-PALLET\",\"name\":\"pallet\"}");
        long cart = create("/hu-types", "{\"code\":\"ZT-CART\",\"name\":\"cart\"}");
        call(put(BASE + "/hu-types/" + carton + "/placed-in"),
                "{\"placedIn\":[{\"outerHuTypeId\":" + pallet + ",\"maxQty\":40},{\"outerHuTypeId\":" + cart + "}]}", 200);
        reset(audit);

        String row = call(get(BASE + "/hu-types/" + carton), null, 200);
        long version = ((Number) JsonPath.read(row, "$.version")).longValue();
        String after = call(put(BASE + "/hu-types/" + carton + "/placed-in"),
                "{\"placedIn\":[{\"outerHuTypeId\":" + pallet + ",\"maxQty\":40}],\"version\":" + version + "}", 200);

        assertThat((List<?>) JsonPath.read(after, "$[*].outerHuTypeId")).hasSize(1);
        assertThat(count("hu_type")).isEqualTo(3);
        assertThat(rows("select count(*) from hu_type_nesting where inner_hu_type_id = " + carton)).isEqualTo(1);
        verify(audit).record(argThat((AuditEvent e) -> "ASSIGN_NESTING".equals(e.action())));
        verify(audit, never()).record(argThat((AuditEvent e) -> "DELETE".equals(e.action())));
    }

    // ---- the version the screen sends -------------------------------------------------------------------

    @Test
    void aStaleVersionIsRefusedWithoutChangingAnything() throws Exception {
        long area = create("/areas", "{\"code\":\"ZT-AREA\",\"name\":\"a\",\"areaType\":\"STORAGE\"}");
        long type = create("/location-types", "{\"code\":\"ZT-LT\",\"name\":\"t\"}");
        long al1 = create("/locations", "{\"code\":\"ZT-AL1\",\"areaId\":" + area + ",\"locationTypeId\":" + type + "}");
        long conv = create("/equipment", "{\"code\":\"ZT-CONV\",\"name\":\"c\",\"equipmentTypeId\":1}");
        call(put(BASE + "/equipment/" + conv + "/locations"), "{\"locationIds\":[" + al1 + "]}", 200);

        String error = call(put(BASE + "/equipment/" + conv + "/locations"),
                "{\"locationIds\":[],\"version\":0}", 409);
        assertThat((String) JsonPath.read(error, "$.code")).isEqualTo("STALE_VERSION");
        assertThat(rows("select count(*) from equipment_location where equipment_id = " + conv)).isEqualTo(1);
        verify(audit, never()).record(argThat((AuditEvent e) -> "DELETE".equals(e.action())));
    }
}
