package com.dimos.wms.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
                                 @NotBlank @Size(max = 128) String newPassword) {
    }

    /** Where the browser is registered as a workstation (or office mode) and whether that restricts the user. */
    record WorkstationInfo(String mode, boolean staleCookie, String code, String name, String type, String groupCode,
                           String locationCode, boolean restricted) {
        static WorkstationInfo of(WorkstationContext c) {
            return new WorkstationInfo(c.mode().name(), c.staleCookie(), c.code(), c.name(), c.type(), c.groupCode(),
                    c.locationCode(), c.restricts());
        }
    }

    record MeResponse(String username, String displayName, List<String> roles, List<String> permissions,
                      boolean mustChangePassword, WorkstationInfo workstation, int passwordMinLength) {
        /** The permissions shown are the ones that apply at this workstation. */
        static MeResponse of(WmsUserPrincipal p, WorkstationContext ctx, int passwordMinLength) {
            WmsUserPrincipal effective = ctx.restricts() ? WorkstationFilter.restrict(p, ctx) : p;
            return new MeResponse(effective.username(), effective.displayName(), effective.roles(), effective.permissions(),
                    effective.mustChangePassword(), WorkstationInfo.of(ctx), passwordMinLength);
        }
    }

    record Problem(String code, String message) {
    }

    private final AuthService auth;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    private final UserSessions sessions;
    private final WorkstationResolver workstationResolver;
    private final ParameterService params;

    AuthController(AuthService auth, SecurityContextRepository contextRepository, UserSessions sessions,
                   WorkstationResolver workstationResolver, ParameterService params) {
        this.params = params;
        this.sessions = sessions;
        this.workstationResolver = workstationResolver;
        this.auth = auth;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                            HttpServletResponse response) {
        AuthService.LoginResult result = auth.login(body.username(), body.password(), request.getRemoteAddr());
        switch (result.status()) {
            case LOCKED:
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new Problem("ACCOUNT_LOCKED",
                        "This account is temporarily locked after too many failed attempts. Try again later or ask an administrator."));
            case INVALID_CREDENTIALS:
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new Problem("INVALID_CREDENTIALS", "Invalid user name or password."));
            default:
                break;
        }
        if (request.getSession(false) != null) {
            request.changeSessionId(); // protects against session fixation
        }
        startSession(result.principal(), request, response);
        return ResponseEntity.ok(MeResponse.of(result.principal(), workstationResolver.resolve(request, result.principal().siteId()),
                auth.passwordMinLength(result.principal().siteId())));
    }

    @GetMapping("/me")
    MeResponse me(Authentication authentication, HttpServletRequest request) {
        WmsUserPrincipal principal = (WmsUserPrincipal) authentication.getPrincipal(); // already restricted by WorkstationFilter
        Object ctx = request.getAttribute(WorkstationFilter.ATTRIBUTE);
        WorkstationContext workstation = ctx instanceof WorkstationContext c ? c : WorkstationContext.OFFICE;
        return MeResponse.of(principal, workstation, auth.passwordMinLength(principal.siteId())); // restricting twice gives the same result
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        auth.recordLogout((WmsUserPrincipal) authentication.getPrincipal(), request.getRemoteAddr());
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest body, Authentication authentication,
                                     HttpServletRequest request, HttpServletResponse response) {
        WmsUserPrincipal principal = (WmsUserPrincipal) authentication.getPrincipal();
        AuthService.ChangeStatus status = auth.changePassword(principal, body.currentPassword(), body.newPassword(),
                request.getRemoteAddr());
        switch (status) {
            case WRONG_CURRENT_PASSWORD:
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(new Problem("WRONG_CURRENT_PASSWORD", "The current password is not correct."));
            case TOO_SHORT:
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Problem("PASSWORD_TOO_SHORT",
                        "The new password must have at least " + auth.passwordMinLength(principal.siteId()) + " characters."));
            case SAME_PASSWORD:
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(new Problem("SAME_PASSWORD", "The new password must be different from the current one."));
            default:
                break;
        }
        WmsUserPrincipal updated = principal.withMustChangePassword(false);
        startSession(updated, request, response);
        return ResponseEntity.ok(MeResponse.of(updated, workstationResolver.resolve(request, updated.siteId()),
                auth.passwordMinLength(updated.siteId())));
    }

    private void startSession(WmsUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        // Roles become ROLE_x; every permission becomes an authority of its own (e.g. MASTERDATA_EDIT).
        var authorities = Stream.concat(
                        principal.roles().stream().map(r -> "ROLE_" + r),
                        principal.permissions().stream())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        Authentication token = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(token);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
        sessions.register(principal.userId(), request.getSession());
        // how long this browser may stay idle: a system parameter, possibly different for a workstation (e.g. kiosks)
        WorkstationContext ws = workstationResolver.resolve(request, principal.siteId());
        boolean atWorkstation = ws.mode() == WorkstationContext.Mode.REGISTERED || ws.mode() == WorkstationContext.Mode.DISABLED;
        java.time.Duration timeout = params.durationValue("security.session_timeout", principal.siteId(),
                atWorkstation ? ws.groupId() : null, atWorkstation ? ws.id() : null);
        request.getSession().setMaxInactiveInterval((int) Math.min(timeout.toSeconds(), Integer.MAX_VALUE));
    }
}
