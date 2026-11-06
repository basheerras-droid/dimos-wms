package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A role: a named set of permissions. The built-in roles ({@code builtin}) are locked; custom roles can be created,
 * changed and deleted by a super administrator. Roles are global (not tied to a site).
 */
@Entity
@Table(name = "app_role")
class AppRole {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_role_seq")
    @SequenceGenerator(name = "app_role_seq", sequenceName = "app_role_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "is_builtin", nullable = false, updatable = false)
    private boolean builtin;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<AppPermission> permissions = new HashSet<>();

    protected AppRole() {
    }

    /** A new custom role. */
    static AppRole create(String code, String name, String description, Set<AppPermission> permissions, String actor) {
        AppRole r = new AppRole();
        Instant now = Instant.now();
        r.code = code;
        r.name = name;
        r.description = description;
        r.builtin = false;
        r.permissions = new HashSet<>(permissions);
        r.createdAt = now;
        r.createdBy = actor;
        r.updatedAt = now;
        r.updatedBy = actor;
        return r;
    }

    void update(String name, String description, Set<AppPermission> newPermissions, String actor) {
        this.name = name;
        this.description = description;
        this.permissions.clear();
        this.permissions.addAll(newPermissions);
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    Long getId() { return id; }
    String getCode() { return code; }
    String getName() { return name; }
    String getDescription() { return description; }
    boolean isBuiltin() { return builtin; }
    long getVersion() { return version; }
    Set<AppPermission> getPermissions() { return permissions; }
}
