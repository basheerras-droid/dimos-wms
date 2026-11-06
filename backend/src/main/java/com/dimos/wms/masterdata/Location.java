package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A place where a handling unit can be: a bin, a conveyor position, a station, a hoist platform, ... */
@Entity
@Table(name = "location")
class Location extends SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "location_seq")
    @SequenceGenerator(name = "location_seq", sequenceName = "location_seq", allocationSize = 1)
    Long id;

    @Column(nullable = false)
    String code;

    String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    Area area;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_type_id", nullable = false)
    LocationType locationType;

    @Column(name = "x_mm")
    Integer xMm;

    @Column(name = "y_mm")
    Integer yMm;

    @Column(name = "z_mm")
    Integer zMm;

    @Column(name = "level_no", nullable = false)
    int levelNo;

    String aisle;

    String side;

    @Column(name = "column_no")
    Integer columnNo;

    @Column(name = "row_no")
    Integer rowNo;

    @Column(name = "max_weight_kg")
    BigDecimal maxWeightKg;

    @Column(nullable = false)
    boolean enabled = true;

    @Column(name = "blocked_in", nullable = false)
    boolean blockedIn;

    @Column(name = "blocked_out", nullable = false)
    boolean blockedOut;

    @Column(name = "block_reason")
    String blockReason;

    @Column(name = "external_ref")
    String externalRef;

    @Column(name = "ext_attributes")
    String extAttributes;

    @Override
    Long getId() {
        return id;
    }
}

interface LocationRepository extends SiteRepository<Location> {
}
