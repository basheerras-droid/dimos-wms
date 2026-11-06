package com.dimos.wms.identity;

import jakarta.validation.Valid;
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

/** User groups (permission USER_MANAGE). */
@RestController
@RequestMapping("/api/admin/groups")
@PreAuthorize("hasAuthority('USER_MANAGE')")
class GroupAdminController {

    private final GroupAdminService service;

    GroupAdminController(GroupAdminService service) {
        this.service = service;
    }

    @GetMapping
    GroupAdminService.GroupPage list(@RequestParam(required = false) String q,
                                     @RequestParam(required = false) Boolean enabled,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String sort,
                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.search(q, enabled, page, size, sort, who);
    }

    @GetMapping("/{id}")
    GroupAdminService.GroupView get(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.get(id, who);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GroupAdminService.GroupView create(@Valid @RequestBody GroupAdminService.Request request,
                                       @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.create(request, who);
    }

    @PutMapping("/{id}")
    GroupAdminService.GroupView update(@PathVariable Long id, @Valid @RequestBody GroupAdminService.Request request,
                                       @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.update(id, request, who);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        service.delete(id, who);
    }
}
