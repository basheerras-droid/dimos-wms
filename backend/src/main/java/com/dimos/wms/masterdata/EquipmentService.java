package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class EquipmentService extends MasterDataService<Equipment, EquipmentService.Request, EquipmentService.Response> {

    record Request(
            @NotBlank @Size(max = 50) @Pattern(regexp = AreaService.CODE_PATTERN, message = "use letters, digits, _ . -") String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull Long equipmentTypeId,
            Long areaId,
            Long parentId,
            @Size(max = 255) String plcConnection,
            Boolean enabled,
            @Size(max = 100) String externalRef,
            @Size(max = 4000) String extAttributes,
            Long version) implements VersionedRequest {
    }

    record Response(Long id, String code, String name, Long equipmentTypeId, String equipmentTypeCode, Long areaId,
                    String areaCode, Long parentId, String plcConnection, boolean enabled, String externalRef,
                    String extAttributes, long version, Instant updatedAt, String updatedBy) {
    }

    private final EquipmentRepository repository;
    private final EquipmentTypeRepository types;
    private final AreaRepository areas;
    private final JdbcTemplate jdbc;

    EquipmentService(EquipmentRepository repository, AuditService audit, JsonMapper json,
                     EquipmentTypeRepository types, AreaRepository areas, JdbcTemplate jdbc) {
        super(repository, audit, json);
        this.repository = repository;
        this.types = types;
        this.areas = areas;
        this.jdbc = jdbc;
    }

    @Override
    String entityType() {
        return "equipment";
    }

    @Override
    Equipment newEntity() {
        return new Equipment();
    }

    @Override
    void apply(Equipment e, Request r, WmsUserPrincipal who) {
        String code = normalizeCode(r.code());
        checkCodeFree(e, code);
        EquipmentType type = types.findById(r.equipmentTypeId())
                .orElseThrow(() -> MasterDataException.invalid("equipmentTypeId", "unknown equipment type"));
        Area area = null;
        if (r.areaId() != null) {
            area = areas.findById(r.areaId()).filter(a -> a.siteId.equals(e.siteId))
                    .orElseThrow(() -> MasterDataException.invalid("areaId", "unknown area"));
        }
        Equipment parent = null;
        if (r.parentId() != null) {
            parent = repository.findById(r.parentId()).filter(p -> p.siteId.equals(e.siteId))
                    .orElseThrow(() -> MasterDataException.invalid("parentId", "unknown equipment"));
            for (Equipment p = parent; p != null; p = p.parent) { // no loops: A inside B inside A
                if (p.id.equals(e.id)) {
                    throw MasterDataException.invalid("parentId", "equipment cannot be placed inside itself");
                }
            }
        }
        checkJson("extAttributes", r.extAttributes());
        e.code = code;
        e.name = r.name().trim();
        e.equipmentType = type;
        e.area = area;
        e.parent = parent;
        e.plcConnection = blankToNull(r.plcConnection());
        e.enabled = r.enabled() == null || r.enabled();
        e.externalRef = blankToNull(r.externalRef());
        e.extAttributes = blankToNull(r.extAttributes());
    }

    @Override
    void beforeDelete(Equipment e) {
        jdbc.update("delete from equipment_location where equipment_id = ?", e.id);
    }

    @Override
    Response toResponse(Equipment e) {
        return new Response(e.id, e.code, e.name, e.equipmentType.id, e.equipmentType.code,
                e.area == null ? null : e.area.id, e.area == null ? null : e.area.code,
                e.parent == null ? null : e.parent.id, e.plcConnection, e.enabled, e.externalRef, e.extAttributes,
                e.version, e.updatedAt, e.updatedBy);
    }

    @Override
    Set<String> sortFields() {
        return Set.of("id", "code", "name", "enabled", "updatedAt");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
