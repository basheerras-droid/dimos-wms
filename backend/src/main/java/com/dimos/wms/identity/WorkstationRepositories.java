package com.dimos.wms.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface WorkstationRepository extends JpaRepository<Workstation, Long>, JpaSpecificationExecutor<Workstation> {

    Optional<Workstation> findBySiteIdAndCode(Long siteId, String code);

    /** Normally one; the application keeps it that way. A list so a stray duplicate can never break the lookup. */
    List<Workstation> findBySiteIdAndTokenHash(Long siteId, String tokenHash);
}

interface WorkstationGroupRepository extends JpaRepository<WorkstationGroup, Long>, JpaSpecificationExecutor<WorkstationGroup> {

    Optional<WorkstationGroup> findBySiteIdAndCode(Long siteId, String code);
}
