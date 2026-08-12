package com.sanitaslink.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Notification configuration. */
@ConfigurationProperties(prefix = "sanitaslink.notifications")
public class NotificationProperties {

  /**
   * When true, the logging notification port prints the raw one-time tokens. Intended for
   * development and tests only; must stay false in production.
   */
  private boolean logSecrets = false;

  public boolean isLogSecrets() {
    return logSecrets;
  }

  public void setLogSecrets(boolean logSecrets) {
    this.logSecrets = logSecrets;
  }
}
