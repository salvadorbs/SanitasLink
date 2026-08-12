package com.sanitaslink.core.security;

import com.sanitaslink.core.config.LoginRateLimitProperties;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Simple in-memory fixed-window login rate limiter keyed by client address. Suitable for a
 * single-node deployment; replace with a distributed limiter when scaling out.
 */
@Component
public class LoginRateLimiter {

  private final LoginRateLimitProperties properties;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public LoginRateLimiter(LoginRateLimitProperties properties) {
    this.properties = properties;
  }

  /** Returns true when the key is allowed to attempt a login. */
  public boolean isAllowed(String key) {
    Instant now = Instant.now();
    Window window = windows.computeIfAbsent(key, k -> new Window(now));
    synchronized (window) {
      if (window.started.plus(properties.getWindow()).isBefore(now)) {
        window.started = now;
        window.count = 0;
      }
      if (window.count >= properties.getMaxRequests()) {
        return false;
      }
      window.count++;
      return true;
    }
  }

  public void reset(String key) {
    windows.remove(key);
  }

  private static final class Window {
    private Instant started;
    private int count;

    private Window(Instant started) {
      this.started = started;
    }
  }
}
