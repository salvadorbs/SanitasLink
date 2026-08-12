package com.sanitaslink.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Login rate limiting configuration. */
@ConfigurationProperties(prefix = "sanitaslink.security.login-rate-limit")
public class LoginRateLimitProperties {

  private int maxRequests = 30;
  private Duration window = Duration.ofMinutes(1);
  private boolean trustedProxy = false;

  public int getMaxRequests() {
    return maxRequests;
  }

  public void setMaxRequests(int maxRequests) {
    this.maxRequests = maxRequests;
  }

  public Duration getWindow() {
    return window;
  }

  public void setWindow(Duration window) {
    this.window = window;
  }

  public boolean isTrustedProxy() {
    return trustedProxy;
  }

  public void setTrustedProxy(boolean trustedProxy) {
    this.trustedProxy = trustedProxy;
  }
}
