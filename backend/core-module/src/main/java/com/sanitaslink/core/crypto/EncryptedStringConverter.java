package com.sanitaslink.core.crypto;

import com.sanitaslink.core.config.CryptoProperties;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-GCM encryption-at-rest for sensitive string fields (health data, tax identifiers, notes).
 * Each value uses a fresh 96-bit random IV; the IV is prepended to the ciphertext and the whole
 * payload is stored Base64-encoded.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_LENGTH = 12;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final SecretKey key;

  public EncryptedStringConverter(CryptoProperties properties) {
    String encodedKey = properties.getKey();
    if (encodedKey == null || encodedKey.isBlank()) {
      throw new IllegalStateException(
          "sanitaslink.security.encryption.key must be configured (set SANITASLINK_ENCRYPTION_KEY)");
    }
    byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
    if (keyBytes.length != 32) {
      throw new IllegalStateException("sanitaslink.security.encryption.key must be a 32-byte key");
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
      byte[] combined = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to encrypt sensitive field", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(dbData);
      byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
      byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to decrypt sensitive field", e);
    }
  }
}
