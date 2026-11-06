package com.dimos.wms.masterdata;

import com.dimos.wms.audit.AuditService;
import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
class LocationLinkService
        extends MasterDataService<LocationLink, LocationLinkService.Request, LocationLinkService.Response> {

    record Request(
            @NotNull Long fromLocationId,
            @NotNull Long toLocationId,
            Long huTypeId,
            Long equipmentId,
            @Min(0) @Max(1000000) Integer cost,
            Boolean enabled,
            Long version) implements VersionedRequest {
    }

    record Response(Long id, Long fromLocationId, String fromLocationCode, Long toLocationId,
                    String toLocationCode, Long huTypeId, String huTypeCode, Long equipmentId, String equipmentCode,
                    int cost, boolean enabled, long version, Instant updatedAt, String updatedBy) {
    }

    private final LocationLinkRepository repository;
    private final LocationRepository locations;
    private final HuTypeRepository huTypes;
    private final EquipmentRepository equipment;

    LocationLinkService(LocationLinkRepository repository, AuditService audit, JsonMapper json,
                        LocationRepository locations, HuTypeRepository huTypes, EquipmentRepository equipment) {
        super(repository, audit, json);
        this.repository = repository;
        this.locations = locations;
        this.huTypes = huTypes;
        this.equipment = equipment;
    }

    @Override
    String entityType() {
        return "location_link";
    }

    @Override
    String defaultSort() {
        return "id";
    }

    @Override
    LocationLink newEntity() {
        return new LocationLink();
    }

    @Override
    void apply(LocationLink l, Request r, WmsUserPrincipal who) {
        if (r.fromLocationId().equals(r.toLocationId())) {
            throw MasterDataException.invalid("toLocationId", "a link cannot lead to the same location");
        }
        Location from = locations.findById(r.fromLocationId()).filter(x -> x.siteId.equals(l.siteId))
                .orElseThrow(() -> MasterDataException.invalid("fromLocationId", "unknown location"));
        Location to = locations.findById(r.toLocationId()).filter(x -> x.siteId.equals(l.siteId))
                .orElseThrow(() -> MasterDataException.invalid("toLocationId", "unknown location"));
        HuType hu = r.huTypeId() == null ? null : huTypes.findById(r.huTypeId())
                .filter(x -> x.siteId.equals(l.siteId))
                .orElseThrow(() -> MasterDataException.invalid("huTypeId", "unknown handling-unit type"));
        Equipment eq = r.equipmentId() == null ? null : equipment.findById(r.equipmentId())
                .filter(x -> x.siteId.equals(l.siteId))
                .orElseThrow(() -> MasterDataException.invalid("equipmentId", "unknown equipment"));
        // A duplicate is the same from + to + handling-unit type (empty = all types) + equipment.
        Specification<LocationLink> same = Specs.<LocationLink>bySite(l.siteId)
                .and(refEquals("fromLocation", from.id)).and(refEquals("toLocation", to.id))
                .and(refEquals("huType", hu == null ? null : hu.id)).and(refEquals("equipment", eq == null ? null : eq.id));
        if (l.id != null) {
            same = same.and((root, q, cb) -> cb.notEqual(root.get("id"), l.id));
        }
        if (repository.exists(same)) {
            throw MasterDataException.invalid("toLocationId",
                    "this link already exists (same from, to, handling-unit type and equipment)");
        }
        l.fromLocation = from;
        l.toLocation = to;
        l.huType = hu;
        l.equipment = eq;
        l.cost = r.cost() == null ? 1 : r.cost();
        l.enabled = r.enabled() == null || r.enabled();
    }

    /** "reference equals id", where a missing id means "the reference is empty". */
    static Specification<LocationLink> refEquals(String field, Long id) {
        return (root, q, cb) -> id == null ? cb.isNull(root.get(field)) : cb.equal(root.get(field).get("id"), id);
    }

    @Override
    Response toResponse(LocationLink l) {
        return new Response(l.id, l.fromLocation.id, l.fromLocation.code, l.toLocation.id, l.toLocation.code,
                l.huType == null ? null : l.huType.id, l.huType == null ? null : l.huType.code,
                l.equipment == null ? null : l.equipment.id, l.equipment == null ? null : l.equipment.code,
                l.cost, l.enabled, l.version, l.updatedAt, l.updatedBy);
    }

    @Override
    Set<String> sortFields() {
        return Set.of("id", "cost", "enabled", "updatedAt");
    }
}
