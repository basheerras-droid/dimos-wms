package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata/location-types")
class LocationTypeController
        extends MasterDataController<LocationType, LocationTypeService.Request, LocationTypeService.Response> {

    private final LocationTypeService service;

    LocationTypeController(LocationTypeService service) {
        this.service = service;
    }

    @Override
    protected LocationTypeService service() {
        return service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    PageResult<LocationTypeService.Response> list(@RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "50") int size,
                                                  @RequestParam(required = false) String sort,
                                                  @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.search(Specs.<LocationType>search(q, "code", "name"), page, size, sort, who);
    }
}
