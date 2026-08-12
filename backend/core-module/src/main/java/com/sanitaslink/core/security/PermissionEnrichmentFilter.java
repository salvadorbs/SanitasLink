package com.sanitaslink.core.security;

import com.sanitaslink.core.exception.ErrorCodes;
import com.sanitaslink.core.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Refreshes the authenticated user's authorities from the database on every request, so role and
 * permission changes take effect immediately. Also verifies that the user still has an active
 * membership (or a platform role) and publishes the tenant context for the request.
 */
public class PermissionEnrichmentFilter extends OncePerRequestFilter {

  private final PermissionResolver permissionResolver;
  private final ObjectMapper objectMapper;

  public PermissionEnrichmentFilter(
      PermissionResolver permissionResolver, ObjectMapper objectMapper) {
    this.permissionResolver = permissionResolver;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof UsernamePasswordAuthenticationToken token
          && token.getPrincipal() instanceof AuthenticatedUser principal) {

        PermissionResolver.Resolution resolution = permissionResolver.resolve(principal.id());

        if (!resolution.active()) {
          writeForbidden(response);
          return;
        }

        AuthenticatedUser refreshed =
            new AuthenticatedUser(
                resolution.userId(),
                resolution.email(),
                resolution.officeId(),
                resolution.roles(),
                resolution.admin());
        permissionResolver.toContext(resolution);

        List<GrantedAuthority> authorities = new ArrayList<>();
        resolution
            .roles()
            .forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        resolution.permissions().forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

        UsernamePasswordAuthenticationToken enriched =
            new UsernamePasswordAuthenticationToken(refreshed, token.getCredentials(), authorities);
        SecurityContextHolder.getContext().setAuthentication(enriched);
      }
      filterChain.doFilter(request, response);
    } finally {
      TenantContextHolder.clear();
    }
  }

  private void writeForbidden(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.FORBIDDEN.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    Map<String, Object> body =
        Map.of(
            "type",
            "about:blank",
            "title",
            ErrorCodes.ACCOUNT_DISABLED,
            "status",
            HttpStatus.FORBIDDEN.value(),
            "detail",
            "Your account is no longer active for this office",
            "timestamp",
            Instant.now().toString());
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
