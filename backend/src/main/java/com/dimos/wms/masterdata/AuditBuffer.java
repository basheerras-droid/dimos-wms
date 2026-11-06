package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.util.ArrayList;
import java.util.List;

/**
 * During a spreadsheet import the audit events are held back until the import has really been committed.
 * A dry run or a failed import therefore leaves no audit rows behind for changes that never happened.
 * Outside an import the events go straight to the audit log.
 */
final class AuditBuffer {

    private static final ThreadLocal<List<AuditEvent>> HELD = new ThreadLocal<>();

    private AuditBuffer() {
    }

    static void begin() {
        HELD.set(new ArrayList<>());
    }

    /** Stops holding events back and returns what was held. */
    static List<AuditEvent> end() {
        List<AuditEvent> events = HELD.get();
        HELD.remove();
        return events == null ? List.of() : events;
    }

    static void record(AuditService audit, AuditEvent event) {
        List<AuditEvent> held = HELD.get();
        if (held != null) {
            held.add(event);
        } else {
            audit.record(event);
        }
    }
}
