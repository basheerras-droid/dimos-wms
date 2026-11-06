package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Workstations and the registration of browsers. Registering creates a random token that the controller puts into an
 * HttpOnly cookie; only its SHA-256 hash is stored here. The token is never logged or audited.
 */
@Service
class WorkstationAdminService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> TYPES = Set.of("PC", "HANDHELD", "HMI", "KIOSK");

    record Request(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{1,49}", message = "2-50 characters: letters, digits, . _ -") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "PC|HANDHELD|HMI|KIOSK", message = "must be PC, HANDHELD, HMI or KIOSK") String type,
            Long locationId,
            Long workstationGroupId,
            @Size(max = 255) String hostName,
            @Size(max = 64) @Pattern(regexp = "[0-9A-Fa-f:.]*", message = "an IP address such as 192.168.1.40") String ipAddress,
            Boolean checkIp,
            @Size(max = 255) String defaultPrinter,
            Boolean enabled,
            Long version) {
    }

    record WorkstationView(Long id, String code, String name, String type, Long locationId, String locationCode,
                           Long workstationGroupId, String workstationGroupCode, String hostName, String ipAddress,
                           boolean checkIp, String defaultPrinter, boolean enabled, boolean registered,
                           Instant registeredAt, String registeredBy, long version, Instant updatedAt, String updatedBy) {
    }

    record WorkstationPage(List<WorkstationView> items, long total, int page, int size) {
    }

    /** The view plus the new token, for the controller to put into the cookie. The token is not part of any JSON. */
    record Registration(WorkstationView view, String token) {
    }

    private record WorkstationAudit(String code, String name, String type, String locationCode, String workstationGroupCode,
                                    String hostName, String ipAddress, boolean checkIp, String defaultPrinter,
                                    boolean enabled, boolean registered) {
    }

    private final WorkstationRepository workstations;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final WorkstationResolver resolver;
    private final JsonMapper json;
    private final SecureRandom random = new SecureRandom();

    WorkstationAdminService(WorkstationRepository workstations, JdbcTemplate jdbc, AuditService audit,
                            WorkstationResolver resolver, JsonMapper json) {
        this.workstations = workstations;
        this.jdbc = jdbc;
        this.audit = audit;
        this.resolver = resolver;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public WorkstationPage search(String q, String type, Boolean enabled, Long groupId, int page, int size, String sort,
                                  WmsUserPrincipal who) {
        Specification<Workstation> spec = (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            all.add(cb.equal(root.get("siteId"), who.siteId()));
            if (q != null && !q.isBlank()) {
                String p = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("code")), p, '\\'), cb.like(cb.lower(root.get("name")), p, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("hostName"), "")), p, '\\')));
            }
            if (type != null && !type.isBlank()) {
                all.add(cb.equal(root.get("type"), type));
            }
            if (enabled != null) {
                all.add(cb.equal(root.get("enabled"), enabled));
            }
            if (groupId != null) {
                all.add(cb.equal(root.get("workstationGroupId"), groupId));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Page<Workstation> result = workstations.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortOf(sort)));
        return new WorkstationPage(result.getContent().stream().map(this::view).toList(), result.getTotalElements(),
                result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public WorkstationView get(Long id, WmsUserPrincipal who) {
        return view(load(id, who));
    }

    @Transactional
    public WorkstationView create(Request request, WmsUserPrincipal who) {
        String code = request.code().trim().toUpperCase();
        if (workstations.findBySiteIdAndCode(who.siteId(), code).isPresent()) {
            throw UserAdminException.invalid("code", "this code is already used");
        }
        Workstation w = Workstation.create(who.siteId(), code, who.username());
        apply(w, request, who);
        Workstation saved = workstations.saveAndFlush(w);
        record("WORKSTATION_CREATED", who, saved, null, audited(saved));
        resolver.clearCache();
        return view(saved);
    }

    @Transactional
    public WorkstationView update(Long id, Request request, WmsUserPrincipal who) {
        Workstation w = load(id, who);
        if (request.version() == null) {
            throw UserAdminException.invalid("version", "must be sent when updating");
        }
        if (request.version() != w.getVersion()) {
            throw UserAdminException.stale();
        }
        if (!request.code().trim().equalsIgnoreCase(w.getCode())) {
            throw UserAdminException.invalid("code", "the code of a workstation cannot be changed");
        }
        WorkstationAudit before = audited(w);
        apply(w, request, who);
        Workstation saved = workstations.saveAndFlush(w);
        record("WORKSTATION_UPDATED", who, saved, before, audited(saved));
        resolver.clearCache();
        return view(saved);
    }

    @Transactional
    public void delete(Long id, WmsUserPrincipal who) {
        Workstation w = load(id, who);
        WorkstationAudit before = audited(w);
        workstations.delete(w);
        workstations.flush();
        record("WORKSTATION_DELETED", who, w, before, null);
        resolver.clearCache();
    }

    /** Makes the browser that sends this request the workstation. A browser registered before stops being valid. */
    @Transactional
    public Registration register(Long id, WmsUserPrincipal who) {
        Workstation w = load(id, who);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        boolean replaced = w.getTokenHash() != null;
        w.register(WorkstationResolver.sha256Hex(token), who.username());
        Workstation saved = workstations.saveAndFlush(w);
        audit.record(new AuditEvent(who.username(), "WORKSTATION_REGISTERED", who.siteId(), "workstation",
                String.valueOf(saved.getId()), null, null,
                "Browser registered as workstation " + saved.getCode() + (replaced ? " (the previous browser is no longer valid)" : ""),
                remoteAddr(), null));
        resolver.clearCache();
        return new Registration(view(saved), token);
    }

    /** Ends the registration on the server side: the browser that holds the cookie becomes an ordinary office browser. */
    @Transactional
    public WorkstationView revoke(Long id, WmsUserPrincipal who) {
        Workstation w = load(id, who);
        w.clearRegistration(who.username());
        Workstation saved = workstations.saveAndFlush(w);
        audit.record(new AuditEvent(who.username(), "WORKSTATION_REGISTRATION_REVOKED", who.siteId(), "workstation",
                String.valueOf(saved.getId()), null, null, "Registration of workstation " + saved.getCode() + " revoked",
                remoteAddr(), null));
        resolver.clearCache();
        return view(saved);
    }

    /**
     * "Unregister this browser" (for a super administrator, from the device itself): clears the registration that
     * belongs to the given cookie token, if there is one. Returns the code of the workstation, or null.
     */
    @Transactional
    public String unregisterBrowser(String token, WmsUserPrincipal who) {
        if (token == null) {
            return null;
        }
        List<Workstation> found = workstations.findBySiteIdAndTokenHash(who.siteId(), WorkstationResolver.sha256Hex(token));
        for (Workstation w : found) {
            w.clearRegistration(who.username());
            workstations.saveAndFlush(w);
            audit.record(new AuditEvent(who.username(), "WORKSTATION_BROWSER_UNREGISTERED", who.siteId(), "workstation",
                    String.valueOf(w.getId()), null, null, "Browser unregistered from workstation " + w.getCode()
                    + " on the device itself (back to office mode)", remoteAddr(), null));
        }
        resolver.clearCache();
        return found.isEmpty() ? null : found.get(0).getCode();
    }

    // ---- helpers ----------------------------------------------------------------------------------------

    private void apply(Workstation w, Request r, WmsUserPrincipal who) {
        if (!TYPES.contains(r.type())) {
            throw UserAdminException.invalid("type", "must be PC, HANDHELD, HMI or KIOSK");
        }
        if (r.locationId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from location where id = ? and site_id = ?", Integer.class,
                    r.locationId(), who.siteId());
            if (n == null || n == 0) {
                throw UserAdminException.invalid("locationId", "unknown location");
            }
        }
        if (r.workstationGroupId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from workstation_group where id = ? and site_id = ?", Integer.class,
                    r.workstationGroupId(), who.siteId());
            if (n == null || n == 0) {
                throw UserAdminException.invalid("workstationGroupId", "unknown workstation group");
            }
        }
        boolean checkIp = Boolean.TRUE.equals(r.checkIp());
        String ip = blankToNull(r.ipAddress());
        if (checkIp && ip == null) {
            throw UserAdminException.invalid("ipAddress", "is required when the IP address is checked");
        }
        if (ip != null && !ip.matches("(\\d{1,3}\\.){3}\\d{1,3}|[0-9A-Fa-f:]{2,45}")) {
            throw UserAdminException.invalid("ipAddress", "not a valid IP address");
        }
        w.update(r.name().trim(), r.type(), r.locationId(), r.workstationGroupId(), blankToNull(r.hostName()), ip, checkIp,
                blankToNull(r.defaultPrinter()), r.enabled() == null || r.enabled(), who.username());
    }

    private Workstation load(Long id, WmsUserPrincipal who) {
        return workstations.findById(id).filter(w -> who.siteId().equals(w.getSiteId())).orElseThrow(UserAdminException::notFound);
    }

    private String codeOf(String table, Long id) {
        if (id == null) {
            return null;
        }
        List<String> codes = jdbc.queryForList("select code from " + table + " where id = ?", String.class, id);
        return codes.isEmpty() ? null : codes.get(0);
    }

    private WorkstationView view(Workstation w) {
        return new WorkstationView(w.getId(), w.getCode(), w.getName(), w.getType(), w.getLocationId(),
                codeOf("location", w.getLocationId()), w.getWorkstationGroupId(), codeOf("workstation_group", w.getWorkstationGroupId()),
                w.getHostName(), w.getIpAddress(), w.isCheckIp(), w.getDefaultPrinter(), w.isEnabled(), w.getTokenHash() != null,
                w.getRegisteredAt(), w.getRegisteredBy(), w.getVersion(), w.getUpdatedAt(), w.getUpdatedBy());
    }

    private WorkstationAudit audited(Workstation w) {
        return new WorkstationAudit(w.getCode(), w.getName(), w.getType(), codeOf("location", w.getLocationId()),
                codeOf("workstation_group", w.getWorkstationGroupId()), w.getHostName(), w.getIpAddress(), w.isCheckIp(),
                w.getDefaultPrinter(), w.isEnabled(), w.getTokenHash() != null);
    }

    private void record(String action, WmsUserPrincipal who, Workstation w, WorkstationAudit before, WorkstationAudit after) {
        audit.record(new AuditEvent(who.username(), action, who.siteId(), "workstation", String.valueOf(w.getId()),
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), null, remoteAddr(), null));
    }

    private static Sort sortOf(String sort) {
        String spec = sort == null || sort.isBlank() ? "code,asc" : sort;
        String[] parts = spec.split(",");
        String field = parts[0].trim();
        if (!Set.of("code", "name", "type", "enabled", "updatedAt").contains(field)) {
            throw UserAdminException.invalid("sort", "cannot sort by '" + field + "'");
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field).and(Sort.by("id"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String remoteAddr() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest().getRemoteAddr() : null;
    }
}
