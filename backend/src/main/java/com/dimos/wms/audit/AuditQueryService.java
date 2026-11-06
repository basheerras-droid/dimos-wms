package com.dimos.wms.audit;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public API of the audit module for reading the audit trail (read-only; the trail itself is append-only). */
@Service
public class AuditQueryService {

    static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> SORTABLE = Set.of("occurredAt", "actor", "action", "entityType", "id");

    public record AuditRow(Long id, Instant occurredAt, String actor, String action, String entityType, String entityId,
                           String beforeValue, String afterValue, String detail, String remoteAddr,
                           String correlationId, Long siteId) {
    }

    public record AuditPage(List<AuditRow> items, long total, int page, int size) {
    }

    public record AuditFilters(List<String> actions, List<String> entityTypes) {
    }

    /** What to look for. Every field is optional. */
    public record Query(String actor, String action, String entityType, String entityId, String text, Instant from,
                        Instant to) {
    }

    private final AuditLogRepository repository;

    AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** Events of the site (plus events that belong to no site, such as a login with an unknown user name). */
    @Transactional(readOnly = true)
    public AuditPage search(Long siteId, Query query, int page, int size, String sort) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.or(cb.equal(root.get("siteId"), siteId), cb.isNull(root.get("siteId"))));
            if (has(query.actor())) {
                all.add(cb.like(cb.lower(root.get("actor")), like(query.actor()), '\\'));
            }
            if (has(query.action())) {
                all.add(cb.equal(root.get("action"), query.action()));
            }
            if (has(query.entityType())) {
                all.add(cb.equal(root.get("entityType"), query.entityType()));
            }
            if (has(query.entityId())) {
                all.add(cb.equal(root.get("entityId"), query.entityId().trim()));
            }
            if (has(query.text())) {
                String p = like(query.text());
                all.add(cb.or(cb.like(cb.lower(cb.coalesce(root.get("detail"), "")), p, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("entityId"), "")), p, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("beforeValue"), "")), p, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("afterValue"), "")), p, '\\')));
            }
            if (query.from() != null) {
                all.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
            }
            if (query.to() != null) {
                all.add(cb.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Page<AuditLog> result = repository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortOf(sort)));
        return new AuditPage(result.getContent().stream().map(AuditQueryService::row).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public AuditFilters filters(Long siteId) {
        return new AuditFilters(repository.actions(siteId), repository.entityTypes(siteId));
    }

    private static AuditRow row(AuditLog a) {
        return new AuditRow(a.getId(), a.getOccurredAt(), a.getActor(), a.getAction(), a.getEntityType(),
                a.getEntityId(), a.getBeforeValue(), a.getAfterValue(), a.getDetail(), a.getRemoteAddr(),
                a.getCorrelationId(), a.getSiteId());
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static String like(String text) {
        return "%" + text.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Newest first by default. An unknown sort field is a client mistake, reported as such. */
    private static Sort sortOf(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"));
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new IllegalArgumentException("cannot sort by '" + field + "'");
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field).and(Sort.by(dir, "id"));
    }
}
