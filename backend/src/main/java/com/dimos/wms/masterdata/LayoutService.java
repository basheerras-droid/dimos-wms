package com.dimos.wms.masterdata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compact, read-only data for the layout preview: the plan of one level (locations by x/y with their links) and the
 * rack face of one aisle side (a grid of column number by level number). Plain SQL, no entities: a plant with
 * thousands of locations must stay quick.
 */
@Service
class LayoutService {

    /** What a location looks like on the screen. Disabled wins over blocked. */
    static String state(boolean enabled, boolean blockedIn, boolean blockedOut) {
        if (!enabled) {
            return "DISABLED";
        }
        if (blockedIn && blockedOut) {
            return "BLOCKED_BOTH";
        }
        return blockedIn ? "BLOCKED_IN" : blockedOut ? "BLOCKED_OUT" : "OK";
    }

    record AreaInfo(long id, String code, String areaType) {
    }

    record PlanLocation(long id, String code, long areaId, int x, int y, String state) {
    }

    record PlanLink(long id, long from, long to, boolean enabled) {
    }

    /** {@code level} is null when the site has no locations yet. */
    record Plan(List<Integer> levels, Integer level, List<AreaInfo> areas, List<PlanLocation> locations,
                List<PlanLink> links, int withoutPosition, int crossLevelLinks) {
    }

    record Rack(String aisle, String side, int locations, int minColumn, int maxColumn, int minLevel, int maxLevel) {
    }

    record Cell(long id, String code, int column, int level, String state) {
    }

    record Summary(int total, int usable, int disabled, int blockedIn, int blockedOut, int blockedBoth,
                   int duplicates, int unplaced) {
    }

    /** {@code occupied} is empty until stock exists (inventory step); then the screen shows the real fill level. */
    record Fill(Integer occupied, int capacity, boolean available) {
    }

    record RackFace(String aisle, String side, int minColumn, int maxColumn, int minLevel, int maxLevel,
                    List<Cell> cells, Summary summary, Fill fill) {
    }

    private final JdbcTemplate jdbc;

    LayoutService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Plan plan(Long siteId, Integer requestedLevel) {
        List<Integer> levels = jdbc.queryForList(
                "select distinct level_no from location where site_id = ? order by level_no", Integer.class, siteId);
        if (levels.isEmpty()) {
            return new Plan(levels, null, List.of(), List.of(), List.of(), 0, 0);
        }
        int level = requestedLevel != null && levels.contains(requestedLevel) ? requestedLevel : levels.get(0);

        List<PlanLocation> locations = jdbc.query(
                "select id, code, area_id, x_mm, y_mm, enabled, blocked_in, blocked_out from location "
                        + "where site_id = ? and level_no = ? and x_mm is not null and y_mm is not null order by code",
                (rs, i) -> new PlanLocation(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getInt(5),
                        state(rs.getBoolean(6), rs.getBoolean(7), rs.getBoolean(8))), siteId, level);
        Integer without = jdbc.queryForObject("select count(*) from location where site_id = ? and level_no = ? "
                + "and (x_mm is null or y_mm is null)", Integer.class, siteId, level);

        List<AreaInfo> areas = jdbc.query("select id, code, area_type from area where site_id = ? order by code",
                (rs, i) -> new AreaInfo(rs.getLong(1), rs.getString(2), rs.getString(3)), siteId);

        List<PlanLink> links = jdbc.query("select k.id, k.from_location_id, k.to_location_id, k.enabled "
                        + "from location_link k join location a on a.id = k.from_location_id "
                        + "join location b on b.id = k.to_location_id "
                        + "where k.site_id = ? and a.level_no = ? and b.level_no = ? order by k.id",
                (rs, i) -> new PlanLink(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getBoolean(4)),
                siteId, level, level);
        Integer cross = jdbc.queryForObject("select count(*) from location_link k "
                + "join location a on a.id = k.from_location_id join location b on b.id = k.to_location_id "
                + "where k.site_id = ? and a.level_no <> b.level_no and (a.level_no = ? or b.level_no = ?)",
                Integer.class, siteId, level, level);
        return new Plan(levels, level, areas, locations, links, without == null ? 0 : without,
                cross == null ? 0 : cross);
    }

    /** Every aisle side that has locations with a column number (racks). */
    @Transactional(readOnly = true)
    public List<Rack> racks(Long siteId) {
        return jdbc.query("select aisle, side, count(*), min(column_no), max(column_no), min(level_no), max(level_no) "
                        + "from location where site_id = ? and aisle is not null and column_no is not null "
                        + "group by aisle, side order by aisle, side",
                (rs, i) -> new Rack(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        rs.getInt(6), rs.getInt(7)), siteId);
    }

    @Transactional(readOnly = true)
    public RackFace rackFace(Long siteId, String aisle, String side) {
        String sideSql = side == null || side.isBlank() ? " and side is null" : " and side = ?";
        List<Object> args = new ArrayList<>(List.of(siteId, aisle));
        if (!(side == null || side.isBlank())) {
            args.add(side);
        }
        List<Cell> cells = new ArrayList<>();
        int[] unplaced = {0};
        jdbc.query("select id, code, column_no, level_no, enabled, blocked_in, blocked_out, "
                        + "case when column_no is null then 1 else 0 end from location "
                        + "where site_id = ? and aisle = ?" + sideSql + " order by column_no, level_no, code",
                rs -> {
                    if (rs.getInt(8) == 1) {
                        unplaced[0]++;
                    } else {
                        cells.add(new Cell(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                                state(rs.getBoolean(5), rs.getBoolean(6), rs.getBoolean(7))));
                    }
                }, args.toArray());

        int usable = 0, disabled = 0, blockedIn = 0, blockedOut = 0, blockedBoth = 0, duplicates = 0;
        Map<String, Integer> seen = new HashMap<>();
        int minColumn = Integer.MAX_VALUE, maxColumn = Integer.MIN_VALUE, minLevel = Integer.MAX_VALUE,
                maxLevel = Integer.MIN_VALUE;
        for (Cell c : cells) {
            switch (c.state()) {
                case "OK" -> usable++;
                case "DISABLED" -> disabled++;
                case "BLOCKED_IN" -> blockedIn++;
                case "BLOCKED_OUT" -> blockedOut++;
                default -> blockedBoth++;
            }
            if (seen.merge(c.column() + "/" + c.level(), 1, Integer::sum) > 1) {
                duplicates++;
            }
            minColumn = Math.min(minColumn, c.column());
            maxColumn = Math.max(maxColumn, c.column());
            minLevel = Math.min(minLevel, c.level());
            maxLevel = Math.max(maxLevel, c.level());
        }
        if (cells.isEmpty()) {
            minColumn = maxColumn = minLevel = maxLevel = 0;
        }
        // "usable" for planning = enabled and not blocked in either direction
        Summary summary = new Summary(cells.size(), usable, disabled, blockedIn, blockedOut, blockedBoth, duplicates,
                unplaced[0]);
        return new RackFace(aisle, side == null || side.isBlank() ? null : side, minColumn, maxColumn, minLevel,
                maxLevel, cells, summary, new Fill(null, usable, false));
    }
}
