package com.dimos.wms.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    List<AppUser> findByPasswordHashIsNull();

    /** How many enabled users of a site have the role (used to keep at least one super administrator). */
    long countBySiteIdAndEnabledTrueAndRoles_Code(Long siteId, String roleCode);
}
