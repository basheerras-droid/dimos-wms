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
@RequestMapping("/api/masterdata/equipment")
class EquipmentController extends MasterDataController<Equipment, EquipmentService.Request, EquipmentService.Response> {

    private final EquipmentService service;

    EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @Override
    protected EquipmentService service() {
        return service;
    }

    @GetMapping
    @PreAuthorize(VIEW)
    PageResult<EquipmentService.Response> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) Long equipmentTypeId,
                                               @RequestParam(required = false) Long areaId,
                                               @RequestParam(required = false) Boolean enabled,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size,
                                               @RequestParam(required = false) String sort,
                                               @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.search(Specs.all(Specs.<Equipment>search(q, "code", "name"),
                equipmentTypeId == null ? null : (Specification<Equipment>) (root, query, cb) -> cb.equal(root.get("equipmentType").get("id"), equipmentTypeId),
                areaId == null ? null : (Specification<Equipment>) (root, query, cb) -> cb.equal(root.get("area").get("id"), areaId),
                Specs.<Equipment>equal("enabled", enabled)), page, size, sort, who);
    }
}
