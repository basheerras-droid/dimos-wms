package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditQueryService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only view of the audit trail (permission AUDIT_VIEW). */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasAuthority('AUDIT_VIEW')")
class AuditAdminController {

    private final AuditQueryService audit;

    AuditAdminController(AuditQueryService audit) {
        this.audit = audit;
    }

    /** {@code from} and {@code to} are ISO instants such as 2026-11-06T08:00:00Z. */
    @GetMapping
    AuditQueryService.AuditPage list(@RequestParam(required = false) String actor,
                                     @RequestParam(required = false) String action,
                                     @RequestParam(required = false) String entityType,
                                     @RequestParam(required = false) String entityId,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String sort,
                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        try {
            return audit.search(who.siteId(),
                    new AuditQueryService.Query(actor, action, entityType, entityId, q, instant(from), instant(to)),
                    page, size, sort);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /** The actions and entity types that occur in the trail, for the filter drop-downs. */
    @GetMapping("/filters")
    AuditQueryService.AuditFilters filters(@AuthenticationPrincipal WmsUserPrincipal who) {
        return audit.filters(who.siteId());
    }

    private static Instant instant(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dates must look like 2026-11-06T08:00:00Z");
        }
    }
}
