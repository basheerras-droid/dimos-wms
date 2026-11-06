package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Roles and the permission catalogue. The four built-in roles are locked. Custom roles can be created, changed and
 * deleted, but only by a super administrator. Changing a role signs out the people who have it, so they get the new
 * rights at their next login.
 */
@Service
class RoleAdminService {

    static final String SUPER_ADMIN = "SUPER_ADMIN";

    record PermissionView(String code, String module, String description) {
    }

    record RoleView(Long id, String code, String name, String description, boolean builtin, List<String> permissions,
                    int userCount, int groupCount, long version) {
    }

    record RoleRequest(
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,49}", message = "capital letters, digits and _, starting with a letter") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull List<@NotBlank String> permissions,
            Long version) {
    }

    private record RoleAudit(String code, String name, String description, List<String> permissions) {
    }

    private final AppRoleRepository roles;
    private final AppPermissionRepository permissions;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final UserSessions sessions;
    private final JsonMapper json;

    RoleAdminService(AppRoleRepository roles, AppPermissionRepository permissions, JdbcTemplate jdbc,
                     AuditService audit, UserSessions sessions, JsonMapper json) {
        this.roles = roles;
        this.permissions = permissions;
        this.jdbc = jdbc;
        this.audit = audit;
        this.sessions = sessions;
        this.json = json;
    }

    public List<PermissionView> permissionCatalogue() {
        return PermissionCatalogue.ALL.stream()
                .map(e -> new PermissionView(e.code(), e.module(), e.description())).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        Map<Long, Integer> users = counts("select role_id, count(*) from user_role group by role_id");
        Map<Long, Integer> groups = Map.of(); // user groups arrive in part 2
        return roles.findAll(Sort.by("id")).stream().map(r -> view(r, users, groups)).toList();
    }

    @Transactional
    public RoleView create(RoleRequest request, WmsUserPrincipal who) {
        requireSuperAdmin(who);
        String code = request.code();
        if (PermissionCatalogue.exists("ROLE_" + code)) {
            // the login turns role X into the authority ROLE_X; that must never equal a permission
            throw UserAdminException.invalid("code", "this code is reserved");
        }
        if (roles.findByCodeIn(List.of(code)).size() > 0) {
            throw UserAdminException.invalid("code", "this code is already used");
        }
        AppRole saved = roles.saveAndFlush(AppRole.create(code, request.name().trim(), blankToNull(request.description()),
                resolve(request.permissions()), who.username()));
        record("ROLE_CREATED", who, saved, null, audited(saved));
        return view(saved, Map.of(), Map.of());
    }

    @Transactional
    public RoleView update(Long id, RoleRequest request, WmsUserPrincipal who) {
        requireSuperAdmin(who);
        AppRole role = load(id);
        requireCustom(role, "changed");
        if (request.version() == null) {
            throw UserAdminException.invalid("version", "must be sent when updating");
        }
        if (request.version() != role.getVersion()) {
            throw UserAdminException.stale();
        }
        if (!request.code().equals(role.getCode())) {
            throw UserAdminException.invalid("code", "the code of a role cannot be changed");
        }
        RoleAudit before = audited(role);
        role.update(request.name().trim(), blankToNull(request.description()), resolve(request.permissions()), who.username());
        AppRole saved = roles.saveAndFlush(role);
        record("ROLE_UPDATED", who, saved, before, audited(saved));
        signOutHolders(saved.getId(), who);
        return view(saved, counts("select role_id, count(*) from user_role where role_id = " + id + " group by role_id"),
                Map.of());
    }

    @Transactional
    public void delete(Long id, WmsUserPrincipal who) {
        requireSuperAdmin(who);
        AppRole role = load(id);
        requireCustom(role, "deleted");
        Integer users = jdbc.queryForObject("select count(*) from user_role where role_id = ?", Integer.class, id);
        Integer groups = 0; // user groups arrive in part 2
        List<String> used = new java.util.ArrayList<>();
        if (users != null && users > 0) {
            used.add(users + (users == 1 ? " user" : " users"));
        }
        if (groups != null && groups > 0) {
            used.add(groups + (groups == 1 ? " user group" : " user groups"));
        }
        if (!used.isEmpty()) {
            throw new UserAdminException(org.springframework.http.HttpStatus.CONFLICT, "IN_USE",
                    "Role " + role.getCode() + " cannot be deleted: it is still used by " + String.join(" and ", used)
                            + ". Remove the role from them first.", List.of());
        }
        RoleAudit before = audited(role);
        try {
            roles.delete(role);
            roles.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new UserAdminException(org.springframework.http.HttpStatus.CONFLICT, "IN_USE",
                    "This role is still used by other records and cannot be deleted.", List.of());
        }
        record("ROLE_DELETED", who, role, before, null);
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private static void requireSuperAdmin(WmsUserPrincipal who) {
        if (!who.roles().contains(SUPER_ADMIN)) {
            throw UserAdminException.notAllowed("Only a super administrator can change roles.");
        }
    }

    private static void requireCustom(AppRole role, String verb) {
        if (role.isBuiltin()) {
            throw UserAdminException.notAllowed("The built-in role " + role.getCode() + " is locked and cannot be " + verb + ".");
        }
    }

    private AppRole load(Long id) {
        return roles.findById(id).orElseThrow(UserAdminException::notFound);
    }

    private Set<AppPermission> resolve(List<String> codes) {
        Set<String> wanted = new HashSet<>(codes);
        Set<AppPermission> found = new HashSet<>(permissions.findByCodeIn(wanted));
        Set<String> missing = new TreeSet<>(wanted);
        found.forEach(p -> missing.remove(p.getCode()));
        if (!missing.isEmpty()) {
            throw UserAdminException.invalid("permissions", "unknown permission " + String.join(", ", missing));
        }
        return found;
    }

    private Map<Long, Integer> counts(String sql) {
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query(sql, rs -> {
            result.put(rs.getLong(1), rs.getInt(2));
        });
        return result;
    }

    private RoleView view(AppRole r, Map<Long, Integer> users, Map<Long, Integer> groups) {
        List<String> codes = r.getPermissions().stream().map(AppPermission::getCode).sorted().toList();
        return new RoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(), r.isBuiltin(), codes,
                users.getOrDefault(r.getId(), 0), groups.getOrDefault(r.getId(), 0), r.getVersion());
    }

    private RoleAudit audited(AppRole r) {
        return new RoleAudit(r.getCode(), r.getName(), r.getDescription(),
                r.getPermissions().stream().map(AppPermission::getCode).sorted().toList());
    }

    private void signOutHolders(Long roleId, WmsUserPrincipal who) {
        List<Long> holders = jdbc.queryForList(
                "select distinct user_id from user_role where role_id = ?", Long.class, roleId);
        holders.stream().filter(u -> !u.equals(who.userId())).forEach(sessions::invalidateAll);
    }

    private void record(String action, WmsUserPrincipal who, AppRole role, RoleAudit before, RoleAudit after) {
        audit.record(new AuditEvent(who.username(), action, who.siteId(), "app_role", String.valueOf(role.getId()),
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), null, remoteAddr(), null));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String remoteAddr() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }
}
