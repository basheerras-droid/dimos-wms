package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata/areas")
class AreaController extends MasterDataController<Area, AreaService.Request, AreaService.Response> {

    private final AreaService service;

    AreaController(AreaService service) {
        this.service = service;
    }

    @Override
    protected AreaService service() {
        return service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    PageResult<AreaService.Response> list(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) String areaType,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size,
                                          @RequestParam(required = false) String sort,
                                          @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.search(Specs.all(Specs.<Area>search(q, "code", "name"), Specs.<Area>equal("areaType", areaType)),
                page, size, sort, who);
    }
}
