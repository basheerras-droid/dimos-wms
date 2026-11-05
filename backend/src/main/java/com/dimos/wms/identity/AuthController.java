package com.dimos.wms.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.stream.Collectors;
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
                                 @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }

    record MeResponse(String username, String displayName, List<String> roles, boolean mustChangePassword) {
        static MeResponse of(WmsUserPrincipal p) {
            return new MeResponse(p.username(), p.displayName(), p.roles(), p.mustChangePassword());
        }
    }

    record Problem(String code, String message) {
    }

    private final AuthService auth;
    private final SecurityContextRepository contextRepository;
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    AuthController(AuthService auth, SecurityContextRepository contextRepository) {
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
        return ResponseEntity.ok(MeResponse.of(result.principal()));
    }

    @GetMapping("/me")
    MeResponse me(Authentication authentication) {
        return MeResponse.of((WmsUserPrincipal) authentication.getPrincipal());
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
            case SAME_PASSWORD:
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                        .body(new Problem("SAME_PASSWORD", "The new password must be different from the current one."));
            default:
                break;
        }
        WmsUserPrincipal updated = principal.withMustChangePassword(false);
        startSession(updated, request, response);
        return ResponseEntity.ok(MeResponse.of(updated));
    }

    private void startSession(WmsUserPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        var authorities = principal.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        Authentication token = UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(token);
        holder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }
}
