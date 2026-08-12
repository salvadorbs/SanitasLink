package com.sanitaslink.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Encryption-at-rest configuration for sensitive clinical fields. */
@ConfigurationProperties(prefix = "sanitaslink.security.encryption")
public class CryptoProperties {

  /** Base64-encoded 256-bit AES key. Required outside the {@code dev} profile. */
  private String key;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
}
