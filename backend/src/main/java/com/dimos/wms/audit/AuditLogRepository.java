package com.dimos.wms.audit;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Query("select distinct a.action from AuditLog a where a.siteId = :site or a.siteId is null order by a.action")
    List<String> actions(@Param("site") Long site);

    @Query("select distinct a.entityType from AuditLog a where a.entityType is not null "
            + "and (a.siteId = :site or a.siteId is null) order by a.entityType")
    List<String> entityTypes(@Param("site") Long site);
}
