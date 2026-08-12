package com.sanitaslink.core.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Builds the initial principal from the JWT. The role claims are only used as a bootstrap: a
 * subsequent filter refreshes roles and permissions from the database on every request.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    String email = jwt.getClaimAsString("email");
    List<String> roles = jwt.getClaimAsStringList("roles");
    String officeIdClaim = jwt.getClaimAsString("office_id");
    UUID officeId = officeIdClaim == null ? null : UUID.fromString(officeIdClaim);
    boolean admin = roles != null && roles.stream().anyMatch("ADMIN"::equals);

    AuthenticatedUser principal = new AuthenticatedUser(userId, email, officeId, roles, admin);

    Collection<GrantedAuthority> authorities =
        roles == null
            ? List.of()
            : roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());

    return new UsernamePasswordAuthenticationToken(principal, jwt.getTokenValue(), authorities);
  }
}
