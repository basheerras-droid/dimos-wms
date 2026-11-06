package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata/hu-types")
class HuTypeController extends MasterDataController<HuType, HuTypeService.Request, HuTypeService.Response> {

    private final HuTypeService service;

    HuTypeController(HuTypeService service) {
        this.service = service;
    }

    @Override
    protected HuTypeService service() {
        return service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    PageResult<HuTypeService.Response> list(@RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean container,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size,
                                            @RequestParam(required = false) String sort,
                                            @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.search(Specs.all(Specs.<HuType>search(q, "code", "name"),
                Specs.<HuType>equal("container", container)), page, size, sort, who);
    }
}
