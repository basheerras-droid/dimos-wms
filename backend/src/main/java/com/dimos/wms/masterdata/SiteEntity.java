package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;

/** Columns every business table carries: site, optimistic-lock version and who/when created and changed. */
@MappedSuperclass
abstract class SiteEntity {

    @Column(name = "site_id", nullable = false, updatable = false)
    Long siteId;

    @Version
    @Column(nullable = false)
    long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    String createdBy;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    String updatedBy;

    abstract Long getId();

    void stamp(String actor) {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
            createdBy = actor;
        }
        updatedAt = now;
        updatedBy = actor;
    }
}
