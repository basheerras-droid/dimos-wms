package com.dimos.wms.identity;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    List<AppRole> findByCodeIn(Collection<String> codes);
}
