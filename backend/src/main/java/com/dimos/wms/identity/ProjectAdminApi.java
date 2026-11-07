package com.dimos.wms.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the project loader may load on the administration side: user groups, workstation groups, workstations and
 * parameter overrides. Rows are written through the normal admin services (same checks and audit as the screens);
 * a row that equals the stored record is reported as unchanged and not touched. Joins the caller's transaction.
 */
@Service
public class ProjectAdminApi {

    /** The file kinds, in the order they must be loaded (workstations need workstation groups, parameters need both). */
    public static final List<String> FILE_KEYS = List.of("user-groups", "workstation-groups", "workstations", "parameters");

    public record StepResult(String key, long created, long updated, long unchanged, long errors, List<String> messages) {
        public boolean ok() {
            return errors == 0;
        }
    }

    private final GroupAdminService userGroups;
    private final WorkstationGroupAdminService workstationGroups;
    private final WorkstationAdminService workstations;
    private final ParameterService parameters;
    private final WorkstationGroupRepository workstationGroupRepository;
    private final WorkstationRepository workstationRepository;
    private final UserGroupRepository userGroupRepository;
    private final JdbcTemplate jdbc;

    ProjectAdminApi(GroupAdminService userGroups, WorkstationGroupAdminService workstationGroups,
                    WorkstationAdminService workstations, ParameterService parameters,
                    WorkstationGroupRepository workstationGroupRepository, WorkstationRepository workstationRepository,
                    UserGroupRepository userGroupRepository, JdbcTemplate jdbc) {
        this.userGroups = userGroups;
        this.workstationGroups = workstationGroups;
        this.workstations = workstations;
        this.parameters = parameters;
        this.workstationGroupRepository = workstationGroupRepository;
        this.workstationRepository = workstationRepository;
        this.userGroupRepository = userGroupRepository;
        this.jdbc = jdbc;
    }

    private enum Outcome { CREATED, UPDATED, UNCHANGED }

    private static final class Tally {
        long created, updated, unchanged, errors;
        final List<String> messages = new ArrayList<>();
    }

    private interface RowAction {
        Outcome run(Map<String, String> row);
    }

    private StepResult each(String key, List<Map<String, String>> rows, RowAction action) {
        Tally t = new Tally();
        for (Map<String, String> row : rows) {
            try {
                switch (action.run(row)) {
                    case CREATED -> t.created++;
                    case UPDATED -> t.updated++;
                    case UNCHANGED -> t.unchanged++;
                }
            } catch (RuntimeException ex) {
                t.errors++;
                if (t.messages.size() < 20) {
                    String why = ex instanceof UserAdminException u && !u.details.isEmpty() ? String.join("; ", u.details)
                            : ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    t.messages.add("row " + row.get("#row") + ": " + why);
                }
            }
        }
        return new StepResult(key, t.created, t.updated, t.unchanged, t.errors, t.messages);
    }

    @Transactional
    public StepResult load(String key, List<Map<String, String>> rows, WmsUserPrincipal who) {
        return switch (key) {
            case "user-groups" -> each(key, rows, r -> userGroup(r, who));
            case "workstation-groups" -> each(key, rows, r -> workstationGroup(r, who));
            case "workstations" -> each(key, rows, r -> workstation(r, who));
            case "parameters" -> each(key, rows, r -> parameter(r, who));
            default -> throw new IllegalArgumentException("unknown project file type '" + key + "'");
        };
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private static String text(Map<String, String> row, String column) {
        String v = row.get(column);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String required(Map<String, String> row, String column) {
        String v = text(row, column);
        if (v == null) {
            throw new IllegalArgumentException(column + ": is required");
        }
        return v;
    }

    private static boolean bool(Map<String, String> row, String column, boolean fallback) {
        String v = text(row, column);
        if (v == null) {
            return fallback;
        }
        return switch (v.toUpperCase()) {
            case "TRUE", "YES", "1", "Y" -> true;
            case "FALSE", "NO", "0", "N" -> false;
            default -> throw new IllegalArgumentException(column + ": '" + v + "' is not TRUE or FALSE");
        };
    }

    private static List<String> list(Map<String, String> row, String column) {
        String v = text(row, column);
        return v == null ? List.of() : Arrays.stream(v.split("[;|]")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String code(Map<String, String> row, String column) {
        return required(row, column).toUpperCase();
    }

    // ---- user groups ------------------------------------------------------------------------------------

    private Outcome userGroup(Map<String, String> row, WmsUserPrincipal who) {
        String code = code(row, "code");
        List<String> roles = list(row, "roles").stream().map(String::toUpperCase).sorted().toList();
        var existing = userGroupRepository.findBySiteIdAndCode(who.siteId(), code);
        if (existing.isEmpty()) {
            userGroups.create(new GroupAdminService.Request(code, required(row, "name"), text(row, "description"), null,
                    bool(row, "enabled", true), roles, List.of(), null), who);
            return Outcome.CREATED;
        }
        GroupAdminService.GroupView v = userGroups.get(existing.get().getId(), who);
        String name = required(row, "name");
        boolean enabled = bool(row, "enabled", true);
        if (v.name().equals(name) && Objects.equals(v.description(), text(row, "description")) && v.enabled() == enabled
                && v.roles().stream().sorted().toList().equals(roles)) {
            return Outcome.UNCHANGED;
        }
        userGroups.update(v.id(), new GroupAdminService.Request(code, name, text(row, "description"), v.externalRef(), enabled, roles,
                v.members().stream().map(GroupAdminService.Member::id).toList(), v.version()), who);
        return Outcome.UPDATED;
    }

    // ---- workstation groups -----------------------------------------------------------------------------

    private Outcome workstationGroup(Map<String, String> row, WmsUserPrincipal who) {
        String code = code(row, "code");
        List<String> permissions = list(row, "allowed_permissions").stream().map(String::toUpperCase).sorted().toList();
        var existing = workstationGroupRepository.findBySiteIdAndCode(who.siteId(), code);
        if (existing.isEmpty()) {
            workstationGroups.create(new WorkstationGroupAdminService.Request(code, required(row, "name"), text(row, "description"),
                    bool(row, "enabled", true), permissions, null), who);
            return Outcome.CREATED;
        }
        WorkstationGroupAdminService.GroupView v = workstationGroups.get(existing.get().getId(), who);
        String name = required(row, "name");
        boolean enabled = bool(row, "enabled", true);
        if (v.name().equals(name) && Objects.equals(v.description(), text(row, "description")) && v.enabled() == enabled
                && v.allowedPermissions().stream().sorted().toList().equals(permissions)) {
            return Outcome.UNCHANGED;
        }
        workstationGroups.update(v.id(), new WorkstationGroupAdminService.Request(code, name, text(row, "description"), enabled,
                permissions, v.version()), who);
        return Outcome.UPDATED;
    }

    // ---- workstations -----------------------------------------------------------------------------------

    private Long locationId(String locationCode, WmsUserPrincipal who) {
        if (locationCode == null) {
            return null;
        }
        List<Long> ids = jdbc.queryForList("select id from location where site_id = ? and code = ?", Long.class, who.siteId(),
                locationCode.toUpperCase());
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("location_code: unknown location '" + locationCode + "'");
        }
        return ids.get(0);
    }

    private Long workstationGroupId(String groupCode, WmsUserPrincipal who) {
        if (groupCode == null) {
            return null;
        }
        return workstationGroupRepository.findBySiteIdAndCode(who.siteId(), groupCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("workstation_group_code: unknown workstation group '" + groupCode + "'"))
                .getId();
    }

    private Outcome workstation(Map<String, String> row, WmsUserPrincipal who) {
        String code = code(row, "code");
        String name = required(row, "name");
        String type = required(row, "type").toUpperCase();
        Long location = locationId(text(row, "location_code"), who);
        Long group = workstationGroupId(text(row, "workstation_group_code"), who);
        boolean checkIp = bool(row, "check_ip", false);
        boolean enabled = bool(row, "enabled", true);
        var existing = workstationRepository.findBySiteIdAndCode(who.siteId(), code);
        if (existing.isEmpty()) {
            workstations.create(new WorkstationAdminService.Request(code, name, type, location, group, text(row, "host_name"),
                    text(row, "ip_address"), checkIp, text(row, "default_printer"), enabled, null), who);
            return Outcome.CREATED;
        }
        WorkstationAdminService.WorkstationView v = workstations.get(existing.get().getId(), who);
        if (v.name().equals(name) && v.type().equals(type) && Objects.equals(v.locationId(), location)
                && Objects.equals(v.workstationGroupId(), group) && Objects.equals(v.hostName(), text(row, "host_name"))
                && Objects.equals(v.ipAddress(), text(row, "ip_address")) && v.checkIp() == checkIp
                && Objects.equals(v.defaultPrinter(), text(row, "default_printer")) && v.enabled() == enabled) {
            return Outcome.UNCHANGED;
        }
        workstations.update(v.id(), new WorkstationAdminService.Request(code, name, type, location, group, text(row, "host_name"),
                text(row, "ip_address"), checkIp, text(row, "default_printer"), enabled, v.version()), who);
        return Outcome.UPDATED;
    }

    // ---- parameters -------------------------------------------------------------------------------------

    private Outcome parameter(Map<String, String> row, WmsUserPrincipal who) {
        String code = required(row, "parameter");
        String scope = required(row, "scope").toUpperCase();
        String value = text(row, "value");
        if (value == null) {
            throw new IllegalArgumentException("value: is required");
        }
        String scopeCode = text(row, "scope_code");
        Long ref = switch (scope) {
            case "SITE", "GLOBAL" -> null;
            case "WS_GROUP" -> workstationGroupId(scopeCode, who);
            case "WORKSTATION" -> workstationRepository.findBySiteIdAndCode(who.siteId(), scopeCode == null ? "" : scopeCode.toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("scope_code: unknown workstation '" + scopeCode + "'")).getId();
            default -> throw new IllegalArgumentException("scope: must be SITE, WS_GROUP or WORKSTATION");
        };
        if (!scope.equals("SITE") && !scope.equals("GLOBAL") && ref == null) {
            throw new IllegalArgumentException("scope_code: is required for scope " + scope);
        }
        ParameterService.ParameterView view = parameters.list(null, who).stream().filter(p -> p.code().equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("parameter: unknown parameter '" + code + "'"));
        Function<ParameterService.Override, Boolean> same = o -> o.scope().equals(scope)
                && (ref == null || o.scopeRefId() == ref);
        ParameterService.Override current = view.overrides().stream().filter(same::apply).findFirst().orElse(null);
        if (current != null && current.value().equals(value)) {
            return Outcome.UNCHANGED;
        }
        parameters.set(code, new ParameterService.SetRequest(scope, ref, value, current == null ? null : current.version()), who);
        return current == null ? Outcome.CREATED : Outcome.UPDATED;
    }
}
