package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A permission. The list is fixed in code ({@link PermissionCatalogue}); the table is the target of the role links. */
@Entity
@Table(name = "app_permission")
class AppPermission {

    @Id
    private Long id;

    @Column(nullable = false, updatable = false, insertable = false)
    private String code;

    @Column(updatable = false, insertable = false)
    private String description;

    @Column(nullable = false, updatable = false, insertable = false)
    private String module;

    protected AppPermission() {
    }

    Long getId() {
        return id;
    }

    String getCode() {
        return code;
    }

    String getDescription() {
        return description;
    }

    String getModule() {
        return module;
    }
}
