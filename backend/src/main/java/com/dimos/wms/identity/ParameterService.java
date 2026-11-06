package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * System parameters. A parameter is defined by a migration (type, default, security bounds, allowed scopes); values that
 * differ from the default are stored per scope. The most specific value wins:
 * WORKSTATION, then WS_GROUP, then SITE, then GLOBAL, then the default.
 * Every value is checked on the server against the type and the bounds of the definition, which cannot be widened
 * from the screen. Values are cached in memory for a short time; any change clears the cache.
 */
@Service
class ParameterService {

    private static final Logger log = LoggerFactory.getLogger(ParameterService.class);
    private static final long CACHE_MILLIS = 30_000;

    enum Scope { GLOBAL, SITE, WS_GROUP, WORKSTATION }

    record Definition(long id, String code, String module, String name, String description, String type, String unit,
                      String defaultValue, String min, String max, List<String> choices, List<Scope> allowedScopes,
                      boolean restartNeeded) {
    }

    private record Stored(long id, long definitionId, Scope scope, long refId, String value, long version,
                          Instant updatedAt, String updatedBy) {
    }

    private record Snapshot(Map<String, Definition> byCode, Map<Long, Definition> byId, Map<String, Stored> values) {
    }

    /** The value that applies, and where it comes from (source DEFAULT, GLOBAL, SITE, WS_GROUP or WORKSTATION). */
    record Effective(String value, String source, Long sourceRefId, String sourceRefCode) {
    }

    record Override(Long id, String scope, long scopeRefId, String scopeRefCode, String value, long version,
                    Instant updatedAt, String updatedBy) {
    }

    record ParameterView(String code, String module, String name, String description, String type, String unit,
                         String defaultValue, String min, String max, List<String> choices, List<String> allowedScopes,
                         boolean restartNeeded, Effective effective, List<Override> overrides) {
    }

    /** {@code scopeRefId} is ignored for GLOBAL and SITE. {@code version} is required when a value exists already. */
    record SetRequest(@jakarta.validation.constraints.NotBlank String scope, Long scopeRefId,
                      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 255) String value,
                      Long version) {
    }

    private record ValueAudit(String parameter, String scope, String reference, String value) {
    }

    private final JdbcTemplate jdbc;
    private final ParameterValueRepository values;
    private final AuditService audit;
    private final JsonMapper json;
    private volatile Snapshot snapshot;
    private volatile long loadedAt;

    ParameterService(JdbcTemplate jdbc, ParameterValueRepository values, AuditService audit, JsonMapper json) {
        this.jdbc = jdbc;
        this.values = values;
        this.audit = audit;
        this.json = json;
    }

    // ---- reading values for the application ------------------------------------------------------------------

    /** A whole-number parameter at site level (GLOBAL / SITE values). */
    int intValue(String code, Long siteId) {
        Snapshot s = snap();
        Definition d = definition(s, code);
        String value = effective(d, s, siteId, null, null).value();
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            log.error("Parameter {} has the unusable value '{}'; using the default {}", code, value, d.defaultValue());
            return Integer.parseInt(d.defaultValue());
        }
    }

    /** A duration parameter, optionally for a workstation (and its group). */
    Duration durationValue(String code, Long siteId, Long groupId, Long workstationId) {
        Snapshot s = snap();
        Definition d = definition(s, code);
        String value = effective(d, s, siteId, groupId, workstationId).value();
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException ex) {
            log.error("Parameter {} has the unusable value '{}'; using the default {}", code, value, d.defaultValue());
            return Duration.parse(d.defaultValue());
        }
    }

    /** Call after anything that changes values or workstations. */
    void invalidate() {
        snapshot = null;
    }

    // ---- screens -----------------------------------------------------------------------------------------------

    /** All parameters with their effective value; with a workstation, as seen at that workstation. */
    @Transactional(readOnly = true)
    public List<ParameterView> list(Long workstationId, WmsUserPrincipal who) {
        Snapshot s = snap();
        Long groupId = null;
        if (workstationId != null) {
            List<Long> groups = jdbc.query("select workstation_group_id from workstation where id = ? and site_id = ?",
                    (rs, i) -> (Long) rs.getObject(1), workstationId, who.siteId());
            if (groups.isEmpty()) {
                throw UserAdminException.notFound();
            }
            groupId = groups.get(0);
        }
        Map<Long, String> groupCodes = codes("select id, code from workstation_group where site_id = ?", who.siteId());
        Map<Long, String> workstationCodes = codes("select id, code from workstation where site_id = ?", who.siteId());
        List<ParameterView> result = new ArrayList<>();
        for (Definition d : s.byCode().values().stream().sorted(java.util.Comparator.comparing(Definition::module)
                .thenComparing(Definition::code)).toList()) {
            result.add(view(d, s, who.siteId(), groupId, workstationId, groupCodes, workstationCodes));
        }
        return result;
    }

    @Transactional
    public ParameterView set(String code, SetRequest request, WmsUserPrincipal who) {
        Snapshot s = snap();
        Definition d = definition(s, code);
        Scope scope = parseScope(request.scope());
        if (!d.allowedScopes().contains(scope)) {
            throw UserAdminException.invalid("scope", "this parameter cannot be set for " + scope.name());
        }
        long ref = refOf(scope, request.scopeRefId(), who);
        String normalised = normalise(d, request.value());
        Long siteId = scope == Scope.GLOBAL ? null : who.siteId();

        ParameterValue existing = values.findByParameterDefinitionIdAndScopeAndScopeRefId(d.id(), scope.name(), ref).orElse(null);
        String before = existing == null ? null : existing.getValue();
        if (existing == null) {
            ParameterValue created = values.saveAndFlush(ParameterValue.create(siteId, d.id(), scope.name(), ref, normalised, who.username()));
            record("PARAMETER_VALUE_SET", who, created.getId(), null, valueAudit(d, scope, ref, normalised));
        } else {
            if (request.version() == null) {
                throw UserAdminException.invalid("version", "must be sent when changing an existing value");
            }
            if (request.version() != existing.getVersion()) {
                throw UserAdminException.stale();
            }
            if (!normalised.equals(before)) {
                existing.change(normalised, who.username());
                values.saveAndFlush(existing);
                record("PARAMETER_VALUE_SET", who, existing.getId(), valueAudit(d, scope, ref, before), valueAudit(d, scope, ref, normalised));
            }
        }
        invalidate();
        return viewFor(d, who, null);
    }

    @Transactional
    public ParameterView remove(String code, String scopeName, Long scopeRefId, WmsUserPrincipal who) {
        Snapshot s = snap();
        Definition d = definition(s, code);
        Scope scope = parseScope(scopeName);
        long ref = refOf(scope, scopeRefId, who);
        ParameterValue existing = values.findByParameterDefinitionIdAndScopeAndScopeRefId(d.id(), scope.name(), ref)
                .orElseThrow(UserAdminException::notFound);
        String before = existing.getValue();
        values.delete(existing);
        values.flush();
        record("PARAMETER_VALUE_REMOVED", who, existing.getId(), valueAudit(d, scope, ref, before), null);
        invalidate();
        return viewFor(d, who, null);
    }

    /** A workstation or workstation group was deleted: its values go with it. */
    void deleteValuesOf(Scope scope, long refId) {
        jdbc.update("delete from parameter_value where scope = ? and scope_ref_id = ?", scope.name(), refId);
        invalidate();
    }

    // ---- resolution ----------------------------------------------------------------------------------------------

    private Effective effective(Definition d, Snapshot s, Long siteId, Long groupId, Long workstationId) {
        if (workstationId != null && d.allowedScopes().contains(Scope.WORKSTATION)) {
            Stored v = s.values().get(key(d.id(), Scope.WORKSTATION, workstationId));
            if (v != null) {
                return new Effective(v.value(), "WORKSTATION", workstationId, null);
            }
        }
        if (groupId != null && d.allowedScopes().contains(Scope.WS_GROUP)) {
            Stored v = s.values().get(key(d.id(), Scope.WS_GROUP, groupId));
            if (v != null) {
                return new Effective(v.value(), "WS_GROUP", groupId, null);
            }
        }
        if (siteId != null && d.allowedScopes().contains(Scope.SITE)) {
            Stored v = s.values().get(key(d.id(), Scope.SITE, siteId));
            if (v != null) {
                return new Effective(v.value(), "SITE", siteId, null);
            }
        }
        if (d.allowedScopes().contains(Scope.GLOBAL)) {
            Stored v = s.values().get(key(d.id(), Scope.GLOBAL, 0));
            if (v != null) {
                return new Effective(v.value(), "GLOBAL", null, null);
            }
        }
        return new Effective(d.defaultValue(), "DEFAULT", null, null);
    }

    private ParameterView viewFor(Definition d, WmsUserPrincipal who, Long workstationId) {
        Snapshot s = snap();
        Map<Long, String> groupCodes = codes("select id, code from workstation_group where site_id = ?", who.siteId());
        Map<Long, String> workstationCodes = codes("select id, code from workstation where site_id = ?", who.siteId());
        return view(d, s, who.siteId(), null, workstationId, groupCodes, workstationCodes);
    }

    private ParameterView view(Definition d, Snapshot s, Long siteId, Long groupId, Long workstationId,
                               Map<Long, String> groupCodes, Map<Long, String> workstationCodes) {
        Effective e = effective(d, s, siteId, groupId, workstationId);
        String refCode = switch (e.source()) {
            case "WS_GROUP" -> groupCodes.get(e.sourceRefId());
            case "WORKSTATION" -> workstationCodes.get(e.sourceRefId());
            default -> null;
        };
        List<Override> overrides = new ArrayList<>();
        for (Stored v : s.values().values()) {
            if (v.definitionId() != d.id()) {
                continue;
            }
            String code = switch (v.scope()) {
                case GLOBAL -> v.refId() == 0 ? "" : null;
                case SITE -> siteId != null && v.refId() == siteId ? "" : null;
                case WS_GROUP -> groupCodes.get(v.refId());
                case WORKSTATION -> workstationCodes.get(v.refId());
            };
            if (code != null) { // values of other sites are not shown
                overrides.add(new Override(v.id(), v.scope().name(), v.refId(), code.isEmpty() ? null : code, v.value(),
                        v.version(), v.updatedAt(), v.updatedBy()));
            }
        }
        overrides.sort(java.util.Comparator.comparing((Override o) -> Scope.valueOf(o.scope()).ordinal())
                .thenComparing(o -> String.valueOf(o.scopeRefCode())));
        return new ParameterView(d.code(), d.module(), d.name(), d.description(), d.type(), d.unit(), d.defaultValue(), d.min(),
                d.max(), d.choices(), d.allowedScopes().stream().map(Scope::name).toList(), d.restartNeeded(),
                new Effective(e.value(), e.source(), e.sourceRefId(), refCode), overrides);
    }

    // ---- validation ------------------------------------------------------------------------------------------------

    /** Checks a value against the type and bounds of the definition and returns it in its stored form. */
    static String normalise(Definition d, String raw) {
        String text = raw == null ? "" : raw.trim();
        switch (d.type()) {
            case "INT" -> {
                long n;
                try {
                    n = Long.parseLong(text);
                } catch (NumberFormatException ex) {
                    throw UserAdminException.invalid("value", "must be a whole number");
                }
                if ((d.min() != null && n < Long.parseLong(d.min())) || (d.max() != null && n > Long.parseLong(d.max()))) {
                    throw UserAdminException.invalid("value", "must be between " + d.min() + " and " + d.max()
                            + (d.unit() == null ? "" : " " + d.unit()));
                }
                return Long.toString(n);
            }
            case "BOOL" -> {
                if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
                    throw UserAdminException.invalid("value", "must be true or false");
                }
                return text.toLowerCase();
            }
            case "TEXT" -> {
                if (text.isEmpty()) {
                    throw UserAdminException.invalid("value", "must not be empty");
                }
                return text;
            }
            case "CHOICE" -> {
                if (!d.choices().contains(text)) {
                    throw UserAdminException.invalid("value", "must be one of " + String.join(", ", d.choices()));
                }
                return text;
            }
            case "DURATION" -> {
                Duration v;
                try {
                    v = Duration.parse(text.toUpperCase());
                } catch (DateTimeParseException ex) {
                    throw UserAdminException.invalid("value", "must be a duration such as PT30M (30 minutes) or PT2H (2 hours)");
                }
                if (v.isNegative() || v.isZero()
                        || (d.min() != null && v.compareTo(Duration.parse(d.min())) < 0)
                        || (d.max() != null && v.compareTo(Duration.parse(d.max())) > 0)) {
                    throw UserAdminException.invalid("value", "must be between " + human(Duration.parse(d.min())) + " and "
                            + human(Duration.parse(d.max())));
                }
                return v.toString();
            }
            default -> throw new IllegalStateException("Unknown parameter type " + d.type());
        }
    }

    private static String human(Duration d) {
        long minutes = d.toMinutes();
        if (minutes % 60 == 0) {
            long h = minutes / 60;
            return h + (h == 1 ? " hour" : " hours");
        }
        return minutes + " minutes";
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private static Scope parseScope(String name) {
        try {
            return Scope.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw UserAdminException.invalid("scope", "must be GLOBAL, SITE, WS_GROUP or WORKSTATION");
        }
    }

    private long refOf(Scope scope, Long requested, WmsUserPrincipal who) {
        switch (scope) {
            case GLOBAL:
                return 0;
            case SITE:
                return who.siteId();
            case WS_GROUP:
                return checkedRef("workstation_group", requested, who, "workstation group");
            default:
                return checkedRef("workstation", requested, who, "workstation");
        }
    }

    private long checkedRef(String table, Long id, WmsUserPrincipal who, String what) {
        if (id == null) {
            throw UserAdminException.invalid("scopeRefId", "choose the " + what);
        }
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where id = ? and site_id = ?", Integer.class, id, who.siteId());
        if (n == null || n == 0) {
            throw UserAdminException.invalid("scopeRefId", "unknown " + what);
        }
        return id;
    }

    private Map<Long, String> codes(String sql, Long siteId) {
        Map<Long, String> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getLong(1), rs.getString(2));
        }, siteId);
        return result;
    }

    private Definition definition(Snapshot s, String code) {
        Definition d = s.byCode().get(code);
        if (d == null) {
            throw UserAdminException.notFound();
        }
        return d;
    }

    private static String key(long definitionId, Scope scope, long ref) {
        return definitionId + "|" + scope.name() + "|" + ref;
    }

    private Snapshot snap() {
        Snapshot s = snapshot;
        long now = System.currentTimeMillis();
        if (s == null || now - loadedAt > CACHE_MILLIS) {
            s = load();
            snapshot = s;
            loadedAt = now;
        }
        return s;
    }

    private Snapshot load() {
        Map<String, Definition> byCode = new HashMap<>();
        Map<Long, Definition> byId = new HashMap<>();
        jdbc.query("select id, code, module, name, description, value_type, unit, default_value, min_value, max_value, choices, "
                + "allowed_scopes, restart_needed from parameter_definition", rs -> {
            String choices = rs.getString(11);
            List<Scope> scopes = Arrays.stream(rs.getString(12).split(",")).map(String::trim).map(Scope::valueOf).toList();
            Definition d = new Definition(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                    rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                    choices == null ? List.of() : Arrays.stream(choices.split(",")).map(String::trim).toList(), scopes,
                    rs.getBoolean(13));
            byCode.put(d.code(), d);
            byId.put(d.id(), d);
        });
        Map<String, Stored> stored = new HashMap<>();
        jdbc.query("select id, parameter_definition_id, scope, scope_ref_id, value, version, updated_at, updated_by from parameter_value", rs -> {
            Scope scope = Scope.valueOf(rs.getString(3));
            Stored v = new Stored(rs.getLong(1), rs.getLong(2), scope, rs.getLong(4), rs.getString(5), rs.getLong(6),
                    rs.getTimestamp(7).toInstant(), rs.getString(8));
            stored.put(key(v.definitionId(), scope, v.refId()), v);
        });
        return new Snapshot(byCode, byId, stored);
    }

    private ValueAudit valueAudit(Definition d, Scope scope, long ref, String value) {
        String reference = switch (scope) {
            case GLOBAL -> "all sites";
            case SITE -> "site " + ref;
            case WS_GROUP -> codeOrId("workstation_group", ref);
            case WORKSTATION -> codeOrId("workstation", ref);
        };
        return new ValueAudit(d.code(), scope.name(), reference, value);
    }

    private String codeOrId(String table, long id) {
        List<String> found = jdbc.queryForList("select code from " + table + " where id = ?", String.class, id);
        return found.isEmpty() ? String.valueOf(id) : found.get(0);
    }

    private void record(String action, WmsUserPrincipal who, Long id, ValueAudit before, ValueAudit after) {
        audit.record(new AuditEvent(who.username(), action, who.siteId(), "parameter_value", String.valueOf(id),
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), null, remoteAddr(), null));
    }

    private static String remoteAddr() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }
}
