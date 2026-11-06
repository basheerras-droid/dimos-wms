package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata/locations")
class LocationController extends MasterDataController<Location, LocationService.Request, LocationService.Response> {

    private final LocationService service;

    LocationController(LocationService service) {
        this.service = service;
    }

    @Override
    protected LocationService service() {
        return service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    PageResult<LocationService.Response> list(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) Long areaId,
                                              @RequestParam(required = false) Long locationTypeId,
                                              @RequestParam(required = false) Integer levelNo,
                                              @RequestParam(required = false) Boolean enabled,
                                              @RequestParam(required = false) Boolean blocked,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size,
                                              @RequestParam(required = false) String sort,
                                              @AuthenticationPrincipal WmsUserPrincipal who) {
        Specification<Location> blockedFilter = blocked == null ? null : (root, query, cb) -> {
            var isBlocked = cb.or(cb.isTrue(root.get("blockedIn")), cb.isTrue(root.get("blockedOut")));
            return blocked ? isBlocked : cb.not(isBlocked);
        };
        return service.search(Specs.all(Specs.<Location>search(q, "code", "name"),
                areaId == null ? null : (Specification<Location>) (root, query, cb) -> cb.equal(root.get("area").get("id"), areaId),
                locationTypeId == null ? null : (Specification<Location>) (root, query, cb) -> cb.equal(root.get("locationType").get("id"), locationTypeId),
                Specs.<Location>equal("levelNo", levelNo),
                Specs.<Location>equal("enabled", enabled),
                blockedFilter), page, size, sort, who);
    }

    /** Block / unblock / enable / disable many locations at once. */
    @PostMapping("/bulk")
    @PreAuthorize(EDIT)
    LocationService.BulkResult bulk(@Valid @RequestBody LocationService.BulkRequest request,
                                    @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.bulk(request, who);
    }
}
