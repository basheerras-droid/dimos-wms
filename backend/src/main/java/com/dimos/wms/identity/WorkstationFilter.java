package com.dimos.wms.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the workstation to every API request of a signed-in user: effective permissions = the user's permissions
 * AND the allowed list of the workstation group (empty list = no restriction). Done on the server for every request,
 * for this request only (nothing is stored in the session), so a change of the workstation takes effect at once.
 *
 * Rule: a restriction never removes WORKSTATION_MANAGE from a SUPER_ADMIN, so administration of workstations can never
 * be locked out.
 */
class WorkstationFilter extends OncePerRequestFilter {

    static final String ATTRIBUTE = "wms.workstation";

    private final WorkstationResolver resolver;

    WorkstationFilter(WorkstationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof WmsUserPrincipal p && request.getRequestURI().startsWith("/api/")) {
            WorkstationContext ctx = resolver.resolve(request, p.siteId());
            request.setAttribute(ATTRIBUTE, ctx);
            if (ctx.restricts()) {
                WmsUserPrincipal restricted = restrict(p, ctx);
                List<SimpleGrantedAuthority> authorities = Stream.concat(
                                restricted.roles().stream().map(r -> "ROLE_" + r), restricted.permissions().stream())
                        .map(SimpleGrantedAuthority::new).toList();
                SecurityContext context = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(restricted, null, authorities));
                SecurityContextHolder.getContextHolderStrategy().setContext(context); // this request only
            }
        }
        chain.doFilter(request, response);
    }

    static WmsUserPrincipal restrict(WmsUserPrincipal p, WorkstationContext ctx) {
        Set<String> allowed = ctx.mode() == WorkstationContext.Mode.DISABLED ? Set.of() : ctx.restriction();
        List<String> kept = new ArrayList<>(p.permissions().stream().filter(allowed::contains).toList());
        boolean superAdmin = p.roles().contains(RoleAdminService.SUPER_ADMIN);
        if (superAdmin && p.permissions().contains(PermissionCatalogue.WORKSTATION_MANAGE)
                && !kept.contains(PermissionCatalogue.WORKSTATION_MANAGE)) {
            kept.add(PermissionCatalogue.WORKSTATION_MANAGE);
        }
        return new WmsUserPrincipal(p.userId(), p.siteId(), p.username(), p.displayName(), p.roles(), kept,
                p.mustChangePassword());
    }
}
