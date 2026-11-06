package com.dimos.wms.identity;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AppPermissionRepository extends JpaRepository<AppPermission, Long> {

    List<AppPermission> findByCodeIn(Collection<String> codes);
}
