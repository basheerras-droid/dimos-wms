package com.dimos.wms.identity;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** System parameters: PARAMETER_VIEW to look, PARAMETER_EDIT to change. */
@RestController
@RequestMapping("/api/admin/parameters")
class ParameterController {

    private final ParameterService service;

    ParameterController(ParameterService service) {
        this.service = service;
    }

    /** All parameters; with {@code workstationId} the effective values as seen at that workstation. */
    @GetMapping
    @PreAuthorize("hasAuthority('PARAMETER_VIEW')")
    List<ParameterService.ParameterView> list(@RequestParam(required = false) Long workstationId,
                                              @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.list(workstationId, who);
    }

    @PutMapping("/{code}/values")
    @PreAuthorize("hasAuthority('PARAMETER_EDIT')")
    ParameterService.ParameterView set(@PathVariable String code, @Valid @RequestBody ParameterService.SetRequest request,
                                       @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.set(code, request, who);
    }

    /** Removes one value: the parameter falls back to the next less specific value or the default. */
    @DeleteMapping("/{code}/values")
    @PreAuthorize("hasAuthority('PARAMETER_EDIT')")
    ParameterService.ParameterView remove(@PathVariable String code, @RequestParam String scope,
                                          @RequestParam(required = false) Long scopeRefId,
                                          @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.remove(code, scope, scopeRefId, who);
    }
}
