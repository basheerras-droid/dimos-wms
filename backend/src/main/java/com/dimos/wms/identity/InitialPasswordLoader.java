package com.dimos.wms.identity;

import com.dimos.wms.audit.AuditEvent;
import com.dimos.wms.audit.AuditService;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * On startup, gives every user that has no password yet its initial password from the environment variable
 * WMS_INIT_PASSWORD_&lt;USERNAME&gt;. The value is hashed immediately and never logged.
 */
@Component
class InitialPasswordLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialPasswordLoader.class);
    static final String PREFIX = "WMS_INIT_PASSWORD_";
    private static final String ACTOR = "system";

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final Environment env;

    InitialPasswordLoader(AppUserRepository users, PasswordEncoder encoder, AuditService audit, Environment env) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.env = env;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (AppUser user : users.findByPasswordHashIsNull()) {
            String variable = PREFIX + user.getUsername().toUpperCase(Locale.ROOT);
            String initial = env.getProperty(variable);
            if (initial == null || initial.isBlank()) {
                log.warn("User '{}' has no password and cannot log in. Set the environment variable {} and restart.",
                        user.getUsername(), variable);
                continue;
            }
            user.setPasswordHash(encoder.encode(initial), true, ACTOR);
            audit.record(new AuditEvent(ACTOR, "PASSWORD_INITIALISED", user.getSiteId(), "app_user",
                    String.valueOf(user.getId()), null, null,
                    "Initial password set for '" + user.getUsername() + "' from " + variable
                            + "; change required at first login", null, null));
            log.info("Initial password set for user '{}' (change required at first login).", user.getUsername());
        }
    }
}
