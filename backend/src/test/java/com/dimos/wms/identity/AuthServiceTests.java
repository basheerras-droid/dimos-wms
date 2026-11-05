package com.dimos.wms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTests {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AppUserRepository repo = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private AuthService service;
    private AppUser user;

    private static void set(Object target, String field, Object value) throws Exception {
        Field f = AppUser.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        service = new AuthService(repo, encoder, audit, 5, 15);
        Constructor<AppUser> c = AppUser.class.getDeclaredConstructor();
        c.setAccessible(true);
        user = c.newInstance();
        set(user, "id", 7L);
        set(user, "siteId", 1L);
        set(user, "username", "admin");
        set(user, "displayName", "Administrator");
        set(user, "passwordHash", encoder.encode("correct-horse-battery"));
        when(repo.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(user));
    }

    private List<String> auditedActions() {
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).record(ev.capture());
        return ev.getAllValues().stream().map(AuditEvent::action).toList();
    }

    @Test
    void correctPasswordLogsInAndResetsCounter() {
        AuthService.LoginResult wrong = service.login("admin", "nope", "10.0.0.1");
        assertThat(wrong.status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        assertThat(user.getFailedAttempts()).isEqualTo(1);

        AuthService.LoginResult ok = service.login("admin", "correct-horse-battery", "10.0.0.1");

        assertThat(ok.status()).isEqualTo(AuthService.Status.OK);
        assertThat(ok.principal().username()).isEqualTo("admin");
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(auditedActions()).containsExactly("LOGIN_FAILURE", "LOGIN_SUCCESS");
    }

    @Test
    void fifthWrongPasswordLocksAccountAndEvenCorrectPasswordIsRefused() {
        for (int i = 1; i <= 4; i++) {
            assertThat(service.login("admin", "bad" + i, null).status())
                    .isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        }
        AuthService.LoginResult fifth = service.login("admin", "bad5", null);
        assertThat(fifth.status()).isEqualTo(AuthService.Status.LOCKED);
        assertThat(user.getLockedUntil()).isAfter(Instant.now().plusSeconds(14 * 60));

        AuthService.LoginResult correctButLocked = service.login("admin", "correct-horse-battery", null);
        assertThat(correctButLocked.status()).isEqualTo(AuthService.Status.LOCKED);
        assertThat(auditedActions()).contains("ACCOUNT_LOCKED");
    }

    @Test
    void expiredLockStartsCountingAgain() throws Exception {
        set(user, "lockedUntil", Instant.now().minusSeconds(60));
        set(user, "failedAttempts", 5);

        assertThat(service.login("admin", "correct-horse-battery", null).status()).isEqualTo(AuthService.Status.OK);
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void unknownUserIsRefusedAndAudited() {
        when(repo.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThat(service.login("ghost", "whatever", null).status()).isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        assertThat(auditedActions()).containsExactly("LOGIN_FAILURE");
    }

    @Test
    void userWithoutPasswordCannotLogIn() throws Exception {
        set(user, "passwordHash", null);
        for (int i = 0; i < 10; i++) {
            assertThat(service.login("admin", "anything", null).status())
                    .isEqualTo(AuthService.Status.INVALID_CREDENTIALS);
        }
        // never counted, never locked - otherwise anyone could lock the account before its first password exists
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void changePasswordChecksCurrentAndClearsForcedChange() {
        when(repo.findById(7L)).thenReturn(Optional.of(user));
        WmsUserPrincipal p = service.principalOf(user);

        assertThat(service.changePassword(p, "wrong", "a-brand-new-password", null))
                .isEqualTo(AuthService.ChangeStatus.WRONG_CURRENT_PASSWORD);
        assertThat(service.changePassword(p, "correct-horse-battery", "correct-horse-battery", null))
                .isEqualTo(AuthService.ChangeStatus.SAME_PASSWORD);
        assertThat(service.changePassword(p, "correct-horse-battery", "a-brand-new-password", null))
                .isEqualTo(AuthService.ChangeStatus.OK);

        assertThat(encoder.matches("a-brand-new-password", user.getPasswordHash())).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(auditedActions()).contains("PASSWORD_CHANGE_FAILURE", "PASSWORD_CHANGED");
        verify(audit, atLeastOnce()).record(any());
    }
}
