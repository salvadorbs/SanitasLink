package com.sanitaslink.core.auth;

import com.sanitaslink.core.config.JwtProperties;
import com.sanitaslink.core.config.RefreshCookieProperties;
import java.util.Collection;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the HttpOnly refresh cookie. The raw refresh token is only ever written to this cookie and
 * is never exposed in JSON responses or logs.
 */
@Component
public class AuthCookies {

  private final RefreshCookieProperties properties;
  private final JwtProperties jwtProperties;

  public AuthCookies(RefreshCookieProperties properties, JwtProperties jwtProperties) {
    this.properties = properties;
    this.jwtProperties = jwtProperties;
  }

  /** Creates the refresh cookie carrying the new raw token. */
  public ResponseCookie refreshCookie(String rawRefreshToken) {
    return ResponseCookie.from(properties.getName(), rawRefreshToken)
        .httpOnly(true)
        .secure(properties.isSecure())
        .sameSite(properties.getSameSite())
        .path(properties.getPath())
        .maxAge(jwtProperties.getRefreshTokenTtl())
        .build();
  }

  /** Expires the refresh cookie, used on logout. */
  public ResponseCookie clearRefreshCookie() {
    return ResponseCookie.from(properties.getName(), "")
        .httpOnly(true)
        .secure(properties.isSecure())
        .sameSite(properties.getSameSite())
        .path(properties.getPath())
        .maxAge(0)
        .build();
  }

  /**
   * Serializes a response cookie to a Set-Cookie header value. {@code ResponseCookie.toString()}
   * intentionally omits HttpOnly/SameSite/Secure, so those attributes are appended manually.
   */
  public String setCookieHeader(ResponseCookie cookie) {
    StringBuilder value = new StringBuilder(cookie.toString());
    if (cookie.isHttpOnly()) {
      value.append("; HttpOnly");
    }
    if (cookie.getSameSite() != null) {
      value.append("; SameSite=").append(cookie.getSameSite());
    }
    if (cookie.isSecure()) {
      value.append("; Secure");
    }
    return value.toString();
  }

  /** Extracts the raw refresh token from the request cookies, or null when absent. */
  public String rawRefreshToken(Collection<HttpCookie> cookies) {
    if (cookies == null) {
      return null;
    }
    return cookies.stream()
        .filter(cookie -> properties.getName().equals(cookie.getName()))
        .map(HttpCookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
