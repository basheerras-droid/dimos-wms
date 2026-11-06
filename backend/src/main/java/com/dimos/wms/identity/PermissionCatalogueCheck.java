package com.dimos.wms.identity;

import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * At start-up: the permissions in the database must be exactly the ones in {@link PermissionCatalogue}. A difference
 * means a migration or the catalogue was forgotten, so it is reported loudly (the server still starts).
 */
@Component
class PermissionCatalogueCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogueCheck.class);

    private final AppPermissionRepository permissions;

    PermissionCatalogueCheck(AppPermissionRepository permissions) {
        this.permissions = permissions;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> inDb = new HashSet<>();
        permissions.findAll().forEach(p -> inDb.add(p.getCode()));
        Set<String> inCode = new HashSet<>();
        PermissionCatalogue.ALL.forEach(e -> inCode.add(e.code()));
        Set<String> onlyDb = new HashSet<>(inDb);
        onlyDb.removeAll(inCode);
        Set<String> onlyCode = new HashSet<>(inCode);
        onlyCode.removeAll(inDb);
        if (!onlyDb.isEmpty() || !onlyCode.isEmpty()) {
            log.error("Permission catalogue and database differ. Only in the database: {}. Only in the code: {}.",
                    onlyDb, onlyCode);
        }
    }
}
