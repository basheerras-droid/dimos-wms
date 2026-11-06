package com.dimos.wms.masterdata;

import com.dimos.wms.identity.WmsUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Equipment types are one shared list; only SUPER_ADMIN may add, rename or delete (never the built-in ones). */
@RestController
@RequestMapping("/api/masterdata/equipment-types")
class EquipmentTypeController {

    private static final String SUPER = "hasRole('SUPER_ADMIN')";

    private final EquipmentTypeService service;

    EquipmentTypeController(EquipmentTypeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(MasterDataController.VIEW)
    List<EquipmentTypeService.Response> list(@RequestParam(required = false) String q) {
        return service.list(q);
    }

    @GetMapping("/{id}")
    @PreAuthorize(MasterDataController.VIEW)
    EquipmentTypeService.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SUPER)
    EquipmentTypeService.Response create(@Valid @RequestBody EquipmentTypeService.Request request,
                                         @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.create(request, who);
    }

    @PutMapping("/{id}")
    @PreAuthorize(SUPER)
    EquipmentTypeService.Response update(@PathVariable Long id, @Valid @RequestBody EquipmentTypeService.Request request,
                                         @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.update(id, request, who);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(SUPER)
    void delete(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        service.delete(id, who);
    }
}
