package com.dimos.wms.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
interface SiteRepository<E> extends JpaRepository<E, Long>, JpaSpecificationExecutor<E> {
}
