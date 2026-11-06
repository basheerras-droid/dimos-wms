package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/** What a location is used for (storage bin, conveyor position, hoist platform, ...). */
@Entity
@Table(name = "location_type")
class LocationType extends SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_type_seq")
    @SequenceGenerator(name = "location_type_seq", sequenceName = "location_type_seq", allocationSize = 1)
    Long id;

    @Column(nullable = false)
    String code;

    @Column(nullable = false)
    String name;

    @Column(name = "is_storage", nullable = false)
    boolean storage;

    @Column(name = "is_transfer", nullable = false)
    boolean transfer;

    @Column(name = "is_workstation", nullable = false)
    boolean workstation;

    @Column(name = "capacity_hu", nullable = false)
    int capacityHu = 1;

    @Override
    Long getId() {
        return id;
    }
}

interface LocationTypeRepository extends SiteRepository<LocationType> {
}
