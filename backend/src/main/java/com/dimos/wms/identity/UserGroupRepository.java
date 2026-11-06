package com.dimos.wms.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface UserGroupRepository extends JpaRepository<UserGroup, Long>, JpaSpecificationExecutor<UserGroup> {

    Optional<UserGroup> findBySiteIdAndCode(Long siteId, String code);
}
