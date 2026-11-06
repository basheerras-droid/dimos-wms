package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * User groups: a name, some roles and some members. Members inherit the roles of the group. Because a group can carry
 * the SUPER_ADMIN role, only a super administrator may create or change such a group. Every change signs the affected
 * members out so they get their new rights at the next sign-in.
 */
@Service
class GroupAdminService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final int MAX_PAGE_SIZE = 200;

    record Request(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,49}", message = "2-50 characters: letters, digits, . _ -") String code,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @Size(max = 255) String externalRef,
            Boolean enabled,
            @NotNull List<@NotBlank String> roles,
            @NotNull List<Long> memberIds,
            Long version) {
    }

    record Member(Long id, String username, String displayName) {
    }

    /** {@code members} is only filled when one group is read (not in lists). */
    record GroupView(Long id, String code, String name, String description, String externalRef, boolean enabled,
                     List<String> roles, int memberCount, List<Member> members, long version,
                     java.time.Instant updatedAt, String updatedBy) {
    }

    record GroupPage(List<GroupView> items, long total, int page, int size) {
    }

    private record GroupAudit(String code, String name, String description, String externalRef, boolean enabled,
                              List<String> roles, List<String> members) {
    }

    private final UserGroupRepository groups;
    private final AppRoleRepository roles;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final AuditService audit;
    private final UserSessions sessions;
    private final JsonMapper json;

    GroupAdminService(UserGroupRepository groups, AppRoleRepository roles, JdbcTemplate jdbc,
                      NamedParameterJdbcTemplate named, AuditService audit, UserSessions sessions, JsonMapper json) {
        this.groups = groups;
        this.roles = roles;
        this.jdbc = jdbc;
        this.named = named;
        this.audit = audit;
        this.sessions = sessions;
        this.json = json;
    }

    // ---- reading ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public GroupPage search(String q, Boolean enabled, int page, int size, String sort, WmsUserPrincipal who) {
        Specification<UserGroup> spec = (root, query, cb) -> {
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
        Page<UserGroup> result = groups.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortOf(sort)));
        Map<Long, List<String>> roleCodes = roleCodesBySite(who.siteId());
        Map<Long, Integer> memberCounts = new HashMap<>();
        jdbc.query("select m.user_group_id, count(*) from user_group_member m join user_group g on g.id = m.user_group_id "
                + "where g.site_id = ? group by m.user_group_id", rs -> {
            memberCounts.put(rs.getLong(1), rs.getInt(2));
        }, who.siteId());
        List<GroupView> items = result.getContent().stream()
                .map(g -> view(g, roleCodes.getOrDefault(g.getId(), List.of()), memberCounts.getOrDefault(g.getId(), 0), null))
                .toList();
        return new GroupPage(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public GroupView get(Long id, WmsUserPrincipal who) {
        return detail(load(id, who));
    }

    // ---- changing ---------------------------------------------------------------------------------------

    @Transactional
    public GroupView create(Request request, WmsUserPrincipal who) {
        String code = request.code().trim().toUpperCase();
        if (groups.findBySiteIdAndCode(who.siteId(), code).isPresent()) {
            throw UserAdminException.invalid("code", "this code is already used");
        }
        List<AppRole> newRoles = resolveRoles(request.roles());
        requireSuperAdminForSuperRole(newRoles, who);
        List<Long> memberIds = checkedMembers(request.memberIds(), who);
        UserGroup saved = groups.saveAndFlush(UserGroup.create(who.siteId(), code, request.name().trim(),
                blankToNull(request.description()), blankToNull(request.externalRef()),
                request.enabled() == null || request.enabled(), who.username()));
        writeLinks(saved.getId(), newRoles, memberIds);
        GroupView after = detail(saved);
        record("GROUP_CREATED", who, saved, null, audited(saved));
        signOut(memberIds, who);
        return after;
    }

    @Transactional
    public GroupView update(Long id, Request request, WmsUserPrincipal who) {
        UserGroup group = load(id, who);
        if (request.version() == null) {
            throw UserAdminException.invalid("version", "must be sent when updating");
        }
        if (request.version() != group.getVersion()) {
            throw UserAdminException.stale();
        }
        if (!request.code().trim().equalsIgnoreCase(group.getCode())) {
            throw UserAdminException.invalid("code", "the code of a group cannot be changed");
        }
        requireSuperAdminToChange(group, who);
        List<AppRole> newRoles = resolveRoles(request.roles());
        requireSuperAdminForSuperRole(newRoles, who);
        List<Long> memberIds = checkedMembers(request.memberIds(), who);

        GroupAudit before = audited(group);
        List<Long> oldMembers = memberIdsOf(id);
        group.update(request.name().trim(), blankToNull(request.description()), blankToNull(request.externalRef()),
                request.enabled() == null || request.enabled(), who.username());
        UserGroup saved = groups.saveAndFlush(group);
        writeLinks(id, newRoles, memberIds);
        record("GROUP_UPDATED", who, saved, before, audited(saved));
        Set<Long> affected = new LinkedHashSet<>(oldMembers);
        affected.addAll(memberIds);
        signOut(affected, who);
        return detail(saved);
    }

    @Transactional
    public void delete(Long id, WmsUserPrincipal who) {
        UserGroup group = load(id, who);
        requireSuperAdminToChange(group, who);
        GroupAudit before = audited(group);
        List<Long> members = memberIdsOf(id);
        jdbc.update("delete from user_group_member where user_group_id = ?", id);
        jdbc.update("delete from user_group_role where user_group_id = ?", id);
        groups.delete(group);
        groups.flush();
        record("GROUP_DELETED", who, group, before, null);
        signOut(members, who);
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private void requireSuperAdminToChange(UserGroup group, WmsUserPrincipal who) {
        boolean holdsSuper = roleCodesOf(group.getId()).contains(SUPER_ADMIN);
        if (holdsSuper && !who.roles().contains(SUPER_ADMIN)) {
            throw UserAdminException.notAllowed("Only a super administrator can change a group that carries the SUPER_ADMIN role.");
        }
    }

    private static void requireSuperAdminForSuperRole(List<AppRole> newRoles, WmsUserPrincipal who) {
        if (newRoles.stream().anyMatch(r -> SUPER_ADMIN.equals(r.getCode())) && !who.roles().contains(SUPER_ADMIN)) {
            throw UserAdminException.notAllowed("Only a super administrator can give a group the SUPER_ADMIN role.");
        }
    }

    private List<AppRole> resolveRoles(List<String> requested) {
        Set<String> wanted = new HashSet<>(requested);
        List<AppRole> found = roles.findByCodeIn(wanted);
        Set<String> missing = new java.util.TreeSet<>(wanted);
        found.forEach(r -> missing.remove(r.getCode()));
        if (!missing.isEmpty()) {
            throw UserAdminException.invalid("roles", "unknown role " + String.join(", ", missing));
        }
        return found;
    }

    /** The ids, without duplicates, after checking that every one is a user of this site. */
    private List<Long> checkedMembers(List<Long> ids, WmsUserPrincipal who) {
        List<Long> distinct = new ArrayList<>(new LinkedHashSet<>(ids));
        if (!distinct.isEmpty()) {
            Integer found = named.queryForObject("select count(*) from app_user where site_id = :site and id in (:ids)",
                    new MapSqlParameterSource("site", who.siteId()).addValue("ids", distinct), Integer.class);
            if (found == null || found != distinct.size()) {
                throw UserAdminException.invalid("memberIds", "unknown user");
            }
        }
        return distinct;
    }

    private void writeLinks(Long groupId, List<AppRole> newRoles, List<Long> memberIds) {
        jdbc.update("delete from user_group_role where user_group_id = ?", groupId);
        jdbc.update("delete from user_group_member where user_group_id = ?", groupId);
        for (AppRole r : newRoles) {
            jdbc.update("insert into user_group_role (user_group_id, role_id) values (?, ?)", groupId, r.getId());
        }
        jdbc.batchUpdate("insert into user_group_member (user_group_id, user_id) values (?, ?)",
                memberIds.stream().map(m -> new Object[] {groupId, m}).toList());
    }

    private UserGroup load(Long id, WmsUserPrincipal who) {
        return groups.findById(id).filter(g -> who.siteId().equals(g.getSiteId())).orElseThrow(UserAdminException::notFound);
    }

    private List<String> roleCodesOf(Long groupId) {
        return jdbc.queryForList("select r.code from user_group_role gr join app_role r on r.id = gr.role_id "
                + "where gr.user_group_id = ? order by r.code", String.class, groupId);
    }

    private List<Long> memberIdsOf(Long groupId) {
        return jdbc.queryForList("select user_id from user_group_member where user_group_id = ?", Long.class, groupId);
    }

    private List<Member> membersOf(Long groupId) {
        return jdbc.query("select u.id, u.username, u.display_name from user_group_member m join app_user u on u.id = m.user_id "
                        + "where m.user_group_id = ? order by u.username",
                (rs, i) -> new Member(rs.getLong(1), rs.getString(2), rs.getString(3)), groupId);
    }

    private Map<Long, List<String>> roleCodesBySite(Long siteId) {
        Map<Long, List<String>> result = new HashMap<>();
        jdbc.query("select gr.user_group_id, r.code from user_group_role gr join app_role r on r.id = gr.role_id "
                + "join user_group g on g.id = gr.user_group_id where g.site_id = ? order by r.code", rs -> {
            result.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2));
        }, siteId);
        return result;
    }

    private GroupView detail(UserGroup g) {
        List<Member> members = membersOf(g.getId());
        return view(g, roleCodesOf(g.getId()), members.size(), members);
    }

    private GroupView view(UserGroup g, List<String> roleCodes, int memberCount, List<Member> members) {
        return new GroupView(g.getId(), g.getCode(), g.getName(), g.getDescription(), g.getExternalRef(), g.isEnabled(),
                roleCodes, memberCount, members, g.getVersion(), g.getUpdatedAt(), g.getUpdatedBy());
    }

    private GroupAudit audited(UserGroup g) {
        return new GroupAudit(g.getCode(), g.getName(), g.getDescription(), g.getExternalRef(), g.isEnabled(),
                roleCodesOf(g.getId()), membersOf(g.getId()).stream().map(Member::username).toList());
    }

    private void signOut(java.util.Collection<Long> userIds, WmsUserPrincipal who) {
        userIds.stream().filter(u -> !u.equals(who.userId())).forEach(sessions::invalidateAll);
    }

    private void record(String action, WmsUserPrincipal who, UserGroup group, GroupAudit before, GroupAudit after) {
        audit.record(new AuditEvent(who.username(), action, who.siteId(), "user_group", String.valueOf(group.getId()),
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
