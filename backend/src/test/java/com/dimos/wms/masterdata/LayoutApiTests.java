package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/** The layout preview data, on a private test site (9003) with a plan and a pallet rack of INL size. */
@SpringBootTest
@AutoConfigureMockMvc
class LayoutApiTests {

    private static final long SITE = 9003L;
    private static final String AUDIT = "CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'";
    private static final long AREA = 9_300_001, TYPE = 9_300_011;

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private static UsernamePasswordAuthenticationToken viewer(String... permissions) {
        var principal = new WmsUserPrincipal(999_999_999L, SITE, "layout-test", "T", List.of("VIEWER"),
                List.of(permissions), false);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }

    @BeforeEach
    @AfterEach
    void cleanUp() {
        String bySite = " where site_id = " + SITE;
        jdbc.update("delete from location_link" + bySite);
        jdbc.update("delete from location" + bySite);
        jdbc.update("delete from location_type" + bySite);
        jdbc.update("delete from area" + bySite);
        jdbc.update("delete from site where id = " + SITE);
    }

    private void base() {
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + SITE + ", 'ZTLAYOUT', 'Layout test site', 0, " + AUDIT + ")");
        jdbc.update("insert into area (id, site_id, code, name, area_type, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + AREA + ", " + SITE + ", 'STO', 'Storage', 'STORAGE', 0, " + AUDIT + ")");
        jdbc.update("insert into location_type (id, site_id, code, name, is_storage, is_transfer, is_workstation, capacity_hu, "
                + "version, created_at, created_by, updated_at, updated_by) values (" + TYPE + ", " + SITE
                + ", 'BIN', 'Bin', true, false, false, 1, 0, " + AUDIT + ")");
    }

    private void location(long id, String code, Integer x, Integer y, int level, String aisle, String side, Integer column,
                          boolean enabled, boolean blockedIn, boolean blockedOut) {
        jdbc.update("insert into location (id, site_id, code, area_id, location_type_id, x_mm, y_mm, level_no, aisle, side, "
                        + "column_no, enabled, blocked_in, blocked_out, block_reason, version, created_at, created_by, updated_at, updated_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, " + AUDIT + ")",
                id, SITE, code, AREA, TYPE, x, y, level, aisle, side, column, enabled, blockedIn, blockedOut,
                blockedIn || blockedOut ? "test" : null);
    }

    private String json(String url) throws Exception {
        return mvc.perform(get(url).with(authentication(viewer("MASTERDATA_VIEW")))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void planShowsOneLevelWithItsLinksAndCountsWhatCannotBeDrawn() throws Exception {
        base();
        location(9_310_001, "P1", 0, 0, 0, null, null, null, true, false, false);
        location(9_310_002, "P2", 1000, 0, 0, null, null, null, false, false, false);
        location(9_310_003, "P3", 2000, 0, 0, null, null, null, true, true, false);
        location(9_310_004, "NOPOS", null, null, 0, null, null, null, true, false, false);
        location(9_310_005, "UP1", 0, 0, 1, null, null, null, true, false, false);
        jdbc.update("insert into location_link (id, site_id, from_location_id, to_location_id, cost, enabled, version, created_at, "
                + "created_by, updated_at, updated_by) values (9320001, " + SITE + ", 9310001, 9310002, 1, true, 0, " + AUDIT + ")");
        jdbc.update("insert into location_link (id, site_id, from_location_id, to_location_id, cost, enabled, version, created_at, "
                + "created_by, updated_at, updated_by) values (9320002, " + SITE + ", 9310002, 9310003, 1, false, 0, " + AUDIT + ")");
        jdbc.update("insert into location_link (id, site_id, from_location_id, to_location_id, cost, enabled, version, created_at, "
                + "created_by, updated_at, updated_by) values (9320003, " + SITE + ", 9310003, 9310005, 1, true, 0, " + AUDIT + ")");

        String plan = json("/api/masterdata/layout/plan"); // lowest level by default
        assertThat(JsonPath.<Integer>read(plan, "$.level")).isEqualTo(0);
        assertThat(JsonPath.<List<Integer>>read(plan, "$.levels")).containsExactly(0, 1);
        assertThat(JsonPath.<List<String>>read(plan, "$.locations[*].code")).containsExactly("P1", "P2", "P3");
        assertThat(JsonPath.<List<String>>read(plan, "$.locations[*].state")).containsExactly("OK", "DISABLED", "BLOCKED_IN");
        assertThat(JsonPath.<Integer>read(plan, "$.withoutPosition")).isEqualTo(1);
        assertThat(JsonPath.<List<Boolean>>read(plan, "$.links[*].enabled")).containsExactly(true, false);
        assertThat(JsonPath.<Integer>read(plan, "$.crossLevelLinks")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(plan, "$.areas[*].code")).containsExactly("STO");

        String upper = json("/api/masterdata/layout/plan?levelNo=1");
        assertThat(JsonPath.<List<String>>read(upper, "$.locations[*].code")).containsExactly("UP1");
        assertThat(JsonPath.<List<?>>read(upper, "$.links")).isEmpty();
        // an unknown level falls back to the lowest one instead of failing
        assertThat(JsonPath.<Integer>read(json("/api/masterdata/layout/plan?levelNo=99"), "$.level")).isEqualTo(0);
    }

    @Test
    void anEmptySiteGivesAnEmptyPlanAndNoRacks() throws Exception {
        base();
        String plan = json("/api/masterdata/layout/plan");
        assertThat(JsonPath.<Object>read(plan, "$.level")).isNull();
        assertThat(JsonPath.<List<?>>read(plan, "$.locations")).isEmpty();
        assertThat(JsonPath.<List<?>>read(json("/api/masterdata/layout/racks"), "$")).isEmpty();
    }

    @Test
    void aPalletRackOfInlSizeIsServedAsAGrid() throws Exception {
        base();
        List<Object[]> rows = new ArrayList<>();
        long id = 9_400_000;
        for (String side : List.of("L", "R")) {
            for (int column = 1; column <= 74; column++) {
                for (int level = 1; level <= 12; level++) {
                    boolean disabled = side.equals("L") && column == 5 && level == 1;
                    boolean blockedIn = side.equals("L") && column == 6 && level == 2;
                    boolean blockedOut = side.equals("L") && column == 7 && level == 3;
                    boolean both = side.equals("L") && column == 8 && level == 4;
                    rows.add(new Object[] {id++, SITE, "A01-" + side + "-" + column + "-" + level, AREA, TYPE, level, "A01", side,
                            column, !disabled, blockedIn || both, blockedOut || both});
                }
            }
        }
        jdbc.batchUpdate("insert into location (id, site_id, code, area_id, location_type_id, level_no, aisle, side, column_no, "
                + "enabled, blocked_in, blocked_out, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, " + AUDIT + ")", rows);
        // one location of the same aisle side without a column, and one duplicate cell
        location(9_499_001, "A01-L-NOCOL", null, null, 1, "A01", "L", null, true, false, false);
        location(9_499_002, "A01-L-DUP", null, null, 12, "A01", "L", 74, true, false, false);

        List<?> racks = JsonPath.read(json("/api/masterdata/layout/racks"), "$");
        assertThat(racks).hasSize(2);
        String first = json("/api/masterdata/layout/racks");
        assertThat(JsonPath.<List<String>>read(first, "$[*].side")).containsExactly("L", "R");
        assertThat(JsonPath.<Integer>read(first, "$[0].maxColumn")).isEqualTo(74);
        assertThat(JsonPath.<Integer>read(first, "$[0].maxLevel")).isEqualTo(12);

        long started = System.nanoTime();
        String face = json("/api/masterdata/layout/rack-face?aisle=A01&side=L");
        long ms = (System.nanoTime() - started) / 1_000_000;
        assertThat(JsonPath.<Integer>read(face, "$.minColumn")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(face, "$.maxColumn")).isEqualTo(74);
        assertThat(JsonPath.<Integer>read(face, "$.maxLevel")).isEqualTo(12);
        assertThat(JsonPath.<Integer>read(face, "$.summary.total")).isEqualTo(74 * 12 + 1); // +1: the duplicate cell
        assertThat(JsonPath.<Integer>read(face, "$.summary.disabled")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(face, "$.summary.blockedIn")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(face, "$.summary.blockedOut")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(face, "$.summary.blockedBoth")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(face, "$.summary.usable")).isEqualTo(74 * 12 + 1 - 4);
        assertThat(JsonPath.<Integer>read(face, "$.summary.duplicates")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(face, "$.summary.unplaced")).isEqualTo(1);
        assertThat(JsonPath.<Boolean>read(face, "$.fill.available")).isFalse(); // no stock yet
        assertThat(JsonPath.<Integer>read(face, "$.fill.capacity")).isEqualTo(74 * 12 + 1 - 4);
        assertThat(JsonPath.<List<String>>read(face, "$.cells[?(@.column == 5 && @.level == 1)].state")).containsExactly("DISABLED");
        assertThat(JsonPath.<List<String>>read(face, "$.cells[?(@.column == 8 && @.level == 4)].state")).containsExactly("BLOCKED_BOTH");
        assertThat(ms).as("rack face of 889 cells, ms").isLessThan(2_000);

        // the other side is separate and clean; a side that does not exist is simply empty
        String right = json("/api/masterdata/layout/rack-face?aisle=A01&side=R");
        assertThat(JsonPath.<Integer>read(right, "$.summary.total")).isEqualTo(74 * 12);
        assertThat(JsonPath.<Integer>read(right, "$.summary.usable")).isEqualTo(74 * 12);
        assertThat(JsonPath.<Integer>read(json("/api/masterdata/layout/rack-face?aisle=NOPE&side=L"), "$.summary.total")).isZero();
    }

    @Test
    void racksWithoutASideWork() throws Exception {
        base();
        location(9_450_001, "N1", null, null, 1, "B01", null, 1, true, false, false);
        location(9_450_002, "N2", null, null, 2, "B01", null, 1, true, false, false);
        String face = json("/api/masterdata/layout/rack-face?aisle=B01");
        assertThat(JsonPath.<Integer>read(face, "$.summary.total")).isEqualTo(2);
        assertThat(JsonPath.<Object>read(face, "$.side")).isNull();
    }

    @Test
    void onlyPeopleWhoMayViewMasterDataSeeTheLayout() throws Exception {
        for (String url : List.of("/api/masterdata/layout/plan", "/api/masterdata/layout/racks",
                "/api/masterdata/layout/rack-face?aisle=A")) {
            mvc.perform(get(url).with(authentication(viewer("AUDIT_VIEW")))).andExpect(status().isForbidden());
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }
}
