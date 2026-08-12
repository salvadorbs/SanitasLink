package com.sanitaslink.core.notification;

import java.time.Instant;

/** Outbound notification port. Implementations deliver email/SMS to the user. */
public interface NotificationPort {

  void sendInvitation(String email, String officeName, String token, Instant expiresAt);

  void sendPasswordReset(String email, String token, Instant expiresAt);
}
