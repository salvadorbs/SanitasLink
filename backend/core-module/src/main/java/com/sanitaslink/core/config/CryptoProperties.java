package com.sanitaslink.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Encryption-at-rest configuration for sensitive clinical fields. */
@ConfigurationProperties(prefix = "sanitaslink.security.encryption")
public class CryptoProperties {

  /** Base64-encoded 256-bit AES key. Required outside the {@code dev} profile. */
  private String key;

  /** Optional previous base64-encoded 256-bit AES key, used only to read data during rotation. */
  private String previousKey;

  /** Version identifier written into new ciphertext (default 1). */
  private int version = 1;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getPreviousKey() {
    return previousKey;
  }

  public void setPreviousKey(String previousKey) {
    this.previousKey = previousKey;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }
}
