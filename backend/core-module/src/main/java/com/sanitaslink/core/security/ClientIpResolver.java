package com.sanitaslink.core.security;

import com.sanitaslink.core.config.LoginRateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP consistently for rate limiting and audit metadata. Behind a reverse proxy
 * the direct peer address is the proxy itself, so every client would share a single rate-limit
 * bucket; when {@code sanitaslink.security.login-rate-limit.trusted-proxy} is enabled, the
 * left-most entry of {@code X-Forwarded-For} (the original client, appended by the proxy) is used
 * instead. The flag must only be enabled when the deployment is guaranteed to sit behind a proxy
 * that overwrites {@code X-Forwarded-For} and rejects client-supplied values.
 */
@Component
public class ClientIpResolver {

  private final LoginRateLimitProperties loginRateLimitProperties;

  public ClientIpResolver(LoginRateLimitProperties loginRateLimitProperties) {
    this.loginRateLimitProperties = loginRateLimitProperties;
  }

  public String clientIp(HttpServletRequest request) {
    if (loginRateLimitProperties.isTrustedProxy()) {
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
    }
    return request.getRemoteAddr();
  }
}
