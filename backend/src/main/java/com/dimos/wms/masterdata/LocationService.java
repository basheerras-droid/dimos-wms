package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class LocationService extends MasterDataService<Location, LocationService.Request, LocationService.Response> {

    static final int MAX_BULK = 1000;

    record Request(
            @NotBlank @Size(max = 50) @Pattern(regexp = AreaService.CODE_PATTERN, message = "use letters, digits, _ . -") String code,
            @Size(max = 100) String name,
            @NotNull Long areaId,
            @NotNull Long locationTypeId,
            Integer xMm, Integer yMm, Integer zMm,
            Integer levelNo,
            @Size(max = 20) String aisle,
            @Size(max = 10) String side,
            Integer columnNo, Integer rowNo,
            @DecimalMin("0") BigDecimal maxWeightKg,
            Boolean enabled, Boolean blockedIn, Boolean blockedOut,
            @Size(max = 255) String blockReason,
            @Size(max = 100) String externalRef,
            @Size(max = 4000) String extAttributes,
            Long version) implements VersionedRequest {
    }

    record Response(Long id, String code, String name, Long areaId, String areaCode, Long locationTypeId,
                    String locationTypeCode, Integer xMm, Integer yMm, Integer zMm, int levelNo, String aisle,
                    String side, Integer columnNo, Integer rowNo, BigDecimal maxWeightKg, boolean enabled,
                    boolean blockedIn, boolean blockedOut, String blockReason, String externalRef,
                    String extAttributes, long version, Instant updatedAt, String updatedBy) {
    }

    enum BulkAction { BLOCK_IN, UNBLOCK_IN, BLOCK_OUT, UNBLOCK_OUT, ENABLE, DISABLE }

    record BulkRequest(@NotNull @Size(min = 1, max = MAX_BULK) List<Long> ids, @NotNull BulkAction action,
                       @Size(max = 255) String reason) {
    }

    record BulkResult(int updated) {
    }

    private final AreaRepository areas;
    private final LocationTypeRepository types;

    LocationService(LocationRepository repository, AuditService audit, JsonMapper json, AreaRepository areas,
                    LocationTypeRepository types) {
        super(repository, audit, json);
        this.areas = areas;
        this.types = types;
    }

    @Override
    String entityType() {
        return "location";
    }

    @Override
    Location newEntity() {
        return new Location();
    }

    @Override
    void apply(Location l, Request r, WmsUserPrincipal who) {
        String code = normalizeCode(r.code());
        checkCodeFree(l, code);
        l.area = areas.findById(r.areaId()).filter(a -> a.siteId.equals(l.siteId))
                .orElseThrow(() -> MasterDataException.invalid("areaId", "unknown area"));
        l.locationType = types.findById(r.locationTypeId()).filter(t -> t.siteId.equals(l.siteId))
                .orElseThrow(() -> MasterDataException.invalid("locationTypeId", "unknown location type"));
        boolean blockedIn = Boolean.TRUE.equals(r.blockedIn());
        boolean blockedOut = Boolean.TRUE.equals(r.blockedOut());
        if ((blockedIn || blockedOut) && (r.blockReason() == null || r.blockReason().isBlank())) {
            throw MasterDataException.invalid("blockReason", "a reason is required when the location is blocked");
        }
        checkJson("extAttributes", r.extAttributes());
        l.code = code;
        l.name = blankToNull(r.name());
        l.xMm = r.xMm();
        l.yMm = r.yMm();
        l.zMm = r.zMm();
        l.levelNo = r.levelNo() == null ? 0 : r.levelNo();
        l.aisle = blankToNull(r.aisle());
        l.side = blankToNull(r.side());
        l.columnNo = r.columnNo();
        l.rowNo = r.rowNo();
        l.maxWeightKg = r.maxWeightKg();
        l.enabled = r.enabled() == null || r.enabled();
        l.blockedIn = blockedIn;
        l.blockedOut = blockedOut;
        l.blockReason = blockedIn || blockedOut ? r.blockReason().trim() : null;
        l.externalRef = blankToNull(r.externalRef());
        l.extAttributes = blankToNull(r.extAttributes());
    }

    @Override
    Response toResponse(Location l) {
        return new Response(l.id, l.code, l.name, l.area.id, l.area.code, l.locationType.id, l.locationType.code,
                l.xMm, l.yMm, l.zMm, l.levelNo, l.aisle, l.side, l.columnNo, l.rowNo, l.maxWeightKg, l.enabled,
                l.blockedIn, l.blockedOut, l.blockReason, l.externalRef, l.extAttributes, l.version, l.updatedAt,
                l.updatedBy);
    }

    @Override
    java.util.List<Usage> usages() {
        return java.util.List.of(
                new Usage("link that starts here", "links that start here",
                        "select count(*) from location_link where from_location_id = ?"),
                new Usage("link that ends here", "links that end here",
                        "select count(*) from location_link where to_location_id = ?"),
                new Usage("piece of equipment it is assigned to", "pieces of equipment it is assigned to",
                        "select count(*) from equipment_location where location_id = ?"),
                new Usage("workstation placed here", "workstations placed here",
                        "select count(*) from workstation where location_id = ?"));
    }

    @Override
    String displayName(Location l) {
        return "Location " + l.code;
    }

    @Override
    Set<String> sortFields() {
        return Set.of("id", "code", "name", "levelNo", "aisle", "columnNo", "rowNo", "enabled", "updatedAt");
    }

    /** Block / unblock / enable / disable many locations at once. All or nothing; each change is audited. */
    @Transactional
    public BulkResult bulk(BulkRequest request, WmsUserPrincipal who) {
        boolean block = request.action() == BulkAction.BLOCK_IN || request.action() == BulkAction.BLOCK_OUT;
        if (block && (request.reason() == null || request.reason().isBlank())) {
            throw MasterDataException.invalid("reason", "a reason is required when blocking");
        }
        List<Long> ids = request.ids().stream().distinct().toList();
        int updated = 0;
        for (Long id : ids) {
            Location l = load(id, who);
            Response before = toResponse(l);
            switch (request.action()) {
                case BLOCK_IN -> l.blockedIn = true;
                case UNBLOCK_IN -> l.blockedIn = false;
                case BLOCK_OUT -> l.blockedOut = true;
                case UNBLOCK_OUT -> l.blockedOut = false;
                case ENABLE -> l.enabled = true;
                case DISABLE -> l.enabled = false;
            }
            if (block) {
                l.blockReason = request.reason().trim();
            } else if (!l.blockedIn && !l.blockedOut) {
                l.blockReason = null;
            }
            if (!toResponse(l).equals(before)) { // something really changed
                l.stamp(who.username());
                updated++;
                record("BULK_" + request.action(), who, id, before, toResponse(l));
            }
        }
        try {
            flush();
        } catch (DataIntegrityViolationException ex) {
            throw MasterDataException.stale();
        }
        return new BulkResult(updated);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
