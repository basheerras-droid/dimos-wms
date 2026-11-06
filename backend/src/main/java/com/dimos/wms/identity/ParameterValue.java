package com.dimos.wms.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** A parameter value that differs from the default, for one scope. See {@link ParameterService}. */
@Entity
@Table(name = "parameter_value")
class ParameterValue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "parameter_value_seq")
    @SequenceGenerator(name = "parameter_value_seq", sequenceName = "parameter_value_seq", allocationSize = 1)
    private Long id;

    /** Empty for GLOBAL values (they belong to no single site). */
    @Column(name = "site_id", updatable = false)
    private Long siteId;

    @Column(name = "parameter_definition_id", nullable = false, updatable = false)
    private Long parameterDefinitionId;

    @Column(nullable = false, updatable = false)
    private String scope;

    @Column(name = "scope_ref_id", nullable = false, updatable = false)
    private Long scopeRefId;

    @Column(nullable = false)
    private String value;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected ParameterValue() {
    }

    static ParameterValue create(Long siteId, Long definitionId, String scope, Long scopeRefId, String value, String actor) {
        ParameterValue v = new ParameterValue();
        Instant now = Instant.now();
        v.siteId = siteId;
        v.parameterDefinitionId = definitionId;
        v.scope = scope;
        v.scopeRefId = scopeRefId;
        v.value = value;
        v.createdAt = now;
        v.createdBy = actor;
        v.updatedAt = now;
        v.updatedBy = actor;
        return v;
    }

    void change(String newValue, String actor) {
        this.value = newValue;
        this.updatedAt = Instant.now();
        this.updatedBy = actor;
    }

    Long getId() { return id; }
    String getValue() { return value; }
    long getVersion() { return version; }
}

interface ParameterValueRepository extends JpaRepository<ParameterValue, Long> {

    Optional<ParameterValue> findByParameterDefinitionIdAndScopeAndScopeRefId(Long definitionId, String scope, Long scopeRefId);
}
