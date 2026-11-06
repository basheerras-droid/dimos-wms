package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata/location-links")
class LocationLinkController
        extends MasterDataController<LocationLink, LocationLinkService.Request, LocationLinkService.Response> {

    private final LocationLinkService service;

    LocationLinkController(LocationLinkService service) {
        this.service = service;
    }

    @Override
    protected LocationLinkService service() {
        return service;
    }

    /** {@code locationId} finds links that start or end at one location; {@code q} searches both location codes. */
    @GetMapping
    @PreAuthorize(VIEW)
    PageResult<LocationLinkService.Response> list(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) Long locationId,
                                                  @RequestParam(required = false) Long huTypeId,
                                                  @RequestParam(required = false) Long equipmentId,
                                                  @RequestParam(required = false) Boolean enabled,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size,
                                                  @RequestParam(required = false) String sort,
                                                  @AuthenticationPrincipal WmsUserPrincipal who) {
        Specification<LocationLink> text = q == null || q.isBlank() ? null : (root, query, cb) -> {
            String p = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            return cb.or(cb.like(cb.lower(root.get("fromLocation").get("code")), p, '\\'),
                    cb.like(cb.lower(root.get("toLocation").get("code")), p, '\\'));
        };
        Specification<LocationLink> atLocation = locationId == null ? null : (root, query, cb) -> cb.or(
                cb.equal(root.get("fromLocation").get("id"), locationId),
                cb.equal(root.get("toLocation").get("id"), locationId));
        Specification<LocationLink> hu = huTypeId == null ? null : (root, query, cb) ->
                cb.equal(root.get("huType").get("id"), huTypeId);
        Specification<LocationLink> eq = equipmentId == null ? null : (root, query, cb) ->
                cb.equal(root.get("equipment").get("id"), equipmentId);
        return service.search(Specs.all(text, atLocation, hu, eq, Specs.<LocationLink>equal("enabled", enabled)),
                page, size, sort, who);
    }
}
