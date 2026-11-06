package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** A zone of the site (inbound, storage, outbound, ...). */
@Entity
@Table(name = "area")
class Area extends SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "area_seq")
    @SequenceGenerator(name = "area_seq", sequenceName = "area_seq", allocationSize = 1)
    Long id;

    @Column(nullable = false)
    String code;

    @Column(nullable = false)
    String name;

    @Column(name = "area_type", nullable = false)
    String areaType;

    @Override
    Long getId() {
        return id;
    }
}

interface AreaRepository extends SiteRepository<Area> {
}
