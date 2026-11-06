package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A handling-unit type (carton, pallet, ULD, bin, cart, ...). */
@Entity
@Table(name = "hu_type")
class HuType extends SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hu_type_seq")
    @SequenceGenerator(name = "hu_type_seq", sequenceName = "hu_type_seq", allocationSize = 1)
    Long id;

    @Column(nullable = false)
    String code;

    @Column(nullable = false)
    String name;

    @Column(name = "length_mm")
    Integer lengthMm;

    @Column(name = "width_mm")
    Integer widthMm;

    @Column(name = "height_mm")
    Integer heightMm;

    @Column(name = "tare_kg")
    BigDecimal tareKg;

    @Column(name = "max_gross_kg")
    BigDecimal maxGrossKg;

    @Column(name = "is_container", nullable = false)
    boolean container;

    @Override
    Long getId() {
        return id;
    }
}

interface HuTypeRepository extends SiteRepository<HuType> {
}
