package com.dimos.wms.identity;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User administration (permission USER_MANAGE). */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('USER_MANAGE')")
class UserAdminController {

    private final UserAdminService service;

    UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping("/users")
    UserAdminService.UserPage list(@RequestParam(required = false) String q,
                                   @RequestParam(required = false) Boolean enabled,
                                   @RequestParam(required = false) String role,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size,
                                   @RequestParam(required = false) String sort,
                                   @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.search(q, enabled, role, page, size, sort, who);
    }

    @GetMapping("/users/{id}")
    UserAdminService.UserView get(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.get(id, who);
    }

    @GetMapping("/roles")
    List<UserAdminService.RoleView> roles() {
        return service.roles();
    }

    /** The answer contains the temporary password once, so it must never be cached. */
    @PostMapping("/users")
    ResponseEntity<UserAdminService.CreatedUser> create(@Valid @RequestBody UserAdminService.Request request,
                                                        @AuthenticationPrincipal WmsUserPrincipal who) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.create(request, who));
    }

    @PutMapping("/users/{id}")
    UserAdminService.UserView update(@PathVariable Long id, @Valid @RequestBody UserAdminService.Request request,
                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.update(id, request, who);
    }

    @PostMapping("/users/{id}/reset-password")
    ResponseEntity<UserAdminService.TemporaryPassword> resetPassword(@PathVariable Long id,
                                                                     @AuthenticationPrincipal WmsUserPrincipal who) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resetPassword(id, who));
    }

    @PostMapping("/users/{id}/unlock")
    UserAdminService.UserView unlock(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return service.unlock(id, who);
    }
}
