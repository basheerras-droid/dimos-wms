package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Opens the list endpoint of EVERY master-data table the way the web screens do (default sort, paging, search text,
 * and every sort field the server allows, ascending and descending). A list that fails with HTTP 500 can then not go
 * unnoticed again. Works on whatever data is in the database (even none): it is about the queries being valid.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ListEndpointsTests {

    /** entityType() of each service -> list URL. A new master-data table must be added here (the test checks it). */
    private static final Map<String, String> PATHS = Map.of(
            "area", "/api/masterdata/areas",
            "location_type", "/api/masterdata/location-types",
            "hu_type", "/api/masterdata/hu-types",
            "location", "/api/masterdata/locations",
            "location_link", "/api/masterdata/location-links",
            "equipment", "/api/masterdata/equipment");

    @Autowired
    MockMvc mvc;

    @Autowired
    List<MasterDataService<?, ?, ?>> services;

    @MockitoBean
    AuditService audit;

    private static UsernamePasswordAuthenticationToken viewer() {
        var principal = new WmsUserPrincipal(1L, 1L, "list-test", "Test", List.of("VIEWER"),
                List.of("MASTERDATA_VIEW"), false);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_VIEWER"), new SimpleGrantedAuthority("MASTERDATA_VIEW")));
    }

    private MvcResult list(String url) throws Exception {
        return mvc.perform(get(url).with(authentication(viewer()))).andExpect(status().isOk()).andReturn();
    }

    @Test
    void everyMasterDataTableIsCoveredByThisTest() {
        assertThat(services.stream().map(MasterDataService::entityType).toList())
                .containsExactlyInAnyOrderElementsOf(PATHS.keySet());
    }

    @Test
    void everyListOpensWithDefaultSortPagingSearchAndEverySortField() throws Exception {
        List<String> failures = new ArrayList<>();
        for (MasterDataService<?, ?, ?> service : services) {
            String path = PATHS.get(service.entityType());
            List<String> urls = new ArrayList<>();
            urls.add(path);                                                  // server defaults
            urls.add(path + "?page=0&size=25&sort=" + service.defaultSort() + "&q=");
            urls.add(path + "?page=0&size=25&sort=" + service.defaultSort() + "&q=a");
            urls.add(path + "?page=1&size=200");
            for (String field : service.sortFields()) {
                urls.add(path + "?page=0&size=25&sort=" + field + ",asc");
                urls.add(path + "?page=0&size=25&sort=" + field + ",desc");
            }
            for (String url : urls) {
                try {
                    list(url);
                } catch (AssertionError | Exception e) {
                    failures.add(url + " -> " + e.getMessage().lines().findFirst().orElse(""));
                }
            }
        }
        assertThat(failures).as("list URLs that did not answer 200").isEmpty();
    }

    @Test
    void equipmentTypeListOpens() throws Exception {
        list("/api/masterdata/equipment-types");
        list("/api/masterdata/equipment-types?q=con");
    }

    @Test
    void listsWithTheFiltersOfTheWebScreensOpen() throws Exception {
        for (String url : List.of(
                "/api/masterdata/areas?areaType=STORAGE",
                "/api/masterdata/hu-types?container=true",
                "/api/masterdata/locations?areaId=1&locationTypeId=1&levelNo=0&enabled=true&blocked=false",
                "/api/masterdata/locations?blocked=true",
                "/api/masterdata/location-links?locationId=1&huTypeId=1&equipmentId=1&enabled=true&q=a",
                "/api/masterdata/equipment?equipmentTypeId=1&areaId=1&enabled=true")) {
            list(url);
        }
    }
}
