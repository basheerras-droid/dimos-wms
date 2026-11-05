package com.dimos.wms.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** While a user must change the password, the API only allows: who am I, change password, log out. */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of("/api/auth/me", "/api/auth/change-password",
            "/api/auth/logout");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof WmsUserPrincipal p && p.mustChangePassword()
                && request.getRequestURI().startsWith("/api/") && !ALLOWED.contains(request.getRequestURI())) {
            JsonErrors.write(response, 403, "PASSWORD_CHANGE_REQUIRED", "You must change your password first.");
            return;
        }
        chain.doFilter(request, response);
    }
}
