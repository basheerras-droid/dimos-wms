package com.dimos.wms.masterdata;

import static com.dimos.wms.masterdata.ImportDefinition.opt;
import static com.dimos.wms.masterdata.ImportDefinition.req;

import com.dimos.wms.masterdata.ImportRun.Status;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The importable tables, in the order they must be loaded: areas, location types, HU types, locations, links,
 * equipment, then the three assignment tables. A row whose code already exists updates that record, otherwise it
 * creates one. A column that is left out of the file keeps the value of existing records.
 */
@Component
class MasterDataImporters {

    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final AreaRepository areas;
    private final LocationTypeRepository locationTypes;
    private final HuTypeRepository huTypes;
    private final LocationRepository locations;
    private final LocationLinkRepository links;
    private final EquipmentRepository equipment;
    private final EquipmentTypeRepository equipmentTypes;
    private final AreaService areaService;
    private final LocationTypeService locationTypeService;
    private final HuTypeService huTypeService;
    private final LocationService locationService;
    private final LocationLinkService linkService;
    private final EquipmentService equipmentService;
    private final AssignmentService assignments;

    private final List<ImportDefinition> definitions;

    MasterDataImporters(Validator validator, JdbcTemplate jdbc, AreaRepository areas,
                        LocationTypeRepository locationTypes, HuTypeRepository huTypes, LocationRepository locations,
                        LocationLinkRepository links, EquipmentRepository equipment,
                        EquipmentTypeRepository equipmentTypes, AreaService areaService,
                        LocationTypeService locationTypeService, HuTypeService huTypeService,
                        LocationService locationService, LocationLinkService linkService,
                        EquipmentService equipmentService, AssignmentService assignments) {
        this.validator = validator;
        this.jdbc = jdbc;
        this.areas = areas;
        this.locationTypes = locationTypes;
        this.huTypes = huTypes;
        this.locations = locations;
        this.links = links;
        this.equipment = equipment;
        this.equipmentTypes = equipmentTypes;
        this.areaService = areaService;
        this.locationTypeService = locationTypeService;
        this.huTypeService = huTypeService;
        this.locationService = locationService;
        this.linkService = linkService;
        this.equipmentService = equipmentService;
        this.assignments = assignments;
        this.definitions = List.of(areaDefinition(), locationTypeDefinition(), huTypeDefinition(),
                locationDefinition(), linkDefinition(), equipmentDefinition(), equipmentLocationDefinition(),
                locationTypeHuTypeDefinition(), nestingDefinition());
    }

    /** In import order. */
    List<ImportDefinition> all() {
        return definitions;
    }

    ImportDefinition get(String key) {
        return definitions.stream().filter(d -> d.key().equals(key)).findFirst()
                .orElseThrow(() -> MasterDataException.notFound("import type '" + key + "'"));
    }

    // ---- areas ------------------------------------------------------------------------------------------

    private ImportDefinition areaDefinition() {
        return new ImportDefinition("areas", "Areas", List.of(
                req("code", "INB", "Unique code of the area"),
                req("name", "Inbound", "Name shown to users"),
                req("area_type", "INBOUND", "INBOUND, STORAGE, OUTBOUND, BUFFER, WORKSTATION or TRANSPORT")),
                (table, run) -> each(table, run, l -> {
                    String code = codeOf(l);
                    Area ex = run.find(areas, code).orElse(null);
                    var request = valid(new AreaService.Request(code, l.str("name", of(ex, a -> a.name)),
                            upper(l.str("area_type", of(ex, a -> a.areaType))), of(ex, a -> a.version)));
                    return save(ex, areaService, ex == null ? null : ex.id, request, run);
                }),
                site -> rows(areas, site, a -> List.of(a.code, a.name, a.areaType)));
    }

    // ---- location types ---------------------------------------------------------------------------------

    private ImportDefinition locationTypeDefinition() {
        return new ImportDefinition("location-types", "Location types", List.of(
                req("code", "RACK_BIN", "Unique code"),
                req("name", "Rack bin", "Name shown to users"),
                opt("is_storage", "TRUE", "TRUE if handling units are stored here"),
                opt("is_transfer", "FALSE", "TRUE for transfer / hand-over positions"),
                opt("is_workstation", "FALSE", "TRUE for work stations"),
                opt("capacity_hu", "1", "How many handling units fit (default 1)")),
                (table, run) -> each(table, run, l -> {
                    String code = codeOf(l);
                    LocationType ex = run.find(locationTypes, code).orElse(null);
                    var request = valid(new LocationTypeService.Request(code, l.str("name", of(ex, t -> t.name)),
                            l.bool("is_storage", of(ex, t -> t.storage)), l.bool("is_transfer", of(ex, t -> t.transfer)),
                            l.bool("is_workstation", of(ex, t -> t.workstation)),
                            l.integer("capacity_hu", of(ex, t -> t.capacityHu)), of(ex, t -> t.version)));
                    return save(ex, locationTypeService, ex == null ? null : ex.id, request, run);
                }),
                site -> rows(locationTypes, site, t -> List.of(t.code, t.name, yn(t.storage), yn(t.transfer),
                        yn(t.workstation), String.valueOf(t.capacityHu))));
    }

    // ---- handling-unit types ----------------------------------------------------------------------------

    private ImportDefinition huTypeDefinition() {
        return new ImportDefinition("hu-types", "Handling-unit types", List.of(
                req("code", "PALLET", "Unique code"),
                req("name", "Euro pallet", "Name shown to users"),
                opt("length_mm", "1200", "Length in mm"),
                opt("width_mm", "800", "Width in mm"),
                opt("height_mm", "144", "Height in mm"),
                opt("tare_kg", "25", "Empty weight in kg"),
                opt("max_gross_kg", "1500", "Maximum total weight in kg"),
                opt("is_container", "TRUE", "TRUE if other handling units can be placed on/in it")),
                (table, run) -> each(table, run, l -> {
                    String code = codeOf(l);
                    HuType ex = run.find(huTypes, code).orElse(null);
                    var request = valid(new HuTypeService.Request(code, l.str("name", of(ex, h -> h.name)),
                            l.integer("length_mm", of(ex, h -> h.lengthMm)), l.integer("width_mm", of(ex, h -> h.widthMm)),
                            l.integer("height_mm", of(ex, h -> h.heightMm)), l.decimal("tare_kg", of(ex, h -> h.tareKg)),
                            l.decimal("max_gross_kg", of(ex, h -> h.maxGrossKg)),
                            l.bool("is_container", of(ex, h -> h.container)), of(ex, h -> h.version)));
                    return save(ex, huTypeService, ex == null ? null : ex.id, request, run);
                }),
                site -> rows(huTypes, site, h -> List.of(h.code, h.name, s(h.lengthMm), s(h.widthMm), s(h.heightMm),
                        s(h.tareKg), s(h.maxGrossKg), yn(h.container))));
    }

    // ---- locations --------------------------------------------------------------------------------------

    private ImportDefinition locationDefinition() {
        return new ImportDefinition("locations", "Locations", List.of(
                req("code", "A-01-02-03", "Unique code of the location"),
                opt("name", "", "Optional name"),
                req("area_code", "STO", "Code of an existing area"),
                req("location_type_code", "RACK_BIN", "Code of an existing location type"),
                opt("x_mm", "12000", "X position in mm (for the layout view)"),
                opt("y_mm", "3400", "Y position in mm"),
                opt("z_mm", "0", "Z (height) in mm"),
                opt("level_no", "1", "Level / floor number (default 0)"),
                opt("aisle", "A", "Aisle"),
                opt("side", "L", "Side of the aisle"),
                opt("column_no", "2", "Column number in the rack"),
                opt("row_no", "3", "Row (level) number in the rack"),
                opt("max_weight_kg", "1000", "Maximum load in kg"),
                opt("enabled", "TRUE", "FALSE = location is switched off (default TRUE)"),
                opt("blocked_in", "FALSE", "TRUE = nothing may be put in"),
                opt("blocked_out", "FALSE", "TRUE = nothing may be taken out"),
                opt("block_reason", "", "Required when blocked_in or blocked_out is TRUE"),
                opt("external_ref", "", "Address of this location in the PLC / host system"),
                opt("ext_attributes", "", "Project-specific extra fields as JSON text")),
                (table, run) -> each(table, run, l -> {
                    String code = codeOf(l);
                    Location ex = run.find(locations, code).orElse(null);
                    Area area = run.require(areas, l.str("area_code"), "area_code", "area");
                    LocationType type = run.require(locationTypes, l.str("location_type_code"),
                            "location_type_code", "location type");
                    var request = valid(new LocationService.Request(code, l.str("name", of(ex, x -> x.name)),
                            area.id, type.id, l.integer("x_mm", of(ex, x -> x.xMm)),
                            l.integer("y_mm", of(ex, x -> x.yMm)), l.integer("z_mm", of(ex, x -> x.zMm)),
                            l.integer("level_no", of(ex, x -> x.levelNo)), l.str("aisle", of(ex, x -> x.aisle)),
                            l.str("side", of(ex, x -> x.side)), l.integer("column_no", of(ex, x -> x.columnNo)),
                            l.integer("row_no", of(ex, x -> x.rowNo)),
                            l.decimal("max_weight_kg", of(ex, x -> x.maxWeightKg)),
                            l.bool("enabled", of(ex, x -> x.enabled)), l.bool("blocked_in", of(ex, x -> x.blockedIn)),
                            l.bool("blocked_out", of(ex, x -> x.blockedOut)),
                            l.str("block_reason", of(ex, x -> x.blockReason)),
                            l.str("external_ref", of(ex, x -> x.externalRef)),
                            l.str("ext_attributes", of(ex, x -> x.extAttributes)), of(ex, x -> x.version)));
                    return save(ex, locationService, ex == null ? null : ex.id, request, run);
                }),
                site -> rows(locations, site, x -> List.of(x.code, s(x.name), x.area.code, x.locationType.code,
                        s(x.xMm), s(x.yMm), s(x.zMm), String.valueOf(x.levelNo), s(x.aisle), s(x.side),
                        s(x.columnNo), s(x.rowNo), s(x.maxWeightKg), yn(x.enabled), yn(x.blockedIn),
                        yn(x.blockedOut), s(x.blockReason), s(x.externalRef), s(x.extAttributes))));
    }

    // ---- location links ---------------------------------------------------------------------------------

    private ImportDefinition linkDefinition() {
        return new ImportDefinition("location-links", "Location links", List.of(
                req("from_location_code", "IN-01", "Code of the location the unit moves from"),
                req("to_location_code", "IN-02", "Code of the location the unit moves to"),
                opt("hu_type_code", "", "Empty = valid for all handling-unit types"),
                opt("equipment_code", "", "Equipment that performs the move (optional)"),
                opt("cost", "1", "Routing cost (default 1)"),
                opt("enabled", "TRUE", "FALSE = link switched off (default TRUE)")),
                (table, run) -> each(table, run, l -> {
                    Location from = run.require(locations, l.str("from_location_code"), "from_location_code", "location");
                    Location to = run.require(locations, l.str("to_location_code"), "to_location_code", "location");
                    HuType hu = run.optional(huTypes, l.str("hu_type_code"), "hu_type_code", "handling-unit type");
                    Equipment eq = run.optional(equipment, l.str("equipment_code"), "equipment_code", "equipment");
                    var spec = Specs.<LocationLink>bySite(run.siteId())
                            .and(LocationLinkService.refEquals("fromLocation", from.id))
                            .and(LocationLinkService.refEquals("toLocation", to.id))
                            .and(LocationLinkService.refEquals("huType", hu == null ? null : hu.id))
                            .and(LocationLinkService.refEquals("equipment", eq == null ? null : eq.id));
                    LocationLink ex = links.findOne(spec).orElse(null);
                    var request = valid(new LocationLinkService.Request(from.id, to.id, hu == null ? null : hu.id,
                            eq == null ? null : eq.id, l.integer("cost", of(ex, x -> x.cost)),
                            l.bool("enabled", of(ex, x -> x.enabled)), of(ex, x -> x.version)));
                    return save(ex, linkService, ex == null ? null : ex.id, request, run);
                }, l -> String.valueOf(l.str("from_location_code")) + " -> " + l.str("to_location_code")
                        + (l.str("hu_type_code") == null ? "" : " [" + l.str("hu_type_code") + "]")
                        + (l.str("equipment_code") == null ? "" : " via " + l.str("equipment_code"))),
                site -> rows(links, site, x -> List.of(x.fromLocation.code, x.toLocation.code,
                        x.huType == null ? "" : x.huType.code, x.equipment == null ? "" : x.equipment.code,
                        String.valueOf(x.cost), yn(x.enabled)), "id"));
    }

    // ---- equipment --------------------------------------------------------------------------------------

    private ImportDefinition equipmentDefinition() {
        return new ImportDefinition("equipment", "Equipment", List.of(
                req("code", "HOIST-1", "Unique code"),
                req("name", "Hoist 1", "Name shown to users"),
                req("equipment_type_code", "HOIST", "Code of an equipment type, e.g. CONVEYOR, HOIST, AGV"),
                opt("area_code", "", "Code of an existing area"),
                opt("parent_code", "", "Equipment this belongs to - the parent must be listed earlier in the file"),
                opt("plc_connection", "", "Connection details for the PLC (later phase)"),
                opt("enabled", "TRUE", "FALSE = equipment switched off (default TRUE)"),
                opt("external_ref", "", "Address of this equipment in the PLC / host system"),
                opt("ext_attributes", "", "Project-specific extra fields as JSON text")),
                (table, run) -> {
                    Map<String, Long> typeIds = equipmentTypes.findAll().stream()
                            .collect(Collectors.toMap(t -> t.code, t -> t.id));
                    each(table, run, l -> {
                        String code = codeOf(l);
                        Equipment ex = run.find(equipment, code).orElse(null);
                        String typeCode = upper(l.str("equipment_type_code"));
                        Long typeId = typeCode == null ? null : typeIds.get(typeCode);
                        if (typeId == null) {
                            throw new ImportRowException("equipment_type_code: unknown equipment type '"
                                    + l.str("equipment_type_code") + "'");
                        }
                        Area area = l.has("area_code")
                                ? run.optional(areas, l.str("area_code"), "area_code", "area")
                                : of(ex, x -> x.area);
                        Equipment parent = l.has("parent_code")
                                ? run.optional(equipment, l.str("parent_code"), "parent_code", "equipment")
                                : of(ex, x -> x.parent);
                        var request = valid(new EquipmentService.Request(code, l.str("name", of(ex, x -> x.name)),
                                typeId, area == null ? null : area.id, parent == null ? null : parent.id,
                                l.str("plc_connection", of(ex, x -> x.plcConnection)),
                                l.bool("enabled", of(ex, x -> x.enabled)),
                                l.str("external_ref", of(ex, x -> x.externalRef)),
                                l.str("ext_attributes", of(ex, x -> x.extAttributes)), of(ex, x -> x.version)));
                        return save(ex, equipmentService, ex == null ? null : ex.id, request, run);
                    });
                },
                site -> rows(equipment, site, x -> List.of(x.code, x.name, x.equipmentType.code,
                        x.area == null ? "" : x.area.code, x.parent == null ? "" : x.parent.code,
                        s(x.plcConnection), yn(x.enabled), s(x.externalRef), s(x.extAttributes))));
    }

    // ---- assignments ------------------------------------------------------------------------------------

    private ImportDefinition equipmentLocationDefinition() {
        return new ImportDefinition("equipment-locations", "Equipment - locations", List.of(
                req("equipment_code", "HOIST-1", "Code of an existing piece of equipment"),
                req("location_code", "H1-L1", "Code of an existing location that belongs to it")),
                (table, run) -> {
                    Map<Long, Set<Long>> current = new HashMap<>();
                    Map<Long, Set<Long>> added = new LinkedHashMap<>();
                    Map<Long, Integer> firstRow = new HashMap<>();
                    each(table, run, l -> {
                        Equipment e = run.require(equipment, l.str("equipment_code"), "equipment_code", "equipment");
                        Location loc = run.require(locations, l.str("location_code"), "location_code", "location");
                        Set<Long> cur = current.computeIfAbsent(e.id,
                                id -> new HashSet<>(assignments.locationsOfEquipment(id, run.who).ids()));
                        firstRow.putIfAbsent(e.id, l.number());
                        if (cur.contains(loc.id)) {
                            return Status.UNCHANGED;
                        }
                        added.computeIfAbsent(e.id, id -> new LinkedHashSet<>()).add(loc.id);
                        return Status.ADD;
                    }, l -> l.str("equipment_code") + " / " + l.str("location_code"));
                    added.forEach((equipmentId, extra) -> {
                        Set<Long> merged = new LinkedHashSet<>(current.get(equipmentId));
                        merged.addAll(extra);
                        try {
                            assignments.setLocationsOfEquipment(equipmentId,
                                    new AssignmentService.LocationIds(new ArrayList<>(merged), null), run.who);
                        } catch (MasterDataException ex) {
                            run.fail(firstRow.get(equipmentId), message(ex));
                        }
                    });
                },
                site -> jdbc.query("select e.code, l.code from equipment_location el "
                        + "join equipment e on e.id = el.equipment_id join location l on l.id = el.location_id "
                        + "where e.site_id = ? order by e.code, l.code",
                        (rs, i) -> List.of(rs.getString(1), rs.getString(2)), site));
    }

    private ImportDefinition locationTypeHuTypeDefinition() {
        return new ImportDefinition("location-type-hu-types", "Location type - handling-unit types", List.of(
                req("location_type_code", "RACK_BIN", "Code of an existing location type"),
                req("hu_type_code", "PALLET", "Code of a handling-unit type that may be stored on this location type")),
                (table, run) -> {
                    Map<Long, Set<Long>> current = new HashMap<>();
                    Map<Long, Set<Long>> added = new LinkedHashMap<>();
                    Map<Long, Integer> firstRow = new HashMap<>();
                    each(table, run, l -> {
                        LocationType t = run.require(locationTypes, l.str("location_type_code"),
                                "location_type_code", "location type");
                        HuType h = run.require(huTypes, l.str("hu_type_code"), "hu_type_code", "handling-unit type");
                        Set<Long> cur = current.computeIfAbsent(t.id,
                                id -> new HashSet<>(assignments.huTypesOfLocationType(id, run.who).ids()));
                        firstRow.putIfAbsent(t.id, l.number());
                        if (cur.contains(h.id)) {
                            return Status.UNCHANGED;
                        }
                        added.computeIfAbsent(t.id, id -> new LinkedHashSet<>()).add(h.id);
                        return Status.ADD;
                    }, l -> l.str("location_type_code") + " / " + l.str("hu_type_code"));
                    added.forEach((typeId, extra) -> {
                        Set<Long> merged = new LinkedHashSet<>(current.get(typeId));
                        merged.addAll(extra);
                        try {
                            assignments.setHuTypesOfLocationType(typeId,
                                    new AssignmentService.HuTypeIds(new ArrayList<>(merged), null), run.who);
                        } catch (MasterDataException ex) {
                            run.fail(firstRow.get(typeId), message(ex));
                        }
                    });
                },
                site -> jdbc.query("select lt.code, h.code from location_type_hu_type x "
                        + "join location_type lt on lt.id = x.location_type_id join hu_type h on h.id = x.hu_type_id "
                        + "where lt.site_id = ? order by lt.code, h.code",
                        (rs, i) -> List.of(rs.getString(1), rs.getString(2)), site));
    }

    private ImportDefinition nestingDefinition() {
        return new ImportDefinition("hu-type-nesting", "Handling-unit nesting", List.of(
                req("inner_hu_type_code", "CARTON", "The handling-unit type that is placed in / on another"),
                req("outer_hu_type_code", "PALLET", "The handling-unit type that carries it"),
                opt("max_qty", "40", "Largest number of inner units on one outer unit (empty = no limit)")),
                (table, run) -> {
                    Map<Long, Map<Long, Integer>> current = new HashMap<>();
                    Map<Long, Map<Long, Integer>> wanted = new LinkedHashMap<>();
                    Map<Long, Integer> firstRow = new HashMap<>();
                    each(table, run, l -> {
                        HuType inner = run.require(huTypes, l.str("inner_hu_type_code"), "inner_hu_type_code",
                                "handling-unit type");
                        HuType outer = run.require(huTypes, l.str("outer_hu_type_code"), "outer_hu_type_code",
                                "handling-unit type");
                        Integer maxQty = l.integer("max_qty", null);
                        Map<Long, Integer> cur = current.computeIfAbsent(inner.id, id -> {
                            Map<Long, Integer> m = new LinkedHashMap<>();
                            assignments.placedIn(id, run.who).forEach(n -> m.put(n.outerHuTypeId(), n.maxQty()));
                            return m;
                        });
                        firstRow.putIfAbsent(inner.id, l.number());
                        boolean known = cur.containsKey(outer.id);
                        if (known && Objects.equals(cur.get(outer.id), maxQty)) {
                            return Status.UNCHANGED;
                        }
                        wanted.computeIfAbsent(inner.id, id -> new LinkedHashMap<>()).put(outer.id, maxQty);
                        return known ? Status.UPDATE : Status.ADD;
                    }, l -> l.str("inner_hu_type_code") + " in " + l.str("outer_hu_type_code"));
                    wanted.forEach((innerId, changes) -> {
                        Map<Long, Integer> merged = new LinkedHashMap<>(current.get(innerId));
                        merged.putAll(changes);
                        List<AssignmentService.Nesting> list = merged.entrySet().stream()
                                .map(e -> new AssignmentService.Nesting(e.getKey(), e.getValue())).toList();
                        try {
                            assignments.setPlacedIn(innerId, new AssignmentService.NestingList(list, null), run.who);
                        } catch (MasterDataException ex) {
                            run.fail(firstRow.get(innerId), message(ex));
                        }
                    });
                },
                site -> jdbc.query("select i.code, o.code, n.max_qty from hu_type_nesting n "
                        + "join hu_type i on i.id = n.inner_hu_type_id join hu_type o on o.id = n.outer_hu_type_id "
                        + "where i.site_id = ? order by i.code, o.code",
                        (rs, i) -> List.of(rs.getString(1), rs.getString(2),
                                rs.getObject(3) == null ? "" : String.valueOf(rs.getInt(3))), site));
    }

    // ---- shared helpers ---------------------------------------------------------------------------------

    private static void each(Table table, ImportRun run, Function<Table.Line, Status> action) {
        each(table, run, action, l -> {
            String code = l.str("code");
            return code == null ? "row " + l.number() : code.toUpperCase();
        });
    }

    private static void each(Table table, ImportRun run, Function<Table.Line, Status> action,
                             Function<Table.Line, String> key) {
        for (Table.Line line : table.lines()) {
            run.row(line.number(), key.apply(line), () -> action.apply(line));
        }
    }

    private static String codeOf(Table.Line l) {
        String code = l.str("code");
        if (code == null) {
            throw new ImportRowException("code: is required");
        }
        return code.toUpperCase();
    }

    private static String upper(String s) {
        return s == null ? null : s.toUpperCase();
    }

    private static <E, T> T of(E existing, Function<E, T> getter) {
        return existing == null ? null : getter.apply(existing);
    }

    /** Creates (no existing record) or updates through the normal service; reports CREATE / UPDATE / UNCHANGED. */
    private static <E extends SiteEntity, Q extends VersionedRequest, R> Status save(
            E existing, MasterDataService<E, Q, R> service, Long id, Q request, ImportRun run) {
        if (existing == null) {
            service.create(request, run.who);
            return Status.CREATE;
        }
        R before = service.get(id, run.who);
        R after = service.update(id, request, run.who);
        return before.equals(after) ? Status.UNCHANGED : Status.UPDATE;
    }

    /** Applies the same field rules as the web forms (the services themselves only check business rules). */
    private <T> T valid(T request) {
        Set<ConstraintViolation<T>> problems = validator.validate(request);
        if (!problems.isEmpty()) {
            throw new ImportRowException(problems.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage()).sorted().collect(Collectors.joining("; ")));
        }
        return request;
    }

    private static String message(MasterDataException ex) {
        return ex.details.isEmpty() ? ex.getMessage() : String.join("; ", ex.details);
    }

    private <E extends SiteEntity> List<List<String>> rows(SiteRepository<E> repository, Long site,
                                                           Function<E, List<String>> mapper) {
        return rows(repository, site, mapper, "code");
    }

    private <E extends SiteEntity> List<List<String>> rows(SiteRepository<E> repository, Long site,
                                                           Function<E, List<String>> mapper, String sortBy) {
        return repository.findAll(Specs.<E>bySite(site), Sort.by(sortBy)).stream().map(mapper).toList();
    }

    private static String s(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String yn(boolean value) {
        return value ? "TRUE" : "FALSE";
    }
}
