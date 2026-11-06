package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/**
 * Administration of user accounts. Rules: only a super administrator may create, change, reset or unlock a super
 * administrator; nobody can disable themselves or change their own roles; at least one enabled super administrator
 * must remain. Passwords are never chosen or seen by the administrator: a random temporary password is shown once and
 * the user must change it at the next login. Every action is audited (never with a password or hash).
 */
@Service
class UserAdminService {

    static final String SUPER_ADMIN = "SUPER_ADMIN";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;
    private static final int MAX_PAGE_SIZE = 200;

    record Request(
            @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{2,99}|", message = "3-100 characters: letters, digits, . _ -") String username,
            @NotBlank @Size(max = 150) String displayName,
            @Email @Size(max = 255) String email,
            @NotEmpty List<@NotBlank String> roles,
            Boolean enabled,
            Long version) {
    }

    record UserView(Long id, String username, String displayName, String email, boolean enabled, List<String> roles,
                    boolean hasPassword, boolean mustChangePassword, boolean locked, Instant lockedUntil,
                    int failedAttempts, Instant lastLoginAt, long version, Instant updatedAt, String updatedBy) {
    }

    record UserPage(List<UserView> items, long total, int page, int size) {
    }

    record CreatedUser(UserView user, String temporaryPassword) {
    }

    record TemporaryPassword(String temporaryPassword) {
    }

    /** What the audit log keeps about a user: no password, no hash. */
    private record UserAudit(String username, String displayName, String email, boolean enabled, List<String> roles) {
    }

    private final AppUserRepository users;
    private final AppRoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final UserSessions sessions;
    private final JsonMapper json;
    private final Clock clock = Clock.systemUTC();
    private final SecureRandom random = new SecureRandom();

    UserAdminService(AppUserRepository users, AppRoleRepository roles, PasswordEncoder encoder, AuditService audit,
                     UserSessions sessions, JsonMapper json) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
        this.sessions = sessions;
        this.json = json;
    }

    // ---- reading ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserPage search(String q, Boolean enabled, String role, int page, int size, String sort, WmsUserPrincipal who) {
        Specification<AppUser> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> all = new java.util.ArrayList<>();
            all.add(cb.equal(root.get("siteId"), who.siteId()));
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                all.add(cb.or(cb.like(cb.lower(root.get("username")), pattern, '\\'),
                        cb.like(cb.lower(root.get("displayName")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.get("email"), "")), pattern, '\\')));
            }
            if (enabled != null) {
                all.add(cb.equal(root.get("enabled"), enabled));
            }
            if (role != null && !role.isBlank()) {
                all.add(cb.equal(root.join("roles").get("code"), role));
            }
            return cb.and(all.toArray(Predicate[]::new));
        };
        Page<AppUser> result = users.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sortOf(sort)));
        return new UserPage(result.getContent().stream().map(this::view).toList(), result.getTotalElements(),
                result.getNumber(), result.getSize());
    }

    @Transactional(readOnly = true)
    public UserView get(Long id, WmsUserPrincipal who) {
        return view(load(id, who));
    }

    // ---- changing ---------------------------------------------------------------------------------------

    @Transactional
    public CreatedUser create(Request request, WmsUserPrincipal who) {
        String username = request.username() == null ? "" : request.username().trim();
        if (username.isEmpty()) {
            throw UserAdminException.invalid("username", "must not be blank");
        }
        if (users.findByUsernameIgnoreCase(username).isPresent()) {
            throw UserAdminException.invalid("username", "this user name is already used");
        }
        Set<AppRole> newRoles = resolveRoles(request.roles());
        requireSuperAdminToGrant(newRoles, who);
        String temporary = newTemporaryPassword();
        AppUser user = users.saveAndFlush(AppUser.create(who.siteId(), username, request.displayName().trim(),
                blankToNull(request.email()), encoder.encode(temporary), newRoles, who.username()));
        record("USER_CREATED", who, user, null, audited(user));
        return new CreatedUser(view(user), temporary);
    }

    @Transactional
    public UserView update(Long id, Request request, WmsUserPrincipal who) {
        AppUser user = load(id, who);
        if (request.version() == null) {
            throw UserAdminException.invalid("version", "must be sent when updating");
        }
        if (request.version() != user.getVersion()) {
            throw UserAdminException.stale();
        }
        requireSuperAdminToChange(user, who);
        if (request.username() != null && !request.username().isBlank()
                && !request.username().trim().equalsIgnoreCase(user.getUsername())) {
            throw UserAdminException.invalid("username", "the user name cannot be changed");
        }
        Set<AppRole> newRoles = resolveRoles(request.roles());
        boolean enabled = request.enabled() == null || request.enabled();
        boolean self = user.getId().equals(who.userId());
        Set<String> newCodes = codes(newRoles);
        if (self && !enabled) {
            throw UserAdminException.notAllowed("You cannot disable your own account.");
        }
        if (self && !newCodes.equals(new HashSet<>(user.getRoleCodes()))) {
            throw UserAdminException.notAllowed("You cannot change your own roles.");
        }
        requireSuperAdminToGrant(newRoles, who);
        // the last enabled super administrator may be neither disabled nor demoted
        boolean wasActiveSuper = user.isEnabled() && user.hasRole(SUPER_ADMIN);
        boolean stillActiveSuper = enabled && newCodes.contains(SUPER_ADMIN);
        if (wasActiveSuper && !stillActiveSuper
                && users.countBySiteIdAndEnabledTrueAndRoles_Code(who.siteId(), SUPER_ADMIN) <= 1) {
            throw UserAdminException.notAllowed("At least one enabled super administrator must remain.");
        }

        UserAudit before = audited(user);
        boolean wasEnabled = user.isEnabled();
        user.updateAccount(request.displayName().trim(), blankToNull(request.email()), enabled, newRoles, who.username());
        AppUser saved = users.saveAndFlush(user);
        UserAudit after = audited(saved);
        record(!wasEnabled && enabled ? "USER_ENABLED" : wasEnabled && !enabled ? "USER_DISABLED" : "USER_UPDATED",
                who, saved, before, after);
        if (wasEnabled && !enabled) {
            sessions.invalidateAll(saved.getId()); // signed out at once
        }
        return view(saved);
    }

    @Transactional
    public TemporaryPassword resetPassword(Long id, WmsUserPrincipal who) {
        AppUser user = load(id, who);
        if (user.getId().equals(who.userId())) {
            throw UserAdminException.notAllowed("Use \"Change password\" in the user menu for your own account.");
        }
        requireSuperAdminToChange(user, who);
        String temporary = newTemporaryPassword();
        user.setPasswordHash(encoder.encode(temporary), true, who.username());
        user.unlock(who.username());
        users.saveAndFlush(user);
        audit.record(new AuditEvent(who.username(), "USER_PASSWORD_RESET", who.siteId(), "app_user",
                String.valueOf(user.getId()), null, null,
                "Temporary password set for '" + user.getUsername() + "'; change required at next login",
                remoteAddr(), null));
        sessions.invalidateAll(user.getId()); // old sessions must not outlive the old password
        return new TemporaryPassword(temporary);
    }

    @Transactional
    public UserView unlock(Long id, WmsUserPrincipal who) {
        AppUser user = load(id, who);
        requireSuperAdminToChange(user, who);
        user.unlock(who.username());
        AppUser saved = users.saveAndFlush(user);
        audit.record(new AuditEvent(who.username(), "USER_UNLOCKED", who.siteId(), "app_user",
                String.valueOf(saved.getId()), null, null, "Lock removed for '" + saved.getUsername() + "'",
                remoteAddr(), null));
        return view(saved);
    }

    // ---- rules and helpers --------------------------------------------------------------------------------

    private void requireSuperAdminToChange(AppUser target, WmsUserPrincipal who) {
        if (target.hasRole(SUPER_ADMIN) && !who.roles().contains(SUPER_ADMIN)) {
            throw UserAdminException.notAllowed("Only a super administrator can change a super administrator.");
        }
    }

    private void requireSuperAdminToGrant(Set<AppRole> newRoles, WmsUserPrincipal who) {
        if (codes(newRoles).contains(SUPER_ADMIN) && !who.roles().contains(SUPER_ADMIN)) {
            throw UserAdminException.notAllowed("Only a super administrator can give the SUPER_ADMIN role.");
        }
    }

    private Set<AppRole> resolveRoles(List<String> requested) {
        Set<String> wanted = new HashSet<>(requested);
        Set<AppRole> found = new HashSet<>(roles.findByCodeIn(wanted));
        Set<String> missing = new HashSet<>(wanted);
        missing.removeAll(codes(found));
        if (!missing.isEmpty()) {
            throw UserAdminException.invalid("roles", "unknown role " + String.join(", ", missing));
        }
        return found;
    }

    private static Set<String> codes(Set<AppRole> set) {
        Set<String> result = new HashSet<>();
        set.forEach(r -> result.add(r.getCode()));
        return result;
    }

    private AppUser load(Long id, WmsUserPrincipal who) {
        return users.findById(id).filter(u -> who.siteId().equals(u.getSiteId())).orElseThrow(UserAdminException::notFound);
    }

    private String newTemporaryPassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }

    private UserView view(AppUser u) {
        Instant now = clock.instant();
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.isEnabled(), u.getRoleCodes(),
                u.getPasswordHash() != null, u.isMustChangePassword(), u.isLocked(now), u.getLockedUntil(),
                u.getFailedAttempts(), u.getLastLoginAt(), u.getVersion(), u.getUpdatedAt(), u.getUpdatedBy());
    }

    private UserAudit audited(AppUser u) {
        return new UserAudit(u.getUsername(), u.getDisplayName(), u.getEmail(), u.isEnabled(), u.getRoleCodes());
    }

    private void record(String action, WmsUserPrincipal who, AppUser user, UserAudit before, UserAudit after) {
        audit.record(new AuditEvent(who.username(), action, who.siteId(), "app_user", String.valueOf(user.getId()),
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after), null, remoteAddr(), null));
    }

    private static Sort sortOf(String sort) {
        String spec = sort == null || sort.isBlank() ? "username,asc" : sort;
        String[] parts = spec.split(",");
        String field = parts[0].trim();
        if (!Set.of("username", "displayName", "email", "enabled", "lastLoginAt", "updatedAt").contains(field)) {
            throw UserAdminException.invalid("sort", "cannot sort by '" + field + "'");
        }
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
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
