package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only view of a permission (maintained by migrations). */
@Entity
@Table(name = "app_permission")
class AppPermission {

    @Id
    private Long id;

    @Column(nullable = false, updatable = false, insertable = false)
    private String code;

    protected AppPermission() {
    }

    String getCode() {
        return code;
    }
}
