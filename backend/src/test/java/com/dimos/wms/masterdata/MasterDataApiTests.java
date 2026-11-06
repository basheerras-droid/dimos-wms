package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Master-data REST API against the real database. Every test is rolled back; the audit service is mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MasterDataApiTests {

    private static final String BASE = "/api/masterdata";

    @Autowired
    MockMvc mvc;

    @Autowired
    jakarta.persistence.EntityManager em;

    @MockitoBean
    AuditService audit;

    // ---- users -----------------------------------------------------------------------------------------

    private static UsernamePasswordAuthenticationToken user(String role, String... permissions) {
        var principal = new WmsUserPrincipal(1L, 1L, "test-" + role.toLowerCase(), "Test", List.of(role),
                List.of(permissions), false);
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private static final UsernamePasswordAuthenticationToken SUPER =
            user("SUPER_ADMIN", "MASTERDATA_VIEW", "MASTERDATA_EDIT");
    private static final UsernamePasswordAuthenticationToken ADMIN =
            user("ADMIN", "MASTERDATA_VIEW", "MASTERDATA_EDIT");
    private static final UsernamePasswordAuthenticationToken VIEWER = user("VIEWER", "MASTERDATA_VIEW");
    private static final UsernamePasswordAuthenticationToken NOBODY = user("OPERATOR");

    // ---- helpers ---------------------------------------------------------------------------------------

    private String send(MockHttpServletRequestBuilder request, UsernamePasswordAuthenticationToken who, String body,
                        int expected) throws Exception {
        MockHttpServletRequestBuilder r = request.with(authentication(who)).with(csrf());
        if (body != null) {
            r = r.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        String response = mvc.perform(r).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        // In production every request has its own session; the test shares one transaction, so mimic that.
        if (expected < 400) {
            em.flush();
        }
        em.clear();
        return response;
    }

    private long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private long version(String json) {
        return ((Number) JsonPath.read(json, "$.version")).longValue();
    }

    private String unique(String prefix) {
        return prefix + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private String createArea(String code) throws Exception {
        return send(post(BASE + "/areas"), ADMIN,
                "{\"code\":\"" + code + "\",\"name\":\"Test area\",\"areaType\":\"STORAGE\"}", 201);
    }

    private String createLocationType(String code) throws Exception {
        return send(post(BASE + "/location-types"), ADMIN,
                "{\"code\":\"" + code + "\",\"name\":\"Bin\",\"storage\":true,\"capacityHu\":1}", 201);
    }

    private String createLocation(String code, long areaId, long typeId) throws Exception {
        return send(post(BASE + "/locations"), ADMIN, "{\"code\":\"" + code + "\",\"areaId\":" + areaId
                + ",\"locationTypeId\":" + typeId + ",\"levelNo\":1,\"xMm\":1000,\"yMm\":2000}", 201);
    }

    private String createHuType(String code) throws Exception {
        return send(post(BASE + "/hu-types"), ADMIN, "{\"code\":\"" + code + "\",\"name\":\"Test HU\"}", 201);
    }

    // ---- tests -----------------------------------------------------------------------------------------

    @Test
    void areaCrudSearchAndAudit() throws Exception {
        String code = unique("AR");
        String created = createArea(code);
        long areaId = id(created);

        mvc.perform(get(BASE + "/areas").param("q", code.toLowerCase()).with(authentication(VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value(code));

        String updated = send(put(BASE + "/areas/" + areaId), ADMIN, "{\"code\":\"" + code
                + "\",\"name\":\"Renamed\",\"areaType\":\"BUFFER\",\"version\":" + version(created) + "}", 200);
        assertThat((String) JsonPath.read(updated, "$.name")).isEqualTo("Renamed");
        assertThat(version(updated)).isGreaterThan(version(created));

        send(delete(BASE + "/areas/" + areaId), ADMIN, null, 204);
        send(get(BASE + "/areas/" + areaId), ADMIN, null, 404);

        // create, update and delete were all audited, with before/after values where they exist
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "CREATE".equals(e.action())
                && "area".equals(e.entityType()) && e.beforeValue() == null && e.afterValue().contains(code)));
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "UPDATE".equals(e.action())
                && e.beforeValue().contains("Test area") && e.afterValue().contains("Renamed")));
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "DELETE".equals(e.action())
                && e.beforeValue() != null && e.afterValue() == null));
    }

    @Test
    void staleUpdateReturns409WithClearMessage() throws Exception {
        String code = unique("ST");
        String created = createArea(code);
        long areaId = id(created);
        String body = "{\"code\":\"" + code + "\",\"name\":\"%s\",\"areaType\":\"STORAGE\",\"version\":"
                + version(created) + "}";
        send(put(BASE + "/areas/" + areaId), ADMIN, body.formatted("First editor"), 200);
        // the second editor still holds the old version
        String error = send(put(BASE + "/areas/" + areaId), ADMIN, body.formatted("Second editor"), 409);
        assertThat((String) JsonPath.read(error, "$.message"))
                .isEqualTo("This record was changed by someone else - reload");
        assertThat((String) JsonPath.read(error, "$.code")).isEqualTo("STALE_VERSION");
    }

    @Test
    void duplicateCodeAndBadInputAreRefusedWithFieldMessages() throws Exception {
        String code = unique("DU");
        createArea(code);
        String dup = send(post(BASE + "/areas"), ADMIN,
                "{\"code\":\"" + code.toLowerCase() + "\",\"name\":\"x\",\"areaType\":\"STORAGE\"}", 400);
        assertThat((List<String>) JsonPath.read(dup, "$.details")).containsExactly("code: this code is already used");
        send(post(BASE + "/areas"), ADMIN, "{\"code\":\"OK1\",\"name\":\"x\",\"areaType\":\"MOON\"}", 400);
        send(put(BASE + "/areas/999999"), ADMIN,
                "{\"code\":\"OK1\",\"name\":\"x\",\"areaType\":\"STORAGE\",\"version\":0}", 404);
    }

    @Test
    void locationReferencesAreChecked() throws Exception {
        String bad = send(post(BASE + "/locations"), ADMIN,
                "{\"code\":\"" + unique("L") + "\",\"areaId\":999999,\"locationTypeId\":999999}", 400);
        assertThat((List<String>) JsonPath.read(bad, "$.details")).containsExactly("areaId: unknown area");

        long area = id(createArea(unique("AR")));
        long type = id(createLocationType(unique("LT")));
        String noReason = send(post(BASE + "/locations"), ADMIN, "{\"code\":\"" + unique("L") + "\",\"areaId\":" + area
                + ",\"locationTypeId\":" + type + ",\"blockedIn\":true}", 400);
        assertThat((List<String>) JsonPath.read(noReason, "$.details"))
                .containsExactly("blockReason: a reason is required when the location is blocked");
        send(post(BASE + "/locations"), ADMIN, "{\"code\":\"" + unique("L") + "\",\"areaId\":" + area
                + ",\"locationTypeId\":" + type + ",\"extAttributes\":\"{not json\"}", 400);
    }

    @Test
    void usedRecordCannotBeDeletedAndBulkActionsWork() throws Exception {
        long area = id(createArea(unique("AR")));
        long type = id(createLocationType(unique("LT")));
        long loc1 = id(createLocation(unique("L1"), area, type));
        long loc2 = id(createLocation(unique("L2"), area, type));

        String inUse = send(delete(BASE + "/areas/" + area), ADMIN, null, 409);
        assertThat((String) JsonPath.read(inUse, "$.code")).isEqualTo("IN_USE");
    }

    @Test
    void bulkBlockUnblockDisable() throws Exception {
        long area = id(createArea(unique("AR")));
        long type = id(createLocationType(unique("LT")));
        long loc1 = id(createLocation(unique("L1"), area, type));
        long loc2 = id(createLocation(unique("L2"), area, type));
        String ids = "[" + loc1 + "," + loc2 + "]";

        send(post(BASE + "/locations/bulk"), ADMIN, "{\"ids\":" + ids + ",\"action\":\"BLOCK_IN\"}", 400); // no reason
        String result = send(post(BASE + "/locations/bulk"), ADMIN,
                "{\"ids\":" + ids + ",\"action\":\"BLOCK_IN\",\"reason\":\"Rack inspection\"}", 200);
        assertThat((Integer) JsonPath.read(result, "$.updated")).isEqualTo(2);

        String one = send(get(BASE + "/locations/" + loc1), VIEWER, null, 200);
        assertThat((Boolean) JsonPath.read(one, "$.blockedIn")).isTrue();
        assertThat((String) JsonPath.read(one, "$.blockReason")).isEqualTo("Rack inspection");

        send(post(BASE + "/locations/bulk"), ADMIN, "{\"ids\":" + ids + ",\"action\":\"UNBLOCK_IN\"}", 200);
        send(post(BASE + "/locations/bulk"), ADMIN, "{\"ids\":" + ids + ",\"action\":\"DISABLE\"}", 200);
        one = send(get(BASE + "/locations/" + loc1), VIEWER, null, 200);
        assertThat((Boolean) JsonPath.read(one, "$.blockedIn")).isFalse();
        assertThat((Object) JsonPath.read(one, "$.blockReason")).isNull();
        assertThat((Boolean) JsonPath.read(one, "$.enabled")).isFalse();

        mvc.perform(get(BASE + "/locations").param("enabled", "false").param("areaId", String.valueOf(area))
                        .with(authentication(VIEWER)))
                .andExpect(jsonPath("$.total").value(2));
        verify(audit, atLeastOnce()).record(argThat((AuditEvent e) -> "BULK_BLOCK_IN".equals(e.action())));
    }

    @Test
    void duplicateAndSelfLinksAreBlocked() throws Exception {
        long area = id(createArea(unique("AR")));
        long type = id(createLocationType(unique("LT")));
        long a = id(createLocation(unique("LA"), area, type));
        long b = id(createLocation(unique("LB"), area, type));

        String link = "{\"fromLocationId\":" + a + ",\"toLocationId\":" + b + ",\"cost\":3}";
        send(post(BASE + "/location-links"), ADMIN, link, 201);
        String dup = send(post(BASE + "/location-links"), ADMIN, link, 400);
        assertThat((String) JsonPath.read(dup, "$.details[0]")).startsWith("toLocationId: this link already exists");
        send(post(BASE + "/location-links"), ADMIN,
                "{\"fromLocationId\":" + a + ",\"toLocationId\":" + a + "}", 400);
        // the reverse direction and a type-specific link are different links
        send(post(BASE + "/location-links"), ADMIN, "{\"fromLocationId\":" + b + ",\"toLocationId\":" + a + "}", 201);
        long hu = id(createHuType(unique("HU")));
        send(post(BASE + "/location-links"), ADMIN,
                "{\"fromLocationId\":" + a + ",\"toLocationId\":" + b + ",\"huTypeId\":" + hu + "}", 201);

        mvc.perform(get(BASE + "/location-links").param("locationId", String.valueOf(a)).with(authentication(VIEWER)))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void equipmentTypesAreProtected() throws Exception {
        // 13 built-in types are listed
        String list = mvc.perform(get(BASE + "/equipment-types").with(authentication(VIEWER))).andReturn()
                .getResponse().getContentAsString();
        assertThat((List<?>) JsonPath.read(list, "$[?(@.seeded == true)]")).hasSize(13);
        long conveyor = ((Number) ((List<?>) JsonPath.read(list, "$[?(@.code == 'CONVEYOR')].id")).get(0)).longValue();

        // only SUPER_ADMIN can add, change or delete
        String body = "{\"code\":\"" + unique("TT") + "\",\"name\":\"Tugger train\"}";
        send(post(BASE + "/equipment-types"), ADMIN, body, 403);
        String created = send(post(BASE + "/equipment-types"), SUPER, body, 201);
        assertThat((Boolean) JsonPath.read(created, "$.seeded")).isFalse();

        // built-in: name may change, code may not, delete is refused
        send(put(BASE + "/equipment-types/" + conveyor), SUPER, "{\"code\":\"CONVEYOR\",\"name\":\"Belt conveyor\"}", 200);
        String renamed = send(put(BASE + "/equipment-types/" + conveyor), SUPER,
                "{\"code\":\"BELT\",\"name\":\"Belt conveyor\"}", 422);
        assertThat((String) JsonPath.read(renamed, "$.code")).isEqualTo("NOT_ALLOWED");
        send(delete(BASE + "/equipment-types/" + conveyor), SUPER, null, 422);

        // a user-added type can be renamed and deleted
        send(delete(BASE + "/equipment-types/" + id(created)), SUPER, null, 204);
    }

    @Test
    void equipmentWithLocationsAndParent() throws Exception {
        long area = id(createArea(unique("AR")));
        long type = id(createLocationType(unique("LT")));
        long loc = id(createLocation(unique("L"), area, type));
        String types = mvc.perform(get(BASE + "/equipment-types").with(authentication(VIEWER))).andReturn()
                .getResponse().getContentAsString();
        long hoistType = ((Number) ((List<?>) JsonPath.read(types, "$[?(@.code == 'HOIST')].id")).get(0)).longValue();

        String parent = send(post(BASE + "/equipment"), ADMIN, "{\"code\":\"" + unique("EQ")
                + "\",\"name\":\"Line\",\"equipmentTypeId\":" + hoistType + ",\"areaId\":" + area + "}", 201);
        long parentId = id(parent);
        String child = send(post(BASE + "/equipment"), ADMIN, "{\"code\":\"" + unique("EC")
                + "\",\"name\":\"Child\",\"equipmentTypeId\":" + hoistType + ",\"parentId\":" + parentId + "}", 201);

        // the parent cannot be placed inside its own child
        String loop = send(put(BASE + "/equipment/" + parentId), ADMIN, "{\"code\":\"" + JsonPath.read(parent, "$.code")
                + "\",\"name\":\"Line\",\"equipmentTypeId\":" + hoistType + ",\"parentId\":" + id(child)
                + ",\"version\":" + version(parent) + "}", 400);
        assertThat((String) JsonPath.read(loop, "$.details[0]")).startsWith("parentId:");

        String assigned = send(put(BASE + "/equipment/" + id(child) + "/locations"), ADMIN,
                "{\"locationIds\":[" + loc + "]}", 200);
        assertThat((List<?>) JsonPath.read(assigned, "$.ids")).hasSize(1);
        send(put(BASE + "/equipment/" + id(child) + "/locations"), ADMIN, "{\"locationIds\":[999999]}", 400);

        // deleting equipment removes its location assignments
        send(delete(BASE + "/equipment/" + id(child)), ADMIN, null, 204);
    }

    @Test
    void huTypeNestingAndAllowedTypes() throws Exception {
        long carton = id(createHuType(unique("CT")));
        long pallet = id(createHuType(unique("PL")));
        long uld = id(createHuType(unique("UL")));

        send(put(BASE + "/hu-types/" + carton + "/placed-in"), ADMIN,
                "{\"placedIn\":[{\"outerHuTypeId\":" + pallet + ",\"maxQty\":40}]}", 200);
        send(put(BASE + "/hu-types/" + pallet + "/placed-in"), ADMIN,
                "{\"placedIn\":[{\"outerHuTypeId\":" + uld + "}]}", 200);
        // ULD inside carton would close the circle carton -> pallet -> ULD -> carton
        send(put(BASE + "/hu-types/" + uld + "/placed-in"), ADMIN,
                "{\"placedIn\":[{\"outerHuTypeId\":" + carton + "}]}", 400);
        send(put(BASE + "/hu-types/" + uld + "/placed-in"), ADMIN,
                "{\"placedIn\":[{\"outerHuTypeId\":" + uld + "}]}", 400);

        long type = id(createLocationType(unique("LT")));
        String allowed = send(put(BASE + "/location-types/" + type + "/hu-types"), ADMIN,
                "{\"huTypeIds\":[" + pallet + "," + uld + "]}", 200);
        assertThat((List<?>) JsonPath.read(allowed, "$.ids")).hasSize(2);
        mvc.perform(get(BASE + "/location-types/" + type + "/hu-types").with(authentication(VIEWER)))
                .andExpect(jsonPath("$.ids.length()").value(2));
    }

    @Test
    void permissionsAreEnforced() throws Exception {
        String code = unique("PM");
        String body = "{\"code\":\"" + code + "\",\"name\":\"x\",\"areaType\":\"STORAGE\"}";
        send(post(BASE + "/areas"), VIEWER, body, 403);                    // view only
        send(get(BASE + "/areas"), NOBODY, null, 403);                     // no master-data permission at all
        send(get(BASE + "/locations"), NOBODY, null, 403);
        send(get(BASE + "/areas"), VIEWER, null, 200);
        send(post(BASE + "/areas"), ADMIN, body, 201);
        send(post(BASE + "/locations/bulk"), VIEWER, "{\"ids\":[1],\"action\":\"ENABLE\"}", 403);
        mvc.perform(get(BASE + "/areas")).andExpect(status().isUnauthorized());
    }

    @Test
    void sortingOnUnknownFieldIsRefusedAndPageSizeIsCapped() throws Exception {
        send(get(BASE + "/areas?sort=password,asc"), VIEWER, null, 400);
        mvc.perform(get(BASE + "/areas").param("size", "100000").with(authentication(VIEWER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(200));
    }
}
