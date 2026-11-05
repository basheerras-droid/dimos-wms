package com.dimos.wms.audit;

/** What to write to the audit trail. Never put passwords or other secrets in these fields. */
public record AuditEvent(String actor, String action, Long siteId, String entityType, String entityId,
                         String beforeValue, String afterValue, String detail, String remoteAddr,
                         String correlationId) {

    public static AuditEvent of(String actor, String action, String entityType, String entityId, String detail) {
        return new AuditEvent(actor, action, null, entityType, entityId, null, null, detail, null, null);
    }
}
