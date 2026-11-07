package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the project loader (module "project") may do with master data: count it, delete all of it, load one file,
 * and run the configuration check. Everything joins the caller's transaction, so a whole project data set is
 * replaced completely or not at all. The caller decides on commit or rollback.
 */
@Service
public class ProjectDataApi {

    /** Master-data files in the order they must be loaded. */
    public static final List<String> FILE_KEYS = List.of("areas", "location-types", "hu-types", "locations",
            "equipment", "location-links", "equipment-locations", "location-type-hu-types", "hu-type-nesting");

    public record StepResult(String key, long created, long updated, long unchanged, long errors,
                             List<String> messages) {
        public boolean ok() {
            return errors == 0;
        }
    }

    public record CheckResult(String status, long errors, long warnings, List<String> messages) {
    }

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final ImportExportService importer;
    private final ConfigurationValidator validator;

    ProjectDataApi(JdbcTemplate jdbc, EntityManager entityManager, ImportExportService importer,
                   ConfigurationValidator validator) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.importer = importer;
        this.validator = validator;
    }

    /** One table per entry: where its rows are counted. "site_id" tables are counted per site. */
    private static final Map<String, String> COUNT_SQL = new LinkedHashMap<>();

    static {
        COUNT_SQL.put("areas", "select count(*) from area where site_id = ?");
        COUNT_SQL.put("locationTypes", "select count(*) from location_type where site_id = ?");
        COUNT_SQL.put("huTypes", "select count(*) from hu_type where site_id = ?");
        COUNT_SQL.put("huTypeNesting", "select count(*) from hu_type_nesting n join hu_type h on h.id = n.outer_hu_type_id "
                + "where h.site_id = ?");
        COUNT_SQL.put("locationTypeHuTypes", "select count(*) from location_type_hu_type x join location_type t "
                + "on t.id = x.location_type_id where t.site_id = ?");
        COUNT_SQL.put("locations", "select count(*) from location where site_id = ?");
        COUNT_SQL.put("locationLinks", "select count(*) from location_link where site_id = ?");
        COUNT_SQL.put("equipment", "select count(*) from equipment where site_id = ?");
        COUNT_SQL.put("equipmentLocations", "select count(*) from equipment_location x join equipment e "
                + "on e.id = x.equipment_id where e.site_id = ?");
        COUNT_SQL.put("customEquipmentTypes", "select count(*) from equipment_type where is_seeded = false and ? is not null");
        COUNT_SQL.put("workstationsWithLocation", "select count(*) from workstation where site_id = ? and location_id is not null");
    }

    /** How much master data the site has now (what "Replace" would delete). */
    @Transactional(readOnly = true)
    public Map<String, Long> counts(Long siteId) {
        Map<String, Long> result = new LinkedHashMap<>();
        COUNT_SQL.forEach((name, sql) -> result.put(name, jdbc.queryForObject(sql, Long.class, siteId)));
        return result;
    }

    /**
     * Deletes ALL master data of the site: areas, location types, HU types (and what hangs on them), locations, links,
     * equipment, assignments and custom equipment types. Workstations stay, only their location is cleared.
     * Returns what was deleted (same names as {@link #counts}).
     */
    @Transactional
    public Map<String, Long> deleteAll(Long siteId) {
        entityManager.flush();
        Map<String, Long> deleted = counts(siteId);
        jdbc.update("update workstation set location_id = null where site_id = ?", siteId);
        jdbc.update("delete from location_link where site_id = ?", siteId);
        jdbc.update("delete from equipment_location where equipment_id in (select id from equipment where site_id = ?)", siteId);
        jdbc.update("delete from location_type_hu_type where location_type_id in (select id from location_type where site_id = ?)", siteId);
        jdbc.update("delete from hu_type_nesting where outer_hu_type_id in (select id from hu_type where site_id = ?) "
                + "or inner_hu_type_id in (select id from hu_type where site_id = ?)", siteId, siteId);
        jdbc.update("update equipment set parent_id = null where site_id = ?", siteId);
        jdbc.update("delete from equipment where site_id = ?", siteId);
        jdbc.update("delete from location where site_id = ?", siteId);
        jdbc.update("delete from location_type where site_id = ?", siteId);
        jdbc.update("delete from hu_type where site_id = ?", siteId);
        jdbc.update("delete from area where site_id = ?", siteId);
        // equipment types are shared by all sites: remove only custom ones that no equipment uses any more
        int types = jdbc.update("delete from equipment_type where is_seeded = false and not exists "
                + "(select 1 from equipment e where e.equipment_type_id = equipment_type.id)");
        deleted.put("customEquipmentTypes", (long) types);
        entityManager.clear(); // the session must not remember rows that are gone
        return deleted;
    }

    /** Loads one master-data file (key from {@link #FILE_KEYS}) in the caller's transaction. */
    @Transactional
    public StepResult load(String key, String filename, byte[] data, WmsUserPrincipal who) {
        ImportExportService.Result r = importer.runStep(key, filename, data, who);
        List<String> messages = new ArrayList<>(r.fileErrors());
        r.rows().stream().filter(x -> x.status() == ImportRun.Status.ERROR).limit(20)
                .forEach(x -> messages.add("row " + x.row() + " (" + x.key() + "): " + x.message()));
        return new StepResult(key, r.created() + r.added(), r.updated(), r.unchanged(), r.errors(), messages);
    }

    /** Reads a file as plain rows (column name as written -> text), for the loader's own file types. */
    public List<Map<String, String>> readRows(String filename, byte[] data) {
        Table table = TableReader.read(filename, data);
        List<Map<String, String>> rows = new ArrayList<>();
        for (Table.Line line : table.lines()) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("#row", String.valueOf(line.number()));
            for (String header : table.headers()) {
                row.put(header, line.str(header));
            }
            rows.add(row);
        }
        return rows;
    }

    /** The configuration check (the same one as the "Check configuration" screen), with its findings as text. */
    @Transactional
    public CheckResult check(Long siteId) {
        entityManager.flush();
        ConfigurationValidator.Report report = validator.check(siteId);
        List<String> messages = report.rules().stream().filter(x -> !"PASS".equals(x.status()))
                .map(x -> x.status() + ": " + x.title() + " (" + x.count() + ")").collect(Collectors.toList());
        long errors = report.rules().stream().filter(x -> "ERROR".equals(x.status())).count();
        long warnings = report.rules().stream().filter(x -> "WARNING".equals(x.status())).count();
        return new CheckResult(report.status(), errors, warnings, messages);
    }
}
