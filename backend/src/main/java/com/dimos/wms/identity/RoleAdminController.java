package com.dimos.wms.identity;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The permission catalogue and the roles. Reading is open to whoever manages users or roles (the user screen needs the
 * role list); changing needs ROLE_MANAGE and, in addition, the SUPER_ADMIN role (checked in the service).
 */
@RestController
@RequestMapping("/api/admin")
class RoleAdminController {

    private static final String READ = "hasAnyAuthority('ROLE_MANAGE', 'USER_MANAGE')";
    private static final String WRITE = "hasAuthority('ROLE_MANAGE')";

    private final RoleAdminService service;

    RoleAdminController(RoleAdminService service) {
        this.service = service;
    }

    @GetMapping("/permissions")
    @PreAuthorize(READ)
    List<RoleAdminService.PermissionView> permissions() {
        return service.permissionCatalogue();
    }

    @GetMapping("/roles")
    @PreAuthorize(READ)
    List<RoleAdminService.RoleView> roles() {
        return service.list();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITE)
    RoleAdminService.RoleView create(@Valid @RequestBody RoleAdminService.RoleRequest request,
                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.create(request, who);
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize(WRITE)
    RoleAdminService.RoleView update(@PathVariable Long id, @Valid @RequestBody RoleAdminService.RoleRequest request,
                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.update(id, request, who);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITE)
    void delete(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        service.delete(id, who);
    }
}
