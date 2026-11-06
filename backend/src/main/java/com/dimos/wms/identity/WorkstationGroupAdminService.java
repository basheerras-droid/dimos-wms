package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Workstation groups. A group may carry a list of allowed permissions; at a workstation of that group users only have
 * the permissions in the list (empty list = no restriction). The restriction is applied by {@link WorkstationFilter}.
 */
@Service
class WorkstationGroupAdminService {

    private static final int MAX_PAGE_SIZE = 200;

    record Request(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,49}", message = "2-50 characters: letters, digits, . _ -") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            Boolean enabled,
            @NotNull List<@NotBlank String> allowedPermissions,
            Long version) {
    }

    record GroupView(Long id, String code, String name, String description, boolean enabled, List<String> allowedPermissions,
                     int workstationCount, long version, Instant updatedAt, String updatedBy) {
    }

    record GroupPage(List<GroupView> items, long total, int page, int size) {
    }

    private record GroupAudit(String code, String name, String description, boolean enabled, List<String> allowedPermissions) {
    }

    private final WorkstationGroupRepository groups;
    private final AppPermissionRepository permissions;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final WorkstationResolver resolver;
    private final ParameterService parameters;
    private final JsonMapper json;

    WorkstationGroupAdminService(WorkstationGroupRepository groups, AppPermissionRepository permissions, JdbcTemplate jdbc,
                                 AuditService audit, WorkstationResolver resolver, ParameterService parameters, JsonMapper json) {
        this.groups = groups;
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.audit = audit;
        this.resolver = resolver;
        this.parameters = parameters;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public GroupPage search(String q, Boolean enabled, int page, int size, String sort, WmsUserPrincipal who) {
        Specification<WorkstationGroup> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("siteId"), who.siteId()));
            if (q != null && !q.isBlank()) {
                String p = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("code")), p, '\\'), cb.like(cb.lower(root.get("name")), p, '\\')));
            }
            if (enabled != null) {
                all.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Page<WorkstationGroup> result = groups.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortOf(sort)));
        Map<Long, List<String>> allowed = new HashMap<>();
        jdbc.query("select x.workstation_group_id, p.code from workstation_group_permission x "
                + "join app_permission p on p.id = x.permission_id join workstation_group g on g.id = x.workstation_group_id "
                + "where g.site_id = ? order by p.code", rs -> {
            allowed.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        }, who.siteId());
        Map<Long, Integer> counts = new HashMap<>();
        jdbc.query("select workstation_group_id, count(*) from workstation where site_id = ? and workstation_group_id is not null "
                + "group by workstation_group_id", rs -> {
            counts.put(rs.getLong(1), rs.getInt(2));
        }, who.siteId());
        return new GroupPage(result.getContent().stream()
                .map(g -> view(g, allowed.getOrDefault(g.getId(), List.of()), counts.getOrDefault(g.getId(), 0))).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public GroupView get(Long id, WmsUserPrincipal who) {
        return detail(load(id, who));
    }

    @Transactional
    public GroupView create(Request request, WmsUserPrincipal who) {
        String code = request.code().trim().toUpperCase();
        if (groups.findBySiteIdAndCode(who.siteId(), code).isPresent()) {
            throw UserAdminException.invalid("code", "this code is already used");
        }
        List<AppPermission> allowed = resolve(request.allowedPermissions());
        WorkstationGroup saved = groups.saveAndFlush(WorkstationGroup.create(who.siteId(), code, request.name().trim(),
                blankToNull(request.description()), request.enabled() == null || request.enabled(), who.username()));
        writeAllowed(saved.getId(), allowed);
        record("WORKSTATION_GROUP_CREATED", who, saved, null, audited(saved));
        resolver.clearCache();
        return detail(saved);
    }

    @Transactional
    public GroupView update(Long id, Request request, WmsUserPrincipal who) {
        WorkstationGroup group = load(id, who);
        if (request.version() == null) {
            throw UserAdminException.invalid("version", "must be sent when updating");
        }
        if (request.version() != group.getVersion()) {
            throw UserAdminException.stale();
        }
        if (!request.code().trim().equalsIgnoreCase(group.getCode())) {
            throw UserAdminException.invalid("code", "the code of a workstation group cannot be changed");
        }
        List<AppPermission> allowed = resolve(request.allowedPermissions());
        GroupAudit before = audited(group);
        group.update(request.name().trim(), blankToNull(request.description()), request.enabled() == null || request.enabled(), who.username());
        WorkstationGroup saved = groups.saveAndFlush(group);
        writeAllowed(id, allowed);
        record("WORKSTATION_GROUP_UPDATED", who, saved, before, audited(saved));
        resolver.clearCache();
        return detail(saved);
    }

    @Transactional
    public void delete(Long id, WmsUserPrincipal who) {
        WorkstationGroup group = load(id, who);
        Integer used = jdbc.queryForObject("select count(*) from workstation where workstation_group_id = ?", Integer.class, id);
        if (used != null && used > 0) {
            throw new UserAdminException(org.springframework.http.HttpStatus.CONFLICT, "IN_USE",
                    "Workstation group " + group.getCode() + " cannot be deleted: it is still used by " + used
                            + (used == 1 ? " workstation" : " workstations") + ". Move them to another group first.", List.of());
        }
        GroupAudit before = audited(group);
        jdbc.update("delete from workstation_group_permission where workstation_group_id = ?", id);
        groups.delete(group);
        groups.flush();
        record("WORKSTATION_GROUP_DELETED", who, group, before, null);
        parameters.deleteValuesOf(ParameterService.Scope.WS_GROUP, id);
        resolver.clearCache();
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private List<AppPermission> resolve(List<String> codes) {
        Set<String> wanted = new HashSet<>(codes);
        List<AppPermission> found = permissions.findByCodeIn(wanted);
        Set<String> missing = new TreeSet<>(wanted);
        found.forEach(p -> missing.remove(p.getCode()));
        if (!missing.isEmpty()) {
            throw UserAdminException.invalid("allowedPermissions", "unknown permission " + String.join(", ", missing));
        }
        return found;
    }

    private void writeAllowed(Long groupId, List<AppPermission> allowed) {
        jdbc.update("delete from workstation_group_permission where workstation_group_id = ?", groupId);
        for (AppPermission p : allowed) {
            jdbc.update("insert into workstation_group_permission (workstation_group_id, permission_id) values (?, ?)", groupId, p.getId());
        }
    }

    private WorkstationGroup load(Long id, WmsUserPrincipal who) {
        return groups.findById(id).filter(g -> who.siteId().equals(g.getSiteId())).orElseThrow(UserAdminException::notFound);
    }

    private List<String> allowedOf(Long groupId) {
        return jdbc.queryForList("select p.code from workstation_group_permission x join app_permission p on p.id = x.permission_id "
                + "where x.workstation_group_id = ? order by p.code", String.class, groupId);
    }

    private GroupView detail(WorkstationGroup g) {
        Integer used = jdbc.queryForObject("select count(*) from workstation where workstation_group_id = ?", Integer.class, g.getId());
        return view(g, allowedOf(g.getId()), used == null ? 0 : used);
    }

    private GroupView view(WorkstationGroup g, List<String> allowed, int workstations) {
        return new GroupView(g.getId(), g.getCode(), g.getName(), g.getDescription(), g.isEnabled(), allowed, workstations,
                g.getVersion(), g.getUpdatedAt(), g.getUpdatedBy());
    }

    private GroupAudit audited(WorkstationGroup g) {
        return new GroupAudit(g.getCode(), g.getName(), g.getDescription(), g.isEnabled(), allowedOf(g.getId()));
    }

    private void record(String action, WmsUserPrincipal who, WorkstationGroup group, GroupAudit before, GroupAudit after) {
        audit.record(new AuditEvent(who.username(), action, who.siteId(), "workstation_group", String.valueOf(group.getId()),
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), null, remoteAddr(), null));
    }

    private static Sort sortOf(String sort) {
        String spec = sort == null || sort.isBlank() ? "code,asc" : sort;
        String[] parts = spec.split(",");
        String field = parts[0].trim();
        if (!Set.of("code", "name", "enabled", "updatedAt").contains(field)) {
            throw UserAdminException.invalid("sort", "cannot sort by '" + field + "'");
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field).and(Sort.by("id"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String remoteAddr() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }
}
