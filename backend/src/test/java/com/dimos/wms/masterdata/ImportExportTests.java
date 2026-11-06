package com.dimos.wms.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Spreadsheet import/export through the REST API. Imports really commit (that is what is being tested), so the rows
 * use the code prefix ZT and are removed before and after every test. The audit service is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImportExportTests {

    private static final String BASE = "/api/masterdata";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    AuditService audit;

    private static UsernamePasswordAuthenticationToken user(String role, String... permissions) {
        var principal = new WmsUserPrincipal(1L, 1L, "test-" + role.toLowerCase(), "Test", List.of(role),
                List.of(permissions), false);
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String p : permissions) {
            authorities.add(new SimpleGrantedAuthority(p));
        }
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
    }

    private static final UsernamePasswordAuthenticationToken ADMIN =
            user("ADMIN", "MASTERDATA_VIEW", "MASTERDATA_EDIT");
    private static final UsernamePasswordAuthenticationToken VIEWER = user("VIEWER", "MASTERDATA_VIEW");

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

    private MvcResult upload(String type, String filename, byte[] content, boolean dryRun,
                             UsernamePasswordAuthenticationToken who, int expected) throws Exception {
        return mvc.perform(multipart(BASE + "/import/" + type)
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", content))
                        .param("dryRun", String.valueOf(dryRun)).with(authentication(who)).with(csrf()))
                .andExpect(status().is(expected)).andReturn();
    }

    private String csvImport(String type, String csv, boolean dryRun, int expected) throws Exception {
        return upload(type, "data.csv", csv.getBytes(StandardCharsets.UTF_8), dryRun, ADMIN, expected)
                .getResponse().getContentAsString();
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where code like 'ZT%'", Integer.class);
    }

    @SuppressWarnings("unchecked")
    private static String rowMessage(String json, String path) {
        return ((List<String>) JsonPath.read(json, path)).get(0);
    }

    private static int number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).intValue();
    }

    private static final String AREAS = """
            code,name,area_type
            ZT-INB,Test inbound,INBOUND
            ZT-STO,Test storage,STORAGE
            """;

    private void loadBase() throws Exception {
        csvImport("areas", AREAS, false, 200);
        csvImport("location-types", "code,name,is_storage\nZT-BIN,Test bin,TRUE\nZT-POS,Test position,FALSE\n", false, 200);
    }

    // ---- tests -----------------------------------------------------------------------------------------

    @Test
    void dryRunChangesNothingAndWritesNoAudit() throws Exception {
        String result = csvImport("areas", AREAS, true, 200);
        assertThat(number(result, "$.created")).isEqualTo(2);
        assertThat((Boolean) JsonPath.read(result, "$.applied")).isFalse();
        assertThat(count("area")).isZero();
        verify(audit, never()).record(any());
    }

    @Test
    void applyCreatesThenSameFileIsUnchangedThenChangeUpdates() throws Exception {
        String applied = csvImport("areas", AREAS, false, 200);
        assertThat((Boolean) JsonPath.read(applied, "$.applied")).isTrue();
        assertThat(count("area")).isEqualTo(2);
        verify(audit, times(2)).record(argThat((AuditEvent e) -> "CREATE".equals(e.action()) && "area".equals(e.entityType())));
        verify(audit).record(argThat((AuditEvent e) -> "IMPORT".equals(e.action()) && e.detail().contains("2 created")));

        long versionBefore = jdbc.queryForObject("select version from area where code = 'ZT-INB'", Long.class);
        reset(audit);
        String again = csvImport("areas", AREAS, false, 200);
        assertThat(number(again, "$.unchanged")).isEqualTo(2);
        assertThat(number(again, "$.created") + number(again, "$.updated")).isZero();
        assertThat(jdbc.queryForObject("select version from area where code = 'ZT-INB'", Long.class))
                .isEqualTo(versionBefore); // re-importing an unchanged file does not touch the records

        String changed = csvImport("areas", AREAS.replace("Test inbound", "Goods in"), false, 200);
        assertThat(number(changed, "$.updated")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from area where code = 'ZT-INB'", String.class)).isEqualTo("Goods in");
        // a column that is left out keeps its value
        csvImport("location-types", "code,name,is_storage,capacity_hu\nZT-BIN,Bin,TRUE,5\n", false, 200);
        csvImport("location-types", "code,name\nZT-BIN,Bin renamed\n", false, 200);
        assertThat(jdbc.queryForObject("select capacity_hu from location_type where code = 'ZT-BIN'", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("select name from location_type where code = 'ZT-BIN'", String.class))
                .isEqualTo("Bin renamed");
    }

    @Test
    void oneBadRowShowsTheErrorAndApplyWritesNothing() throws Exception {
        loadBase();
        String file = """
                code,area_code,location_type_code,x_mm
                ZT-L1,ZT-STO,ZT-BIN,1000
                ZT-L2,ZT-NOWHERE,ZT-BIN,2000
                ZT-L3,ZT-STO,ZT-BIN,abc
                ZT-L4,ZT-STO,ZT-BIN,4000
                """;
        String dry = csvImport("locations", file, true, 200);
        assertThat(number(dry, "$.errors")).isEqualTo(2);
        assertThat(number(dry, "$.created")).isEqualTo(2);
        assertThat(rowMessage(dry, "$.rows[?(@.row == 3)].message"))
                .isEqualTo("area_code: unknown area 'ZT-NOWHERE'");
        assertThat(rowMessage(dry, "$.rows[?(@.row == 4)].message"))
                .isEqualTo("x_mm: 'abc' is not a whole number");

        reset(audit);
        String refused = csvImport("locations", file, false, 422);
        assertThat((Boolean) JsonPath.read(refused, "$.applied")).isFalse();
        assertThat(count("location")).isZero(); // not even the two good rows
        verify(audit, never()).record(any());
    }

    @Test
    void fileProblemsAreReportedBeforeAnyRow() throws Exception {
        String unknown = csvImport("areas", "code,name,area_type,colour\nZT-A,x,STORAGE,red\n", true, 200);
        assertThat((String) JsonPath.read(unknown, "$.fileErrors[0]")).startsWith("Unknown column 'colour'");
        String missing = csvImport("areas", "code,name\nZT-A,x\n", true, 200);
        assertThat((String) JsonPath.read(missing, "$.fileErrors[0]")).isEqualTo("Required column 'area_type' is missing.");
        // a file that cannot be used at all is a file-level message, not an error page
        String pdf = upload("areas", "data.pdf", "x".getBytes(), true, ADMIN, 200).getResponse().getContentAsString();
        assertThat((String) JsonPath.read(pdf, "$.fileErrors[0]")).isEqualTo("Use a .csv or .xlsx file.");
        String empty = upload("areas", "data.csv", new byte[0], true, ADMIN, 200).getResponse().getContentAsString();
        assertThat((String) JsonPath.read(empty, "$.fileErrors[0]")).isEqualTo("The file is empty.");
        // applying such a file is refused
        upload("areas", "data.pdf", "x".getBytes(), false, ADMIN, 422);
    }

    @Test
    void mixedSeparatorsGiveOneClearFileMessage() throws Exception {
        String tabHeaderCommaRows = "code\tname\tarea_type\nZT-A,Alpha,STORAGE\nZT-B,Beta,STORAGE\n";
        String r1 = csvImport("areas", tabHeaderCommaRows, true, 200);
        assertThat((List<String>) JsonPath.read(r1, "$.fileErrors")).containsExactly(
                "The header row uses TAB but row 2 uses comma - use one separator in the whole file.");
        assertThat((List<?>) JsonPath.read(r1, "$.rows")).isEmpty(); // no per-row errors
        assertThat(number(r1, "$.errors")).isEqualTo(1);

        String commaHeaderSemicolonRows = "code,name,area_type\nZT-A;Alpha;STORAGE\n";
        String r2 = csvImport("areas", commaHeaderSemicolonRows, true, 200);
        assertThat((List<String>) JsonPath.read(r2, "$.fileErrors")).containsExactly(
                "The header row uses comma but row 2 uses semicolon - use one separator in the whole file.");

        // applying is refused and nothing is written
        csvImport("areas", tabHeaderCommaRows, false, 422);
        assertThat(count("area")).isZero();

        // consistent files of every kind still work
        assertThat(number(csvImport("areas", "code\tname\tarea_type\nZT-T\tTab\tSTORAGE\n", true, 200), "$.created")).isEqualTo(1);
        assertThat(number(csvImport("areas", "code;name;area_type\nZT-S;Semi;STORAGE\n", true, 200), "$.created")).isEqualTo(1);
        // commas inside quotes are not a second separator
        assertThat(number(csvImport("areas", "code,name,area_type\nZT-Q,\"Hall 1, north\",STORAGE\n", true, 200), "$.created")).isEqualTo(1);
    }

    @Test
    void theTemplateUsesCommasAndAUtf8ByteOrderMark() throws Exception {
        byte[] bytes = mvc.perform(get(BASE + "/import/areas/template").with(authentication(VIEWER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF); // UTF-8 BOM so Excel shows special characters
        String text = new String(bytes, StandardCharsets.UTF_8).substring(1);
        assertThat(text.lines().findFirst().orElse("")).isEqualTo("code,name,area_type");
        assertThat(text).doesNotContain("\t").doesNotContain(";");
    }

    @Test
    void duplicateRowInFileAndFieldRulesAreChecked() throws Exception {
        String dup = csvImport("areas", "code,name,area_type\nZT-A,x,STORAGE\nzt-a,y,STORAGE\nZT-B,y,MOON\n,y,STORAGE\n",
                true, 200);
        assertThat(number(dup, "$.errors")).isEqualTo(3);
        assertThat(rowMessage(dup, "$.rows[?(@.row == 3)].message"))
                .isEqualTo("the same record appears twice in this file");
        assertThat(rowMessage(dup, "$.rows[?(@.row == 4)].message")).contains("areaType");
        assertThat(rowMessage(dup, "$.rows[?(@.row == 5)].message")).isEqualTo("code: is required");
    }

    @Test
    void semicolonCsvWithBomAndXlsxBothWork() throws Exception {
        byte[] semicolon = ("﻿Code;Name;Area Type\r\nZT-A;Alpha;STORAGE\r\n").getBytes(StandardCharsets.UTF_8);
        String r1 = upload("areas", "excel.csv", semicolon, false, ADMIN, 200).getResponse().getContentAsString();
        assertThat(number(r1, "$.created")).isEqualTo(1);

        byte[] xlsx = TableWriter.write(true, "areas", List.of("code", "name", "area_type"),
                List.of(List.of("ZT-B", "Beta", "BUFFER"), List.of("ZT-C", "Gamma", "OUTBOUND")));
        String r2 = upload("areas", "areas.xlsx", xlsx, false, ADMIN, 200).getResponse().getContentAsString();
        assertThat(number(r2, "$.created")).isEqualTo(2);
        assertThat(count("area")).isEqualTo(3);
    }

    @Test
    void exportAndTemplateRoundTrip() throws Exception {
        csvImport("areas", AREAS, false, 200);
        byte[] exported = mvc.perform(get(BASE + "/export/areas").param("format", "xlsx").with(authentication(VIEWER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        // exporting and importing again changes nothing
        String back = upload("areas", "areas.xlsx", exported, false, ADMIN, 200).getResponse().getContentAsString();
        assertThat(number(back, "$.errors")).isZero();
        assertThat(number(back, "$.created") + number(back, "$.updated")).isZero();

        String template = mvc.perform(get(BASE + "/import/locations/template").with(authentication(VIEWER)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(template).contains("code,name,area_code,location_type_code,x_mm");
        assertThat(template.lines().count()).isEqualTo(2); // header + one example row
    }

    @Test
    void csvExportDefusesFormulas() {
        assertThat(TableWriter.safeForSpreadsheet("=1+1")).isEqualTo("'=1+1");
        assertThat(TableWriter.safeForSpreadsheet("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(TableWriter.safeForSpreadsheet("-cmd")).isEqualTo("'-cmd");
        assertThat(TableWriter.safeForSpreadsheet("-1500")).isEqualTo("-1500"); // a negative coordinate is fine
        assertThat(TableWriter.safeForSpreadsheet("A-01")).isEqualTo("A-01");
    }

    @Test
    void fullChainInImportOrder() throws Exception {
        loadBase();
        csvImport("hu-types", "code,name,is_container\nZT-CARTON,Carton,FALSE\nZT-PALLET,Pallet,TRUE\n", false, 200);
        csvImport("locations", "code,area_code,location_type_code,level_no\nZT-L1,ZT-INB,ZT-POS,0\nZT-L2,ZT-STO,ZT-BIN,1\n",
                false, 200);
        String links = csvImport("location-links",
                "from_location_code,to_location_code,hu_type_code,cost\nZT-L1,ZT-L2,,2\nZT-L1,ZT-L2,ZT-PALLET,1\n",
                false, 200);
        assertThat(number(links, "$.created")).isEqualTo(2);
        csvImport("equipment", "code,name,equipment_type_code\nZT-HOIST,Hoist,hoist\nZT-CHILD,Child,SHUTTLE\n", false, 200);
        csvImport("equipment", "code,name,equipment_type_code,parent_code\nZT-CHILD,Child,SHUTTLE,ZT-HOIST\n", false, 200);

        String eqLoc = csvImport("equipment-locations", "equipment_code,location_code\nZT-HOIST,ZT-L1\nZT-HOIST,ZT-L2\n",
                false, 200);
        assertThat(number(eqLoc, "$.added")).isEqualTo(2);
        assertThat(number(csvImport("equipment-locations", "equipment_code,location_code\nZT-HOIST,ZT-L1\n", false, 200),
                "$.unchanged")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from equipment_location el join equipment e on e.id = el.equipment_id "
                + "where e.code = 'ZT-HOIST'", Integer.class)).isEqualTo(2);

        csvImport("location-type-hu-types", "location_type_code,hu_type_code\nZT-BIN,ZT-PALLET\n", false, 200);
        csvImport("hu-type-nesting", "inner_hu_type_code,outer_hu_type_code,max_qty\nZT-CARTON,ZT-PALLET,40\n", false, 200);
        String loop = csvImport("hu-type-nesting", "inner_hu_type_code,outer_hu_type_code\nZT-PALLET,ZT-CARTON\n", false, 422);
        assertThat((String) JsonPath.read(loop, "$.rows[0].message")).contains("placedIn");
        assertThat(jdbc.queryForObject("select count(*) from hu_type_nesting n join hu_type h on h.id = n.inner_hu_type_id "
                + "where h.code like 'ZT%'", Integer.class)).isEqualTo(1); // only our own test rows

        // unknown equipment type is a row error
        String badType = csvImport("equipment", "code,name,equipment_type_code\nZT-X,X,SPACESHIP\n", true, 200);
        assertThat((String) JsonPath.read(badType, "$.rows[0].message")).contains("unknown equipment type");
    }

    @Test
    void onlyEditorsCanImport() throws Exception {
        upload("areas", "data.csv", AREAS.getBytes(), true, VIEWER, 403);
        mvc.perform(get(BASE + "/import/types").with(authentication(VIEWER))).andExpect(status().isOk());
        mvc.perform(get(BASE + "/export/nothing").with(authentication(VIEWER))).andExpect(status().isNotFound());
    }
}
