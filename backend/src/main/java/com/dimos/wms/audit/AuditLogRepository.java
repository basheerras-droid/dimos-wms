package com.dimos.wms.audit;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
