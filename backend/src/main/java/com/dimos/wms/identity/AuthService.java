package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, lockout and password change rules. Every outcome is written to the audit log. */
@Service
public class AuthService {

    public enum Status { OK, INVALID_CREDENTIALS, LOCKED }

    public record LoginResult(Status status, WmsUserPrincipal principal, Instant lockedUntil) {
    }

    public enum ChangeStatus { OK, WRONG_CURRENT_PASSWORD, SAME_PASSWORD }

    /** Hash of a throw-away value, compared against when the user does not exist so timing does not reveal it. */
    private final String dummyHash;

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock = Clock.systemUTC();

    AuthService(AppUserRepository users, PasswordEncoder encoder, AuditService audit,
                @Value("${wms.security.max-failed-attempts:5}") int maxAttempts,
                @Value("${wms.security.lockout-minutes:15}") long lockoutMinutes) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.maxAttempts = maxAttempts;
        this.lockDuration = Duration.ofMinutes(lockoutMinutes);
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
        boolean passwordOk = false;
        if (user.getPasswordHash() != null) {
            passwordOk = encoder.matches(password, user.getPasswordHash());
        } else {
            encoder.matches(password, dummyHash); // no password set yet: same timing, always a failure
        }
        if (!passwordOk || !user.isEnabled()) {
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
        if (encoder.matches(next, user.getPasswordHash())) {
            return ChangeStatus.SAME_PASSWORD;
        }
        user.setPasswordHash(encoder.encode(next), false, user.getUsername());
        users.save(user);
        audit(user, "PASSWORD_CHANGED", null, remoteAddr);
        return ChangeStatus.OK;
    }

    WmsUserPrincipal principalOf(AppUser u) {
        return new WmsUserPrincipal(u.getId(), u.getSiteId(), u.getUsername(), u.getDisplayName(),
                u.getRoleCodes(), u.isMustChangePassword());
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
