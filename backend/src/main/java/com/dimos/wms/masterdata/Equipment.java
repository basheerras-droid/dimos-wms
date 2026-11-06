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

/** A piece of equipment: a conveyor line, a hoist, a shuttle, a scanner, ... Equipment can be nested. */
@Entity
@Table(name = "equipment")
class Equipment extends SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "equipment_seq")
    @SequenceGenerator(name = "equipment_seq", sequenceName = "equipment_seq", allocationSize = 1)
    Long id;

    @Column(nullable = false)
    String code;

    @Column(nullable = false)
    String name;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "equipment_type_id", nullable = false)
    EquipmentType equipmentType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "area_id")
    Area area;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    Equipment parent;

    @Column(name = "plc_connection")
    String plcConnection;

    @Column(nullable = false)
    boolean enabled = true;

    @Column(name = "external_ref")
    String externalRef;

    @Column(name = "ext_attributes")
    String extAttributes;

    @Override
    Long getId() {
        return id;
    }
}

interface EquipmentRepository extends SiteRepository<Equipment> {
}
