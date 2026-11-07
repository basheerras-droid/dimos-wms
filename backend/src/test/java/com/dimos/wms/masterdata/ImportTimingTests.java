package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.dimos.wms.identity.WmsUserPrincipal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * How long does an import of the size of the first real project (DFC: about 5 000 locations and 10 000 links) take?
 * Runs against a private test site with the real audit log (every change is audited, as in production) and prints the
 * times, which are recorded in docs/test-evidence/STEP-E.md. The limits asserted here are generous: they only catch a
 * serious slow-down.
 */
@SpringBootTest
class ImportTimingTests {

    private static final long SITE = 9007L;
    private static final int LOCATIONS = 5_000;
    private static final int LINKS = 10_000;
    private static final String ACTOR = "zt-timing";

    @Autowired
    ImportExportService importer;

    @Autowired
    JdbcTemplate jdbc;

    private final WmsUserPrincipal who = new WmsUserPrincipal(999_999_999L, SITE, ACTOR, "Timing", List.of("SUPER_ADMIN"),
            List.of("MASTERDATA_EDIT"), false);

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("insert into site (id, code, name, version, created_at, created_by, updated_at, updated_by) "
                + "values (" + SITE + ", 'ZTTIMING', 'Timing test site', 0, CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP, 'test')");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from audit_log where site_id = " + SITE + " or actor = '" + ACTOR + "'");
        jdbc.update("delete from location_link where site_id = " + SITE);
        jdbc.update("delete from location where site_id = " + SITE);
        jdbc.update("delete from location_type where site_id = " + SITE);
        jdbc.update("delete from area where site_id = " + SITE);
        jdbc.update("delete from site where id = " + SITE);
        jdbc.update("delete from audit_log where actor = '" + ACTOR + "'");
    }

    private static byte[] locationsCsv() {
        StringBuilder sb = new StringBuilder("code,name,area_code,location_type_code,x_mm,y_mm,z_mm,level_no,aisle,side,column_no,row_no\n");
        for (int i = 0; i < LOCATIONS; i++) {
            int column = i % 100, row = i / 100;
            sb.append("LOC-").append(String.format("%05d", i)).append(",Location ").append(i).append(",STO,BIN,")
                    .append(column * 1100).append(',').append(row * 1200).append(",0,").append(1 + row % 5).append(",A")
                    .append(row % 10).append(',').append(i % 2 == 0 ? "L" : "R").append(',').append(column).append(',').append(row).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] linksCsv() {
        StringBuilder sb = new StringBuilder("from_location_code,to_location_code,cost\n");
        int n = 0;
        for (int i = 0; i + 1 < LOCATIONS && n < LINKS - 2; i++) {
            sb.append(String.format("LOC-%05d,LOC-%05d,1%n", i, i + 1));
            sb.append(String.format("LOC-%05d,LOC-%05d,1%n", i + 1, i));
            n += 2;
        }
        sb.append("LOC-00000,LOC-00002,2\nLOC-00001,LOC-00003,2\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ImportExportService.Result run(String type, String name, byte[] data, boolean dryRun) {
        long started = System.nanoTime();
        ImportExportService.Result r = importer.process(type, name, data, dryRun, who);
        long ms = (System.nanoTime() - started) / 1_000_000;
        System.out.printf("IMPORT-TIMING %-15s %-8s rows=%-6d created=%-6d updated=%-4d unchanged=%-6d errors=%-3d %6.1f s%n",
                type, dryRun ? "dry run" : "apply", r.created() + r.updated() + r.unchanged() + r.errors(), r.created(), r.updated(),
                r.unchanged(), r.errors(), ms / 1000.0);
        return r;
    }

    @Test
    void importOfFiveThousandLocationsAndTenThousandLinksIsFastEnough() {
        long total = System.nanoTime();
        run("areas", "areas.csv", "code,name,area_type\nSTO,Storage,STORAGE\n".getBytes(StandardCharsets.UTF_8), false);
        run("location-types", "types.csv", "code,name,is_storage,capacity_hu\nBIN,Bin,TRUE,1\n".getBytes(StandardCharsets.UTF_8), false);

        byte[] locations = locationsCsv();
        byte[] links = linksCsv();

        ImportExportService.Result dryL = run("locations", "locations.csv", locations, true);
        assertThat(dryL.errors()).isZero();
        assertThat(dryL.created()).isEqualTo(LOCATIONS);
        assertThat(jdbc.queryForObject("select count(*) from location where site_id = " + SITE, Integer.class)).isZero(); // nothing written

        ImportExportService.Result applyL = run("locations", "locations.csv", locations, false);
        assertThat(applyL.applied()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from location where site_id = " + SITE, Integer.class)).isEqualTo(LOCATIONS);

        ImportExportService.Result dryK = run("location-links", "links.csv", links, true);
        assertThat(dryK.errors()).isZero();
        assertThat(dryK.created()).isEqualTo(LINKS);

        ImportExportService.Result applyK = run("location-links", "links.csv", links, false);
        assertThat(applyK.applied()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from location_link where site_id = " + SITE, Integer.class)).isEqualTo(LINKS);

        // loading the same files again changes nothing (and is the usual case when a data set is reloaded)
        ImportExportService.Result again = run("locations", "locations.csv", locations, false);
        assertThat(again.unchanged()).isEqualTo(LOCATIONS);
        ImportExportService.Result againLinks = run("location-links", "links.csv", links, false);
        assertThat(againLinks.unchanged()).isEqualTo(LINKS);

        long seconds = (System.nanoTime() - total) / 1_000_000_000L;
        System.out.println("IMPORT-TIMING total for everything above: " + seconds + " s");
        assertThat(seconds).as("whole sequence in seconds").isLessThan(900);
    }
}
