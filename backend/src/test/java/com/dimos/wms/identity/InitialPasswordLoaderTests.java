package com.dimos.wms.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.lang.reflect.Constructor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialPasswordLoaderTests {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final AppUserRepository repo = mock(AppUserRepository.class);
    private final AuditService audit = mock(AuditService.class);

    private AppUser user(String name) throws Exception {
        Constructor<AppUser> c = AppUser.class.getDeclaredConstructor();
        c.setAccessible(true);
        AppUser u = c.newInstance();
        var f = AppUser.class.getDeclaredField("username");
        f.setAccessible(true);
        f.set(u, name);
        return u;
    }

    @Test
    void hashesPasswordFromEnvironmentAndAudits() throws Exception {
        AppUser admin = user("admin");
        when(repo.findByPasswordHashIsNull()).thenReturn(List.of(admin));
        var env = new MockEnvironment().withProperty("WMS_INIT_PASSWORD_ADMIN", "S3cret-test-pw");

        new InitialPasswordLoader(repo, encoder, audit, env).run(null);

        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("S3cret-test-pw");
        assertThat(encoder.matches("S3cret-test-pw", admin.getPasswordHash())).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(ev.capture());
        assertThat(ev.getValue().action()).isEqualTo("PASSWORD_INITIALISED");
        assertThat(ev.getValue().detail()).doesNotContain("S3cret-test-pw");
    }

    @Test
    void withoutVariableLeavesUserWithoutPassword() throws Exception {
        AppUser rasheed = user("rasheed");
        when(repo.findByPasswordHashIsNull()).thenReturn(List.of(rasheed));

        new InitialPasswordLoader(repo, encoder, audit, new MockEnvironment()).run(null);

        assertThat(rasheed.getPasswordHash()).isNull();
        verify(audit, never()).record(any());
    }
}
