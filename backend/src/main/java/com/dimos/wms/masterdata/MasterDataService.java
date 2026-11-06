package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared create / read / update / delete / list rules for the site-owned master-data tables:
 * everything is limited to the site of the logged-in user, updates need the version the user saw
 * (otherwise HTTP 409), and every change is written to the audit log with before and after values.
 */
abstract class MasterDataService<E extends SiteEntity, Q extends VersionedRequest, R> {

    static final int MAX_PAGE_SIZE = 200;
    private static final int AUDIT_MAX = 4000;

    private final SiteRepository<E> repository;

    @Autowired
    private JdbcTemplate usageJdbc;
    protected final AuditService audit;
    protected final JsonMapper json;

    protected MasterDataService(SiteRepository<E> repository, AuditService audit, JsonMapper json) {
        this.repository = repository;
        this.audit = audit;
        this.json = json;
    }

    /** Table name used in the audit log, e.g. "area". */
    abstract String entityType();

    abstract E newEntity();

    /** Copies the request into the entity and checks every rule (references, codes, ...). */
    abstract void apply(E entity, Q request, WmsUserPrincipal who);

    abstract R toResponse(E entity);

    /** Field names the list may be sorted by (anything else is refused). */
    abstract Set<String> sortFields();

    String defaultSort() {
        return "code";
    }

    /**
     * Something that can stop a record from being deleted: {@code countSql} counts the records that use it, with one
     * parameter, the id. The labels are used in the message, e.g. "3 locations".
     */
    record Usage(String singular, String plural, String countSql) {
    }

    /** What other records may use this one (default: nothing is checked, the database still protects it). */
    List<Usage> usages() {
        return List.of();
    }

    /** How the record is called in messages (default: its id). */
    String displayName(E entity) {
        return String.valueOf(entity.getId());
    }

    /** Called before a record is deleted, to remove rows that only belong to it. */
    void beforeDelete(E entity) {
    }

    // ---- operations -----------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResult<R> search(Specification<E> filter, int page, int size, String sort, WmsUserPrincipal who) {
        Specification<E> spec = Specs.<E>bySite(who.siteId());
        if (filter != null) { // "no filter" is normal (empty search box); combining with null would fail
            spec = spec.and(filter);
        }
        Page<E> result = repository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), parseSort(sort)));
        return new PageResult<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public R get(Long id, WmsUserPrincipal who) {
        return toResponse(load(id, who));
    }

    @Transactional
    public R create(Q request, WmsUserPrincipal who) {
        E entity = newEntity();
        entity.siteId = who.siteId();
        apply(entity, request, who);
        entity.stamp(who.username());
        E saved = repository.saveAndFlush(entity);
        R after = toResponse(saved);
        record("CREATE", who, saved.getId(), null, after);
        return after;
    }

    @Transactional
    public R update(Long id, Q request, WmsUserPrincipal who) {
        E entity = load(id, who);
        if (request.version() == null) {
            throw MasterDataException.invalid("version", "must be sent when updating");
        }
        if (request.version() != entity.version) {
            throw MasterDataException.stale();
        }
        R before = toResponse(entity);
        apply(entity, request, who);
        if (toResponse(entity).equals(before)) {
            return before; // nothing really changed: no new version, no audit row
        }
        entity.stamp(who.username());
        E saved = repository.saveAndFlush(entity);
        R after = toResponse(saved);
        record("UPDATE", who, saved.getId(), before, after);
        return after;
    }

    @Transactional
    public void delete(Long id, WmsUserPrincipal who) {
        E entity = load(id, who);
        R before = toResponse(entity);
        String blockers = blockers(entity);
        if (blockers != null) { // say what is in the way (asked first: after a failed delete the transaction is unusable)
            throw MasterDataException.inUse(displayName(entity) + " cannot be deleted: it is still used by " + blockers
                    + ". Remove or change those first.");
        }
        try {
            beforeDelete(entity);
            repository.delete(entity);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw MasterDataException.inUse();
        }
        record("DELETE", who, id, before, null);
    }

    // ---- helpers for subclasses -----------------------------------------------------------------------

    /** "3 locations and 1 piece of equipment", or null when nothing uses the record. */
    private String blockers(E entity) {
        List<String> parts = new java.util.ArrayList<>();
        for (Usage u : usages()) {
            Integer n = usageJdbc.queryForObject(u.countSql(), Integer.class, entity.getId());
            if (n != null && n > 0) {
                parts.add(n + " " + (n == 1 ? u.singular() : u.plural()));
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        return parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    protected void flush() {
        repository.flush();
    }

    protected E load(Long id, WmsUserPrincipal who) {
        return repository.findById(id).filter(e -> who.siteId().equals(e.siteId))
                .orElseThrow(() -> MasterDataException.notFound(entityType()));
    }

    /** Codes are stored upper-case and unique per site. */
    protected boolean codeTaken(Long siteId, String code, Long excludeId) {
        Specification<E> spec = Specs.<E>bySite(siteId).and((root, q, cb) -> cb.equal(root.get("code"), code));
        if (excludeId != null) {
            spec = spec.and((root, q, cb) -> cb.notEqual(root.get("id"), excludeId));
        }
        return repository.exists(spec);
    }

    protected String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    protected void checkCodeFree(E entity, String code) {
        if (codeTaken(entity.siteId, code, entity.getId())) {
            throw MasterDataException.invalid("code", "this code is already used");
        }
    }

    /** Checks that a text is valid JSON (project-specific extra fields). */
    protected void checkJson(String field, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            json.readTree(text);
        } catch (RuntimeException ex) {
            throw MasterDataException.invalid(field, "must be valid JSON");
        }
    }

    protected void record(String action, WmsUserPrincipal who, Object entityId, Object before, Object after) {
        AuditBuffer.record(audit, new AuditEvent(who.username(), action, who.siteId(), entityType(), String.valueOf(entityId),
                toJson(before), toJson(after), null, remoteAddr(), null));
    }

    protected String toJson(Object value) {
        if (value == null) {
            return null;
        }
        String text = json.writeValueAsString(value);
        return text.length() > AUDIT_MAX ? text.substring(0, AUDIT_MAX - 3) + "..." : text;
    }

    static String remoteAddr() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }

    private Sort parseSort(String sort) {
        String spec = sort == null || sort.isBlank() ? defaultSort() : sort;
        String[] parts = spec.split(",");
        String field = parts[0].trim();
        if (!sortFields().contains(field)) {
            throw MasterDataException.invalid("sort", "cannot sort by '" + field + "'");
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort result = Sort.by(dir, field);
        return field.equals("id") ? result : result.and(Sort.by("id")); // stable paging
    }
}
