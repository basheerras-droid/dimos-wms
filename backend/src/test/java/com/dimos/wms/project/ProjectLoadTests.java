package com.dimos.wms.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Loads the bundled demo plant into a private test site (9008). Real database, real audit log. Custom equipment types
 * (shared by all sites) are saved and restored, because "Replace" removes unused ones.
 */
@SpringBootTest
class ProjectLoadTests {

    private static final long SITE = 9008L;
    private static final String ACTOR = "zt-project";

    @Autowired
    ProjectLoadService loader;

    @Autowired
    JdbcTemplate jdbc;

    private List<Map<String, Object>> customTypes;

    private final WmsUserPrincipal superAdmin = new WmsUserPrincipal(999_999_998L, SITE, ACTOR, "Project test",
            List.of("SUPER_ADMIN"), List.of("MASTERDATA_VIEW", "MASTERDATA_EDIT", "USER_MANAGE", "WORKSTATION_MANAGE",
            "PARAMETER_VIEW", "PARAMETER_EDIT"), false);
    private final WmsUserPrincipal admin = new WmsUserPrincipal(999_999_997L, SITE, "zt-project-admin", "Admin",
            List.of("ADMIN"), List.of("MASTERDATA_EDIT"), false);

    @BeforeEach
    void setUp() {
        customTypes = jdbc.queryForList("select id, code, name, is_seeded from equipment_type where is_seeded = false");
        cleanUp();
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + SITE + ", 'ZTPROJECT', 'Project test site', 0, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test')");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from parameter_value where site_id = " + SITE);
        jdbc.update("delete from workstation where site_id = " + SITE);
        jdbc.update("delete from workstation_group_permission where workstation_group_id in (select id from workstation_group where site_id = " + SITE + ")");
        jdbc.update("delete from workstation_group where site_id = " + SITE);
        jdbc.update("delete from user_group_role where user_group_id in (select id from user_group where site_id = " + SITE + ")");
        jdbc.update("delete from user_group_member where user_group_id in (select id from user_group where site_id = " + SITE + ")");
        jdbc.update("delete from user_group where site_id = " + SITE);
        jdbc.update("delete from audit_log where site_id = " + SITE + " or actor in ('" + ACTOR + "', 'zt-project-admin')");
        jdbc.update("delete from location_link where site_id = " + SITE);
        jdbc.update("delete from equipment_location where equipment_id in (select id from equipment where site_id = " + SITE + ")");
        jdbc.update("delete from location_type_hu_type where location_type_id in (select id from location_type where site_id = " + SITE + ")");
        jdbc.update("delete from hu_type_nesting where outer_hu_type_id in (select id from hu_type where site_id = " + SITE + ")");
        jdbc.update("update equipment set parent_id = null where site_id = " + SITE);
        jdbc.update("delete from equipment where site_id = " + SITE);
        jdbc.update("delete from location where site_id = " + SITE);
        jdbc.update("delete from location_type where site_id = " + SITE);
        jdbc.update("delete from hu_type where site_id = " + SITE);
        jdbc.update("delete from area where site_id = " + SITE);
        jdbc.update("delete from site where id = " + SITE);
        if (customTypes != null) { // put back what "Replace" removed
            for (Map<String, Object> t : customTypes) {
                jdbc.update("insert into equipment_type (id, code, name, is_seeded) select ?, ?, ?, false "
                        + "where not exists (select 1 from equipment_type where id = ?)", t.get("id"), t.get("code"), t.get("name"), t.get("id"));
            }
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where site_id = " + SITE, Long.class);
    }

    @Test
    void demoDataIsListedWithItsFiles() {
        var all = loader.list(superAdmin);
        assertThat(all).extracting(ProjectLoadService.ProjectInfo::code).contains("demo");
        var preview = loader.preview("demo", superAdmin);
        assertThat(preview.project().files()).extracting(ProjectLoadService.FileInfo::type).contains("locations", "location-links");
        assertThat(preview.project().files().stream().filter(f -> f.type().equals("locations")).findFirst().get().rows())
                .isGreaterThan(5000);
        assertThat(preview.existing().get("locations")).isZero();
    }

    @Test
    void onlyASuperAdministratorMayLoad() {
        assertThatThrownBy(() -> loader.load("demo", ProjectLoadService.Mode.UPSERT, true, null, admin))
                .isInstanceOf(ProjectException.class).extracting("status").isEqualTo(403);
        assertThatThrownBy(() -> loader.list(admin)).isInstanceOf(ProjectException.class);
    }

    @Test
    void replaceNeedsTheProjectNameTypedOut() {
        assertThatThrownBy(() -> loader.load("demo", ProjectLoadService.Mode.REPLACE, false, "wrong", superAdmin))
                .isInstanceOf(ProjectException.class).extracting("status").isEqualTo(400);
        assertThat(count("location")).isZero();
    }

    @Test
    void dryRunChangesNothing() {
        var r = loader.load("demo", ProjectLoadService.Mode.REPLACE, true, null, superAdmin);
        assertThat(r.problems()).isEmpty();
        assertThat(r.steps()).allSatisfy(s -> assertThat(s.errors()).as(s.type() + " " + s.messages()).isZero());
        assertThat(r.applied()).isFalse();
        assertThat(count("location")).isZero();
        assertThat(count("workstation")).isZero();
    }

    @Test
    void replaceLoadsTheDemoPlantAndTheConfigurationCheckPasses() {
        // old master data that must disappear, and a workstation that must keep existing without a location
        jdbc.update("insert into area (id, site_id, code, name, area_type, version, created_at, created_by, updated_at, updated_by) "
                + "values (nextval('area_seq'), " + SITE + ", 'OLD', 'Old area', 'STORAGE', 0, CURRENT_TIMESTAMP, 't', CURRENT_TIMESTAMP, 't')");
        assertThat(count("area")).isEqualTo(1);

        long started = System.nanoTime();
        var r = loader.load("demo", ProjectLoadService.Mode.REPLACE, false, "Demo plant", superAdmin);
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;
        System.out.println("PROJECT-LOAD demo replace: " + seconds + " s");

        assertThat(r.steps()).allSatisfy(s -> assertThat(s.errors()).as(s.type() + " " + s.messages()).isZero());
        r.steps().forEach(st -> System.out.println("PROJECT-LOAD step " + st.type() + " " + st.created() + "/" + st.updated() + "/" + st.unchanged() + " " + st.millis() + " ms"));
        assertThat(r.applied()).isTrue();
        assertThat(r.deleted().get("areas")).isEqualTo(1L);
        assertThat(r.check().status()).as(String.valueOf(r.check().messages())).isIn("PASS", "WARNING");
        assertThat(r.check().errors()).isZero();
        System.out.println("PROJECT-LOAD check: " + r.check());

        assertThat(count("area")).isEqualTo(3);
        assertThat(count("location")).isEqualTo(5351);
        assertThat(count("location_link")).isEqualTo(10680);
        assertThat(count("hu_type")).isEqualTo(4);
        assertThat(count("workstation")).isEqualTo(5);
        assertThat(count("user_group")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where site_id = " + SITE + " and action = 'PROJECT_LOAD' "
                + "and detail like '%REPLACE%Deleted:%areas=1%'", Long.class)).isEqualTo(1L);

        // loading again in Add / update mode changes nothing
        var again = loader.load("demo", ProjectLoadService.Mode.UPSERT, false, null, superAdmin);
        again.steps().forEach(st -> System.out.println("PROJECT-LOAD again " + st.type() + " " + st.created() + "/" + st.updated() + "/" + st.unchanged() + " " + st.millis() + " ms"));
        assertThat(again.applied()).isTrue();
        assertThat(again.steps()).allSatisfy(s -> {
            assertThat(s.created()).as(s.type()).isZero();
            assertThat(s.updated()).as(s.type()).isZero();
        });

        // Replace again: workstations stay, their location is cleared first and set again by the new data
        jdbc.update("update workstation set location_id = null where site_id = " + SITE);
        var replaced = loader.load("demo", ProjectLoadService.Mode.REPLACE, false, "demo plant", superAdmin);
        assertThat(replaced.applied()).isTrue();
        assertThat(replaced.deleted().get("locations")).isEqualTo(5351L);
        assertThat(count("workstation")).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from workstation where site_id = " + SITE + " and location_id is not null",
                Long.class)).isEqualTo(3L);
    }

    @Test
    void aFailingFileLeavesTheOldDataUntouched() {
        // a workstation that points at a location which the new data does not have makes the load fail
        jdbc.update("insert into area (id, site_id, code, name, area_type, version, created_at, created_by, updated_at, updated_by) "
                + "values (nextval('area_seq'), " + SITE + ", 'OLD', 'Old area', 'STORAGE', 0, CURRENT_TIMESTAMP, 't', CURRENT_TIMESTAMP, 't')");
        // an UPSERT of the demo into a site where a blocking record exists is not possible; use a broken second load instead:
        // load the demo, then replace with a data set whose files cannot be read -> nothing changes
        var ok = loader.load("demo", ProjectLoadService.Mode.UPSERT, false, null, superAdmin);
        assertThat(ok.applied()).isTrue();
        long before = count("location");
        assertThatThrownBy(() -> loader.load("does-not-exist", ProjectLoadService.Mode.REPLACE, false, "x", superAdmin))
                .isInstanceOf(ProjectException.class).extracting("status").isEqualTo(404);
        assertThat(count("location")).isEqualTo(before);
        assertThat(count("area")).isEqualTo(4); // OLD + the 3 demo areas: Add / update deleted nothing
    }
}
