package com.dimos.wms.masterdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Shared list of equipment types for all sites. Seeded types keep their code and cannot be deleted. */
@Entity
@Table(name = "equipment_type")
class EquipmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "equipment_type_seq")
    @SequenceGenerator(name = "equipment_type_seq", sequenceName = "equipment_type_seq", allocationSize = 1)
    Long id;

    @Column(nullable = false)
    String code;

    @Column(nullable = false)
    String name;

    @Column(name = "is_seeded", nullable = false, updatable = false)
    boolean seeded;
}

interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long>, JpaSpecificationExecutor<EquipmentType> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
