package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class AreaService extends MasterDataService<Area, AreaService.Request, AreaService.Response> {

    static final String CODE_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.\\-]*";

    record Request(
            @NotBlank @Size(max = 30) @Pattern(regexp = CODE_PATTERN, message = "use letters, digits, _ . -") String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "INBOUND|STORAGE|OUTBOUND|BUFFER|WORKSTATION|TRANSPORT",
                    message = "must be INBOUND, STORAGE, OUTBOUND, BUFFER, WORKSTATION or TRANSPORT") String areaType,
            Long version) implements VersionedRequest {
    }

    record Response(Long id, String code, String name, String areaType, long version, Instant updatedAt,
                    String updatedBy) {
    }

    AreaService(AreaRepository repository, AuditService audit, JsonMapper json) {
        super(repository, audit, json);
    }

    @Override
    String entityType() {
        return "area";
    }

    @Override
    Area newEntity() {
        return new Area();
    }

    @Override
    void apply(Area a, Request r, WmsUserPrincipal who) {
        String code = normalizeCode(r.code());
        checkCodeFree(a, code);
        a.code = code;
        a.name = r.name().trim();
        a.areaType = r.areaType();
    }

    @Override
    Response toResponse(Area a) {
        return new Response(a.id, a.code, a.name, a.areaType, a.version, a.updatedAt, a.updatedBy);
    }

    @Override
    Set<String> sortFields() {
        return Set.of("id", "code", "name", "areaType", "updatedAt");
    }
}
