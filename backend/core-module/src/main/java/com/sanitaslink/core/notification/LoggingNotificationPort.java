package com.sanitaslink.core.notification;

import com.sanitaslink.core.config.NotificationProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Development-only notification port that logs the one-time token so the flow can be exercised
 * without an email provider. The raw token is only logged when {@code
 * sanitaslink.notifications.log-secrets} is enabled (dev/test profiles).
 */
@Component
public class LoggingNotificationPort implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationPort.class);

  private final NotificationProperties properties;

  public LoggingNotificationPort(NotificationProperties properties) {
    this.properties = properties;
  }

  @Override
  public void sendInvitation(String email, String officeName, String token, Instant expiresAt) {
    log.info(
        "Invitation notification for {} to join '{}', expires {}", email, officeName, expiresAt);
    if (properties.isLogSecrets()) {
      log.info("Invitation token for {}: {}", email, token);
    }
  }

  @Override
  public void sendPasswordReset(String email, String token, Instant expiresAt) {
    log.info("Password reset notification for {}, expires {}", email, expiresAt);
    if (properties.isLogSecrets()) {
      log.info("Password reset token for {}: {}", email, token);
    }
  }
}
