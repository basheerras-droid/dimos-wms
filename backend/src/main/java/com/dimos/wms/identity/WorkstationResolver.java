package com.dimos.wms.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Finds the workstation of a request from the {@value #COOKIE} cookie. Only the SHA-256 hash of the cookie value is
 * compared with the database. Results are cached for a few seconds (changes clear the cache at once).
 */
@Component
class WorkstationResolver {

    static final String COOKIE = "WMS_WS";
    private static final long TTL_MILLIS = 20_000;

    private record Cached(WorkstationContext context, boolean ipCheck, String ipAddress, long expires) {
    }

    private final JdbcTemplate jdbc;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    WorkstationResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static String sha256Hex(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** The registration cookie value of the request, or null. */
    static String tokenOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    WorkstationContext resolve(HttpServletRequest request, Long siteId) {
        String token = tokenOf(request);
        if (token == null) {
            return WorkstationContext.OFFICE;
        }
        String hash = sha256Hex(token);
        String key = siteId + ":" + hash;
        long now = System.currentTimeMillis();
        Cached cached = cache.get(key);
        if (cached == null || cached.expires() < now) {
            cached = load(siteId, hash, now + TTL_MILLIS);
            cache.put(key, cached);
        }
        WorkstationContext ctx = cached.context();
        if (ctx.mode() == WorkstationContext.Mode.REGISTERED && cached.ipCheck()
                && !String.valueOf(cached.ipAddress()).equals(request.getRemoteAddr())) {
            // the cookie is on another machine than the one registered: no workstation rights, and say why
            return new WorkstationContext(WorkstationContext.Mode.IP_MISMATCH, false, ctx.id(), ctx.code(), ctx.name(),
                    ctx.type(), ctx.groupCode(), ctx.locationCode(), null, ctx.groupId());
        }
        return ctx;
    }

    /** Call after any change to workstations, groups or registrations. */
    void clearCache() {
        cache.clear();
    }

    private Cached load(Long siteId, String hash, long expires) {
        List<Object[]> rows = jdbc.query("select w.id, w.code, w.name, w.workstation_type, w.enabled, w.check_ip, w.ip_address, "
                        + "w.workstation_group_id, g.code, g.enabled, l.code from workstation w "
                        + "left join workstation_group g on g.id = w.workstation_group_id "
                        + "left join location l on l.id = w.location_id where w.site_id = ? and w.token_hash = ?",
                (rs, i) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        rs.getBoolean(6), rs.getString(7), rs.getObject(8), rs.getString(9), rs.getObject(10), rs.getString(11)},
                siteId, hash);
        if (rows.isEmpty()) {
            return new Cached(WorkstationContext.officeWithStaleCookie(), false, null, expires); // cookie no longer valid
        }
        Object[] r = rows.get(0);
        boolean enabled = (Boolean) r[4];
        Set<String> restriction = null;
        if (r[7] != null && Boolean.TRUE.equals(r[9])) { // a disabled group restricts nothing
            Set<String> allowed = new HashSet<>(jdbc.queryForList("select p.code from workstation_group_permission x "
                    + "join app_permission p on p.id = x.permission_id where x.workstation_group_id = ?", String.class,
                    ((Number) r[7]).longValue()));
            restriction = allowed.isEmpty() ? null : allowed; // an empty list means "no restriction"
        }
        WorkstationContext ctx = new WorkstationContext(enabled ? WorkstationContext.Mode.REGISTERED : WorkstationContext.Mode.DISABLED,
                false, (Long) r[0], (String) r[1], (String) r[2], (String) r[3], (String) r[8], (String) r[10], restriction,
                r[7] == null ? null : ((Number) r[7]).longValue());
        return new Cached(ctx, (Boolean) r[5], (String) r[6], expires);
    }
}
