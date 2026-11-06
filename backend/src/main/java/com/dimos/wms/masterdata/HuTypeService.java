package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class HuTypeService extends MasterDataService<HuType, HuTypeService.Request, HuTypeService.Response> {

    record Request(
            @NotBlank @Size(max = 30) @Pattern(regexp = AreaService.CODE_PATTERN, message = "use letters, digits, _ . -") String code,
            @NotBlank @Size(max = 100) String name,
            @Min(1) Integer lengthMm, @Min(1) Integer widthMm, @Min(1) Integer heightMm,
            @DecimalMin("0") BigDecimal tareKg, @DecimalMin("0") BigDecimal maxGrossKg,
            Boolean container,
            Long version) implements VersionedRequest {
    }

    record Response(Long id, String code, String name, Integer lengthMm, Integer widthMm, Integer heightMm,
                    BigDecimal tareKg, BigDecimal maxGrossKg, boolean container, long version, Instant updatedAt,
                    String updatedBy) {
    }

    private final JdbcTemplate jdbc;

    HuTypeService(HuTypeRepository repository, AuditService audit, JsonMapper json, JdbcTemplate jdbc) {
        super(repository, audit, json);
        this.jdbc = jdbc;
    }

    @Override
    String entityType() {
        return "hu_type";
    }

    @Override
    HuType newEntity() {
        return new HuType();
    }

    @Override
    void apply(HuType h, Request r, WmsUserPrincipal who) {
        String code = normalizeCode(r.code());
        checkCodeFree(h, code);
        if (r.tareKg() != null && r.maxGrossKg() != null && r.maxGrossKg().compareTo(r.tareKg()) < 0) {
            throw MasterDataException.invalid("maxGrossKg", "must not be less than the tare weight");
        }
        h.code = code;
        h.name = r.name().trim();
        h.lengthMm = r.lengthMm();
        h.widthMm = r.widthMm();
        h.heightMm = r.heightMm();
        h.tareKg = r.tareKg();
        h.maxGrossKg = r.maxGrossKg();
        h.container = Boolean.TRUE.equals(r.container());
    }

    /** The "may be placed in" rows of this type belong to it; rows where it is the outer type block the delete. */
    @Override
    void beforeDelete(HuType h) {
        jdbc.update("delete from hu_type_nesting where inner_hu_type_id = ?", h.id);
    }

    @Override
    Response toResponse(HuType h) {
        return new Response(h.id, h.code, h.name, h.lengthMm, h.widthMm, h.heightMm, h.tareKg, h.maxGrossKg,
                h.container, h.version, h.updatedAt, h.updatedBy);
    }

    @Override
    Set<String> sortFields() {
        return Set.of("id", "code", "name", "lengthMm", "widthMm", "heightMm", "updatedAt");
    }
}
