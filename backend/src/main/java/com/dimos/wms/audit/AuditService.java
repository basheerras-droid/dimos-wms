package com.dimos.wms.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public API of the audit module.
 *
 * Normally an event is written at once in its own transaction, so a failed login is still recorded even if the caller
 * rolls back. A bulk operation that may still be rolled back (loading a project data set) can hold the events back with
 * {@link #startHolding()} and write them in one go, after the commit, with {@link #recordAll(List)}.
 */
@Service
public class AuditService {

    private static final ThreadLocal<List<AuditEvent>> HELD = new ThreadLocal<>();

    private final AuditWriter writer;

    AuditService(AuditWriter writer) {
        this.writer = writer;
    }

    /** From now on, events recorded by this thread are held back instead of written. */
    public static void startHolding() {
        HELD.set(new ArrayList<>());
    }

    /** Stops holding and returns what was held (empty if nothing was started). */
    public static List<AuditEvent> endHolding() {
        List<AuditEvent> held = HELD.get();
        HELD.remove();
        return held == null ? List.of() : held;
    }

    public void record(AuditEvent event) {
        AuditEvent e = withCorrelation(event);
        List<AuditEvent> held = HELD.get();
        if (held != null) {
            held.add(e);
        } else {
            writer.write(List.of(e));
        }
    }

    /** Writes many events in a single transaction (used for events that were held back). */
    public void recordAll(List<AuditEvent> events) {
        if (!events.isEmpty()) {
            writer.write(events.stream().map(AuditService::withCorrelation).toList());
        }
    }

    private static AuditEvent withCorrelation(AuditEvent event) {
        return event.correlationId() != null ? event : new AuditEvent(event.actor(),
                event.action(), event.siteId(), event.entityType(), event.entityId(), event.beforeValue(),
                event.afterValue(), event.detail(), event.remoteAddr(), MDC.get("correlationId"));
    }
}

/** Writes audit rows in their own transaction. A separate bean, so the transaction rule applies to every call. */
@Component
class AuditWriter {

    private final AuditLogRepository repository;
    private final Clock clock = Clock.systemUTC();

    AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(List<AuditEvent> events) {
        Instant now = clock.instant();
        repository.saveAll(events.stream().map(e -> new AuditLog(e, now)).toList());
    }
}
