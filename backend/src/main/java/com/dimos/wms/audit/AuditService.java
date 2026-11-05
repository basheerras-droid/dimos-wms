package com.dimos.wms.audit;

import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the audit module. */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock = Clock.systemUTC();

    AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Runs in its own transaction so a failed login is still recorded even if the caller rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        AuditEvent e = event.correlationId() != null ? event : new AuditEvent(event.actor(),
                event.action(), event.siteId(), event.entityType(), event.entityId(), event.beforeValue(),
                event.afterValue(), event.detail(), event.remoteAddr(), MDC.get("correlationId"));
        repository.save(new AuditLog(e, clock.instant()));
    }
}
