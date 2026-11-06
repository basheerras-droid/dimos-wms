package com.dimos.wms.identity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out what a user may do: the roles they have directly plus the roles of every enabled group they belong to;
 * their permissions are the union of the permissions of all those roles. (A workstation can restrict this further
 * for a request; that is applied separately, per request.)
 */
@Service
class RightsService {

    /** Why a permission is held: through a role the user has directly (group is null) or through a group. */
    record Via(String role, String group) {
    }

    record GroupMembership(String code, boolean enabled) {
    }

    record Rights(List<String> directRoles, List<GroupMembership> groups, List<String> roles, List<String> permissions,
                  Map<String, List<Via>> via) {
    }

    private final JdbcTemplate jdbc;
    private final AppRoleRepository roles;

    RightsService(JdbcTemplate jdbc, AppRoleRepository roles) {
        this.jdbc = jdbc;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public Rights of(AppUser user) {
        List<String> direct = user.getRoleCodes();
        List<GroupMembership> memberships = new ArrayList<>();
        // role code -> why the user has it (direct and/or through groups)
        Map<String, List<Via>> reasons = new LinkedHashMap<>();
        direct.forEach(r -> reasons.computeIfAbsent(r, k -> new ArrayList<>()).add(new Via(r, null)));

        jdbc.query("select g.code, g.enabled from user_group_member m join user_group g on g.id = m.user_group_id "
                + "where m.user_id = ? order by g.code", rs -> {
            memberships.add(new GroupMembership(rs.getString(1), rs.getBoolean(2)));
        }, user.getId());
        jdbc.query("select g.code, r.code from user_group_member m "
                + "join user_group g on g.id = m.user_group_id and g.enabled = true "
                + "join user_group_role gr on gr.user_group_id = g.id join app_role r on r.id = gr.role_id "
                + "where m.user_id = ? order by g.code, r.code", rs -> {
            String role = rs.getString(2);
            reasons.computeIfAbsent(role, k -> new ArrayList<>()).add(new Via(role, rs.getString(1)));
        }, user.getId());

        Set<String> allRoles = new TreeSet<>(reasons.keySet());
        Map<String, List<Via>> permissionVia = new LinkedHashMap<>();
        for (AppRole role : roles.findByCodeIn(allRoles)) {
            for (AppPermission p : role.getPermissions()) {
                List<Via> list = permissionVia.computeIfAbsent(p.getCode(), k -> new ArrayList<>());
                list.addAll(reasons.getOrDefault(role.getCode(), List.of()));
            }
        }
        List<String> permissions = new ArrayList<>(new LinkedHashSet<>(permissionVia.keySet()));
        permissions.sort(String::compareTo);
        return new Rights(direct, memberships, new ArrayList<>(allRoles), permissions, permissionVia);
    }
}
