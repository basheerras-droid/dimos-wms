package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dimos.wms.masterdata.ConfigurationValidator.Report;
import com.dimos.wms.masterdata.ConfigurationValidator.RuleResult;
import com.dimos.wms.identity.WmsUserPrincipal;
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

/**
 * The configuration validator on a private test site (id 9001) so it never depends on, or disturbs, real master data.
 * Rows are inserted with plain SQL and removed again before and after every test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfigurationValidatorTests {

    private static final long SITE = 9001L;
    private static final String AUDIT = "CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test'";

    // area / location type / HU type ids
    private static final long INB = 9_000_001, STO = 9_000_002, OUT = 9_000_003, MID = 9_000_004;
    private static final long POS = 9_000_011, BIN = 9_000_012;
    private static final long PALLET = 9_000_021, ORPHAN = 9_000_022;

    @Autowired
    ConfigurationValidator validator;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        String bySite = " where site_id = " + SITE;
        jdbc.update("delete from equipment_location where equipment_id in (select id from equipment" + bySite + ")");
        jdbc.update("delete from location_link" + bySite);
        jdbc.update("delete from equipment" + bySite);
        jdbc.update("delete from location" + bySite);
        jdbc.update("delete from location_type_hu_type where location_type_id in (select id from location_type" + bySite + ")");
        jdbc.update("delete from hu_type" + bySite);
        jdbc.update("delete from location_type" + bySite);
        jdbc.update("delete from area" + bySite);
        jdbc.update("delete from site where id = " + SITE);
    }

    // ---- data helpers ----------------------------------------------------------------------------------

    private void basePlant() {
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + SITE + ", 'ZTSITE', 'Validator test site', 0, " + AUDIT + ")");
        area(INB, "INB", "INBOUND");
        area(STO, "STO", "STORAGE");
        area(OUT, "OUT", "OUTBOUND");
        area(MID, "MID", "TRANSPORT");
        locationType(POS, "POS", false);
        locationType(BIN, "BIN", true);
        huType(PALLET, "PALLET");
        jdbc.update("insert into location_type_hu_type values (?, ?)", POS, PALLET);
        jdbc.update("insert into location_type_hu_type values (?, ?)", BIN, PALLET);
    }

    private void area(long id, String code, String type) {
        jdbc.update("insert into area (id, site_id, code, name, area_type, version, created_at, created_by, updated_at, "
                + "updated_by) values (?, ?, ?, ?, ?, 0, " + AUDIT + ")", id, SITE, code, code, type);
    }

    private void locationType(long id, String code, boolean storage) {
        jdbc.update("insert into location_type (id, site_id, code, name, is_storage, is_transfer, is_workstation, "
                + "capacity_hu, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, ?, ?, false, false, 1, 0, " + AUDIT + ")", id, SITE, code, code, storage);
    }

    private void huType(long id, String code) {
        jdbc.update("insert into hu_type (id, site_id, code, name, is_container, version, created_at, created_by, "
                + "updated_at, updated_by) values (?, ?, ?, ?, false, 0, " + AUDIT + ")", id, SITE, code, code);
    }

    private void location(long id, String code, long area, long type, Integer x, Integer y, int level, boolean enabled) {
        jdbc.update("insert into location (id, site_id, code, area_id, location_type_id, x_mm, y_mm, level_no, enabled, "
                        + "blocked_in, blocked_out, version, created_at, created_by, updated_at, updated_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, false, false, 0, " + AUDIT + ")",
                id, SITE, code, area, type, x, y, level, enabled);
    }

    private void link(long id, long from, long to, Long hu, Long equipment, boolean enabled) {
        jdbc.update("insert into location_link (id, site_id, from_location_id, to_location_id, hu_type_id, equipment_id, "
                + "cost, enabled, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, ?, ?, ?, 1, ?, 0, " + AUDIT + ")", id, SITE, from, to, hu, equipment, enabled);
    }

    private void equipment(long id, String code, long typeId, Long parent) {
        jdbc.update("insert into equipment (id, site_id, code, name, equipment_type_id, parent_id, enabled, version, "
                + "created_at, created_by, updated_at, updated_by) values (?, ?, ?, ?, ?, ?, true, 0, " + AUDIT + ")",
                id, SITE, code, code, typeId, parent);
    }

    private void equipmentLocation(long equipment, long location) {
        jdbc.update("insert into equipment_location values (?, ?)", equipment, location);
    }

    private static final long IN1 = 9_100_001, C1 = 9_100_002, S1 = 9_100_003, S2 = 9_100_004, OUT1 = 9_100_005;
    private static final long CONV = 9_200_001;

    /** A small, correct plant: inbound -> conveyor -> two storage bins -> conveyor -> outbound. */
    private void goodPlant() {
        basePlant();
        location(IN1, "IN1", INB, POS, 0, 0, 0, true);
        location(C1, "C1", MID, POS, 1000, 0, 0, true);
        location(S1, "S1", STO, BIN, 2000, 0, 1, true);
        location(S2, "S2", STO, BIN, 2000, 500, 1, true);
        location(OUT1, "OUT1", OUT, POS, 3000, 0, 0, true);
        link(9_300_001, IN1, C1, null, null, true);
        link(9_300_002, C1, S1, null, null, true);
        link(9_300_003, C1, S2, null, null, true);
        link(9_300_004, S1, C1, null, null, true);
        link(9_300_005, S2, C1, null, null, true);
        link(9_300_006, C1, OUT1, null, null, true);
        equipment(CONV, "CONV-1", 1, null); // 1 = CONVEYOR (seeded)
        equipmentLocation(CONV, IN1);
        equipmentLocation(CONV, C1);
    }

    private static RuleResult rule(Report report, String code) {
        return report.rules().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }

    // ---- tests -----------------------------------------------------------------------------------------

    @Test
    void aCorrectPlantPasses() {
        goodPlant();
        Report report = validator.check(SITE);
        assertThat(report.rules().stream().filter(r -> !r.status().equals("PASS")).map(RuleResult::code)).isEmpty();
        assertThat(report.status()).isEqualTo("PASS");
        assertThat(report.counts()).containsEntry("locations", 5L).containsEntry("links", 6L);
    }

    @Test
    void emptySiteHasNothingToComplainAbout() {
        basePlant();
        assertThat(validator.check(SITE).status()).isEqualTo("PASS");
    }

    @Test
    void everyKindOfProblemIsFound() {
        goodPlant();
        // duplicate link
        link(9_300_010, IN1, C1, null, null, true);
        // all-types link next to a type-specific link for the same pair
        link(9_300_011, C1, OUT1, PALLET, null, true);
        // a storage bin nobody can reach and that has no way out; also at the same spot as S1
        location(9_100_010, "S3", STO, BIN, 2000, 0, 1, true);
        // a disabled location with an enabled link
        location(9_100_011, "DIS", MID, POS, 5000, 0, 0, false);
        link(9_300_012, C1, 9_100_011, null, null, true);
        // a location without coordinates
        location(9_100_012, "NOXY", MID, POS, null, null, 0, true);
        // equipment without locations, and a second conveyor on IN1
        equipment(9_200_002, "CONV-EMPTY", 1, null);
        equipment(9_200_003, "CONV-2", 1, null);
        equipmentLocation(9_200_003, IN1);
        // a used location type that accepts nothing, and an HU type accepted nowhere
        locationType(9_000_013, "NOHU", false);
        location(9_100_013, "NH1", MID, 9_000_013, 7000, 0, 0, true);
        huType(ORPHAN, "ORPHAN");

        Report report = validator.check(SITE);
        assertThat(report.status()).isEqualTo("ERROR");

        assertThat(rule(report, "LINK_DUPLICATE").status()).isEqualTo("ERROR");
        assertThat(rule(report, "LINK_DUPLICATE").items().get(0).entityId()).isEqualTo(9_300_010L);

        RuleResult overlap = rule(report, "LINK_ALL_AND_SPECIFIC");
        assertThat(overlap.status()).isEqualTo("WARNING");
        assertThat(overlap.items().get(0).code()).isEqualTo("C1 -> OUT1");
        assertThat(overlap.items().get(0).message()).contains("PALLET");

        assertThat(rule(report, "STORAGE_NOT_REACHABLE").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactly("S3");
        assertThat(rule(report, "STORAGE_NO_EXIT").status()).isEqualTo("ERROR");
        assertThat(rule(report, "LINK_DISABLED_LOCATION").status()).isEqualTo("WARNING");
        assertThat(rule(report, "LOCATION_NO_COORDINATES").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactly("NOXY");
        assertThat(rule(report, "LOCATION_SAME_COORDINATES").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactlyInAnyOrder("S1", "S3");
        assertThat(rule(report, "EQUIPMENT_NO_LOCATIONS").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactly("CONV-EMPTY");
        assertThat(rule(report, "LOCATION_DOUBLE_EQUIPMENT").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactly("IN1");
        RuleResult noHu = rule(report, "LOCATION_TYPE_NO_HU_TYPE");
        assertThat(noHu.status()).isEqualTo("ERROR"); // NOHU is used by a location
        assertThat(noHu.items().get(0).code()).isEqualTo("NOHU");
        assertThat(rule(report, "HU_TYPE_ACCEPTED_NOWHERE").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactly("ORPHAN");
        // NOXY, DIS (disabled: skipped), NH1 have no links: incoming/outgoing warnings, but storage bins are exempt
        assertThat(rule(report, "LOCATION_NO_INCOMING").items()).extracting(ConfigurationValidator.Item::code)
                .contains("NOXY", "NH1").doesNotContain("S3", "DIS");
        // every item points at a record the screen can open
        report.rules().forEach(r -> r.items().forEach(i -> assertThat(i.message()).isNotBlank()));
    }

    @Test
    void anAllTypesLinkAloneOrSpecificLinksAloneAreNotFlagged() {
        goodPlant();
        link(9_300_020, IN1, C1, PALLET, null, true);    // same pair as the all-types link IN1 -> C1: flagged
        link(9_300_021, C1, S1, null, 9_200_001L, true);  // different equipment, still all types: not a mix
        Report report = validator.check(SITE);
        assertThat(rule(report, "LINK_ALL_AND_SPECIFIC").items()).extracting(ConfigurationValidator.Item::code)
                .containsExactly("IN1 -> C1");
        assertThat(rule(report, "LINK_DUPLICATE").count()).isZero();
    }

    @Test
    void noInboundAreaIsReportedOnceNotPerStorageLocation() {
        goodPlant();
        jdbc.update("update area set area_type = 'TRANSPORT' where id = ?", INB);
        RuleResult r = rule(validator.check(SITE), "STORAGE_NOT_REACHABLE");
        assertThat(r.count()).isEqualTo(1);
        assertThat(r.items().get(0).message()).contains("no enabled location in an INBOUND area");
    }

    @Test
    void twentyThousandLocationsAreCheckedInUnderFiveSeconds() {
        basePlant();
        int storage = 19_998;
        location(IN1, "IN1", INB, POS, 0, 0, 0, true);
        location(C1, "HUB", MID, POS, 1, 0, 0, true);
        location(OUT1, "OUT1", OUT, POS, 2, 0, 0, true);
        List<Object[]> locations = new ArrayList<>();
        List<Object[]> links = new ArrayList<>();
        links.add(new Object[] {9_300_000L, SITE, IN1, C1});
        links.add(new Object[] {9_300_001L, SITE, C1, OUT1});
        for (int i = 0; i < storage; i++) {
            long id = 9_400_000L + i;
            locations.add(new Object[] {id, SITE, "S" + i, STO, BIN, 10 + i, 0});
            links.add(new Object[] {9_500_000L + 2L * i, SITE, C1, id});
            links.add(new Object[] {9_500_001L + 2L * i, SITE, id, C1});
        }
        jdbc.batchUpdate("insert into location (id, site_id, code, area_id, location_type_id, x_mm, y_mm, level_no, "
                + "enabled, blocked_in, blocked_out, version, created_at, created_by, updated_at, updated_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, 1, true, false, false, 0, " + AUDIT + ")", locations);
        jdbc.batchUpdate("insert into location_link (id, site_id, from_location_id, to_location_id, cost, enabled, "
                + "version, created_at, created_by, updated_at, updated_by) values (?, ?, ?, ?, 1, true, 0, " + AUDIT + ")",
                links);

        long started = System.nanoTime();
        Report report = validator.check(SITE);
        long wallMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(report.counts()).containsEntry("locations", 20_001L);
        assertThat(report.status()).isEqualTo("PASS");
        assertThat(wallMs).as("validator time in ms for 20 001 locations and %d links", links.size()).isLessThan(5_000);
        System.out.println("VALIDATOR 20001 locations, " + links.size() + " links: " + wallMs + " ms");
    }

    @Test
    void reportIsOnlyForPeopleWhoMayViewMasterData() throws Exception {
        var viewer = new WmsUserPrincipal(1L, 1L, "v", "V", List.of("VIEWER"), List.of("MASTERDATA_VIEW"), false);
        var nobody = new WmsUserPrincipal(1L, 1L, "o", "O", List.of("OPERATOR"), List.of(), false);
        mvc.perform(get("/api/masterdata/validation").with(authentication(token(viewer, "MASTERDATA_VIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(14))
                .andExpect(jsonPath("$.status").exists());
        mvc.perform(get("/api/masterdata/validation").with(authentication(token(nobody)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/masterdata/validation")).andExpect(status().isUnauthorized());
    }

    private static UsernamePasswordAuthenticationToken token(WmsUserPrincipal p, String... authorities) {
        List<SimpleGrantedAuthority> list = new ArrayList<>();
        for (String a : authorities) {
            list.add(new SimpleGrantedAuthority(a));
        }
        return UsernamePasswordAuthenticationToken.authenticated(p, null, list);
    }
}
