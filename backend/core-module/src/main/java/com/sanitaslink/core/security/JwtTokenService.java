package com.sanitaslink.core.security;

import com.sanitaslink.core.config.JwtProperties;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues and decodes HMAC-signed access tokens. */
@Component
public class JwtTokenService {

  private final JwtProperties properties;
  private final JwtEncoder encoder;
  private final JwtDecoder decoder;

  public JwtTokenService(JwtProperties properties, JwtEncoder encoder, JwtDecoder decoder) {
    this.properties = properties;
    this.encoder = encoder;
    this.decoder = decoder;
  }

  /**
   * Issues an access token. The {@code roles} and {@code permissions} claims are informational
   * only: the server re-resolves them from the database on every request.
   */
  public String issueAccessToken(
      AuthenticatedUser user, Collection<String> roles, Collection<String> permissions) {
    Instant now = Instant.now();
    JwtClaimsSet.Builder claims =
        JwtClaimsSet.builder()
            .issuer(properties.getIssuer())
            .subject(user.id().toString())
            .issuedAt(now)
            .expiresAt(now.plus(properties.getAccessTokenTtl()))
            .id(UUID.randomUUID().toString())
            .claim("email", user.email())
            .claim("roles", roles.stream().sorted().toList())
            .claim("permissions", permissions.stream().sorted().toList());
    if (user.officeId() != null) {
      claims.claim("office_id", user.officeId().toString());
    }
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }

  public Jwt decode(String token) {
    return decoder.decode(token);
  }
}
