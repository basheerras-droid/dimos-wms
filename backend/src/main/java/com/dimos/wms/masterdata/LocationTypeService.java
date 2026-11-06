package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class LocationTypeService extends MasterDataService<LocationType, LocationTypeService.Request, LocationTypeService.Response> {

    record Request(
            @NotBlank @Size(max = 30) @Pattern(regexp = AreaService.CODE_PATTERN, message = "use letters, digits, _ . -") String code,
            @NotBlank @Size(max = 100) String name,
            Boolean storage, Boolean transfer, Boolean workstation,
            @Min(0) @Max(100000) Integer capacityHu,
            Long version) implements VersionedRequest {
    }

    record Response(Long id, String code, String name, boolean storage, boolean transfer, boolean workstation,
                    int capacityHu, long version, Instant updatedAt, String updatedBy) {
    }

    private final JdbcTemplate jdbc;

    LocationTypeService(LocationTypeRepository repository, AuditService audit, JsonMapper json, JdbcTemplate jdbc) {
        super(repository, audit, json);
        this.jdbc = jdbc;
    }

    @Override
    String entityType() {
        return "location_type";
    }

    @Override
    LocationType newEntity() {
        return new LocationType();
    }

    @Override
    void apply(LocationType t, Request r, WmsUserPrincipal who) {
        String code = normalizeCode(r.code());
        checkCodeFree(t, code);
        t.code = code;
        t.name = r.name().trim();
        t.storage = Boolean.TRUE.equals(r.storage());
        t.transfer = Boolean.TRUE.equals(r.transfer());
        t.workstation = Boolean.TRUE.equals(r.workstation());
        t.capacityHu = r.capacityHu() == null ? 1 : r.capacityHu();
    }

    @Override
    void beforeDelete(LocationType t) {
        jdbc.update("delete from location_type_hu_type where location_type_id = ?", t.id);
    }

    @Override
    Response toResponse(LocationType t) {
        return new Response(t.id, t.code, t.name, t.storage, t.transfer, t.workstation, t.capacityHu, t.version,
                t.updatedAt, t.updatedBy);
    }

    @Override
    java.util.List<Usage> usages() {
        return java.util.List.of(
                new Usage("location", "locations", "select count(*) from location where location_type_id = ?"));
    }

    @Override
    String displayName(LocationType t) {
        return "Location type " + t.code;
    }

    @Override
    Set<String> sortFields() {
        return Set.of("id", "code", "name", "capacityHu", "updatedAt");
    }
}
