package com.dimos.wms.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of the append-only audit trail. */
@Entity
@Table(name = "audit_log")
class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
    @SequenceGenerator(name = "audit_log_seq", sequenceName = "audit_log_seq", allocationSize = 1)
    private Long id;

    @Column(name = "site_id")
    private Long siteId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "before_value")
    private String beforeValue;

    @Column(name = "after_value")
    private String afterValue;

    private String detail;

    @Column(name = "remote_addr")
    private String remoteAddr;

    @Column(name = "correlation_id")
    private String correlationId;

    protected AuditLog() {
    }

    Long getId() { return id; }
    Long getSiteId() { return siteId; }
    Instant getOccurredAt() { return occurredAt; }
    String getActor() { return actor; }
    String getAction() { return action; }
    String getEntityType() { return entityType; }
    String getEntityId() { return entityId; }
    String getBeforeValue() { return beforeValue; }
    String getAfterValue() { return afterValue; }
    String getDetail() { return detail; }
    String getRemoteAddr() { return remoteAddr; }
    String getCorrelationId() { return correlationId; }

    AuditLog(AuditEvent e, Instant now) {
        this.siteId = e.siteId();
        this.occurredAt = now;
        this.actor = e.actor();
        this.action = e.action();
        this.entityType = e.entityType();
        this.entityId = e.entityId();
        this.beforeValue = e.beforeValue();
        this.afterValue = e.afterValue();
        this.detail = e.detail();
        this.remoteAddr = e.remoteAddr();
        this.correlationId = e.correlationId();
    }
}
