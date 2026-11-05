package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read-only view of a role (roles are maintained by migrations / the administration module later). */
@Entity
@Table(name = "app_role")
class AppRole {

    @Id
    private Long id;

    @Column(nullable = false, updatable = false, insertable = false)
    private String code;

    protected AppRole() {
    }

    String getCode() {
        return code;
    }
}
