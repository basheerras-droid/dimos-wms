package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

/** Read-only view of a role (roles are maintained by migrations / the administration module later). */
@Entity
@Table(name = "app_role")
class AppRole {

    @Id
    private Long id;

    @Column(nullable = false, updatable = false, insertable = false)
    private String code;

    @Column(updatable = false, insertable = false)
    private String name;

    @Column(updatable = false, insertable = false)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AppPermission> permissions = new HashSet<>();

    protected AppRole() {
    }

    String getCode() {
        return code;
    }

    String getName() {
        return name;
    }

    String getDescription() {
        return description;
    }

    Set<AppPermission> getPermissions() {
        return permissions;
    }
}
