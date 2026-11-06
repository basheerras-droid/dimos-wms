package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, lockout and password change rules. Every outcome is written to the audit log. */
@Service
public class AuthService {

    public enum Status { OK, INVALID_CREDENTIALS, LOCKED }

    public record LoginResult(Status status, WmsUserPrincipal principal, Instant lockedUntil) {
    }

    public enum ChangeStatus { OK, WRONG_CURRENT_PASSWORD, SAME_PASSWORD, TOO_SHORT }

    /** Hash of a throw-away value, compared against when the user does not exist so timing does not reveal it. */
    private final String dummyHash;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final RightsService rights;
    private final ParameterService params;
    private final Clock clock = Clock.systemUTC();

    AuthService(AppUserRepository users, PasswordEncoder encoder, AuditService audit, RightsService rights,
                ParameterService params) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.rights = rights;
        this.params = params;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    @Transactional
    public LoginResult login(String username, String password, String remoteAddr) {
        Instant now = clock.instant();
        Optional<AppUser> found = users.findByUsernameIgnoreCase(username.trim());
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            audit(username, "LOGIN_FAILURE", null, null, "Unknown user name", remoteAddr);
            return new LoginResult(Status.INVALID_CREDENTIALS, null, null);
        }
        AppUser user = found.get();
        if (user.isLocked(now)) {
            audit(user, "LOGIN_FAILURE", "Account is locked until " + user.getLockedUntil(), remoteAddr);
            return new LoginResult(Status.LOCKED, null, user.getLockedUntil());
        }
        if (user.getPasswordHash() == null) {
            // No password set yet: always refuse (same timing), but do NOT count it towards lockout, otherwise
            // anyone could lock an account just by trying before its first password exists.
            encoder.matches(password, dummyHash);
            audit(user, "LOGIN_FAILURE", "No password has been set for this account", remoteAddr);
            return new LoginResult(Status.INVALID_CREDENTIALS, null, null);
        }
        boolean passwordOk = encoder.matches(password, user.getPasswordHash());
        if (!passwordOk || !user.isEnabled()) {
            // the lockout rules are system parameters (bounds enforced there), read for the user's site
            int maxAttempts = params.intValue("security.lockout_attempts", user.getSiteId());
            Duration lockDuration = Duration.ofMinutes(params.intValue("security.lockout_minutes", user.getSiteId()));
            boolean locked = user.registerFailedLogin(maxAttempts, lockDuration, now);
            users.save(user);
            audit(user, "LOGIN_FAILURE", !user.isEnabled() ? "Account disabled"
                    : "Wrong password (attempt " + user.getFailedAttempts() + " of " + maxAttempts + ")", remoteAddr);
            if (locked) {
                audit(user, "ACCOUNT_LOCKED", "Locked until " + user.getLockedUntil(), remoteAddr);
                return new LoginResult(Status.LOCKED, null, user.getLockedUntil());
            }
            return new LoginResult(Status.INVALID_CREDENTIALS, null, null);
        }
        user.registerSuccessfulLogin(now);
        users.save(user);
        audit(user, "LOGIN_SUCCESS", null, remoteAddr);
        return new LoginResult(Status.OK, principalOf(user), null);
    }

    public void recordLogout(WmsUserPrincipal principal, String remoteAddr) {
        audit.record(new AuditEvent(principal.username(), "LOGOUT", principal.siteId(), "app_user",
                String.valueOf(principal.userId()), null, null, null, remoteAddr, null));
    }

    @Transactional
    public ChangeStatus changePassword(WmsUserPrincipal principal, String current, String next, String remoteAddr) {
        AppUser user = users.findById(principal.userId()).orElseThrow();
        if (user.getPasswordHash() == null || !encoder.matches(current, user.getPasswordHash())) {
            audit(user, "PASSWORD_CHANGE_FAILURE", "Current password was wrong", remoteAddr);
            return ChangeStatus.WRONG_CURRENT_PASSWORD;
        }
        if (next.length() < passwordMinLength(user.getSiteId())) {
            return ChangeStatus.TOO_SHORT;
        }
        if (encoder.matches(next, user.getPasswordHash())) {
            return ChangeStatus.SAME_PASSWORD;
        }
        user.setPasswordHash(encoder.encode(next), false, user.getUsername());
        users.save(user);
        audit(user, "PASSWORD_CHANGED", null, remoteAddr);
        return ChangeStatus.OK;
    }

    /** The shortest password allowed at the site (system parameter, never below 8). */
    int passwordMinLength(Long siteId) {
        return params.intValue("security.password_min_length", siteId);
    }

    WmsUserPrincipal principalOf(AppUser u) {
        RightsService.Rights r = rights.of(u); // direct roles + roles of the user's enabled groups
        return new WmsUserPrincipal(u.getId(), u.getSiteId(), u.getUsername(), u.getDisplayName(),
                r.roles(), r.permissions(), u.isMustChangePassword());
    }

    private void audit(AppUser user, String action, String detail, String remoteAddr) {
        audit(user.getUsername(), action, user.getSiteId(), String.valueOf(user.getId()), detail, remoteAddr);
    }

    private void audit(String actor, String action, Long siteId, String entityId, String detail, String remoteAddr) {
        String safeActor = actor.length() > 100 ? actor.substring(0, 100) : actor;
        audit.record(new AuditEvent(safeActor, action, siteId, "app_user", entityId, null, null, detail,
                remoteAddr, null));
    }
}
