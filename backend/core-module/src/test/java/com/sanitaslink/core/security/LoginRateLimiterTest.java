package com.sanitaslink.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.sanitaslink.core.config.LoginRateLimitProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

  @Test
  void allowsUpToLimitThenBlocks() {
    LoginRateLimitProperties properties = new LoginRateLimitProperties();
    properties.setMaxRequests(3);
    properties.setWindow(Duration.ofMinutes(1));
    LoginRateLimiter limiter = new LoginRateLimiter(properties);

    assertThat(limiter.isAllowed("10.0.0.1")).isTrue();
    assertThat(limiter.isAllowed("10.0.0.1")).isTrue();
    assertThat(limiter.isAllowed("10.0.0.1")).isTrue();
    assertThat(limiter.isAllowed("10.0.0.1")).isFalse();

    // Different key is unaffected.
    assertThat(limiter.isAllowed("10.0.0.2")).isTrue();
  }

  @Test
  void resetClearsWindow() {
    LoginRateLimitProperties properties = new LoginRateLimitProperties();
    properties.setMaxRequests(1);
    properties.setWindow(Duration.ofMinutes(1));
    LoginRateLimiter limiter = new LoginRateLimiter(properties);

    assertThat(limiter.isAllowed("10.0.0.1")).isTrue();
    assertThat(limiter.isAllowed("10.0.0.1")).isFalse();
    limiter.reset("10.0.0.1");
    assertThat(limiter.isAllowed("10.0.0.1")).isTrue();
  }
}
