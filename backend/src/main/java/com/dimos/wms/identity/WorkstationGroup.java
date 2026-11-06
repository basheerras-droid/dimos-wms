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

/** A set of workstations with the same rules. The allowed permissions live in a link table (empty = no restriction). */
@Entity
@Table(name = "workstation_group")
class WorkstationGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "workstation_group_seq")
    @SequenceGenerator(name = "workstation_group_seq", sequenceName = "workstation_group_seq", allocationSize = 1)
    private Long id;

    @Column(name = "site_id", nullable = false, updatable = false)
    private Long siteId;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

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

    protected WorkstationGroup() {
    }

    static WorkstationGroup create(Long siteId, String code, String name, String description, boolean enabled, String actor) {
        WorkstationGroup g = new WorkstationGroup();
        Instant now = Instant.now();
        g.siteId = siteId;
        g.code = code;
        g.name = name;
        g.description = description;
        g.enabled = enabled;
        g.createdAt = now;
        g.createdBy = actor;
        g.updatedAt = now;
        g.updatedBy = actor;
        return g;
    }

    void update(String name, String description, boolean enabled, String actor) {
        this.name = name;
        this.description = description;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    Long getId() { return id; }
    Long getSiteId() { return siteId; }
    String getCode() { return code; }
    String getName() { return name; }
    String getDescription() { return description; }
    boolean isEnabled() { return enabled; }
    long getVersion() { return version; }
    Instant getUpdatedAt() { return updatedAt; }
    String getUpdatedBy() { return updatedBy; }
}
