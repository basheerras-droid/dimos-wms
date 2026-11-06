package com.dimos.wms.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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

/** Workstations and workstation groups (permission WORKSTATION_MANAGE) and the registration of the calling browser. */
@RestController
@RequestMapping("/api")
class WorkstationAdminController {

    private static final String MANAGE = "hasAuthority('WORKSTATION_MANAGE')";
    /** Long-lived: a registered browser stays registered for years unless its cookies are cleared. */
    private static final Duration COOKIE_LIFETIME = Duration.ofDays(3650);

    private final WorkstationAdminService workstations;
    private final WorkstationGroupAdminService groups;

    WorkstationAdminController(WorkstationAdminService workstations, WorkstationGroupAdminService groups) {
        this.workstations = workstations;
        this.groups = groups;
    }

    // ---- workstation groups ---------------------------------------------------------------------------------

    @GetMapping("/admin/workstation-groups")
    @PreAuthorize(MANAGE)
    WorkstationGroupAdminService.GroupPage listGroups(@RequestParam(required = false) String q,
                                                      @RequestParam(required = false) Boolean enabled,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size,
                                                      @RequestParam(required = false) String sort,
                                                      @AuthenticationPrincipal WmsUserPrincipal who) {
        return groups.search(q, enabled, page, size, sort, who);
    }

    @GetMapping("/admin/workstation-groups/{id}")
    @PreAuthorize(MANAGE)
    WorkstationGroupAdminService.GroupView getGroup(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return groups.get(id, who);
    }

    @PostMapping("/admin/workstation-groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    WorkstationGroupAdminService.GroupView createGroup(@Valid @RequestBody WorkstationGroupAdminService.Request request,
                                                       @AuthenticationPrincipal WmsUserPrincipal who) {
        return groups.create(request, who);
    }

    @PutMapping("/admin/workstation-groups/{id}")
    @PreAuthorize(MANAGE)
    WorkstationGroupAdminService.GroupView updateGroup(@PathVariable Long id,
                                                       @Valid @RequestBody WorkstationGroupAdminService.Request request,
                                                       @AuthenticationPrincipal WmsUserPrincipal who) {
        return groups.update(id, request, who);
    }

    @DeleteMapping("/admin/workstation-groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    void deleteGroup(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        groups.delete(id, who);
    }

    // ---- workstations ---------------------------------------------------------------------------------------

    @GetMapping("/admin/workstations")
    @PreAuthorize(MANAGE)
    WorkstationAdminService.WorkstationPage list(@RequestParam(required = false) String q,
                                                 @RequestParam(required = false) String type,
                                                 @RequestParam(required = false) Boolean enabled,
                                                 @RequestParam(required = false) Long groupId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size,
                                                 @RequestParam(required = false) String sort,
                                                 @AuthenticationPrincipal WmsUserPrincipal who) {
        return workstations.search(q, type, enabled, groupId, page, size, sort, who);
    }

    @GetMapping("/admin/workstations/{id}")
    @PreAuthorize(MANAGE)
    WorkstationAdminService.WorkstationView get(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return workstations.get(id, who);
    }

    @PostMapping("/admin/workstations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    WorkstationAdminService.WorkstationView create(@Valid @RequestBody WorkstationAdminService.Request request,
                                                   @AuthenticationPrincipal WmsUserPrincipal who) {
        return workstations.create(request, who);
    }

    @PutMapping("/admin/workstations/{id}")
    @PreAuthorize(MANAGE)
    WorkstationAdminService.WorkstationView update(@PathVariable Long id,
                                                   @Valid @RequestBody WorkstationAdminService.Request request,
                                                   @AuthenticationPrincipal WmsUserPrincipal who) {
        return workstations.update(id, request, who);
    }

    @DeleteMapping("/admin/workstations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(MANAGE)
    void delete(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        workstations.delete(id, who);
    }

    /** The browser that sends this request becomes the workstation: the token goes into an HttpOnly cookie. */
    @PostMapping("/admin/workstations/{id}/register")
    @PreAuthorize(MANAGE)
    ResponseEntity<WorkstationAdminService.WorkstationView> register(@PathVariable Long id,
                                                                      @AuthenticationPrincipal WmsUserPrincipal who) {
        WorkstationAdminService.Registration r = workstations.register(id, who);
        ResponseCookie cookie = ResponseCookie.from(WorkstationResolver.COOKIE, r.token())
                .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(COOKIE_LIFETIME).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore()).body(r.view());
    }

    @PostMapping("/admin/workstations/{id}/revoke")
    @PreAuthorize(MANAGE)
    WorkstationAdminService.WorkstationView revoke(@PathVariable Long id, @AuthenticationPrincipal WmsUserPrincipal who) {
        return workstations.revoke(id, who);
    }

    /**
     * "Unregister this browser": a super administrator can always put the device he or she is sitting at back to
     * office mode, whatever a workstation group allows. No other permission is needed.
     */
    @PostMapping("/workstation/unregister")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    ResponseEntity<Void> unregisterThisBrowser(HttpServletRequest request, @AuthenticationPrincipal WmsUserPrincipal who) {
        workstations.unregisterBrowser(WorkstationResolver.tokenOf(request), who);
        ResponseCookie gone = ResponseCookie.from(WorkstationResolver.COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, gone.toString()).build();
    }
}
