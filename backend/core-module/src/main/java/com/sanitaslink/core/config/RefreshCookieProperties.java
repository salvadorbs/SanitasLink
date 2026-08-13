package com.sanitaslink.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the HttpOnly refresh cookie. The refresh token is never returned in the JSON
 * body; it travels only as a cookie scoped to the auth endpoints.
 */
@ConfigurationProperties(prefix = "sanitaslink.security.refresh-cookie")
public class RefreshCookieProperties {

  private String name = "sl_refresh";

  /** Whether the cookie requires HTTPS (always true outside local development). */
  private boolean secure = true;

  private String sameSite = "Strict";
  private String path = "/api/v1/auth";

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isSecure() {
    return secure;
  }

  public void setSecure(boolean secure) {
    this.secure = secure;
  }

  public String getSameSite() {
    return sameSite;
  }

  public void setSameSite(String sameSite) {
    this.sameSite = sameSite;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }
}
