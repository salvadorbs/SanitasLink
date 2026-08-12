package com.sanitaslink.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for one-time invitation and password-reset token lifetimes. */
@ConfigurationProperties(prefix = "sanitaslink.tokens")
public class TokenProperties {

  private Duration invitationTtl = Duration.ofHours(72);
  private Duration passwordResetTtl = Duration.ofHours(24);

  public Duration getInvitationTtl() {
    return invitationTtl;
  }

  public void setInvitationTtl(Duration invitationTtl) {
    this.invitationTtl = invitationTtl;
  }

  public Duration getPasswordResetTtl() {
    return passwordResetTtl;
  }

  public void setPasswordResetTtl(Duration passwordResetTtl) {
    this.passwordResetTtl = passwordResetTtl;
  }
}
