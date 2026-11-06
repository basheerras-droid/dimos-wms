package com.dimos.wms.masterdata;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Check configuration": looks for the mistakes that most often break a go-live (links to nowhere, storage that
 * nothing can reach, equipment without locations, ...). It reads the whole site with a few plain SQL queries and
 * works in memory, so even 20 000 locations take well under a second. Nothing is changed.
 *
 * Not checked here because the database already guarantees it: a link pointing to a location that does not exist.
 * Reachability follows enabled links between enabled locations and ignores handling-unit types (per-type routing
 * is a Step G topic).
 */
@Service
class ConfigurationValidator {

    static final int MAX_ITEMS_PER_RULE = 200;

    enum Severity { WARNING, ERROR }

    /** One finding. {@code entityType} + {@code entityId} let the screen link to the record. */
    record Item(Severity severity, String entityType, Long entityId, String code, String message) {
    }

    record RuleResult(String code, String title, String status, long count, boolean truncated, List<Item> items) {
    }

    record Report(String status, Instant checkedAt, long durationMs, Map<String, Long> counts,
                  List<RuleResult> rules) {
    }

    private record Loc(long id, String code, long typeId, String areaType, boolean storage, boolean workstation,
                       boolean enabled, Integer x, Integer y, Integer z, int level) {
    }

    private record Link(long id, long from, long to, Long huType, Long equipment, boolean enabled,
                        Boolean equipmentEnabled) {
    }

    private final JdbcTemplate jdbc;

    ConfigurationValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Report check(Long siteId) {
        long started = System.nanoTime();

        Map<Long, Loc> locs = new LinkedHashMap<>();
        jdbc.query("select l.id, l.code, l.location_type_id, a.area_type, t.is_storage, t.is_workstation, l.enabled, "
                + "l.x_mm, l.y_mm, l.z_mm, l.level_no from location l "
                + "join area a on a.id = l.area_id join location_type t on t.id = l.location_type_id "
                + "where l.site_id = ?", rs -> {
            locs.put(rs.getLong(1), new Loc(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4),
                    rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7), intOrNull(rs.getObject(8)),
                    intOrNull(rs.getObject(9)), intOrNull(rs.getObject(10)), rs.getInt(11)));
        }, siteId);

        List<Link> links = jdbc.query("select k.id, k.from_location_id, k.to_location_id, k.hu_type_id, k.equipment_id, "
                + "k.enabled, e.enabled from location_link k left join equipment e on e.id = k.equipment_id "
                + "where k.site_id = ? order by k.id", (rs, i) -> new Link(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                (Long) rs.getObject(4), (Long) rs.getObject(5), rs.getBoolean(6),
                rs.getObject(7) == null ? null : rs.getBoolean(7)), siteId);

        Map<Long, String> huCodes = new HashMap<>();
        jdbc.query("select id, code from hu_type where site_id = ?", rs -> {
            huCodes.put(rs.getLong(1), rs.getString(2));
        }, siteId);

        Rules r = new Rules();

        // ---- links -----------------------------------------------------------------------------------------
        Map<String, List<Link>> byKey = new LinkedHashMap<>();
        Map<String, List<Link>> byPair = new LinkedHashMap<>();
        for (Link k : links) {
            byKey.computeIfAbsent(k.from + "|" + k.to + "|" + k.huType + "|" + k.equipment, x -> new ArrayList<>()).add(k);
            if (k.enabled) {
                byPair.computeIfAbsent(k.from + "|" + k.to, x -> new ArrayList<>()).add(k);
            }
            Loc from = locs.get(k.from);
            Loc to = locs.get(k.to);
            if (k.enabled && ((from != null && !from.enabled) || (to != null && !to.enabled))) {
                String which = from != null && !from.enabled ? from.code : to.code;
                r.add("LINK_DISABLED_LOCATION", Severity.WARNING, "location-link", k.id, label(k, locs),
                        "The link is enabled but location " + which + " is disabled, so it can never be used.");
            }
            if (k.enabled && Boolean.FALSE.equals(k.equipmentEnabled)) {
                r.add("LINK_DISABLED_EQUIPMENT", Severity.WARNING, "location-link", k.id, label(k, locs),
                        "The link is enabled but the equipment that performs it is disabled.");
            }
        }
        for (List<Link> same : byKey.values()) {
            for (int i = 1; i < same.size(); i++) {
                Link dup = same.get(i);
                r.add("LINK_DUPLICATE", Severity.ERROR, "location-link", dup.id, label(dup, locs),
                        "Duplicate of link #" + same.get(0).id + " (same from, to, handling-unit type and equipment).");
            }
        }
        for (List<Link> pair : byPair.values()) {
            List<Link> allTypes = pair.stream().filter(k -> k.huType == null).toList();
            List<Link> specific = pair.stream().filter(k -> k.huType != null).toList();
            if (!allTypes.isEmpty() && !specific.isEmpty()) {
                String types = specific.stream().map(k -> huCodes.getOrDefault(k.huType, "#" + k.huType)).distinct()
                        .sorted().reduce((a, b) -> a + ", " + b).orElse("");
                r.add("LINK_ALL_AND_SPECIFIC", Severity.WARNING, "location-link", allTypes.get(0).id,
                        label(allTypes.get(0), locs), "The same from/to pair has an all-types link and "
                                + specific.size() + " type-specific link(s) (" + types + "). The specific links "
                                + "add nothing unless their cost or equipment differs - check this is intended.");
            }
        }

        // ---- locations: incoming / outgoing links ------------------------------------------------------------
        Map<Long, List<Long>> out = new HashMap<>();
        Map<Long, List<Long>> in = new HashMap<>();
        for (Link k : links) {
            Loc from = locs.get(k.from);
            Loc to = locs.get(k.to);
            if (k.enabled && from != null && to != null && from.enabled && to.enabled) {
                out.computeIfAbsent(k.from, x -> new ArrayList<>()).add(k.to);
                in.computeIfAbsent(k.to, x -> new ArrayList<>()).add(k.from);
            }
        }
        for (Loc l : locs.values()) {
            if (!l.enabled || l.storage) {
                continue; // storage bins are allowed dead ends
            }
            if (!in.containsKey(l.id) && !"INBOUND".equals(l.areaType)) {
                r.add("LOCATION_NO_INCOMING", Severity.WARNING, "location", l.id, l.code,
                        "No enabled link leads to this location, so nothing can arrive here.");
            }
            if (!out.containsKey(l.id) && !"OUTBOUND".equals(l.areaType) && !l.workstation) {
                r.add("LOCATION_NO_OUTGOING", Severity.WARNING, "location", l.id, l.code,
                        "No enabled link leaves this location, so anything arriving here is stuck.");
            }
        }

        // ---- storage reachable from inbound, and with a way out to outbound ---------------------------------
        boolean anyStorage = locs.values().stream().anyMatch(l -> l.enabled && l.storage);
        if (anyStorage) {
            Set<Long> inbound = ids(locs, l -> l.enabled && "INBOUND".equals(l.areaType));
            Set<Long> outbound = ids(locs, l -> l.enabled && "OUTBOUND".equals(l.areaType));
            if (inbound.isEmpty()) {
                r.add("STORAGE_NOT_REACHABLE", Severity.ERROR, null, null, null,
                        "There is no enabled location in an INBOUND area, so nothing can be put into storage.");
            } else {
                Set<Long> reached = bfs(inbound, out);
                locs.values().stream().filter(l -> l.enabled && l.storage && !reached.contains(l.id))
                        .forEach(l -> r.add("STORAGE_NOT_REACHABLE", Severity.ERROR, "location", l.id, l.code,
                                "No chain of enabled links leads from an inbound location to this storage location."));
            }
            if (outbound.isEmpty()) {
                r.add("STORAGE_NO_EXIT", Severity.ERROR, null, null, null,
                        "There is no enabled location in an OUTBOUND area, so nothing can be taken out of storage.");
            } else {
                Set<Long> canExit = bfs(outbound, in);
                locs.values().stream().filter(l -> l.enabled && l.storage && !canExit.contains(l.id))
                        .forEach(l -> r.add("STORAGE_NO_EXIT", Severity.ERROR, "location", l.id, l.code,
                                "No chain of enabled links leads from this storage location to an outbound location."));
            }
        }

        // ---- location types and handling-unit types ----------------------------------------------------------
        Map<Long, Integer> typeUse = new HashMap<>();
        locs.values().forEach(l -> typeUse.merge(l.typeId, 1, Integer::sum));
        Set<Long> typesWithHu = new HashSet<>();
        Set<Long> acceptedHu = new HashSet<>();
        jdbc.query("select x.location_type_id, x.hu_type_id from location_type_hu_type x "
                + "join location_type t on t.id = x.location_type_id where t.site_id = ?", rs -> {
            typesWithHu.add(rs.getLong(1));
            acceptedHu.add(rs.getLong(2));
        }, siteId);
        jdbc.query("select id, code from location_type where site_id = ? order by code", rs -> {
            long id = rs.getLong(1);
            if (!typesWithHu.contains(id)) {
                int used = typeUse.getOrDefault(id, 0);
                r.add("LOCATION_TYPE_NO_HU_TYPE", used > 0 ? Severity.ERROR : Severity.WARNING, "location-type", id,
                        rs.getString(2), used > 0
                                ? "No handling-unit type is allowed on this location type, but " + used
                                + " location(s) use it - nothing can be placed there."
                                : "No handling-unit type is allowed on this location type (it is not used yet).");
            }
        }, siteId);
        huCodes.forEach((id, code) -> {
            if (!acceptedHu.contains(id)) {
                r.add("HU_TYPE_ACCEPTED_NOWHERE", Severity.WARNING, "hu-type", id, code,
                        "No location type accepts this handling-unit type.");
            }
        });

        // ---- equipment -----------------------------------------------------------------------------------------
        Map<Long, Integer> locationCount = new HashMap<>();
        jdbc.query("select el.equipment_id, count(*) from equipment_location el "
                + "join equipment e on e.id = el.equipment_id where e.site_id = ? group by el.equipment_id", rs -> {
            locationCount.put(rs.getLong(1), rs.getInt(2));
        }, siteId);
        Set<Long> parents = new HashSet<>();
        jdbc.query("select distinct parent_id from equipment where site_id = ? and parent_id is not null", rs -> {
            parents.add(rs.getLong(1));
        }, siteId);
        jdbc.query("select id, code, enabled from equipment where site_id = ? order by code", rs -> {
            long id = rs.getLong(1);
            if (rs.getBoolean(3) && !locationCount.containsKey(id) && !parents.contains(id)) {
                r.add("EQUIPMENT_NO_LOCATIONS", Severity.WARNING, "equipment", id, rs.getString(2),
                        "This equipment has no locations assigned (and no sub-equipment).");
            }
        }, siteId);

        Map<Long, List<String[]>> claims = new HashMap<>(); // location -> [equipment type id, equipment code]
        jdbc.query("select el.location_id, e.equipment_type_id, e.code from equipment_location el "
                + "join equipment e on e.id = el.equipment_id where e.site_id = ? order by e.code", rs -> {
            claims.computeIfAbsent(rs.getLong(1), x -> new ArrayList<>())
                    .add(new String[] {String.valueOf(rs.getLong(2)), rs.getString(3)});
        }, siteId);
        claims.forEach((locationId, list) -> {
            Map<String, List<String>> perType = new LinkedHashMap<>();
            list.forEach(c -> perType.computeIfAbsent(c[0], x -> new ArrayList<>()).add(c[1]));
            perType.values().stream().filter(codes -> codes.size() > 1).forEach(codes -> {
                Loc l = locs.get(locationId);
                r.add("LOCATION_DOUBLE_EQUIPMENT", Severity.ERROR, "location", locationId, l == null ? null : l.code,
                        "Claimed by " + String.join(", ", codes) + " - two pieces of equipment of the same type.");
            });
        });

        // ---- coordinates ---------------------------------------------------------------------------------------
        Map<String, List<Loc>> atPoint = new HashMap<>();
        for (Loc l : locs.values()) {
            if (l.x == null || l.y == null) {
                if (l.enabled) {
                    r.add("LOCATION_NO_COORDINATES", Severity.WARNING, "location", l.id, l.code,
                            "No x/y position, so the layout preview cannot show this location.");
                }
                continue;
            }
            atPoint.computeIfAbsent(l.level + "|" + l.x + "|" + l.y + "|" + (l.z == null ? 0 : l.z),
                    x -> new ArrayList<>(2)).add(l);
        }
        for (List<Loc> same : atPoint.values()) {
            if (same.size() > 1) {
                String codes = same.stream().map(Loc::code).sorted().reduce((a, b) -> a + ", " + b).orElse("");
                for (Loc l : same) {
                    r.add("LOCATION_SAME_COORDINATES", Severity.ERROR, "location", l.id, l.code,
                            "Same position and level as " + codes.replace(l.code + ", ", "").replace(", " + l.code, "")
                                    + ".");
                }
            }
        }

        // ---- result ------------------------------------------------------------------------------------------
        List<RuleResult> results = r.results();
        String overall = results.stream().anyMatch(x -> x.status().equals("ERROR")) ? "ERROR"
                : results.stream().anyMatch(x -> x.status().equals("WARNING")) ? "WARNING" : "PASS";
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("locations", (long) locs.size());
        counts.put("links", (long) links.size());
        counts.put("equipment", jdbc.queryForObject("select count(*) from equipment where site_id = ?", Long.class,
                siteId));
        counts.put("huTypes", (long) huCodes.size());
        return new Report(overall, Instant.now(), (System.nanoTime() - started) / 1_000_000, counts, results);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private static Set<Long> ids(Map<Long, Loc> locs, java.util.function.Predicate<Loc> filter) {
        Set<Long> result = new HashSet<>();
        locs.values().stream().filter(filter).forEach(l -> result.add(l.id));
        return result;
    }

    /** Everything reachable from the start set by following the given neighbour lists. */
    private static Set<Long> bfs(Set<Long> start, Map<Long, List<Long>> neighbours) {
        Set<Long> seen = new HashSet<>(start);
        ArrayDeque<Long> todo = new ArrayDeque<>(start);
        while (!todo.isEmpty()) {
            for (Long next : neighbours.getOrDefault(todo.poll(), List.of())) {
                if (seen.add(next)) {
                    todo.add(next);
                }
            }
        }
        return seen;
    }

    private static String label(Link k, Map<Long, Loc> locs) {
        Loc from = locs.get(k.from);
        Loc to = locs.get(k.to);
        return (from == null ? "?" : from.code) + " -> " + (to == null ? "?" : to.code);
    }

    private static Integer intOrNull(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    /** Collects the findings per rule, keeping the full count but only the first items. */
    private static final class Rules {

        private static final Map<String, String> TITLES = new LinkedHashMap<>();

        static {
            TITLES.put("LINK_DISABLED_LOCATION", "Enabled links to or from a disabled location");
            TITLES.put("LINK_DISABLED_EQUIPMENT", "Enabled links that use disabled equipment");
            TITLES.put("LINK_DUPLICATE", "Duplicate links");
            TITLES.put("LINK_ALL_AND_SPECIFIC", "Same from/to pair has an all-types link and a type-specific link");
            TITLES.put("LOCATION_NO_INCOMING", "Locations nothing can arrive at");
            TITLES.put("LOCATION_NO_OUTGOING", "Locations nothing can leave from");
            TITLES.put("STORAGE_NOT_REACHABLE", "Storage locations not reachable from an inbound location");
            TITLES.put("STORAGE_NO_EXIT", "Storage locations with no path to an outbound location");
            TITLES.put("LOCATION_TYPE_NO_HU_TYPE", "Location types that accept no handling-unit type");
            TITLES.put("HU_TYPE_ACCEPTED_NOWHERE", "Handling-unit types accepted nowhere");
            TITLES.put("EQUIPMENT_NO_LOCATIONS", "Equipment without locations");
            TITLES.put("LOCATION_DOUBLE_EQUIPMENT", "Locations claimed by two pieces of equipment of the same type");
            TITLES.put("LOCATION_SAME_COORDINATES", "Locations at the same position and level");
            TITLES.put("LOCATION_NO_COORDINATES", "Locations without x/y position");
        }

        private final Map<String, List<Item>> items = new HashMap<>();
        private final Map<String, Long> counts = new HashMap<>();
        private final Map<String, Severity> worst = new HashMap<>();

        void add(String rule, Severity severity, String entityType, Long entityId, String code, String message) {
            counts.merge(rule, 1L, Long::sum);
            worst.merge(rule, severity, (a, b) -> a == Severity.ERROR || b == Severity.ERROR ? Severity.ERROR : a);
            List<Item> list = items.computeIfAbsent(rule, x -> new ArrayList<>());
            if (list.size() < MAX_ITEMS_PER_RULE) {
                list.add(new Item(severity, entityType, entityId, code, message));
            }
        }

        List<RuleResult> results() {
            List<RuleResult> out = new ArrayList<>();
            TITLES.forEach((code, title) -> {
                long count = counts.getOrDefault(code, 0L);
                String status = count == 0 ? "PASS" : worst.get(code).name();
                List<Item> list = items.getOrDefault(code, List.of());
                out.add(new RuleResult(code, title, status, count, count > list.size(), list));
            });
            return out;
        }
    }
}
