package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Data for the layout preview (read-only, MASTERDATA_VIEW). */
@RestController
@RequestMapping("/api/masterdata/layout")
class LayoutController {

    private final LayoutService service;

    LayoutController(LayoutService service) {
        this.service = service;
    }

    /** The plan of one level (the lowest level when none is given). */
    @GetMapping("/plan")
    @PreAuthorize(MasterDataController.VIEW)
    LayoutService.Plan plan(@RequestParam(required = false) Integer levelNo, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.plan(who.siteId(), levelNo);
    }

    @GetMapping("/racks")
    @PreAuthorize(MasterDataController.VIEW)
    List<LayoutService.Rack> racks(@AuthenticationPrincipal WmsUserPrincipal who) {
        return service.racks(who.siteId());
    }

    /** One aisle side as a grid. Leave {@code side} out for racks without a side. */
    @GetMapping("/rack-face")
    @PreAuthorize(MasterDataController.VIEW)
    LayoutService.RackFace rackFace(@RequestParam String aisle, @RequestParam(required = false) String side,
                                    @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.rackFace(who.siteId(), aisle, side);
    }
}
