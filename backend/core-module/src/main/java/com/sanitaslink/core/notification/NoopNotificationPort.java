package com.sanitaslink.core.notification;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Default notification port for non-development environments. It intentionally does not log or
 * persist any one-time token; a real provider (email/SMS) is wired here for production.
 */
@Component
@Profile("!dev")
public class NoopNotificationPort implements NotificationPort {

  private static final Logger log = LoggerFactory.getLogger(NoopNotificationPort.class);

  @Override
  public void sendInvitation(String email, String officeName, String token, Instant expiresAt) {
    log.info("Invitation notification queued for {}", email);
  }

  @Override
  public void sendPasswordReset(String email, String token, Instant expiresAt) {
    log.info("Password reset notification queued for {}", email);
  }
}
