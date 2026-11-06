package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The three "which belongs to which" tables: allowed handling-unit types per location type, locations per
 * piece of equipment, and which handling-unit type may be placed in which (nesting). Each save replaces the
 * whole list of the owning record, bumps the owner's version and is audited.
 */
@Service
class AssignmentService {

    record HuTypeIds(@NotNull @Size(max = 1000) List<Long> huTypeIds, Long version) {
    }

    record LocationIds(@NotNull @Size(max = 20000) List<Long> locationIds, Long version) {
    }

    record Nesting(@NotNull Long outerHuTypeId, @Min(1) Integer maxQty) {
    }

    record NestingList(@NotNull @Size(max = 1000) List<@Valid Nesting> placedIn, Long version) {
    }

    /** A record in an assignment list, with its code and name so the screen can show it. */
    record Item(Long id, String code, String name) {
    }

    record Ids(List<Long> ids, long version, List<Item> items) {
        static Ids of(List<Item> items, long version) {
            return new Ids(items.stream().map(Item::id).toList(), version, items);
        }
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final LocationTypeRepository locationTypes;
    private final HuTypeRepository huTypes;
    private final EquipmentRepository equipment;
    private final AuditService audit;
    private final JsonMapper json;

    AssignmentService(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, LocationTypeRepository locationTypes,
                      HuTypeRepository huTypes, EquipmentRepository equipment, AuditService audit, JsonMapper json) {
        this.jdbc = jdbc;
        this.named = named;
        this.locationTypes = locationTypes;
        this.huTypes = huTypes;
        this.equipment = equipment;
        this.audit = audit;
        this.json = json;
    }

    // ---- location type -> allowed handling-unit types ---------------------------------------------------

    @Transactional(readOnly = true)
    public Ids huTypesOfLocationType(Long locationTypeId, WmsUserPrincipal who) {
        LocationType t = ownedLocationType(locationTypeId, who);
        return Ids.of(jdbc.query("select h.id, h.code, h.name from location_type_hu_type x "
                + "join hu_type h on h.id = x.hu_type_id where x.location_type_id = ? order by h.code",
                (rs, i) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3)), t.id), t.version);
    }

    @Transactional
    public Ids setHuTypesOfLocationType(Long locationTypeId, HuTypeIds body, WmsUserPrincipal who) {
        LocationType t = ownedLocationType(locationTypeId, who);
        checkVersion(body.version(), t.version);
        List<Long> before = huTypesOfLocationType(locationTypeId, who).ids();
        Set<Long> wanted = distinct(body.huTypeIds());
        requireAllInSite("hu_type", wanted, who.siteId(), "huTypeIds", "unknown handling-unit type");
        jdbc.update("delete from location_type_hu_type where location_type_id = ?", t.id);
        for (Long id : wanted) {
            jdbc.update("insert into location_type_hu_type (location_type_id, hu_type_id) values (?, ?)", t.id, id);
        }
        t.stamp(who.username());
        locationTypes.saveAndFlush(t);
        record("ASSIGN_HU_TYPES", "location_type", t.id, before, new ArrayList<>(wanted), who);
        return huTypesOfLocationType(locationTypeId, who);
    }

    // ---- equipment -> locations -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Ids locationsOfEquipment(Long equipmentId, WmsUserPrincipal who) {
        Equipment e = ownedEquipment(equipmentId, who);
        return Ids.of(jdbc.query("select l.id, l.code, l.name from equipment_location x "
                + "join location l on l.id = x.location_id where x.equipment_id = ? order by l.code",
                (rs, i) -> new Item(rs.getLong(1), rs.getString(2), rs.getString(3)), e.id), e.version);
    }

    @Transactional
    public Ids setLocationsOfEquipment(Long equipmentId, LocationIds body, WmsUserPrincipal who) {
        Equipment e = ownedEquipment(equipmentId, who);
        checkVersion(body.version(), e.version);
        List<Long> before = locationsOfEquipment(equipmentId, who).ids();
        Set<Long> wanted = distinct(body.locationIds());
        requireAllInSite("location", wanted, who.siteId(), "locationIds", "unknown location");
        jdbc.update("delete from equipment_location where equipment_id = ?", e.id);
        jdbc.batchUpdate("insert into equipment_location (equipment_id, location_id) values (?, ?)",
                wanted.stream().map(id -> new Object[] {e.id, id}).toList());
        e.stamp(who.username());
        equipment.saveAndFlush(e);
        record("ASSIGN_LOCATIONS", "equipment", e.id, before, new ArrayList<>(wanted), who);
        return locationsOfEquipment(equipmentId, who);
    }

    // ---- handling-unit type -> types it may be placed in ------------------------------------------------

    @Transactional(readOnly = true)
    public List<Nesting> placedIn(Long huTypeId, WmsUserPrincipal who) {
        HuType h = ownedHuType(huTypeId, who);
        return jdbc.query("select outer_hu_type_id, max_qty from hu_type_nesting where inner_hu_type_id = ? "
                + "order by outer_hu_type_id", (rs, i) -> new Nesting(rs.getLong(1),
                rs.getObject(2) == null ? null : rs.getInt(2)), h.id);
    }

    @Transactional
    public List<Nesting> setPlacedIn(Long huTypeId, NestingList body, WmsUserPrincipal who) {
        HuType h = ownedHuType(huTypeId, who);
        checkVersion(body.version(), h.version);
        List<Nesting> before = placedIn(huTypeId, who);
        Map<Long, Nesting> wanted = new HashMap<>();
        for (Nesting n : body.placedIn()) {
            wanted.put(n.outerHuTypeId(), n);
        }
        if (wanted.containsKey(h.id)) {
            throw MasterDataException.invalid("placedIn", "a handling-unit type cannot be placed in itself");
        }
        requireAllInSite("hu_type", wanted.keySet(), who.siteId(), "placedIn", "unknown handling-unit type");
        checkNoNestingLoop(h.id, wanted.keySet(), who.siteId());
        jdbc.update("delete from hu_type_nesting where inner_hu_type_id = ?", h.id);
        for (Nesting n : wanted.values()) {
            jdbc.update("insert into hu_type_nesting (inner_hu_type_id, outer_hu_type_id, max_qty) values (?, ?, ?)",
                    h.id, n.outerHuTypeId(), n.maxQty());
        }
        h.stamp(who.username());
        huTypes.saveAndFlush(h);
        List<Nesting> after = placedIn(huTypeId, who);
        record("ASSIGN_NESTING", "hu_type", h.id, before, after, who);
        return after;
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    /** A carton in a pallet in a carton would never end: refuse if any new outer type is already inside this one. */
    private void checkNoNestingLoop(Long inner, Set<Long> newOuters, Long siteId) {
        Map<Long, List<Long>> outersOf = new HashMap<>();
        jdbc.query("select n.inner_hu_type_id, n.outer_hu_type_id from hu_type_nesting n "
                + "join hu_type h on h.id = n.inner_hu_type_id where h.site_id = ? and n.inner_hu_type_id <> ?",
                rs -> {
                    outersOf.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
                }, siteId, inner);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> todo = new ArrayDeque<>(newOuters);
        while (!todo.isEmpty()) {
            Long current = todo.pop();
            if (current.equals(inner)) {
                throw MasterDataException.invalid("placedIn", "this would put a handling-unit type inside itself");
            }
            if (seen.add(current)) {
                todo.addAll(outersOf.getOrDefault(current, List.of()));
            }
        }
    }

    private static Set<Long> distinct(List<Long> ids) {
        return new LinkedHashSet<>(ids);
    }

    private void requireAllInSite(String table, Set<Long> ids, Long siteId, String field, String message) {
        if (ids.isEmpty()) {
            return;
        }
        Integer found = named.queryForObject("select count(*) from " + table + " where site_id = :site and id in (:ids)",
                new MapSqlParameterSource("site", siteId).addValue("ids", ids), Integer.class);
        if (found == null || found != ids.size()) {
            throw MasterDataException.invalid(field, message);
        }
    }

    private static void checkVersion(Long sent, long current) {
        if (sent != null && sent != current) {
            throw MasterDataException.stale();
        }
    }

    private LocationType ownedLocationType(Long id, WmsUserPrincipal who) {
        return locationTypes.findById(id).filter(t -> who.siteId().equals(t.siteId))
                .orElseThrow(() -> MasterDataException.notFound("location_type"));
    }

    private Equipment ownedEquipment(Long id, WmsUserPrincipal who) {
        return equipment.findById(id).filter(t -> who.siteId().equals(t.siteId))
                .orElseThrow(() -> MasterDataException.notFound("equipment"));
    }

    private HuType ownedHuType(Long id, WmsUserPrincipal who) {
        return huTypes.findById(id).filter(t -> who.siteId().equals(t.siteId))
                .orElseThrow(() -> MasterDataException.notFound("hu_type"));
    }

    private void record(String action, String entityType, Long id, Object before, Object after, WmsUserPrincipal who) {
        AuditBuffer.record(audit, new AuditEvent(who.username(), action, who.siteId(), entityType, String.valueOf(id),
                limit(json.writeValueAsString(before)), limit(json.writeValueAsString(after)), null,
                MasterDataService.remoteAddr(), null));
    }

    private static String limit(String text) {
        return text.length() > 4000 ? text.substring(0, 3997) + "..." : text;
    }
}
