package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/masterdata")
class AssignmentController {

    private final AssignmentService service;

    AssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/location-types/{id}/hu-types")
    @PreAuthorize(MasterDataController.VIEW)
    AssignmentService.Ids huTypes(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.huTypesOfLocationType(id, who);
    }

    @PutMapping("/location-types/{id}/hu-types")
    @PreAuthorize(MasterDataController.EDIT)
    AssignmentService.Ids setHuTypes(@PathVariable Long id, @Valid @RequestBody AssignmentService.HuTypeIds body,
                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.setHuTypesOfLocationType(id, body, who);
    }

    @GetMapping("/equipment/{id}/locations")
    @PreAuthorize(MasterDataController.VIEW)
    AssignmentService.Ids locations(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.locationsOfEquipment(id, who);
    }

    @PutMapping("/equipment/{id}/locations")
    @PreAuthorize(MasterDataController.EDIT)
    AssignmentService.Ids setLocations(@PathVariable Long id, @Valid @RequestBody AssignmentService.LocationIds body,
                                       @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.setLocationsOfEquipment(id, body, who);
    }

    @GetMapping("/hu-types/{id}/placed-in")
    @PreAuthorize(MasterDataController.VIEW)
    List<AssignmentService.Nesting> placedIn(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.placedIn(id, who);
    }

    @PutMapping("/hu-types/{id}/placed-in")
    @PreAuthorize(MasterDataController.EDIT)
    List<AssignmentService.Nesting> setPlacedIn(@PathVariable Long id,
                                                @Valid @RequestBody AssignmentService.NestingList body,
                                                @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.setPlacedIn(id, body, who);
    }
}
