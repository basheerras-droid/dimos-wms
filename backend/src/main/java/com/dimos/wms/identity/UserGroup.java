package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A group of users. Members and roles are kept in the link tables (read and written with SQL by
 * {@link GroupAdminService}); this entity holds the group itself.
 */
@Entity
@Table(name = "user_group")
class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_group_seq")
    @SequenceGenerator(name = "user_group_seq", sequenceName = "user_group_seq", allocationSize = 1)
    private Long id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Reserved: the name of the LDAP / Active Directory group this group will be mapped to later. */
    @Column(name = "external_ref")
    private String externalRef;

    @Column(nullable = false)
    private boolean enabled = true;

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

    protected UserGroup() {
    }

    static UserGroup create(Long siteId, String code, String name, String description, String externalRef,
                            boolean enabled, String actor) {
        UserGroup g = new UserGroup();
        Instant now = Instant.now();
        g.siteId = siteId;
        g.code = code;
        g.name = name;
        g.description = description;
        g.externalRef = externalRef;
        g.enabled = enabled;
        g.createdAt = now;
        g.createdBy = actor;
        g.updatedAt = now;
        g.updatedBy = actor;
        return g;
    }

    void update(String name, String description, String externalRef, boolean enabled, String actor) {
        this.name = name;
        this.description = description;
        this.externalRef = externalRef;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    Long getId() { return id; }
    Long getSiteId() { return siteId; }
    String getCode() { return code; }
    String getName() { return name; }
    String getDescription() { return description; }
    String getExternalRef() { return externalRef; }
    boolean isEnabled() { return enabled; }
    long getVersion() { return version; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
}
