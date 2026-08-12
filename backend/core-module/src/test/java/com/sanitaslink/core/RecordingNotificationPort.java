package com.sanitaslink.core;

import com.sanitaslink.core.notification.NotificationPort;
import java.time.Instant;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Test notification port that records the raw one-time tokens so the acceptance and password reset
 * flows can be exercised end to end without a real email provider.
 */
@Component
@Primary
public class RecordingNotificationPort implements NotificationPort {

  private volatile String invitationToken;
  private volatile String invitationEmail;
  private volatile String resetToken;
  private volatile String resetEmail;

  @Override
  public synchronized void sendInvitation(
      String email, String officeName, String token, Instant expiresAt) {
    this.invitationToken = token;
    this.invitationEmail = email;
  }

  @Override
  public synchronized void sendPasswordReset(String email, String token, Instant expiresAt) {
    this.resetToken = token;
    this.resetEmail = email;
  }

  public synchronized String takeInvitationToken() {
    String token = invitationToken;
    invitationToken = null;
    return token;
  }

  public synchronized String lastInvitationEmail() {
    return invitationEmail;
  }

  public synchronized String takeResetToken() {
    String token = resetToken;
    resetToken = null;
    return token;
  }

  public synchronized String lastResetEmail() {
    return resetEmail;
  }
}
